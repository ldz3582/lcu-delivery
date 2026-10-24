package com.lcu.delivery.modules.admin;

import com.lcu.delivery.common.PageResult;
import com.lcu.delivery.common.Result;
import com.lcu.delivery.common.UserContext;
import com.lcu.delivery.modules.settle.MerchantFinanceService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 平台侧提现审核（商家/骑手提现都走这里）。 */
@RestController
@RequestMapping("/api/admin/withdrawals")
public class AdminWithdrawController {

    private final MerchantFinanceService finance;

    public AdminWithdrawController(MerchantFinanceService finance) {
        this.finance = finance;
    }

    @GetMapping
    public Result<PageResult<Map<String, Object>>> list(@RequestParam(required = false) Integer status,
                                                        @RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        return Result.ok(finance.adminWithdrawals(status, page, size));
    }

    public record AuditReq(Boolean pass, String reason) {
    }

    @PostMapping("/{id}/audit")
    public Result<Void> audit(@PathVariable Long id, @RequestBody AuditReq req) {
        finance.auditWithdraw(UserContext.userId(), id, Boolean.TRUE.equals(req.pass()), req.reason());
        return Result.ok();
    }
}
