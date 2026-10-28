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
import com.lcu.delivery.modules.pay.PayService;
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
import org.springframework.context.annotation.Lazy;
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
    /**
     * 取消/拒单需要真正退款，而 PayService 又依赖本类，故用 @Lazy 打破循环依赖。
     */
    private final PayService payService;
    /** 异常分支自动建售后工单（与 AfterSaleService 互相依赖，同样用 @Lazy） */
    private final com.lcu.delivery.modules.aftersale.AfterSaleService afterSaleService;
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
                        CouponService couponService, NotifyService notifyService, JdbcTemplate jdbc,
                        @Lazy PayService payService,
                        @Lazy com.lcu.delivery.modules.aftersale.AfterSaleService afterSaleService) {
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
        this.payService = payService;
        this.afterSaleService = afterSaleService;
    }

    public record CreateReq(Long shopId, Long addressId, List<CartService.Item> items,
                            String remark, LocalDateTime expectTime, Long userCouponId,
                            /** 前端生成的幂等键：同一次结算重复提交（连点/重试）只会建一单 */
                            String clientToken) {
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Map<String, Object> create(Long userId, CreateReq req) {
        // 幂等：带 clientToken 的重复提交直接返回第一单，避免双击/重试建出两张单
        String token = req.clientToken() == null ? null : req.clientToken().trim();
        if (token != null && !token.isEmpty()) {
            if (token.length() > 64) {
                throw new BizException(ErrorCode.PARAM, "clientToken 过长");
            }
            Order exists = orderMapper.selectOne(Wrappers.<Order>lambdaQuery()
                    .eq(Order::getUserId, userId).eq(Order::getClientToken, token).last("limit 1"));
            if (exists != null) {
                log.info("重复提交命中幂等键 {}，返回已有订单 {}", token, exists.getId());
                Map<String, Object> data = createdData(exists);
                data.put("repeated", true);
                return data;
            }
        }
        if (req.shopId() == null || req.addressId() == null) {
            throw new BizException(ErrorCode.PARAM, "缺少店铺或收货地址");
        }
        identityService.requireVerified(userId);
        // 被封禁的用户不能下单（后台「封禁违规用户」）
        Integer userStatus = jdbc.queryForObject("SELECT status FROM `user` WHERE id = ?", Integer.class, userId);
        if (userStatus != null && userStatus == 0) {
            throw new BizException(ErrorCode.FORBIDDEN, "账号已被封禁，如有疑问请联系平台客服");
        }
        Shop shop = shopService.require(req.shopId());
        CartService.Bill bill = cartService.calculate(userId, req.shopId(), req.items(), req.userCouponId());
        if (!bill.reachMin()) {
            throw new BizException(ErrorCode.MIN_AMOUNT, "还差 " + money(bill.minAmount() - bill.goodsAmount()) + " 元起送");
        }

        UserAddress address = addressService.get(userId, req.addressId());
        if (address.getLng() == null || address.getLat() == null) {
            throw new BizException(ErrorCode.PARAM, "请在地图上确认收货位置后再下单");
        }
        if (fenceEnforced() && !fenceService.inside(address.getLng(), address.getLat())) {
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
        // 库存扣到 0 的菜品自动下架（需求 4.2.2「库存不足自动下架」）
        offlineSoldOut(bill.lines());

        int commission = BigDecimal.valueOf(bill.goodsAmount())
                .multiply(shop.getCommissionRate() == null ? new BigDecimal("0.06") : shop.getCommissionRate())
                .setScale(0, RoundingMode.HALF_UP).intValue();
        // 骑手佣金按规则计算（基础 + 距离 + 高峰），不再直接等于用户支付的配送费
        int distanceMeters = 0;
        if (shop.getLng() != null && shop.getLat() != null
                && address.getLng() != null && address.getLat() != null) {
            double m = ShopService.distance(shop.getLng().doubleValue(), shop.getLat().doubleValue(),
                    address.getLng().doubleValue(), address.getLat().doubleValue());
            distanceMeters = (int) Math.round(m);
        }
        int riderIncome = riderPay(bill.deliveryFee(), distanceMeters, LocalDateTime.now());
        // 商家自设的配送范围也要校验（需求 4.2.1「设置配送范围」），null 表示跟随平台围栏
        Integer shopRange = shop.getDeliveryRangeMeters();
        if (shopRange != null && shopRange > 0 && distanceMeters > 0 && distanceMeters > shopRange) {
            throw new BizException(ErrorCode.OUT_OF_FENCE,
                    "超出该商家的配送范围（" + shopRange + " 米，当前约 " + distanceMeters + " 米），请选择更近的商家");
        }
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
        order.setClientToken(token == null || token.isEmpty() ? null : token);
        order.setPayExpireAt(now.plusMinutes(config.getInt("order.pay_timeout_minutes", 15)));
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        try {
            orderMapper.insert(order);
        } catch (org.springframework.dao.DuplicateKeyException dup) {
            // 并发同时提交：唯一索引兜底。赢家事务可能还没提交，短暂重试直到能读到那一单
            for (int i = 0; i < 10; i++) {
                Order exists = orderMapper.selectOne(Wrappers.<Order>lambdaQuery()
                        .eq(Order::getUserId, userId).eq(Order::getClientToken, order.getClientToken())
                        .last("limit 1"));
                if (exists != null) {
                    log.info("并发重复提交命中唯一索引，返回已有订单 {}", exists.getId());
                    Map<String, Object> d = createdData(exists);
                    d.put("repeated", true);
                    return d;
                }
                try {
                    Thread.sleep(150);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            throw new BizException(ErrorCode.ORDER_STATUS, "这一单正在提交中，请勿重复下单，稍后在订单列表查看");
        }

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

        return createdData(order);
    }

    /** 下单返回体：正常建单与幂等命中都用它，保证前端拿到的字段一致。 */
    private Map<String, Object> createdData(Order order) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderId", order.getId());
        data.put("orderNo", order.getOrderNo());
        data.put("payAmount", order.getPayAmount());
        data.put("status", order.getStatus());
        data.put("payExpireAt", order.getPayExpireAt());
        data.put("repeated", false);
        return data;
    }

    /**
     * 我的订单列表。
     *
     * @param status   单状态过滤（兼容老调用）
     * @param statuses 多状态过滤，如 "70,80"（售后 tab 用，避免前端只取第一页再本地过滤导致漏单）
     * @param reviewed 仅对已完成订单有意义：0 未评价 / 1 已评价（null 不过滤）
     */
    public PageResult<Map<String, Object>> list(Long userId, Integer status, String statuses,
                                                Integer reviewed, int page, int size) {
        List<Integer> sts = new ArrayList<>();
        if (statuses != null && !statuses.isBlank()) {
            for (String s : statuses.split(",")) {
                String t = s.trim();
                if (t.isEmpty()) {
                    continue;
                }
                try {
                    sts.add(Integer.parseInt(t));
                } catch (NumberFormatException ignored) {
                    // 非法值忽略
                }
            }
        }
        LambdaQueryWrapper<Order> qw = Wrappers.<Order>lambdaQuery()
                .eq(Order::getUserId, userId)
                .eq(status != null, Order::getStatus, status)
                .in(!sts.isEmpty(), Order::getStatus, sts)
                .orderByDesc(Order::getId);
        if (reviewed != null) {
            // review 表对 order_id 唯一，用 exists 过滤，分页 total 才准
            qw.apply(reviewed == 1
                    ? "exists (select 1 from review r where r.order_id = orders.id)"
                    : "not exists (select 1 from review r where r.order_id = orders.id)");
        }
        Page<Order> p = orderMapper.selectPage(new Page<>(page, Math.min(size, 50)), qw);
        List<Map<String, Object>> rows = new ArrayList<>();
        List<Long> ids = new ArrayList<>();
        for (Order o : p.getRecords()) {
            ids.add(o.getId());
        }
        java.util.Set<Long> reviewedIds = new java.util.HashSet<>();
        if (!ids.isEmpty()) {
            String in = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
            reviewedIds.addAll(jdbc.queryForList(
                    "SELECT order_id FROM review WHERE order_id IN (" + in + ")", Long.class, ids.toArray()));
        }
        for (Order o : p.getRecords()) {
            Map<String, Object> vo = toVo(o, false);
            vo.put("reviewed", reviewedIds.contains(o.getId()));
            rows.add(vo);
        }
        // 回显实际生效的页码/页大小（查询时 size 被钳到 50），避免前端拿 100000 这种值去算分页
        int effSize = Math.min(size, 50);
        int effPage = Math.max(page, 1);
        return new PageResult<>(rows, p.getTotal(), effPage, effSize);
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

    /**
     * 用户取消订单：
     *  - 待支付：直接关闭（60），无退款
     *  - 已支付未接单：全额退款（80），免违约金
     *  - 商家已接单/已出餐：按 order.cancel_penalty_rate 扣违约金后退款，违约金补偿给商家
     *  - 骑手已取餐（配送中）：不允许自助取消，需走售后
     */
    @Transactional
    public void cancel(Long userId, Long orderId, String reason) {
        Order order = requireOwned(userId, orderId);
        int from = order.getStatus() == null ? -1 : order.getStatus();
        String why = (reason == null || reason.isBlank()) ? "用户主动取消" : reason;
        // 订单结束（取消/退款）后配送任务一并释放，否则会一直占着骑手的「同时接单数」
        releaseDeliveryTask(orderId);
        if (from == OrderStatus.UNPAID) {
            restoreStock(orderId);
            couponService.restoreByOrder(orderId);
            order.setCancelReason(why);
            updateStatus(order, OrderStatus.CANCELED, "用户取消订单（未支付）：" + why, 1, userId);
            return;
        }
        if (from == OrderStatus.WAIT_ACCEPT) {
            order.setCancelReason(why);
            orderMapper.updateById(order);
            // 全额退款：内部会回滚库存、返还优惠券、置 80 已退款
            payService.refundByOperator(orderId, null, "用户取消（商家未接单）：" + why, 1, userId);
            return;
        }
        if (from == OrderStatus.COOKING || from == OrderStatus.WAIT_PICKUP) {
            int pay = order.getPayAmount() == null ? 0 : order.getPayAmount();
            int penalty = cancelPenalty(pay);
            int refund = Math.max(0, pay - penalty);
            order.setPenaltyAmount(penalty);
            order.setCancelReason(why);
            orderMapper.updateById(order);
            if (refund > 0) {
                payService.refundByOperator(orderId, refund, "用户取消（商家已接单，扣违约金 "
                        + money(penalty) + " 元）：" + why, 1, userId);
            } else {
                // 违约金等于全额时也要走完取消流程：回滚库存、返还券、置已退款
                restoreStock(orderId);
                couponService.restoreByOrder(orderId);
                updateStatus(order, OrderStatus.REFUNDED, "用户取消，全额抵扣违约金：" + why, 1, userId);
            }
            if (penalty > 0) {
                // 违约金补偿给商家：走钱包流水，保证可对账（不能再裸改 balance）
                settlementService.creditShop(order.getShopId(), penalty, "CANCEL_PENALTY",
                        String.valueOf(orderId), "用户取消违约金补偿");
                log(orderId, from, from, 1, userId, "用户取消违约金 " + money(penalty) + " 元补偿给商家");
            }
            return;
        }
        throw new BizException(ErrorCode.ORDER_STATUS,
                "当前状态不可取消（" + OrderStatus.text(from) + "），配送中/已完成请提交售后申请");
    }

    /** 释放该订单占用的配送任务（取消/退款时调用），避免骑手「在手单数」被僵尸任务占满。 */
    private void releaseDeliveryTask(Long orderId) {
        try {
            jdbc.update("UPDATE delivery_task SET status = 4, rider_id = NULL, updated_at = NOW() "
                    + "WHERE order_id = ? AND status IN (1,2)", orderId);
        } catch (Exception e) {
            log.debug("释放配送任务跳过：{}", e.getMessage());
        }
    }

    /** 违约金：实付 × 配置比例，向下取整到分，最低 1 元。 */    private int cancelPenalty(int payAmount) {
        if (payAmount <= 0) {
            return 0;
        }
        String rateStr = config.get("order.cancel_penalty_rate", "0.20");
        BigDecimal rate;
        try {
            rate = new BigDecimal(rateStr);
        } catch (Exception e) {
            rate = new BigDecimal("0.20");
        }
        if (rate.signum() < 0) {
            rate = BigDecimal.ZERO;
        }
        if (rate.compareTo(BigDecimal.ONE) > 0) {
            rate = BigDecimal.ONE;
        }
        int penalty = BigDecimal.valueOf(payAmount).multiply(rate).setScale(0, RoundingMode.HALF_UP).intValue();
        return Math.min(payAmount, penalty);
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

    /**
     * 商家拒单：原因必填、真正退款（不再只是把状态改成已退款）、累计拒单次数，
     * 达到 merchant.reject_limit 自动暂停营业。
     */
    @Transactional
    public void merchantReject(Long orderId, Long shopId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BizException(ErrorCode.PARAM, "拒单必须填写原因");
        }
        Order order = requireShopOrder(orderId, shopId);
        expectStatus(order, OrderStatus.WAIT_ACCEPT);
        payService.refundByOperator(orderId, null, "商家拒单：" + reason, 2, shopId);
        // 拒单属于异常结束，自动进售后工单池，后台可复核责任（需求第六章）
        try {
            afterSaleService.createSystem(order, 6, "商家拒单：" + reason, null);
        } catch (Exception e) {
            log.warn("拒单自动建售后工单失败：{}", e.getMessage());
        }
        int limit = config.getInt("merchant.reject_limit", 3);
        Shop shop = shopService.require(shopId);
        int rejectCount = (shop.getRejectCount() == null ? 0 : shop.getRejectCount()) + 1;
        shop.setRejectCount(rejectCount);
        shop.setUpdatedAt(LocalDateTime.now());
        boolean paused = false;
        if (limit > 0 && rejectCount >= limit && shop.getBizStatus() != null && shop.getBizStatus() == 1) {
            shop.setBizStatus(0);
            paused = true;
        }
        shopService.updateById(shop);
        log(orderId, OrderStatus.WAIT_ACCEPT, OrderStatus.REFUNDED, 2, shopId,
                "商家拒单（累计第 " + rejectCount + " 次）：" + reason);
        if (paused) {
            log.warn("商家 {} 累计拒单 {} 次，已达上限 {}，自动暂停营业", shopId, rejectCount, limit);
        }
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
        updateStatus(order, to, remark, 4, null);
    }

    /** operatorType：1 用户 2 商家 3 骑手 4 系统 5 平台 */
    public void updateStatus(Order order, int to, String remark, int operatorType, Long operatorId) {
        int from = order.getStatus();
        LocalDateTime now = LocalDateTime.now();
        // 状态流转必须带条件（CAS）：并发「取消两次」「支付与超时取消同时发生」时只有一个能成功，
        // 否则会出现重复退款、库存与优惠券重复回补这类资金问题。
        int rows = orderMapper.update(null, Wrappers.<Order>lambdaUpdate()
                .eq(Order::getId, order.getId())
                .eq(Order::getStatus, from)
                .set(Order::getStatus, to)
                .set(Order::getUpdatedAt, now));
        if (rows == 0) {
            throw new BizException(ErrorCode.ORDER_STATUS, "订单状态已变化，请刷新后重试");
        }
        order.setStatus(to);
        order.setUpdatedAt(now);
        // 其余字段（paid_at / rider_id / cancelled_at / penalty_amount 等）调用方已 set 好，这里一并落库
        orderMapper.updateById(order);
        log(order.getId(), from, to, operatorType, operatorId, remark);
        // 订阅消息：失败只记 notify_log，绝不影响订单主流程
        try {
            notifyService.notifyOrderStatus(order.getUserId(), order.getOrderNo(), order.getId(),
                    OrderStatus.text(to), remark);
        } catch (Exception e) {
            log.debug("订单状态订阅消息发送跳过：{}", e.getMessage());
        }
    }

    /** 围栏强制校验开关：优先 sys_config（后台可改），回落启动参数。 */
    private boolean fenceEnforced() {
        return config.getBool("delivery.fence_enforce", app.fenceEnforce());
    }

    /**
     * 骑手佣金 = 基础佣金 + 距离档位加成 + 高峰加成，夹在 [min, max] 之间，
     * 且不超过用户实付配送费（平台不亏钱给骑手）。
     */
    private int riderPay(int deliveryFee, int distanceMeters, LocalDateTime at) {
        int base = config.getInt("delivery.rider_base_income", 150);
        int freeM = config.getInt("delivery.rider_distance_free_meters", 800);
        int stepM = Math.max(1, config.getInt("delivery.rider_distance_step_meters", 500));
        int stepBonus = config.getInt("delivery.rider_distance_step_bonus", 25);
        int bonusMax = config.getInt("delivery.rider_distance_bonus_max", 100);
        int peak = inPeakWindow(at) ? config.getInt("delivery.peak_bonus", 50) : 0;
        int extra = 0;
        if (distanceMeters > freeM) {
            int steps = (int) Math.ceil((distanceMeters - freeM) / (double) stepM);
            extra = Math.min(bonusMax, steps * stepBonus);
        }
        int amount = base + extra + peak;
        int min = config.getInt("delivery.rider_income_min", 150);
        int max = config.getInt("delivery.rider_income_max", 300);
        amount = Math.max(min, Math.min(max, amount));
        if (deliveryFee > 0) {
            amount = Math.min(amount, deliveryFee);
        }
        return amount;
    }

    /** 是否处于高峰时段（delivery.dispatch_peak_windows：11:00-13:00,17:30-19:00）。 */
    public boolean inPeakWindow(LocalDateTime at) {
        String windows = config.get("delivery.dispatch_peak_windows", "");
        if (windows == null || windows.isBlank()) {
            return false;
        }
        java.time.LocalTime t = at.toLocalTime();
        for (String w : windows.split(",")) {
            String[] p = w.trim().split("-");
            if (p.length != 2) {
                continue;
            }
            try {
                java.time.LocalTime s = java.time.LocalTime.parse(p[0].trim());
                java.time.LocalTime e = java.time.LocalTime.parse(p[1].trim());
                if (!t.isBefore(s) && !t.isAfter(e)) {
                    return true;
                }
            } catch (Exception ignored) {
                // 配置写错就当没配
            }
        }
        return false;
    }

    /** 库存扣完（=0）的菜品自动下架，避免售罄菜还能被加购（需求 4.2.2）。 */
    private void offlineSoldOut(List<CartService.Line> lines) {
        for (CartService.Line line : lines) {
            try {
                Integer stock = line.skuId() != null
                        ? jdbc.queryForObject("SELECT stock FROM product_sku WHERE id = ?", Integer.class, line.skuId())
                        : jdbc.queryForObject("SELECT stock FROM product WHERE id = ?", Integer.class, line.product().getId());
                if (stock != null && stock == 0) {
                    jdbc.update("UPDATE product SET status = 0, auto_offline = 1, updated_at = NOW() "
                            + "WHERE id = ? AND stock = 0", line.product().getId());
                    log.info("菜品 {} 库存售罄，已自动下架", line.product().getId());
                }
            } catch (Exception e) {
                log.debug("售罄下架检查跳过：{}", e.getMessage());
            }
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
                    // 库存可能已被扣到 0（售罄自动下架后），>=0 才允许回滚，否则永远回不来
                    .ge(Product::getStock, 0)
                    .setSql("stock = stock + " + item.getQty()));
            // 之前是「售罄自动下架」的商品，库存补回来以后自动上架，商家不用手动再开
            try {
                jdbc.update("UPDATE product SET status = 1, auto_offline = 0, updated_at = NOW() "
                        + "WHERE id = ? AND auto_offline = 1 AND stock > 0", item.getProductId());
            } catch (Exception e) {
                log.debug("售罄商品自动上架跳过：{}", e.getMessage());
            }
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
        m.put("penaltyAmount", o.getPenaltyAmount());
        m.put("couponId", o.getCouponId());
        m.put("createdAt", o.getCreatedAt());
        if (full) {
            m.put("address", o.getAddressJson());
            m.put("remark", o.getRemark());
            m.put("riderId", o.getRiderId());
            m.put("paidAt", o.getPaidAt());
            m.put("deliveredAt", o.getDeliveredAt());
            m.put("cancelReason", o.getCancelReason());
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
