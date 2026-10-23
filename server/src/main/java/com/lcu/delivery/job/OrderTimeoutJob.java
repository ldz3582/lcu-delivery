package com.lcu.delivery.job;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lcu.delivery.modules.order.OrderService;
import com.lcu.delivery.modules.order.OrderStatus;
import com.lcu.delivery.modules.order.entity.Order;
import com.lcu.delivery.modules.order.mapper.OrderMapper;
import com.lcu.delivery.modules.pay.entity.Payment;
import com.lcu.delivery.modules.pay.mapper.PaymentMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** 未支付订单超时关闭（每分钟）。 */
@Component
public class OrderTimeoutJob {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutJob.class);

    private final OrderMapper orderMapper;
    private final PaymentMapper paymentMapper;
    private final OrderService orderService;

    public OrderTimeoutJob(OrderMapper orderMapper, PaymentMapper paymentMapper, OrderService orderService) {
        this.orderMapper = orderMapper;
        this.paymentMapper = paymentMapper;
        this.orderService = orderService;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 20_000)
    @Transactional
    public void closeExpired() {
        List<Order> list = orderMapper.selectList(Wrappers.<Order>lambdaQuery()
                .eq(Order::getStatus, OrderStatus.UNPAID)
                .lt(Order::getPayExpireAt, LocalDateTime.now())
                .last("limit 50"));
        for (Order order : list) {
            orderService.restoreStock(order.getId());
            orderService.updateStatus(order, OrderStatus.CANCELED, "超时未支付，自动关闭");
            paymentMapper.update(null, Wrappers.<Payment>lambdaUpdate()
                    .eq(Payment::getOrderId, order.getId())
                    .eq(Payment::getStatus, 0)
                    .set(Payment::getStatus, 3)
                    .set(Payment::getUpdatedAt, LocalDateTime.now()));
            log.info("订单 {} 超时关闭", order.getOrderNo());
        }
    }
}
