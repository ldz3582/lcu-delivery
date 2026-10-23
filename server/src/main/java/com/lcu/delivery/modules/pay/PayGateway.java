package com.lcu.delivery.modules.pay;

import com.lcu.delivery.modules.order.entity.Order;
import com.lcu.delivery.modules.pay.entity.Payment;

import java.util.Map;

/**
 * 支付通道适配器：本地用 Mock，生产换微信支付 v3。
 */
public interface PayGateway {

    record PrepayResult(String prepayId, Map<String, Object> params, boolean mock) {
    }

    PrepayResult prepay(Payment payment, Order order);

    boolean mock();
}
