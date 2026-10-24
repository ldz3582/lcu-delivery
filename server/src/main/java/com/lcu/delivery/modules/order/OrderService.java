package com.lcu.delivery.modules.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lcu.delivery.common.BizException;
import com.lcu.delivery.common.Crypto;
import com.lcu.delivery.common.ErrorCode;
import com.lcu.delivery.common.PageResult;
import com.lcu.delivery.config.AppProperties;
import com.lcu.delivery.modules.admin.SysConfigService;
import com.lcu.delivery.modules.cart.CartService;
import com.lcu.delivery.modules.coupon.CouponService;
import com.lcu.delivery.modules.delivery.entity.Rider;
import com.lcu.delivery.modules.delivery.entity.RiderLocation;
import com.lcu.delivery.modules.delivery.mapper.RiderLocationMapper;
import com.lcu.delivery.modules.delivery.mapper.RiderMapper;
import com.lcu.delivery.modules.fence.FenceService;
import com.lcu.delivery.modules.notify.NotifyService;
import com.lcu.delivery.modules.user.IdentityService;
import com.lcu.delivery.modules.order.entity.Order;
import com.lcu.delivery.modules.order.entity.OrderItem;
import com.lcu.delivery.modules.order.entity.OrderStatusLog;
import com.lcu.delivery.modules.order.mapper.OrderItemMapper;
import com.lcu.delivery.modules.order.mapper.OrderMapper;
import com.lcu.delivery.modules.order.mapper.OrderStatusLogMapper;
import com.lcu.delivery.modules.settle.SettlementService;
import com.lcu.delivery.modules.shop.ShopService;
import com.lcu.delivery.modules.shop.entity.Product;
import com.lcu.delivery.modules.shop.entity.Shop;
import com.lcu.delivery.modules.shop.mapper.ProductMapper;
import com.lcu.delivery.modules.user.AddressService;
import com.lcu.delivery.modules.user.entity.UserAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private static final DateTimeFormatter NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final OrderMapper orderMapper;
    private final OrderItemMapper itemMapper;
    private final OrderStatusLogMapper logMapper;
    private final ProductMapper productMapper;
    private final CartService cartService;
    private final AddressService addressService;
    private final FenceService fenceService;
    private final IdentityService identityService;
    private final ShopService shopService;
    private final SysConfigService config;
    private final AppProperties app;
    private final SettlementService settlementService;
    private final RiderMapper riderMapper;
    private final RiderLocationMapper riderLocationMapper;
    private final Crypto crypto;
    private final CouponService couponService;
    private final JdbcTemplate jdbc;
    private final NotifyService notifyService;
    /** 必须注册 JavaTimeModule：地址快照里有 LocalDateTime，否则序列化抛异常会退化成 "{}"。 */
    private final ObjectMapper json = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /** 用户端追踪的六个节点，顺序即展示顺序。 */
    private static final int[] STEP_STATUS = {OrderStatus.UNPAID, OrderStatus.WAIT_ACCEPT, OrderStatus.COOKING,
            OrderStatus.WAIT_PICKUP, OrderStatus.DELIVERING, OrderStatus.FINISHED};
    private static final String[] STEP_KEYS = {"UNPAID", "WAIT_ACCEPT", "COOKING", "WAIT_PICKUP", "DELIVERING", "FINISHED"};
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    public OrderService(OrderMapper orderMapper, OrderItemMapper itemMapper, OrderStatusLogMapper logMapper,
                        ProductMapper productMapper, CartService cartService, AddressService addressService,
                        FenceService fenceService, IdentityService identityService, ShopService shopService, SysConfigService config,
                        AppProperties app, SettlementService settlementService,
                        RiderMapper riderMapper, RiderLocationMapper riderLocationMapper, Crypto crypto,
                        CouponService couponService, NotifyService notifyService, JdbcTemplate jdbc) {
        this.orderMapper = orderMapper;
        this.itemMapper = itemMapper;
        this.logMapper = logMapper;
        this.productMapper = productMapper;
        this.cartService = cartService;
        this.addressService = addressService;
        this.fenceService = fenceService;
        this.identityService = identityService;
        this.shopService = shopService;
        this.config = config;
        this.app = app;
        this.settlementService = settlementService;
        this.riderMapper = riderMapper;
        this.riderLocationMapper = riderLocationMapper;
        this.crypto = crypto;
        this.couponService = couponService;
        this.notifyService = notifyService;
        this.jdbc = jdbc;
    }

    public record CreateReq(Long shopId, Long addressId, List<CartService.Item> items,
                            String remark, LocalDateTime expectTime, Long userCouponId) {
    }

    @Transactional
    public Map<String, Object> create(Long userId, CreateReq req) {
        if (req.shopId() == null || req.addressId() == null) {
            throw new BizException(ErrorCode.PARAM, "缺少店铺或收货地址");
        }
        identityService.requireVerified(userId);
        Shop shop = shopService.require(req.shopId());
        CartService.Bill bill = cartService.calculate(userId, req.shopId(), req.items(), req.userCouponId());
        if (!bill.reachMin()) {
            throw new BizException(ErrorCode.MIN_AMOUNT, "还差 " + money(bill.minAmount() - bill.goodsAmount()) + " 元起送");
        }

        UserAddress address = addressService.get(userId, req.addressId());
        if (address.getLng() == null || address.getLat() == null) {
            throw new BizException(ErrorCode.PARAM, "请在地图上确认收货位置后再下单");
        }
        if (app.fenceEnforce() && !fenceService.inside(address.getLng(), address.getLat())) {
            throw new BizException(ErrorCode.OUT_OF_FENCE, "收货地址超出聊大校园配送范围，暂不支持配送");
        }

        // 扣库存：条件更新，防超卖（有规格按规格扣，没规格按商品扣）
        for (CartService.Line line : bill.lines()) {
            Product p = line.product();
            if (line.skuId() != null) {
                int skuRows = jdbc.update("UPDATE product_sku SET stock = stock - ? WHERE id = ? AND (stock = -1 OR stock >= ?)",
                        line.qty(), line.skuId(), line.qty());
                if (skuRows == 0) {
                    throw new BizException(ErrorCode.STOCK, p.getName() + " 规格库存不足");
                }
                continue;
            }
            if (p.getStock() != null && p.getStock() >= 0) {
                int rows = productMapper.update(null, Wrappers.<Product>lambdaUpdate()
                        .eq(Product::getId, p.getId())
                        .ge(Product::getStock, line.qty())
                        .setSql("stock = stock - " + line.qty()));
                if (rows == 0) {
                    throw new BizException(ErrorCode.STOCK, p.getName() + " 库存不足");
                }
            }
        }

        int commission = BigDecimal.valueOf(bill.goodsAmount())
                .multiply(shop.getCommissionRate() == null ? new BigDecimal("0.06") : shop.getCommissionRate())
                .setScale(0, RoundingMode.HALF_UP).intValue();
        int riderIncome = bill.deliveryFee();
        int shopIncome = bill.goodsAmount() + bill.packFee() - commission;

        LocalDateTime now = LocalDateTime.now();
        Order order = new Order();
        order.setOrderNo(now.format(NO_FMT) + String.format("%06d", (int) (Math.random() * 1_000_000)));
        order.setUserId(userId);
        order.setShopId(shop.getId());
        order.setStatus(OrderStatus.UNPAID);
        order.setCampus(address.getCampus());
        order.setAddressJson(toJson(addressSnapshot(address)));
        order.setLng(address.getLng());
        order.setLat(address.getLat());
        order.setGoodsAmount(bill.goodsAmount());
        order.setPackFee(bill.packFee());
        order.setDeliveryFee(bill.deliveryFee());
        order.setDiscountAmount(bill.discountAmount());
        order.setCouponId(bill.couponId());
        order.setPayAmount(bill.totalAmount());
        order.setCommission(commission);
        order.setRiderIncome(riderIncome);
        order.setShopIncome(shopIncome);
        order.setRemark(req.remark());
        order.setExpectTime(req.expectTime());
        order.setPayExpireAt(now.plusMinutes(config.getInt("order.pay_timeout_minutes", 15)));
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        orderMapper.insert(order);

        for (CartService.Line line : bill.lines()) {
            OrderItem item = new OrderItem();
            item.setOrderId(order.getId());
            item.setProductId(line.product().getId());
            item.setSkuId(line.skuId());
            String skuName = line.skuName();
            String itemName = line.product().getName();
            if (skuName != null && !skuName.isBlank() && !itemName.contains(skuName)) {
                itemName = itemName + "（" + skuName + "）";
            }
            item.setName(itemName);
            item.setImage(line.product().getImage());
            item.setPrice(line.price());
            item.setQty(line.qty());
            item.setSubtotal(line.subtotal());
            itemMapper.insert(item);
        }
        log(order.getId(), -1, OrderStatus.UNPAID, 1, userId, "用户下单");
        // 用券核销放在订单落库之后：条件更新，重复提交只会成功一次
        couponService.consume(bill.userCouponId(), order.getId());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderId", order.getId());
        data.put("orderNo", order.getOrderNo());
        data.put("payAmount", order.getPayAmount());
        data.put("status", order.getStatus());
        data.put("payExpireAt", order.getPayExpireAt());
        return data;
    }

    public PageResult<Map<String, Object>> list(Long userId, Integer status, int page, int size) {
        LambdaQueryWrapper<Order> qw = Wrappers.<Order>lambdaQuery()
                .eq(Order::getUserId, userId)
                .eq(status != null, Order::getStatus, status)
                .orderByDesc(Order::getId);
        Page<Order> p = orderMapper.selectPage(new Page<>(page, Math.min(size, 50)), qw);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Order o : p.getRecords()) {
            rows.add(toVo(o, false));
        }
        return new PageResult<>(rows, p.getTotal(), page, size);
    }

    public Map<String, Object> detail(Long userId, Long orderId) {
        Order order = requireOwned(userId, orderId);
        Map<String, Object> vo = toVo(order, true);
        List<OrderItem> items = itemMapper.selectList(Wrappers.<OrderItem>lambdaQuery()
                .eq(OrderItem::getOrderId, orderId));
        vo.put("items", items);
        Shop shop = shopService.require(order.getShopId());
        vo.put("shopName", shop.getName());
        vo.put("shopPhone", shop.getContactPhone());
        return vo;
    }

    /**
     * 用户端订单追踪：六个节点的时间轴 + 状态流水 + 骑手与最新位置。
     * 轮询友好（字段小、无 join 大表），前端在 10/20/30/40 阶段每 5 秒拉一次。
     */
    public Map<String, Object> track(Long userId, Long orderId) {
        Order order = requireOwned(userId, orderId);
        List<OrderStatusLog> logs = logMapper.selectList(Wrappers.<OrderStatusLog>lambdaQuery()
                .eq(OrderStatusLog::getOrderId, orderId)
                .orderByAsc(OrderStatusLog::getId));

        LocalDateTime[] reachedAt = new LocalDateTime[STEP_STATUS.length];
        reachedAt[0] = order.getCreatedAt();
        for (OrderStatusLog l : logs) {
            if (l.getToStatus() == null) {
                continue;
            }
            for (int i = 0; i < STEP_STATUS.length; i++) {
                if (STEP_STATUS[i] == l.getToStatus() && reachedAt[i] == null) {
                    reachedAt[i] = l.getCreatedAt();
                }
            }
        }

        List<Map<String, Object>> steps = new ArrayList<>();
        for (int i = 0; i < STEP_STATUS.length; i++) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", STEP_KEYS[i]);
            m.put("name", OrderStatus.text(STEP_STATUS[i]));
            m.put("status", STEP_STATUS[i]);
            m.put("done", reachedAt[i] != null);
            m.put("current", order.getStatus() != null && order.getStatus() == STEP_STATUS[i]);
            m.put("time", reachedAt[i] == null ? null : reachedAt[i].format(TS_FMT));
            steps.add(m);
        }

        List<Map<String, Object>> timeline = new ArrayList<>();
        for (OrderStatusLog l : logs) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("status", l.getToStatus());
            m.put("statusText", l.getToStatus() == null ? "" : OrderStatus.text(l.getToStatus()));
            m.put("remark", l.getRemark());
            m.put("operatorType", l.getOperatorType());
            m.put("time", l.getCreatedAt() == null ? null : l.getCreatedAt().format(TS_FMT));
            timeline.add(m);
        }

        Map<String, Object> rider = null;
        Long riderId = order.getRiderId();
        if (riderId != null) {
            Rider r = riderMapper.selectById(riderId);
            if (r != null) {
                rider = new LinkedHashMap<>();
                rider.put("id", r.getId());
                rider.put("name", riderName(r));
                rider.put("phone", r.getPhoneMask());
                rider.put("score", r.getScore());
            }
        }

        RiderLocation loc = riderLocationMapper.selectOne(Wrappers.<RiderLocation>lambdaQuery()
                .eq(RiderLocation::getOrderId, orderId)
                .orderByDesc(RiderLocation::getId)
                .last("limit 1"));
        if (loc == null && riderId != null) {
            loc = riderLocationMapper.selectOne(Wrappers.<RiderLocation>lambdaQuery()
                    .eq(RiderLocation::getRiderId, riderId)
                    .orderByDesc(RiderLocation::getId)
                    .last("limit 1"));
        }

        Map<String, Object> location = null;
        if (loc != null) {
            location = new LinkedHashMap<>();
            location.put("lng", loc.getLng());
            location.put("lat", loc.getLat());
            location.put("accuracy", loc.getAccuracy());
            location.put("updatedAt", loc.getReportedAt() == null ? null : loc.getReportedAt().format(TS_FMT));
        }

        Shop shop = shopService.require(order.getShopId());
        Map<String, Object> shopVo = new LinkedHashMap<>();
        shopVo.put("id", shop.getId());
        shopVo.put("name", shop.getName());
        shopVo.put("lng", shop.getLng());
        shopVo.put("lat", shop.getLat());

        Map<String, Object> destination = new LinkedHashMap<>();
        destination.put("lng", order.getLng());
        destination.put("lat", order.getLat());
        destination.put("text", addressText(order.getAddressJson()));

        Integer eta = null;
        int st = order.getStatus() == null ? -1 : order.getStatus();
        if (st >= OrderStatus.WAIT_ACCEPT && st <= OrderStatus.DELIVERING) {
            if (loc != null && order.getLng() != null && order.getLat() != null) {
                double meters = ShopService.distance(loc.getLng().doubleValue(), loc.getLat().doubleValue(),
                        order.getLng().doubleValue(), order.getLat().doubleValue());
                eta = (int) Math.max(1, Math.ceil(meters / 250.0));
            } else {
                eta = config.getInt("delivery.eta_minutes", 25);
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderId", order.getId());
        data.put("orderNo", order.getOrderNo());
        data.put("status", order.getStatus());
        data.put("statusText", OrderStatus.text(st));
        data.put("etaMinutes", eta);
        data.put("payExpireAt", order.getPayExpireAt());
        data.put("steps", steps);
        data.put("timeline", timeline);
        data.put("rider", rider);
        data.put("location", location);
        data.put("shop", shopVo);
        data.put("destination", destination);
        return data;
    }

    private String riderName(Rider r) {
        String plain = crypto.decrypt(r.getRealNameEnc());
        if (plain == null || plain.isBlank()) {
            plain = r.getRealNameEnc();
        }
        if (plain == null || plain.isBlank() || plain.length() > 12) {
            return "骑手" + r.getId();
        }
        return plain;
    }

    /** 只存订单要用的字段，不要把 userId / deletedAt 这类和配送无关的东西写进快照。 */
    private Map<String, Object> addressSnapshot(UserAddress a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("campus", a.getCampus());
        m.put("detail", a.getDetail());
        m.put("building", a.getBuilding());
        m.put("floor", a.getFloor());
        m.put("room", a.getRoom());
        m.put("remark", a.getRemark());
        m.put("lng", a.getLng());
        m.put("lat", a.getLat());
        return m;
    }

    @SuppressWarnings("unchecked")
    private String addressText(String addressJson) {
        if (addressJson == null || addressJson.isBlank()) {
            return "";
        }
        try {
            Map<String, Object> a = json.readValue(addressJson, Map.class);
            String campus = switch (String.valueOf(a.get("campus"))) {
                case "1" -> "东校区";
                case "2" -> "西校区";
                default -> "";
            };
            StringBuilder sb = new StringBuilder();
            String detail = a.get("detail") == null ? "" : String.valueOf(a.get("detail"));
            // 地址原文里常常已经写了校区，别拼成「东校区 · 东校区 2号楼」
            if (!campus.isEmpty() && !detail.contains(campus)) {
                sb.append(campus).append(" · ");
            }
            if (!detail.isBlank()) {
                sb.append(detail).append(' ');
            }
            for (String key : new String[]{"building", "floor", "room"}) {
                Object v = a.get(key);
                if (v != null && !String.valueOf(v).isBlank() && !detail.contains(String.valueOf(v))) {
                    sb.append(v).append(' ');
                }
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return "";
        }
    }

    public Order requireOwned(Long userId, Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null || !userId.equals(order.getUserId())) {
            throw new BizException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        return order;
    }

    public Order require(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        return order;
    }

    @Transactional
    public void cancel(Long userId, Long orderId, String reason) {
        Order order = requireOwned(userId, orderId);
        if (order.getStatus() != OrderStatus.UNPAID && order.getStatus() != OrderStatus.WAIT_ACCEPT) {
            throw new BizException(ErrorCode.ORDER_STATUS, "当前状态不可取消");
        }
        int from = order.getStatus();
        int to = order.getStatus() == OrderStatus.UNPAID ? OrderStatus.CANCELED : OrderStatus.REFUNDED;
        restoreStock(orderId);
        couponService.restoreByOrder(orderId);
        updateStatus(order, to, "用户取消：" + (reason == null ? "无" : reason));
        log(orderId, from, to, 1, userId, "用户取消订单");
    }

    @Transactional
    public void confirmReceive(Long userId, Long orderId) {
        Order order = requireOwned(userId, orderId);
        if (order.getStatus() != OrderStatus.DELIVERING) {
            throw new BizException(ErrorCode.ORDER_STATUS, "当前状态不可确认收货");
        }
        updateStatus(order, OrderStatus.FINISHED, "用户确认收货");
        order.setFinishedAt(LocalDateTime.now());
        orderMapper.updateById(order);
        settlementService.createForOrder(order);
    }

    // ---------- 商家侧 ----------

    @Transactional
    public void merchantAccept(Long orderId, Long shopId) {
        Order order = requireShopOrder(orderId, shopId);
        expectStatus(order, OrderStatus.WAIT_ACCEPT);
        order.setAcceptedAt(LocalDateTime.now());
        updateStatus(order, OrderStatus.COOKING, "商家接单");
    }

    @Transactional
    public void merchantReject(Long orderId, Long shopId, String reason) {
        Order order = requireShopOrder(orderId, shopId);
        expectStatus(order, OrderStatus.WAIT_ACCEPT);
        restoreStock(orderId);
        couponService.restoreByOrder(orderId);
        updateStatus(order, OrderStatus.REFUNDED, "商家拒单：" + (reason == null ? "无" : reason));
    }

    @Transactional
    public void merchantReady(Long orderId, Long shopId) {
        Order order = requireShopOrder(orderId, shopId);
        expectStatus(order, OrderStatus.COOKING);
        order.setReadyAt(LocalDateTime.now());
        updateStatus(order, OrderStatus.WAIT_PICKUP, "商家出餐");
    }

    private Order requireShopOrder(Long orderId, Long shopId) {
        Order order = require(orderId);
        if (!shopId.equals(order.getShopId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权操作该订单");
        }
        return order;
    }

    // ---------- 骑手侧 ----------

    @Transactional
    public void riderGrab(Long orderId, Long riderId) {
        Order order = require(orderId);
        expectStatus(order, OrderStatus.WAIT_PICKUP);
        int rows = orderMapper.update(null, Wrappers.<Order>lambdaUpdate()
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, OrderStatus.WAIT_PICKUP)
                .isNull(Order::getRiderId)
                .set(Order::getRiderId, riderId)
                .set(Order::getUpdatedAt, LocalDateTime.now()));
        if (rows == 0) {
            throw new BizException(ErrorCode.ORDER_STATUS, "手慢了，这单已被抢走");
        }
        log(orderId, OrderStatus.WAIT_PICKUP, OrderStatus.WAIT_PICKUP, 3, riderId, "骑手接单");
    }

    @Transactional
    public void riderPickup(Long orderId, Long riderId) {
        Order order = requireRiderOrder(orderId, riderId);
        expectStatus(order, OrderStatus.WAIT_PICKUP);
        order.setPickedAt(LocalDateTime.now());
        updateStatus(order, OrderStatus.DELIVERING, "骑手取餐");
    }

    @Transactional
    public void riderDeliver(Long orderId, Long riderId) {
        Order order = requireRiderOrder(orderId, riderId);
        expectStatus(order, OrderStatus.DELIVERING);
        order.setDeliveredAt(LocalDateTime.now());
        order.setFinishedAt(LocalDateTime.now());
        updateStatus(order, OrderStatus.FINISHED, "骑手送达");
        settlementService.createForOrder(order);
    }

    public Order requireRiderOrder(Long orderId, Long riderId) {
        Order order = require(orderId);
        if (!riderId.equals(order.getRiderId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "不是你的订单");
        }
        return order;
    }

    // ---------- 内部 ----------

    public void updateStatus(Order order, int to, String remark) {
        int from = order.getStatus();
        order.setStatus(to);
        order.setUpdatedAt(LocalDateTime.now());
        orderMapper.updateById(order);
        log(order.getId(), from, to, 4, null, remark);
        // 订阅消息：失败只记 notify_log，绝不影响订单主流程
        try {
            notifyService.notifyOrderStatus(order.getUserId(), order.getOrderNo(), order.getId(),
                    OrderStatus.text(to), remark);
        } catch (Exception e) {
            log.debug("订单状态订阅消息发送跳过：{}", e.getMessage());
        }
    }

    public void log(Long orderId, int from, int to, int operatorType, Long operatorId, String remark) {
        OrderStatusLog l = new OrderStatusLog();
        l.setOrderId(orderId);
        l.setFromStatus(from);
        l.setToStatus(to);
        l.setOperatorType(operatorType);
        l.setOperatorId(operatorId);
        l.setRemark(remark);
        l.setCreatedAt(LocalDateTime.now());
        logMapper.insert(l);
    }

    private void expectStatus(Order order, int expect) {
        if (order.getStatus() != expect) {
            throw new BizException(ErrorCode.ORDER_STATUS,
                    "订单状态已变化（当前：" + OrderStatus.text(order.getStatus()) + "）");
        }
    }

    public void restoreStock(Long orderId) {
        List<OrderItem> items = itemMapper.selectList(Wrappers.<OrderItem>lambdaQuery()
                .eq(OrderItem::getOrderId, orderId));
        for (OrderItem item : items) {
            if (item.getSkuId() != null) {
                jdbc.update("UPDATE product_sku SET stock = stock + ? WHERE id = ? AND stock >= 0",
                        item.getQty(), item.getSkuId());
                continue;
            }
            productMapper.update(null, Wrappers.<Product>lambdaUpdate()
                    .eq(Product::getId, item.getProductId())
                    .gt(Product::getStock, 0)
                    .setSql("stock = stock + " + item.getQty()));
        }
    }

    private Map<String, Object> toVo(Order o, boolean full) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", o.getId());
        m.put("orderNo", o.getOrderNo());
        m.put("shopId", o.getShopId());
        m.put("status", o.getStatus());
        m.put("statusText", OrderStatus.text(o.getStatus()));
        m.put("payAmount", o.getPayAmount());
        m.put("goodsAmount", o.getGoodsAmount());
        m.put("packFee", o.getPackFee());
        m.put("deliveryFee", o.getDeliveryFee());
        m.put("discountAmount", o.getDiscountAmount());
        m.put("couponId", o.getCouponId());
        m.put("createdAt", o.getCreatedAt());
        if (full) {
            m.put("address", o.getAddressJson());
            m.put("remark", o.getRemark());
            m.put("riderId", o.getRiderId());
            m.put("paidAt", o.getPaidAt());
            m.put("deliveredAt", o.getDeliveredAt());
        }
        return m;
    }

    private String toJson(Object obj) {
        try {
            return json.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static String money(int fen) {
        return BigDecimal.valueOf(fen).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP).toPlainString();
    }
}
