package com.lcu.delivery.modules.pay;

import com.lcu.delivery.common.Result;
import com.lcu.delivery.common.UserContext;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/pay")
public class PayController {

    private final PayService payService;

    public PayController(PayService payService) {
        this.payService = payService;
    }

    public record PrepayReq(Long orderId) {
    }

    @PostMapping("/prepay")
    public Result<Map<String, Object>> prepay(@RequestBody PrepayReq req) {
        return Result.ok(payService.prepay(UserContext.userId(), req.orderId()));
    }

    @PostMapping("/mock-pay")
    public Result<Map<String, Object>> mockPay(@RequestBody PrepayReq req) {
        return Result.ok(payService.mockPay(UserContext.userId(), req.orderId()));
    }

    @PostMapping("/notify")
    public Map<String, Object> notify(@RequestBody String body) {
        return payService.notify(body);
    }

    public record RefundReq(Long orderId, Integer amount, String reason) {
    }

    @PostMapping("/refund")
    public Result<Void> refund(@RequestBody RefundReq req) {
        payService.refund(req.orderId(), req.amount(), req.reason(), UserContext.userId());
        return Result.ok();
    }
}
