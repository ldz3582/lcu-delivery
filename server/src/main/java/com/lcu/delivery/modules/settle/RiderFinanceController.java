package com.lcu.delivery.modules.settle;

import com.lcu.delivery.common.Result;
import com.lcu.delivery.common.UserContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 骑手端资金：钱包、佣金明细、账单统计、提现、处罚记录。 */
@RestController
@RequestMapping("/api/rider")
public class RiderFinanceController {

    private final RiderFinanceService finance;

    public RiderFinanceController(RiderFinanceService finance) {
        this.finance = finance;
    }

    @GetMapping("/wallet")
    public Result<Map<String, Object>> wallet() {
        return Result.ok(finance.wallet(UserContext.userId()));
    }

    /** 佣金明细（按单）+ 账单汇总 */
    @GetMapping("/settlements")
    public Result<Map<String, Object>> settlements(@RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return Result.ok(finance.settlements(UserContext.userId(), page, size));
    }

    @GetMapping("/summary")
    public Result<Map<String, Object>> summary() {
        return Result.ok(finance.summary(UserContext.userId()));
    }

    @GetMapping("/withdrawals")
    public Result<List<Map<String, Object>>> withdrawals() {
        return Result.ok(finance.withdrawals(UserContext.userId()));
    }

    public record WithdrawReq(Integer amount) {
    }

    @PostMapping("/withdrawals")
    public Result<Long> apply(@RequestBody WithdrawReq req) {
        return Result.ok(finance.applyWithdraw(UserContext.userId(), req.amount()));
    }

    @GetMapping("/penalties")
    public Result<List<Map<String, Object>>> penalties() {
        return Result.ok(finance.penalties(UserContext.userId()));
    }
}
