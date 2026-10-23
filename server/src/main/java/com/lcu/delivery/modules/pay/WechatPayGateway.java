package com.lcu.delivery.modules.pay;

import com.lcu.delivery.common.BizException;
import com.lcu.delivery.common.ErrorCode;
import com.lcu.delivery.config.WechatProperties;
import com.lcu.delivery.modules.order.entity.Order;
import com.lcu.delivery.modules.pay.entity.Payment;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 微信支付 v3（JSAPI）。
 *
 * 接入步骤（拿到商户号后）：
 * 1. 引入官方 SDK：com.github.wechatpay-apiv3:wechatpay-java
 * 2. 用 mchId + serialNo + 私钥构建 RSAAutoCertificateConfig（平台证书自动下载轮换）
 * 3. 调 JsapiService.prepay，拿到 prepay_id 后再用 AppId + 时间戳 + 随机串 + 包名 + 私钥签名，
 *    返回给小程序 wx.requestPayment 的就是这份签名参数
 * 4. 回调 /api/pay/notify 必须验签 + 解密 resource，按 out_trade_no 幂等处理
 * 5. 分账：下单时 attach profit_sharing=Y，支付成功后调 分账 接口给商家/骑手
 *
 * 未配置商户号时直接报错，避免线上误用 mock。
 */
@Component
@ConditionalOnProperty(name = "app.mock-pay", havingValue = "false")
public class WechatPayGateway implements PayGateway {

    private final WechatProperties wechat;

    public WechatPayGateway(WechatProperties wechat) {
        this.wechat = wechat;
    }

    @Override
    public PrepayResult prepay(Payment payment, Order order) {
        WechatProperties.Pay pay = wechat.pay();
        if (pay == null || pay.mchId() == null || pay.mchId().isBlank()) {
            throw new BizException(ErrorCode.PAY_FAILED, "未配置微信支付商户号（WXPAY_MCH_ID）");
        }
        throw new BizException(ErrorCode.PAY_FAILED,
                "微信支付通道尚未接入：请按 WechatPayGateway 注释完成 SDK 接入后重试");
    }

    @Override
    public boolean mock() {
        return false;
    }
}
