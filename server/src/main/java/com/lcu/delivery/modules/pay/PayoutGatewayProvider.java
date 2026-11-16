package com.lcu.delivery.modules.pay;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 打款通道选择器：与 {@link PayGatewayProvider} 同一套逻辑，
 * 保证「支付模式」切到 real 时打款也走真实商家转账，不会出现收真钱却 mock 打款。
 *
 * <p>MerchantFinanceService 注入的仍是 {@code PayoutGateway} 接口，业务零改动。
 */
@Component
public class PayoutGatewayProvider implements PayoutGateway {

    private static final Logger log = LoggerFactory.getLogger(PayoutGatewayProvider.class);

    private final PayConfigService config;
    private final WechatPaySdkFactory factory;

    private volatile PayoutGateway delegate;
    private volatile String delegateKey;

    public PayoutGatewayProvider(PayConfigService config, WechatPaySdkFactory factory) {
        this.config = config;
        this.factory = factory;
    }

    private PayoutGateway current() {
        WechatPaySettings s = config.effective();
        String key = s.fingerprint();
        PayoutGateway cur = delegate;
        if (cur != null && key.equals(delegateKey)) {
            return cur;
        }
        synchronized (this) {
            if (delegate != null && key.equals(delegateKey)) {
                return delegate;
            }
            PayoutGateway built;
            if (s.mock()) {
                built = new MockPayoutGateway();
            } else {
                built = new WechatPayoutGateway(s, factory.build(s));
                log.info("打款通道已切换为真实商家转账：mchId={}", s.mchId());
            }
            delegate = built;
            delegateKey = key;
            return built;
        }
    }

    public void invalidate() {
        synchronized (this) {
            delegate = null;
            delegateKey = null;
        }
    }

    @Override
    public PayoutResult transfer(String outBillNo, String openid, int amount, String remark, String sceneId) {
        return current().transfer(outBillNo, openid, amount, remark, sceneId);
    }

    @Override
    public TransferNotifyResult parseNotify(String body, java.util.Map<String, String> headers) {
        return current().parseNotify(body, headers);
    }

    /** 同上：只读配置，不触发 SDK 构建。 */
    @Override
    public boolean mock() {
        return config.effective().mock();
    }
}
