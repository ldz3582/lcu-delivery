package com.lcu.delivery.modules.pay;

import com.lcu.delivery.config.WechatProperties;
import com.wechat.pay.java.core.http.DefaultHttpClientBuilder;
import com.wechat.pay.java.core.http.HttpClient;
import com.wechat.pay.java.core.http.HttpMethod;
import com.wechat.pay.java.core.http.HttpRequest;
import com.wechat.pay.java.core.http.HttpResponse;
import com.wechat.pay.java.core.http.JsonRequestBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 微信支付「商家转账」打款实现。
 *
 * <p>接口：POST /v3/fund-app/mch-transfer/transfer-bills
 * 需要商户平台已开通「商家转账」，并申请到 transfer_scene_id。
 *
 * <p>注意：调用成功只代表**受理**（state=ACCEPTED/PROCESSING），真正到账取决于用户确认领取
 * 与微信侧处理，最终以异步通知或查单结果为准。
 */
@Component
@ConditionalOnProperty(name = "app.mock-pay", havingValue = "false")
public class WechatPayoutGateway implements PayoutGateway {

    private static final Logger log = LoggerFactory.getLogger(WechatPayoutGateway.class);
    private static final String URL = "https://api.mch.weixin.qq.com/v3/fund-app/mch-transfer/transfer-bills";

    private final WechatProperties wechat;
    private final HttpClient http;
    private final com.wechat.pay.java.core.notification.NotificationParser parser;

    public WechatPayoutGateway(WechatProperties wechat, HttpClient http,
                               com.wechat.pay.java.core.notification.NotificationParser parser) {
        this.wechat = wechat;
        this.http = http;
        this.parser = parser;
    }

    @Override
    @SuppressWarnings("unchecked")
    public PayoutResult transfer(String outBillNo, String openid, int amount, String remark, String sceneId) {
        if (openid == null || openid.isBlank()) {
            return new PayoutResult(false, "收款人缺少 openid：需商家/骑手先用小程序登录过一次", null, null);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("appid", wechat.miniapp().appid());
        body.put("out_bill_no", outBillNo);
        body.put("transfer_scene_id", sceneId == null || sceneId.isBlank() ? "1000" : sceneId);
        body.put("openid", openid);
        body.put("transfer_amount", amount);
        body.put("transfer_remark", remark == null ? "结算打款" : trim(remark, 32));
        body.put("notify_url", wechat.pay().notifyUrl().replace("/api/pay/notify", "/api/pay/transfer-notify"));
        try {
            HttpRequest request = new HttpRequest.Builder()
                    .httpMethod(HttpMethod.POST)
                    .url(URL)
                    .body(new JsonRequestBody.Builder().body(toJson(body)).build())
                    .build();
            HttpResponse<Map> resp = http.execute(request, Map.class);
            Map<String, Object> data = resp.getServiceResponse();
            String state = data == null ? null : String.valueOf(data.get("state"));
            String billNo = data == null ? null : String.valueOf(data.get("transfer_bill_no"));
            // ACCEPTED / PROCESSING 都算受理成功，SUCCESS 是即时到账
            boolean ok = "ACCEPTED".equalsIgnoreCase(state)
                    || "PROCESSING".equalsIgnoreCase(state)
                    || "SUCCESS".equalsIgnoreCase(state);
            log.info("商家转账提交 outBillNo={} amount={} state={} billNo={}", outBillNo, amount, state, billNo);
            return new PayoutResult(ok, ok ? "已受理（" + state + "）" : "微信返回 state=" + state, billNo, state);
        } catch (Exception e) {
            log.error("商家转账失败 outBillNo={}", outBillNo, e);
            return new PayoutResult(false, "转账失败：" + rootMessage(e), null, null);
        }
    }

    @Override
    public boolean mock() {
        return false;
    }

    @Override
    @SuppressWarnings("unchecked")
    public TransferNotifyResult parseNotify(String body, Map<String, String> headers) {
        try {
            com.wechat.pay.java.core.notification.RequestParam rp =
                    new com.wechat.pay.java.core.notification.RequestParam.Builder()
                            .serialNumber(headers.get("wechatpay-serial"))
                            .nonce(headers.get("wechatpay-nonce"))
                            .signature(headers.get("wechatpay-signature"))
                            .timestamp(headers.get("wechatpay-timestamp"))
                            .body(body)
                            .build();
            Map<String, Object> data = parser.parse(rp, Map.class);
            String outBillNo = data == null ? null : String.valueOf(data.get("out_bill_no"));
            String state = data == null ? null : String.valueOf(data.get("state"));
            boolean ok = "SUCCESS".equalsIgnoreCase(state);
            String msg = data == null ? "空响应" : String.valueOf(data.getOrDefault("fail_reason", state));
            log.info("转账通知 outBillNo={} state={}", outBillNo, state);
            return new TransferNotifyResult(outBillNo, ok, msg);
        } catch (Exception e) {
            log.warn("转账通知验签/解密失败", e);
            return new TransferNotifyResult(null, false, "验签失败：" + rootMessage(e));
        }
    }

    private static String toJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (e.getValue() == null) {
                continue;
            }
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(e.getKey()).append("\":");
            Object v = e.getValue();
            if (v instanceof Number || v instanceof Boolean) {
                sb.append(v);
            } else {
                sb.append('"').append(String.valueOf(v).replace("\\", "\\\\").replace("\"", "\\\""))
                        .append('"');
            }
        }
        return sb.append('}').toString();
    }

    private static String trim(String s, int max) {
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
