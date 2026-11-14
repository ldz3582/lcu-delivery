package com.lcu.delivery.modules.pay;

/**
 * 打款通道：把钱包里的钱真正付给商家 / 骑手。
 *
 * <p>A 方案（平台代收）里，用户的钱先全额进平台商户号，结算到钱包后由平台打款出去。
 * 微信侧的出口是「商家转账」（新版：/v3/fund-app/mch-transfer/transfer-bills），
 * 需要商户平台先开通该产品，并按场景申请 transfer_scene_id。
 *
 * <p>前置条件：收款人必须是同一个小程序下的用户（有 openid）。
 * 商家取 shop.owner_user_id，骑手取 rider.user_id，都已能映射到 user.openid。
 */
public interface PayoutGateway {

    /**
     * @param outBillNo 商户侧转账单号（提现单号派生，必须唯一）
     * @param openid    收款人在本小程序下的 openid
     * @param amount    金额，单位分
     * @param remark    转账备注（≤32 字符）
     * @param sceneId   转账场景 ID（佣金报酬常用 1000，需在商户平台申请）
     */
    record PayoutResult(boolean success, String message, String billNo, String state) {
    }

    PayoutResult transfer(String outBillNo, String openid, int amount, String remark, String sceneId);

    /** 转账结果通知（异步到账/失败）。 */
    record TransferNotifyResult(String outBillNo, boolean success, String message) {
    }

    TransferNotifyResult parseNotify(String body, java.util.Map<String, String> headers);

    boolean mock();
}
