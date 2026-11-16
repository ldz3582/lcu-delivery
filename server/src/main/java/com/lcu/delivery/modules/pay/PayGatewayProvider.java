package com.lcu.delivery.modules.pay;

import com.lcu.delivery.common.BizException;
import com.lcu.delivery.common.ErrorCode;
import com.lcu.delivery.modules.order.entity.Order;
import com.lcu.delivery.modules.pay.entity.Payment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 支付通道选择器：按「当前生效配置」把调用转给 mock 或真实微信通道。
 *
 * <p>为什么不直接在 WechatPayGateway 上挂 {@code @ConditionalOnProperty}：
 * 那样模式只能在启动时决定，后台改配置得重启；而且启动时就要下载微信平台证书，
 * 微信接口抖动会让整个应用起不来。这里改成**按需构建 + 配置指纹缓存**：
 * <ul>
 *   <li>配置没变 → 复用已有客户端（不会每次下单都下载证书）</li>
 *   <li>配置变了 → 下次调用时重建（后台保存即生效）</li>
 *   <li>构建失败 → 只影响这次请求，不拖垮进程</li>
 * </ul>
 *
 * <p>PayService 注入的仍然是 {@code PayGateway} 接口，业务代码零改动。
 */
@Component
public class PayGatewayProvider implements PayGateway {

    private static final Logger log = LoggerFactory.getLogger(PayGatewayProvider.class);

    private final PayConfigService config;
    private final WechatPaySdkFactory factory;

    private volatile PayGateway delegate;
    private volatile String delegateKey;

    public PayGatewayProvider(PayConfigService config, WechatPaySdkFactory factory) {
        this.config = config;
        this.factory = factory;
    }

    /** 当前实际生效的通道（供诊断/日志用）。 */
    public String activeChannel() {
        WechatPaySettings s = config.effective();
        return s.mock() ? "mock" : "wechat-v3";
    }

    private PayGateway current() {
        WechatPaySettings s = config.effective();
        String key = s.fingerprint();
        PayGateway cur = delegate;
        if (cur != null && key.equals(delegateKey)) {
            return cur;
        }
        synchronized (this) {
            if (delegate != null && key.equals(delegateKey)) {
                return delegate;
            }
            PayGateway built;
            if (s.mock()) {
                built = new MockPayGateway();
            } else {
                built = new WechatPayGateway(s, factory.build(s));
                log.info("支付通道已切换为真实微信支付：mchId={} appId={}", s.mchId(), s.appId());
            }
            delegate = built;
            delegateKey = key;
            return built;
        }
    }

    /** 配置保存后主动失效，避免等到下次下单才发现。 */
    public void invalidate() {
        synchronized (this) {
            delegate = null;
            delegateKey = null;
        }
    }

    // ---------------------------------------------------------------- 委托

    @Override
    public PrepayResult prepay(Payment payment, Order order, String openid) {
        return current().prepay(payment, order, openid);
    }

    @Override
    public PrepayResult prepayH5(Payment payment, Order order, String clientIp) {
        return current().prepayH5(payment, order, clientIp);
    }

    @Override
    public PrepayResult prepayNative(Payment payment, Order order) {
        return current().prepayNative(payment, order);
    }

    @Override
    public PrepayResult prepayMpJsapi(Payment payment, Order order, String mpOpenid) {
        return current().prepayMpJsapi(payment, order, mpOpenid);
    }

    @Override
    public NotifyResult parseNotify(String body, Map<String, String> headers) {
        return current().parseNotify(body, headers);
    }

    @Override
    public RefundNotifyResult parseRefundNotify(String body, Map<String, String> headers) {
        return current().parseRefundNotify(body, headers);
    }

    @Override
    public NotifyResult query(String outTradeNo) {
        return current().query(outTradeNo);
    }

    @Override
    public RefundResult refund(Payment payment, String refundNo, int amount, String reason) {
        return current().refund(payment, refundNo, amount, reason);
    }

    /**
     * 是否模拟通道。
     *
     * <p>⚠️ 这里**不能**调 current().mock()：那会触发真实通道构建（下载平台证书），
     * 配置不完整或微信不可达时会把「查询状态」这类只读操作也弄成 4001。
     * 模式判断只读配置即可。
     */
    @Override
    public boolean mock() {
        return config.effective().mock();
    }

    /** 给后台「测试连通性」用：强制按给定配置构建一次，返回结果或异常消息。 */
    public String probe() {
        WechatPaySettings s = config.effective();
        if (s.mock()) {
            return "当前为模拟模式，未连接微信支付";
        }
        var missing = s.missing();
        if (!missing.isEmpty()) {
            return "配置不完整：" + String.join("、", missing);
        }
        try {
            factory.build(s);
            return "成功：商户号与私钥通过微信校验，平台证书已下载";
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.PAY_FAILED, "连通性测试失败：" + e.getMessage());
        }
    }
}
