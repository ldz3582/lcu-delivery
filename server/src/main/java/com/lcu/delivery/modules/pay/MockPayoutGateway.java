package com.lcu.delivery.modules.pay;

import java.util.Map;

/**
 * 模拟打款：审核通过即视为已到账，不产生真实转账。
 * 由 {@link PayoutGatewayProvider} 按生效配置选择。
 */
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
