package com.lcu.delivery.modules.pay;

import com.lcu.delivery.modules.order.entity.Order;
import com.lcu.delivery.modules.pay.entity.Payment;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 本地联调用：不产生真实扣款，前端拿到 mock=true 后直接调 /api/pay/mock-pay 模拟支付成功。
 * 生产环境把 app.mock-pay 设为 false 即自动切换到 WechatPayGateway。
 */
@Component
@ConditionalOnProperty(name = "app.mock-pay", havingValue = "true", matchIfMissing = true)
public class MockPayGateway implements PayGateway {

    @Override
    public PrepayResult prepay(Payment payment, Order order) {
        String prepayId = "mock_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("mock", true);
        params.put("orderId", order.getId());
        params.put("outTradeNo", payment.getOutTradeNo());
        params.put("amount", payment.getAmount());
        params.put("hint", "本地模拟支付：调用 POST /api/pay/mock-pay 即视为支付成功");
        return new PrepayResult(prepayId, params, true);
    }

    @Override
    public boolean mock() {
        return true;
    }
}
