package com.lcu.delivery.modules.pay;

import com.lcu.delivery.common.BizException;
import com.lcu.delivery.common.ErrorCode;
import com.lcu.delivery.config.WechatProperties;
import com.lcu.delivery.modules.order.entity.Order;
import com.lcu.delivery.modules.pay.entity.Payment;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.jsapi.JsapiServiceExtension;
import com.wechat.pay.java.service.payments.jsapi.model.Amount;
import com.wechat.pay.java.service.payments.jsapi.model.Payer;
import com.wechat.pay.java.service.payments.jsapi.model.PrepayRequest;
import com.wechat.pay.java.service.payments.jsapi.model.PrepayWithRequestPaymentResponse;
import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.refund.RefundService;
import com.wechat.pay.java.service.refund.model.AmountReq;
import com.wechat.pay.java.service.refund.model.CreateRequest;
import com.wechat.pay.java.service.refund.model.Refund;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 微信支付 v3（JSAPI）。
 *
 * <p>资金路径（A 方案·平台代收）：
 * 用户 → 平台商户号（全额） → 平台内部 settlement 记账 → 商家/骑手钱包 → 提现打款。
 * 所以这里的 out_trade_no 是「支付单号」，收款方固定是平台商户号，不涉及子商户。
 */
@Component
@ConditionalOnProperty(name = "app.mock-pay", havingValue = "false")
public class WechatPayGateway implements PayGateway {

    private static final Logger log = LoggerFactory.getLogger(WechatPayGateway.class);

    private final WechatProperties wechat;
    private final JsapiServiceExtension jsapi;
    private final NotificationParser notificationParser;
    private final RefundService refundService;
    private final com.wechat.pay.java.service.payments.h5.H5Service h5Service;
    private final com.wechat.pay.java.service.payments.nativepay.NativePayService nativePayService;

    public WechatPayGateway(WechatProperties wechat, JsapiServiceExtension jsapi,
                            NotificationParser notificationParser, RefundService refundService,
                            com.wechat.pay.java.service.payments.h5.H5Service h5Service,
                            com.wechat.pay.java.service.payments.nativepay.NativePayService nativePayService) {
        this.wechat = wechat;
        this.jsapi = jsapi;
        this.notificationParser = notificationParser;
        this.refundService = refundService;
        this.h5Service = h5Service;
        this.nativePayService = nativePayService;
    }

    @Override
    public PrepayResult prepay(Payment payment, Order order, String openid) {
        WechatProperties.Pay pay = wechat.pay();
        if (openid == null || openid.isBlank()) {
            throw new BizException(ErrorCode.PAY_FAILED, "缺少 openid，微信 JSAPI 下单必须带 openid");
        }

        PrepayRequest request = new PrepayRequest();
        request.setAppid(wechat.miniapp().appid());
        request.setMchid(pay.mchId());
        request.setDescription("聊大外卖-" + safeDesc(order.getShopId()) + "-" + order.getOrderNo());
        request.setOutTradeNo(payment.getOutTradeNo());
        request.setNotifyUrl(pay.notifyUrl());
        // 支付有效期对齐订单的未支付超时，避免用户付了钱但订单已被关单
        if (order.getPayExpireAt() != null) {
            request.setTimeExpire(order.getPayExpireAt()
                    .atZone(java.time.ZoneId.of("Asia/Shanghai"))
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")));
        }
        Amount amount = new Amount();
        amount.setTotal(payment.getAmount());
        amount.setCurrency("CNY");
        request.setAmount(amount);

        Payer payer = new Payer();
        payer.setOpenid(openid);
        request.setPayer(payer);

        try {
            // 该扩展方法内部完成「下单 + 二次签名」，返回值可直接喂给 wx.requestPayment
            PrepayWithRequestPaymentResponse resp = jsapi.prepayWithRequestPayment(request);
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mock", false);
            params.put("appId", wechat.miniapp().appid());
            params.put("timeStamp", resp.getTimeStamp());
            params.put("nonceStr", resp.getNonceStr());
            params.put("package", resp.getPackageVal());
            params.put("signType", resp.getSignType());
            params.put("paySign", resp.getPaySign());
            params.put("outTradeNo", payment.getOutTradeNo());
            params.put("amount", payment.getAmount());
            log.info("JSAPI 下单成功 outTradeNo={} amount={}", payment.getOutTradeNo(), payment.getAmount());
            return new PrepayResult(resp.getPackageVal(), params, false);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("JSAPI 下单失败 outTradeNo={}", payment.getOutTradeNo(), e);
            throw new BizException(ErrorCode.PAY_FAILED, "微信下单失败：" + rootMessage(e));
        }
    }

    /**
     * 微信外浏览器 H5 支付（MWEB）：返回 h5_url，浏览器跳过去就会唤起微信收银台。
     * scene_info 是必填项，其中 payer_client_ip 必须是用户真实出口 IP。
     */
    @Override
    public PrepayResult prepayH5(Payment payment, Order order, String clientIp) {
        WechatProperties.Pay pay = wechat.pay();
        com.wechat.pay.java.service.payments.h5.model.PrepayRequest request =
                new com.wechat.pay.java.service.payments.h5.model.PrepayRequest();
        request.setAppid(wechat.miniapp().appid());
        request.setMchid(pay.mchId());
        request.setDescription("聊大外卖-H5-" + order.getOrderNo());
        request.setOutTradeNo(payment.getOutTradeNo());
        request.setNotifyUrl(pay.notifyUrl());
        com.wechat.pay.java.service.payments.h5.model.Amount amount =
                new com.wechat.pay.java.service.payments.h5.model.Amount();
        amount.setTotal(payment.getAmount());
        amount.setCurrency("CNY");
        request.setAmount(amount);

        com.wechat.pay.java.service.payments.h5.model.H5Info h5Info =
                new com.wechat.pay.java.service.payments.h5.model.H5Info();
        h5Info.setType("Wap");
        h5Info.setAppName("聊大外卖");
        com.wechat.pay.java.service.payments.h5.model.SceneInfo sceneInfo =
                new com.wechat.pay.java.service.payments.h5.model.SceneInfo();
        sceneInfo.setPayerClientIp(clientIp == null || clientIp.isBlank() ? "127.0.0.1" : clientIp);
        sceneInfo.setH5Info(h5Info);
        request.setSceneInfo(sceneInfo);

        try {
            com.wechat.pay.java.service.payments.h5.model.PrepayResponse resp = h5Service.prepay(request);
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mock", false);
            params.put("channel", "h5");
            params.put("h5Url", resp.getH5Url());
            params.put("outTradeNo", payment.getOutTradeNo());
            params.put("amount", payment.getAmount());
            log.info("H5 下单成功 outTradeNo={} amount={}", payment.getOutTradeNo(), payment.getAmount());
            return new PrepayResult(null, params, false);
        } catch (Exception e) {
            log.error("H5 下单失败 outTradeNo={}", payment.getOutTradeNo(), e);
            throw new BizException(ErrorCode.PAY_FAILED, "微信 H5 下单失败：" + rootMessage(e));
        }
    }

    /** PC 扫码支付（Native）：返回 code_url，前端渲染成二维码。 */
    @Override
    public PrepayResult prepayNative(Payment payment, Order order) {
        WechatProperties.Pay pay = wechat.pay();
        com.wechat.pay.java.service.payments.nativepay.model.PrepayRequest request =
                new com.wechat.pay.java.service.payments.nativepay.model.PrepayRequest();
        request.setAppid(wechat.miniapp().appid());
        request.setMchid(pay.mchId());
        request.setDescription("聊大外卖-扫码-" + order.getOrderNo());
        request.setOutTradeNo(payment.getOutTradeNo());
        request.setNotifyUrl(pay.notifyUrl());
        com.wechat.pay.java.service.payments.nativepay.model.Amount amount =
                new com.wechat.pay.java.service.payments.nativepay.model.Amount();
        amount.setTotal(payment.getAmount());
        amount.setCurrency("CNY");
        request.setAmount(amount);
        try {
            com.wechat.pay.java.service.payments.nativepay.model.PrepayResponse resp =
                    nativePayService.prepay(request);
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mock", false);
            params.put("channel", "native");
            params.put("codeUrl", resp.getCodeUrl());
            params.put("outTradeNo", payment.getOutTradeNo());
            params.put("amount", payment.getAmount());
            log.info("Native 下单成功 outTradeNo={} amount={}", payment.getOutTradeNo(), payment.getAmount());
            return new PrepayResult(null, params, false);
        } catch (Exception e) {
            log.error("Native 下单失败 outTradeNo={}", payment.getOutTradeNo(), e);
            throw new BizException(ErrorCode.PAY_FAILED, "微信扫码下单失败：" + rootMessage(e));
        }
    }

    /**
     * 微信内置浏览器（公众号）里的 JSAPI 支付：appid 用公众号的，openid 用 mp_openid。
     * 签名参数结构与小程序一致，前端同样用 WeixinJSBridge 调起。
     */
    @Override
    public PrepayResult prepayMpJsapi(Payment payment, Order order, String mpOpenid) {
        WechatProperties.Official official = wechat.official();
        if (official == null || official.appid() == null || official.appid().isBlank()) {
            throw new BizException(ErrorCode.PAY_FAILED, "未配置公众号 AppID（WX_MP_APPID），微信内无法支付");
        }
        if (mpOpenid == null || mpOpenid.isBlank()) {
            throw new BizException(ErrorCode.PAY_FAILED, "缺少公众号 openid，请在微信内重新授权登录");
        }
        WechatProperties.Pay pay = wechat.pay();
        PrepayRequest request = new PrepayRequest();
        request.setAppid(official.appid());
        request.setMchid(pay.mchId());
        request.setDescription("聊大外卖-" + order.getOrderNo());
        request.setOutTradeNo(payment.getOutTradeNo());
        request.setNotifyUrl(pay.notifyUrl());
        Amount amount = new Amount();
        amount.setTotal(payment.getAmount());
        amount.setCurrency("CNY");
        request.setAmount(amount);
        Payer payer = new Payer();
        payer.setOpenid(mpOpenid);
        request.setPayer(payer);
        try {
            PrepayWithRequestPaymentResponse resp = jsapi.prepayWithRequestPayment(request);
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mock", false);
            params.put("channel", "mp");
            params.put("appId", official.appid());
            params.put("timeStamp", resp.getTimeStamp());
            params.put("nonceStr", resp.getNonceStr());
            params.put("package", resp.getPackageVal());
            params.put("signType", resp.getSignType());
            params.put("paySign", resp.getPaySign());
            params.put("outTradeNo", payment.getOutTradeNo());
            params.put("amount", payment.getAmount());
            log.info("公众号 JSAPI 下单成功 outTradeNo={}", payment.getOutTradeNo());
            return new PrepayResult(resp.getPackageVal(), params, false);
        } catch (Exception e) {
            log.error("公众号 JSAPI 下单失败 outTradeNo={}", payment.getOutTradeNo(), e);
            throw new BizException(ErrorCode.PAY_FAILED, "微信内下单失败：" + rootMessage(e));
        }
    }

    @Override
    public NotifyResult parseNotify(String body, Map<String, String> headers) {
        try {
            RequestParam requestParam = new RequestParam.Builder()
                    .serialNumber(headers.get("wechatpay-serial"))
                    .nonce(headers.get("wechatpay-nonce"))
                    .signature(headers.get("wechatpay-signature"))
                    .timestamp(headers.get("wechatpay-timestamp"))
                    .body(body)
                    .build();
            // parse 内部完成验签 + AES-GCM 解密；签名不对会抛异常
            Transaction tx = notificationParser.parse(requestParam, Transaction.class);
            boolean success = tx.getTradeState() == Transaction.TradeStateEnum.SUCCESS;
            return new NotifyResult(tx.getOutTradeNo(), tx.getTransactionId(), success);
        } catch (Exception e) {
            log.warn("支付回调验签/解密失败", e);
            throw new BizException(ErrorCode.PAY_FAILED, "回调验签失败：" + rootMessage(e));
        }
    }

    @Override
    public RefundResult refund(Payment payment, String refundNo, int amount, String reason) {
        CreateRequest request = new CreateRequest();
        request.setOutTradeNo(payment.getOutTradeNo());
        request.setOutRefundNo(refundNo);
        request.setReason(reason == null || reason.isBlank() ? "订单退款" : truncate(reason, 80));
        // 退款结果走回调；这里同步拿返回值只是为了拿到 refund_id
        request.setNotifyUrl(wechat.pay().notifyUrl().replace("/api/pay/notify", "/api/pay/refund-notify"));
        AmountReq amountReq = new AmountReq();
        amountReq.setRefund((long) amount);
        amountReq.setTotal((long) payment.getAmount());
        amountReq.setCurrency("CNY");
        request.setAmount(amountReq);
        try {
            Refund refund = refundService.create(request);
            log.info("退款申请提交 outTradeNo={} refundNo={} amount={} status={}",
                    payment.getOutTradeNo(), refundNo, amount, refund.getStatus());
            return new RefundResult(true, "已提交微信退款（" + refund.getStatus() + "）", refund.getRefundId());
        } catch (Exception e) {
            log.error("退款申请失败 outTradeNo={} refundNo={}", payment.getOutTradeNo(), refundNo, e);
            return new RefundResult(false, rootMessage(e), null);
        }
    }

    @Override
    public RefundNotifyResult parseRefundNotify(String body, Map<String, String> headers) {
        try {
            RequestParam requestParam = new RequestParam.Builder()
                    .serialNumber(headers.get("wechatpay-serial"))
                    .nonce(headers.get("wechatpay-nonce"))
                    .signature(headers.get("wechatpay-signature"))
                    .timestamp(headers.get("wechatpay-timestamp"))
                    .body(body)
                    .build();
            com.wechat.pay.java.service.refund.model.RefundNotification n =
                    notificationParser.parse(requestParam,
                            com.wechat.pay.java.service.refund.model.RefundNotification.class);
            boolean success = n.getRefundStatus()
                    == com.wechat.pay.java.service.refund.model.Status.SUCCESS;
            return new RefundNotifyResult(n.getOutRefundNo(), n.getRefundId(), success,
                    success ? "退款成功" : String.valueOf(n.getRefundStatus()));
        } catch (Exception e) {
            log.warn("退款通知验签/解密失败", e);
            throw new BizException(ErrorCode.PAY_FAILED, "退款回调验签失败：" + rootMessage(e));
        }
    }

    @Override
    public NotifyResult query(String outTradeNo) {
        try {
            com.wechat.pay.java.service.payments.jsapi.model.QueryOrderByOutTradeNoRequest req =
                    new com.wechat.pay.java.service.payments.jsapi.model.QueryOrderByOutTradeNoRequest();
            req.setMchid(wechat.pay().mchId());
            req.setOutTradeNo(outTradeNo);
            Transaction tx = jsapi.queryOrderByOutTradeNo(req);
            boolean success = tx.getTradeState() == Transaction.TradeStateEnum.SUCCESS;
            return new NotifyResult(tx.getOutTradeNo(), tx.getTransactionId(), success);
        } catch (Exception e) {
            log.warn("主动查单失败 outTradeNo={}", outTradeNo, e);
            return null;
        }
    }

    @Override
    public boolean mock() {
        return false;
    }

    private static String safeDesc(Long shopId) {
        return shopId == null ? "店铺" : ("店铺" + shopId);
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        String msg = t.getMessage();
        return msg == null || msg.isBlank() ? t.getClass().getSimpleName() : msg;
    }
}
