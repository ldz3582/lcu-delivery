package com.lcu.delivery.job;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lcu.delivery.modules.admin.SysConfigService;
import com.lcu.delivery.modules.aftersale.AfterSaleService;
import com.lcu.delivery.modules.order.OrderService;
import com.lcu.delivery.modules.order.OrderStatus;
import com.lcu.delivery.modules.order.entity.Order;
import com.lcu.delivery.modules.order.mapper.OrderMapper;
import com.lcu.delivery.modules.pay.PayService;
import com.lcu.delivery.modules.pay.entity.Payment;
import com.lcu.delivery.modules.pay.mapper.PaymentMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单超时处理（每分钟）：
 *  1. 未支付超时 → 关闭订单
 *  2. 已支付但商家迟迟不接单 → 自动取消 + 全额退款 + 生成售后工单（需求第六章异常分支）
 */
@Component
public class OrderTimeoutJob {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutJob.class);

    private final OrderMapper orderMapper;
    private final PaymentMapper paymentMapper;
    private final OrderService orderService;
    private final PayService payService;
    private final AfterSaleService afterSaleService;
    private final SysConfigService config;

    public OrderTimeoutJob(OrderMapper orderMapper, PaymentMapper paymentMapper, OrderService orderService,
                           PayService payService, AfterSaleService afterSaleService, SysConfigService config) {
        this.orderMapper = orderMapper;
        this.paymentMapper = paymentMapper;
        this.orderService = orderService;
        this.payService = payService;
        this.afterSaleService = afterSaleService;
        this.config = config;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 20_000)
    @Transactional
    public void closeExpired() {
        closeUnpaid();
        cancelAcceptTimeout();
    }

    /** 未支付超时关闭。 */
    private void closeUnpaid() {
        List<Order> list = orderMapper.selectList(Wrappers.<Order>lambdaQuery()
                .eq(Order::getStatus, OrderStatus.UNPAID)
                .lt(Order::getPayExpireAt, LocalDateTime.now())
                .last("limit 50"));
        for (Order order : list) {
            orderService.restoreStock(order.getId());
            orderService.updateStatus(order, OrderStatus.CANCELED, "超时未支付，自动关闭", 4, null);
            paymentMapper.update(null, Wrappers.<Payment>lambdaUpdate()
                    .eq(Payment::getOrderId, order.getId())
                    .eq(Payment::getStatus, 0)
                    .set(Payment::getStatus, 3)
                    .set(Payment::getUpdatedAt, LocalDateTime.now()));
            log.info("订单 {} 超时关闭", order.getOrderNo());
        }
    }

    /** 已支付但商家超过 order.accept_timeout_minutes 未接单：自动取消 + 退款 + 建售后工单。 */
    private void cancelAcceptTimeout() {
        int minutes = Math.max(1, config.getInt("order.accept_timeout_minutes", 10));
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(minutes);
        List<Order> list = orderMapper.selectList(Wrappers.<Order>lambdaQuery()
                .eq(Order::getStatus, OrderStatus.WAIT_ACCEPT)
                .isNotNull(Order::getPaidAt)
                .lt(Order::getPaidAt, deadline)
                .last("limit 20"));
        for (Order order : list) {
            try {
                payService.refundByOperator(order.getId(), null,
                        "商家接单超时（超过 " + minutes + " 分钟），系统自动取消并退款", 4, null);
                afterSaleService.createSystem(order, 6,
                        "商家接单超时，系统自动取消并全额退款（等待 " + minutes + " 分钟）", null);
                log.info("订单 {} 商家接单超时，已自动取消并退款", order.getOrderNo());
            } catch (Exception e) {
                log.error("接单超时自动处理失败 {}", order.getId(), e);
            }
        }
    }
}
