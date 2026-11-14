package com.lcu.delivery.modules.pay;

import com.lcu.delivery.common.Result;
import com.lcu.delivery.common.UserContext;
import com.lcu.delivery.modules.settle.MerchantFinanceService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/pay")
public class PayController {

    private final PayService payService;
    private final PayoutGateway payoutGateway;
    private final MerchantFinanceService finance;

    public PayController(PayService payService, PayoutGateway payoutGateway, MerchantFinanceService finance) {
        this.payService = payService;
        this.payoutGateway = payoutGateway;
        this.finance = finance;
    }

    /** orderId 必填；scene = MINIAPP（默认）/ H5 / NATIVE，决定走哪条微信支付通道。 */
    public record PrepayReq(Long orderId, String scene) {
    }

    @PostMapping("/prepay")
    public Result<Map<String, Object>> prepay(@RequestBody PrepayReq req,
                                              jakarta.servlet.http.HttpServletRequest http) {
        return Result.ok(payService.prepay(UserContext.userId(), req.orderId(), req.scene(), clientIp(http)));
    }

    /** 取用户真实出口 IP：H5 支付必填，Nginx 反代后要用 X-Forwarded-For 的第一段。 */
    private static String clientIp(jakarta.servlet.http.HttpServletRequest http) {
        String xff = http.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        String real = http.getHeader("X-Real-IP");
        if (real != null && !real.isBlank()) {
            return real.trim();
        }
        return http.getRemoteAddr();
    }

    @PostMapping("/mock-pay")
    public Result<Map<String, Object>> mockPay(@RequestBody PrepayReq req) {
        return Result.ok(payService.mockPay(UserContext.userId(), req.orderId()));
    }

    /** 前端 wx.requestPayment 成功后调用：主动查单兜底，防止回调延迟导致订单停在待支付。 */
    @PostMapping("/sync")
    public Result<Map<String, Object>> sync(@RequestBody PrepayReq req) {
        return Result.ok(payService.syncPay(UserContext.userId(), req.orderId()));
    }

    /**
     * 微信支付结果通知。必须原样返回 {"code":"SUCCESS"}，否则微信会持续重推。
     * 验签失败会抛异常 → 返回非 2xx，微信按失败重试，这是预期行为。
     */
    @PostMapping("/notify")
    public Map<String, Object> notify(@RequestBody String body,
                                      @RequestHeader Map<String, String> headers) {
        return payService.notify(body, lowerKeys(headers));
    }

    /** 微信退款结果通知。 */
    @PostMapping("/refund-notify")
    public Map<String, Object> refundNotify(@RequestBody String body,
                                            @RequestHeader Map<String, String> headers) {
        return payService.refundNotify(body, lowerKeys(headers));
    }

    /** 微信「商家转账」到账/失败通知：驱动提现单从「打款中」走到终态。 */
    @PostMapping("/transfer-notify")
    public Map<String, Object> transferNotify(@RequestBody String body,
                                              @RequestHeader Map<String, String> headers) {
        PayoutGateway.TransferNotifyResult r = payoutGateway.parseNotify(body, lowerKeys(headers));
        finance.handleTransferNotify(r.outBillNo(), r.success(), r.message());
        return Map.of("code", "SUCCESS", "message", "成功");
    }

    public record RefundReq(Long orderId, Integer amount, String reason) {
    }

    @PostMapping("/refund")
    public Result<Void> refund(@RequestBody RefundReq req) {
        payService.refundForUser(UserContext.userId(), req.orderId(), req.amount(), req.reason());
        return Result.ok();
    }

    /** 微信侧请求头大小写不固定，统一转小写后按 wechatpay-xxx 取值。 */
    private static Map<String, String> lowerKeys(Map<String, String> headers) {
        Map<String, String> out = new HashMap<>();
        if (headers != null) {
            headers.forEach((k, v) -> out.put(k.toLowerCase(Locale.ROOT), v));
        }
        return out;
    }
}
