package com.lcu.delivery.modules.pay;

import com.lcu.delivery.common.BizException;
import com.lcu.delivery.common.ErrorCode;
import com.lcu.delivery.modules.order.entity.Order;
import com.lcu.delivery.modules.pay.entity.Payment;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.core.http.HttpMethod;
import com.wechat.pay.java.core.http.HttpRequest;
import com.wechat.pay.java.core.http.HttpResponse;
import com.wechat.pay.java.core.http.JsonRequestBody;
import com.wechat.pay.java.service.payments.jsapi.model.Amount;
import com.wechat.pay.java.service.payments.jsapi.model.Payer;
import com.wechat.pay.java.service.payments.jsapi.model.PrepayRequest;
import com.wechat.pay.java.service.payments.jsapi.model.PrepayWithRequestPaymentResponse;
import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.refund.model.AmountReq;
import com.wechat.pay.java.service.refund.model.CreateRequest;
import com.wechat.pay.java.service.refund.model.Refund;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 微信支付 v3（真实通道）。
 *
 * <p>由 {@link PayGatewayProvider} 按当前生效配置实例化，配置变更会重建，
 * 所以这里不持有任何 Spring 注入的东西，只依赖 {@link WechatPaySettings} + SDK 客户端。
 *
 * <p>资金路径（A 方案·平台代收）：
 * 用户 → 平台商户号（全额）→ 内部 settlement 记账 → 商家/骑手钱包 → 提现打款。
 * out_trade_no 是「支付单号」，收款方固定是平台商户号，不涉及子商户。
 */
public class WechatPayGateway implements PayGateway {

    private static final Logger log = LoggerFactory.getLogger(WechatPayGateway.class);
    private static final String TRANSFER_URL =
            "https://api.mch.weixin.qq.com/v3/fund-app/mch-transfer/transfer-bills";

    private final WechatPaySettings settings;
    private final WechatPaySdkFactory.Sdk sdk;

    public WechatPayGateway(WechatPaySettings settings, WechatPaySdkFactory.Sdk sdk) {
        this.settings = settings;
        this.sdk = sdk;
    }

    // ---------------------------------------------------------------- 小程序 JSAPI

    @Override
    public PrepayResult prepay(Payment payment, Order order, String openid) {
        if (openid == null || openid.isBlank()) {
            throw new BizException(ErrorCode.PAY_FAILED, "缺少 openid，微信 JSAPI 下单必须带 openid");
        }
        PrepayRequest request = new PrepayRequest();
        request.setAppid(settings.appId());
        request.setMchid(settings.mchId());
        request.setDescription("聊大外卖-" + order.getOrderNo());
        request.setOutTradeNo(payment.getOutTradeNo());
        request.setNotifyUrl(settings.notifyUrl());
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
            PrepayWithRequestPaymentResponse resp = sdk.jsapi().prepayWithRequestPayment(request);
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mock", false);
            params.put("channel", "miniapp");
            params.put("appId", settings.appId());
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

    // ---------------------------------------------------------------- 微信内 H5（公众号 JSAPI）

    @Override
    public PrepayResult prepayMpJsapi(Payment payment, Order order, String mpOpenid) {
        if (settings.mpAppId() == null || settings.mpAppId().isBlank()) {
            throw new BizException(ErrorCode.PAY_FAILED, "未配置公众号 AppID，微信内无法支付");
        }
        if (mpOpenid == null || mpOpenid.isBlank()) {
            throw new BizException(ErrorCode.PAY_FAILED, "缺少公众号 openid，请在微信内重新授权登录");
        }
        PrepayRequest request = new PrepayRequest();
        request.setAppid(settings.mpAppId());
        request.setMchid(settings.mchId());
        request.setDescription("聊大外卖-" + order.getOrderNo());
        request.setOutTradeNo(payment.getOutTradeNo());
        request.setNotifyUrl(settings.notifyUrl());
        Amount amount = new Amount();
        amount.setTotal(payment.getAmount());
        amount.setCurrency("CNY");
        request.setAmount(amount);
        Payer payer = new Payer();
        payer.setOpenid(mpOpenid);
        request.setPayer(payer);
        try {
            PrepayWithRequestPaymentResponse resp = sdk.jsapi().prepayWithRequestPayment(request);
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mock", false);
            params.put("channel", "mp");
            params.put("appId", settings.mpAppId());
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

    // ---------------------------------------------------------------- 微信外 H5（MWEB）

    @Override
    public PrepayResult prepayH5(Payment payment, Order order, String clientIp) {
        com.wechat.pay.java.service.payments.h5.model.PrepayRequest request =
                new com.wechat.pay.java.service.payments.h5.model.PrepayRequest();
        request.setAppid(settings.appId());
        request.setMchid(settings.mchId());
        request.setDescription("聊大外卖-H5-" + order.getOrderNo());
        request.setOutTradeNo(payment.getOutTradeNo());
        request.setNotifyUrl(settings.notifyUrl());
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
            com.wechat.pay.java.service.payments.h5.model.PrepayResponse resp = sdk.h5().prepay(request);
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

    // ---------------------------------------------------------------- PC 扫码

    @Override
    public PrepayResult prepayNative(Payment payment, Order order) {
        com.wechat.pay.java.service.payments.nativepay.model.PrepayRequest request =
                new com.wechat.pay.java.service.payments.nativepay.model.PrepayRequest();
        request.setAppid(settings.appId());
        request.setMchid(settings.mchId());
        request.setDescription("聊大外卖-扫码-" + order.getOrderNo());
        request.setOutTradeNo(payment.getOutTradeNo());
        request.setNotifyUrl(settings.notifyUrl());
        com.wechat.pay.java.service.payments.nativepay.model.Amount amount =
                new com.wechat.pay.java.service.payments.nativepay.model.Amount();
        amount.setTotal(payment.getAmount());
        amount.setCurrency("CNY");
        request.setAmount(amount);
        try {
            com.wechat.pay.java.service.payments.nativepay.model.PrepayResponse resp =
                    sdk.nativePay().prepay(request);
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("mock", false);
            params.put("channel", "native");
            params.put("codeUrl", resp.getCodeUrl());
            params.put("outTradeNo", payment.getOutTradeNo());
            params.put("amount", payment.getAmount());
            log.info("Native 下单成功 outTradeNo={}", payment.getOutTradeNo());
            return new PrepayResult(null, params, false);
        } catch (Exception e) {
            log.error("Native 下单失败 outTradeNo={}", payment.getOutTradeNo(), e);
            throw new BizException(ErrorCode.PAY_FAILED, "微信扫码下单失败：" + rootMessage(e));
        }
    }

    // ---------------------------------------------------------------- 回调

    @Override
    public NotifyResult parseNotify(String body, Map<String, String> headers) {
        try {
            RequestParam rp = new RequestParam.Builder()
                    .serialNumber(headers.get("wechatpay-serial"))
                    .nonce(headers.get("wechatpay-nonce"))
                    .signature(headers.get("wechatpay-signature"))
                    .timestamp(headers.get("wechatpay-timestamp"))
                    .body(body)
                    .build();
            Transaction tx = sdk.parser().parse(rp, Transaction.class);
            boolean success = tx.getTradeState() == Transaction.TradeStateEnum.SUCCESS;
            return new NotifyResult(tx.getOutTradeNo(), tx.getTransactionId(), success);
        } catch (Exception e) {
            log.warn("支付回调验签/解密失败", e);
            throw new BizException(ErrorCode.PAY_FAILED, "回调验签失败：" + rootMessage(e));
        }
    }

    @Override
    public RefundNotifyResult parseRefundNotify(String body, Map<String, String> headers) {
        try {
            RequestParam rp = new RequestParam.Builder()
                    .serialNumber(headers.get("wechatpay-serial"))
                    .nonce(headers.get("wechatpay-nonce"))
                    .signature(headers.get("wechatpay-signature"))
                    .timestamp(headers.get("wechatpay-timestamp"))
                    .body(body)
                    .build();
            var n = sdk.parser().parse(rp, com.wechat.pay.java.service.refund.model.RefundNotification.class);
            boolean success = n.getRefundStatus()
                    == com.wechat.pay.java.service.refund.model.Status.SUCCESS;
            return new RefundNotifyResult(n.getOutRefundNo(), n.getRefundId(), success,
                    success ? "退款成功" : String.valueOf(n.getRefundStatus()));
        } catch (Exception e) {
            log.warn("退款通知验签/解密失败", e);
            throw new BizException(ErrorCode.PAY_FAILED, "退款回调验签失败：" + rootMessage(e));
        }
    }

    // ---------------------------------------------------------------- 退款 / 查单

    @Override
    public RefundResult refund(Payment payment, String refundNo, int amount, String reason) {
        CreateRequest request = new CreateRequest();
        request.setOutTradeNo(payment.getOutTradeNo());
        request.setOutRefundNo(refundNo);
        request.setReason(reason == null || reason.isBlank() ? "订单退款" : truncate(reason, 80));
        request.setNotifyUrl(settings.notifyUrl().replace("/api/pay/notify", "/api/pay/refund-notify"));
        AmountReq amountReq = new AmountReq();
        amountReq.setRefund((long) amount);
        amountReq.setTotal((long) payment.getAmount());
        amountReq.setCurrency("CNY");
        request.setAmount(amountReq);
        try {
            Refund r = sdk.refund().create(request);
            log.info("退款申请提交 outTradeNo={} refundNo={} amount={} status={}",
                    payment.getOutTradeNo(), refundNo, amount, r.getStatus());
            return new RefundResult(true, "已提交微信退款（" + r.getStatus() + "）", r.getRefundId());
        } catch (Exception e) {
            log.error("退款申请失败 outTradeNo={} refundNo={}", payment.getOutTradeNo(), refundNo, e);
            return new RefundResult(false, rootMessage(e), null);
        }
    }

    @Override
    public NotifyResult query(String outTradeNo) {
        try {
            var req = new com.wechat.pay.java.service.payments.jsapi.model.QueryOrderByOutTradeNoRequest();
            req.setMchid(settings.mchId());
            req.setOutTradeNo(outTradeNo);
            Transaction tx = sdk.jsapi().queryOrderByOutTradeNo(req);
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

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        String m = t.getMessage();
        return m == null || m.isBlank() ? t.getClass().getSimpleName() : m;
    }
}
