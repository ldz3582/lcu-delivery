package com.lcu.delivery.modules.settle;

import com.lcu.delivery.common.Result;
import com.lcu.delivery.common.UserContext;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** 商家端资金：钱包、营收统计、对账单、提现。 */
@RestController
@RequestMapping("/api/merchant")
public class MerchantFinanceController {

    private final MerchantFinanceService finance;

    public MerchantFinanceController(MerchantFinanceService finance) {
        this.finance = finance;
    }

    @GetMapping("/wallet")
    public Result<Map<String, Object>> wallet() {
        return Result.ok(finance.wallet(UserContext.userId()));
    }

    /** 营收统计：今日 / 本周 / 本月（只算已完成订单） */
    @GetMapping("/summary")
    public Result<Map<String, Object>> summary() {
        return Result.ok(finance.summary(UserContext.userId()));
    }

    @GetMapping("/statements")
    public Result<Map<String, Object>> statements(@RequestParam(required = false) String from,
                                                  @RequestParam(required = false) String to,
                                                  @RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        LocalDate f = from == null || from.isBlank() ? null : LocalDate.parse(from);
        LocalDate t = to == null || to.isBlank() ? null : LocalDate.parse(to);
        return Result.ok(finance.statements(UserContext.userId(), f, t, page, size));
    }

    @GetMapping("/withdrawals")
    public Result<List<Map<String, Object>>> withdrawals() {
        return Result.ok(finance.withdrawals(UserContext.userId()));
    }

    public record WithdrawReq(Integer amount) {
    }

    /** 申请提现（单位分）：可用余额转冻结，等平台审核打款。 */
    @PostMapping("/withdrawals")
    public Result<Long> apply(@RequestBody WithdrawReq req) {
        return Result.ok(finance.applyWithdraw(UserContext.userId(), req.amount()));
    }

}
