package com.lcu.delivery.modules.pay;

import com.lcu.delivery.modules.order.entity.Order;
import com.lcu.delivery.modules.pay.entity.Payment;

import java.util.Map;

/**
 * 支付通道适配器：本地用 Mock，生产换微信支付 v3（JSAPI）。
 *
 * <p>实现必须满足的约定：
 * <ul>
 *   <li>{@link #prepay} 只负责「下单 + 给前端要的参数」，不修改订单状态</li>
 *   <li>{@link #parseNotify} 必须验签 + 解密，返回可信的 outTradeNo / transactionId</li>
 *   <li>{@link #refund} 的 refundNo 由调用方生成并落库，网关侧要做幂等</li>
 * </ul>
 */
public interface PayGateway {

    /**
     * @param prepayId 网关侧预支付标识（微信是 prepay_id，mock 是随机串）
     * @param params   直接回给前端 / 小程序的支付参数
     * @param mock     是否模拟通道
     */
    record PrepayResult(String prepayId, Map<String, Object> params, boolean mock) {
    }

    /**
     * 解密后的支付通知。
     *
     * @param outTradeNo    商户订单号（支付单号）
     * @param transactionId 微信支付订单号
     * @param success       交易是否成功
     */
    record NotifyResult(String outTradeNo, String transactionId, boolean success) {
    }

    /** 退款结果；success=false 时 message 用于落库和排查。 */
    record RefundResult(boolean success, String message, String refundId) {
    }

    /** 退款异步通知（微信 refund-notify）。 */
    record RefundNotifyResult(String outRefundNo, String refundId, boolean success, String message) {
    }

    /**
     * 预支付下单（小程序 JSAPI）。
     *
     * @param openid 用户在对应 AppID 下的 openid，JSAPI 必填
     */
    PrepayResult prepay(Payment payment, Order order, String openid);

    /**
     * 微信外浏览器 H5 支付（MWEB）：返回跳转 URL，浏览器直接打开即可唤起微信收银台。
     *
     * @param clientIp 用户终端 IP，微信必填
     */
    default PrepayResult prepayH5(Payment payment, Order order, String clientIp) {
        throw new com.lcu.delivery.common.BizException(com.lcu.delivery.common.ErrorCode.PAY_FAILED,
                "当前支付通道不支持 H5 支付");
    }

    /** PC 扫码支付（Native）：返回二维码内容 code_url。 */
    default PrepayResult prepayNative(Payment payment, Order order) {
        throw new com.lcu.delivery.common.BizException(com.lcu.delivery.common.ErrorCode.PAY_FAILED,
                "当前支付通道不支持扫码支付");
    }

    /**
     * 微信内置浏览器里的 H5 支付（公众号 JSAPI）。
     * 与小程序 JSAPI 的区别：appid 用公众号的，openid 用公众号 openid。
     */
    default PrepayResult prepayMpJsapi(Payment payment, Order order, String mpOpenid) {
        throw new com.lcu.delivery.common.BizException(com.lcu.delivery.common.ErrorCode.PAY_FAILED,
                "当前支付通道不支持公众号内支付");
    }

    /**
     * 解析支付回调。headers 是微信的 Wechatpay-* 系列请求头（已统一小写 key）。
     */
    NotifyResult parseNotify(String body, Map<String, String> headers);

    /** 申请退款（单位分）。 */
    RefundResult refund(Payment payment, String refundNo, int amount, String reason);

    /** 解析退款结果通知。 */
    RefundNotifyResult parseRefundNotify(String body, Map<String, String> headers);

    /**
     * 主动查单（回调可能延迟或丢失，前端支付成功后用它兜底确认）。
     * 返回 null 表示通道不支持查询。
     */
    NotifyResult query(String outTradeNo);

    boolean mock();
}
