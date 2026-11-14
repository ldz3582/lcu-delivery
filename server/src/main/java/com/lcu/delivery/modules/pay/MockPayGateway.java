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
    public PrepayResult prepay(Payment payment, Order order, String openid) {
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
    public NotifyResult parseNotify(String body, Map<String, String> headers) {
        // 模拟通道没有签名，按约定从 body 里取 outTradeNo 即视为成功
        return new NotifyResult(extract(body, "outTradeNo"), "MOCK" + System.currentTimeMillis(), true);
    }

    /** 模拟 H5 支付：给出一个占位跳转地址，前端据此走「跳转后轮询」流程。 */
    @Override
    public PrepayResult prepayH5(Payment payment, Order order, String clientIp) {
        Map<String, Object> params = base(payment, order);
        params.put("channel", "h5");
        params.put("h5Url", "mock://h5pay/" + payment.getOutTradeNo());
        params.put("hint", "本地模拟 H5 支付：调用 POST /api/pay/mock-pay 即视为支付成功");
        return new PrepayResult("mock_h5_" + payment.getOutTradeNo(), params, true);
    }

    /** 模拟扫码支付：给出占位二维码内容。 */
    @Override
    public PrepayResult prepayNative(Payment payment, Order order) {
        Map<String, Object> params = base(payment, order);
        params.put("channel", "native");
        params.put("codeUrl", "mock://nativepay/" + payment.getOutTradeNo());
        params.put("hint", "本地模拟扫码支付：调用 POST /api/pay/mock-pay 即视为支付成功");
        return new PrepayResult("mock_native_" + payment.getOutTradeNo(), params, true);
    }

    @Override
    public PrepayResult prepayMpJsapi(Payment payment, Order order, String mpOpenid) {
        Map<String, Object> params = base(payment, order);
        params.put("channel", "mp");
        params.put("hint", "本地模拟公众号支付：调用 POST /api/pay/mock-pay 即视为支付成功");
        return new PrepayResult("mock_mp_" + payment.getOutTradeNo(), params, true);
    }

    private Map<String, Object> base(Payment payment, Order order) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("mock", true);
        p.put("orderId", order.getId());
        p.put("outTradeNo", payment.getOutTradeNo());
        p.put("amount", payment.getAmount());
        return p;
    }

    @Override
    public RefundResult refund(Payment payment, String refundNo, int amount, String reason) {
        return new RefundResult(true, "模拟退款成功", "MOCKREFUND" + refundNo);
    }

    @Override
    public RefundNotifyResult parseRefundNotify(String body, Map<String, String> headers) {
        return new RefundNotifyResult(extract(body, "outRefundNo"), null, true, "模拟退款通知");
    }

    @Override
    public NotifyResult query(String outTradeNo) {
        // 模拟通道没有远端订单，交由 mock-pay 直接置成功
        return null;
    }

    @Override
    public boolean mock() {
        return true;
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
