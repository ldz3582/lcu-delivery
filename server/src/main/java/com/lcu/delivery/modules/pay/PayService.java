package com.lcu.delivery.modules.pay;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lcu.delivery.common.BizException;
import com.lcu.delivery.common.ErrorCode;
import com.lcu.delivery.config.AppProperties;
import com.lcu.delivery.modules.coupon.CouponService;
import com.lcu.delivery.modules.order.OrderService;
import com.lcu.delivery.modules.order.OrderStatus;
import com.lcu.delivery.modules.order.entity.Order;
import com.lcu.delivery.modules.order.mapper.OrderMapper;
import com.lcu.delivery.modules.pay.entity.Payment;
import com.lcu.delivery.modules.pay.mapper.PaymentMapper;
import com.lcu.delivery.modules.settle.SettlementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class PayService {

    private static final Logger log = LoggerFactory.getLogger(PayService.class);

    private final PaymentMapper paymentMapper;
    private final OrderMapper orderMapper;
    private final OrderService orderService;
    private final PayGateway gateway;
    private final AppProperties app;
    private final JdbcTemplate jdbc;
    private final CouponService couponService;
    private final SettlementService settlementService;

    public PayService(PaymentMapper paymentMapper, OrderMapper orderMapper, OrderService orderService,
                      PayGateway gateway, AppProperties app, JdbcTemplate jdbc, CouponService couponService,
                      SettlementService settlementService) {
        this.paymentMapper = paymentMapper;
        this.orderMapper = orderMapper;
        this.orderService = orderService;
        this.gateway = gateway;
        this.app = app;
        this.jdbc = jdbc;
        this.couponService = couponService;
        this.settlementService = settlementService;
    }

    @Transactional
    public Map<String, Object> prepay(Long userId, Long orderId) {
        Order order = orderService.requireOwned(userId, orderId);
        if (order.getStatus() != OrderStatus.UNPAID) {
            throw new BizException(ErrorCode.ORDER_STATUS, "订单不是待支付状态");
        }
        Payment payment = paymentMapper.selectOne(Wrappers.<Payment>lambdaQuery()
                .eq(Payment::getOrderId, orderId).eq(Payment::getStatus, 0));
        if (payment == null) {
            payment = new Payment();
            payment.setOrderId(orderId);
            payment.setOutTradeNo(order.getOrderNo() + "P");
            payment.setAmount(order.getPayAmount());
            payment.setStatus(0);
            payment.setProfitSharing(0);
            payment.setCreatedAt(LocalDateTime.now());
            payment.setUpdatedAt(LocalDateTime.now());
            paymentMapper.insert(payment);
        }
        PayGateway.PrepayResult result = gateway.prepay(payment, order);
        payment.setPrepayId(result.prepayId());
        payment.setUpdatedAt(LocalDateTime.now());
        paymentMapper.updateById(payment);

        Map<String, Object> data = new LinkedHashMap<>(result.params());
        data.put("orderId", orderId);
        data.put("outTradeNo", payment.getOutTradeNo());
        return data;
    }

    /** 支付成功回调（模拟或真实）：幂等处理，重复回调不会重复改单。 */
    @Transactional
    public void markPaid(String outTradeNo, String transactionId) {
        Payment payment = paymentMapper.selectOne(Wrappers.<Payment>lambdaQuery()
                .eq(Payment::getOutTradeNo, outTradeNo));
        if (payment == null) {
            log.warn("回调找不到支付单：{}", outTradeNo);
            return;
        }
        if (payment.getStatus() != null && payment.getStatus() == 1) {
            return;
        }
        payment.setStatus(1);
        payment.setTransactionId(transactionId);
        payment.setPaidAt(LocalDateTime.now());
        payment.setProfitSharing(1);
        payment.setUpdatedAt(LocalDateTime.now());
        paymentMapper.updateById(payment);

        Order order = orderMapper.selectById(payment.getOrderId());
        if (order != null && order.getStatus() == OrderStatus.UNPAID) {
            order.setPaidAt(LocalDateTime.now());
            orderService.updateStatus(order, OrderStatus.WAIT_ACCEPT, "支付成功");
        }
    }

    @Transactional
    public Map<String, Object> mockPay(Long userId, Long orderId) {
        if (!app.mockPay()) {
            throw new BizException(ErrorCode.FORBIDDEN, "当前环境未开启模拟支付");
        }
        Order order = orderService.requireOwned(userId, orderId);
        Payment payment = paymentMapper.selectOne(Wrappers.<Payment>lambdaQuery()
                .eq(Payment::getOrderId, orderId).orderByDesc(Payment::getId).last("limit 1"));
        if (payment == null) {
            prepay(userId, orderId);
            payment = paymentMapper.selectOne(Wrappers.<Payment>lambdaQuery()
                    .eq(Payment::getOrderId, orderId).orderByDesc(Payment::getId).last("limit 1"));
        }
        markPaid(payment.getOutTradeNo(), "MOCK" + System.currentTimeMillis());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("paid", true);
        data.put("orderId", orderId);
        data.put("status", OrderStatus.WAIT_ACCEPT);
        return data;
    }

    /** 微信回调入口：真实环境需验签 + 解密 resource；模拟环境接收 outTradeNo。 */
    @Transactional
    public Map<String, Object> notify(String body) {
        if (gateway.mock()) {
            String outTradeNo = extract(body, "outTradeNo");
            markPaid(outTradeNo, "MOCK" + System.currentTimeMillis());
            return Map.of("code", "SUCCESS", "message", "成功");
        }
        throw new BizException(ErrorCode.PAY_FAILED, "微信支付回调验签未实现，请接入 SDK 后启用");
    }

    /**
     * 用户自助退款：只能退自己的单，且只允许「已支付、未完成」阶段。
     * 已完成订单必须走售后工单，由平台仲裁后再退。
     */
    @Transactional
    public void refundForUser(Long userId, Long orderId, Integer amount, String reason) {
        Order order = orderService.requireOwned(userId, orderId);
        int status = order.getStatus();
        boolean refundable = status == OrderStatus.WAIT_ACCEPT
                || status == OrderStatus.COOKING
                || status == OrderStatus.WAIT_PICKUP
                || status == OrderStatus.DELIVERING
                || status == OrderStatus.AFTER_SALE;
        if (!refundable) {
            throw new BizException(ErrorCode.ORDER_STATUS, "当前状态不可直接退款，已完成订单请提交售后申请");
        }
        refundInternal(order, amount, reason, 1, userId, true);
    }

    /** 平台仲裁 / 商家拒单等后台动作触发的退款。 */
    @Transactional
    public void refundByOperator(Long orderId, Integer amount, String reason, int operatorType, Long operatorId) {
        refundInternal(orderService.require(orderId), amount, reason, operatorType, operatorId, true);
    }

    /** 部分退款（售后少餐/漏餐等）：只落退款流水，不改订单终态、不回滚库存、不返还券。 */
    @Transactional
    public void refundPartial(Long orderId, Integer amount, String reason, int operatorType, Long operatorId) {
        refundInternal(orderService.require(orderId), amount, reason, operatorType, operatorId, false);
    }

    private void refundInternal(Order order, Integer amount, String reason, int operatorType, Long operatorId,
                                boolean markRefunded) {
        Long orderId = order.getId();
        Payment payment = paymentMapper.selectOne(Wrappers.<Payment>lambdaQuery()
                .eq(Payment::getOrderId, orderId).eq(Payment::getStatus, 1).last("limit 1"));
        if (payment == null) {
            throw new BizException(ErrorCode.PAY_FAILED, "没有可退款的支付单");
        }
        int refundAmount = amount == null ? payment.getAmount() : amount;
        if (refundAmount <= 0) {
            throw new BizException(ErrorCode.PARAM, "退款金额必须大于 0");
        }
        if (refundAmount > payment.getAmount()) {
            throw new BizException(ErrorCode.PAY_FAILED, "退款金额不能大于支付金额");
        }
        Integer refunded = jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM refund WHERE order_id = ? AND status = 1",
                Integer.class, orderId);
        if (refunded != null && refunded + refundAmount > payment.getAmount()) {
            throw new BizException(ErrorCode.PAY_FAILED, "累计退款不能超过支付金额（已退 "
                    + String.format("%.2f", refunded / 100.0) + " 元）");
        }
        // 同一单可能多次部分退款（售后多次赔付），退单号必须唯一：
        // R + 订单号 + "-" + 该单已退款笔数+1（refund_no 上限 32 字符）
        Integer refundedTimes = jdbc.queryForObject(
                "SELECT COUNT(*) FROM refund WHERE order_id = ?", Integer.class, orderId);
        String refundNo = "R" + order.getOrderNo() + "-" + ((refundedTimes == null ? 0 : refundedTimes) + 1);
        jdbc.update("INSERT INTO refund (order_id, payment_id, refund_no, amount, reason, status, operator_type, operator_id, refunded_at, created_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?)",
                orderId, payment.getId(), refundNo, refundAmount, reason, gateway.mock() ? 1 : 0,
                operatorType, operatorId, gateway.mock() ? LocalDateTime.now() : null, LocalDateTime.now());

        if (!markRefunded) {
            // 部分退款：冲减对应结算单（未结算改金额，已结算从钱包扣回），避免「退了钱还全额结算」
            settlementService.reversePartial(orderId, refundAmount, reason);
            return;
        }
        // 全额退款：结算单作废 / 已打款的从钱包扣回，保证账实一致
        settlementService.reverseFull(orderId, reason);
        if (order.getStatus() != OrderStatus.CANCELED && order.getStatus() != OrderStatus.REFUNDED) {
            orderService.restoreStock(orderId);
        }
        couponService.restoreByOrder(orderId);
        orderService.updateStatus(order, OrderStatus.REFUNDED, "退款：" + (reason == null ? "无" : reason));
    }

    private String extract(String body, String field) {
        if (body == null) {
            return null;
        }
        int i = body.indexOf("\"" + field + "\"");
        if (i < 0) {
            return null;
        }
        int colon = body.indexOf(':', i);
        int q1 = body.indexOf('"', colon + 1);
        int q2 = body.indexOf('"', q1 + 1);
        return (q1 > 0 && q2 > q1) ? body.substring(q1 + 1, q2) : null;
    }
}
