package com.lcu.delivery.modules.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lcu.delivery.common.BizException;
import com.lcu.delivery.common.ErrorCode;
import com.lcu.delivery.common.PageResult;
import com.lcu.delivery.config.AppProperties;
import com.lcu.delivery.modules.admin.SysConfigService;
import com.lcu.delivery.modules.cart.CartService;
import com.lcu.delivery.modules.fence.FenceService;
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
    private final ShopService shopService;
    private final SysConfigService config;
    private final AppProperties app;
    private final SettlementService settlementService;
    private final ObjectMapper json = new ObjectMapper();

    public OrderService(OrderMapper orderMapper, OrderItemMapper itemMapper, OrderStatusLogMapper logMapper,
                        ProductMapper productMapper, CartService cartService, AddressService addressService,
                        FenceService fenceService, ShopService shopService, SysConfigService config,
                        AppProperties app, SettlementService settlementService) {
        this.orderMapper = orderMapper;
        this.itemMapper = itemMapper;
        this.logMapper = logMapper;
        this.productMapper = productMapper;
        this.cartService = cartService;
        this.addressService = addressService;
        this.fenceService = fenceService;
        this.shopService = shopService;
        this.config = config;
        this.app = app;
        this.settlementService = settlementService;
    }

    public record CreateReq(Long shopId, Long addressId, List<CartService.Item> items,
                            String remark, LocalDateTime expectTime) {
    }

    @Transactional
    public Map<String, Object> create(Long userId, CreateReq req) {
        if (req.shopId() == null || req.addressId() == null) {
            throw new BizException(ErrorCode.PARAM, "缺少店铺或收货地址");
        }
        Shop shop = shopService.require(req.shopId());
        CartService.Bill bill = cartService.calculate(req.shopId(), req.items());
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

        // 扣库存：条件更新，防超卖
        for (CartService.Line line : bill.lines()) {
            Product p = line.product();
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
        order.setAddressJson(toJson(address));
        order.setLng(address.getLng());
        order.setLat(address.getLat());
        order.setGoodsAmount(bill.goodsAmount());
        order.setPackFee(bill.packFee());
        order.setDeliveryFee(bill.deliveryFee());
        order.setDiscountAmount(bill.discountAmount());
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
            item.setName(line.product().getName());
            item.setImage(line.product().getImage());
            item.setPrice(line.product().getPrice());
            item.setQty(line.qty());
            item.setSubtotal(line.subtotal());
            itemMapper.insert(item);
        }
        log(order.getId(), -1, OrderStatus.UNPAID, 1, userId, "用户下单");

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
        m.put("deliveryFee", o.getDeliveryFee());
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
