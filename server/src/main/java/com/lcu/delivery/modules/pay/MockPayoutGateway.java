package com.lcu.delivery.modules.pay;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 模拟打款：审核通过即视为已到账，不产生真实转账。
 */
@Component
@ConditionalOnProperty(name = "app.mock-pay", havingValue = "true", matchIfMissing = true)
public class MockPayoutGateway implements PayoutGateway {

    @Override
    public PayoutResult transfer(String outBillNo, String openid, int amount, String remark, String sceneId) {
        return new PayoutResult(true, "模拟打款成功", "MOCKBILL" + outBillNo, "SUCCESS");
    }

    @Override
    public boolean mock() {
        return true;
    }

    @Override
    public TransferNotifyResult parseNotify(String body, java.util.Map<String, String> headers) {
        return new TransferNotifyResult(null, true, "模拟转账通知");
    }
}
