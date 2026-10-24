package com.lcu.delivery.modules.aftersale;

import com.lcu.delivery.common.PageResult;
import com.lcu.delivery.common.Result;
import com.lcu.delivery.common.UserContext;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 售后接口。路径前缀混用是为了对齐既有鉴权规则（/api/merchant/** 商家、/api/admin/** 管理员）。
 */
@RestController
@RequestMapping("/api")
public class AfterSaleController {

    private final AfterSaleService afterSaleService;

    public AfterSaleController(AfterSaleService afterSaleService) {
        this.afterSaleService = afterSaleService;
    }

    @PostMapping("/after-sales")
    public Result<Long> create(@RequestBody AfterSaleService.CreateReq req) {
        return Result.ok(afterSaleService.create(UserContext.userId(), req));
    }

    @GetMapping("/after-sales/mine")
    public Result<PageResult<Map<String, Object>>> mine(@RequestParam(required = false) Integer status,
                                                        @RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "10") int size) {
        return Result.ok(afterSaleService.mine(UserContext.userId(), status, page, size));
    }

    @GetMapping("/after-sales/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.ok(afterSaleService.detail(UserContext.userId(), id, false));
    }

    @PostMapping("/after-sales/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        afterSaleService.cancel(UserContext.userId(), id);
        return Result.ok();
    }

    // ---------------- 商家 ----------------

    @GetMapping("/merchant/after-sales")
    public Result<PageResult<Map<String, Object>>> merchantList(@RequestParam(required = false) Integer status,
                                                                @RequestParam(defaultValue = "1") int page,
                                                                @RequestParam(defaultValue = "20") int size) {
        return Result.ok(afterSaleService.merchantList(UserContext.userId(), status, page, size));
    }

    public record HandleReq(Boolean agree, String remark) {
    }

    @PostMapping("/merchant/after-sales/{id}/handle")
    public Result<Void> handle(@PathVariable Long id, @RequestBody HandleReq req) {
        afterSaleService.merchantHandle(UserContext.userId(), id,
                Boolean.TRUE.equals(req.agree()), req.remark());
        return Result.ok();
    }

    // ---------------- 平台仲裁 ----------------

    @GetMapping("/admin/after-sales")
    public Result<PageResult<Map<String, Object>>> adminList(@RequestParam(required = false) Integer status,
                                                             @RequestParam(defaultValue = "1") int page,
                                                             @RequestParam(defaultValue = "20") int size) {
        return Result.ok(afterSaleService.adminList(status, page, size));
    }

    @GetMapping("/admin/after-sales/{id}")
    public Result<Map<String, Object>> adminDetail(@PathVariable Long id) {
        return Result.ok(afterSaleService.detail(UserContext.userId(), id, true));
    }

    public record JudgeReq(Boolean approve, Integer amount, Integer liableParty, String remark) {
    }

    @PostMapping("/admin/after-sales/{id}/judge")
    public Result<Void> judge(@PathVariable Long id, @RequestBody JudgeReq req) {
        afterSaleService.judge(UserContext.userId(), id, Boolean.TRUE.equals(req.approve()),
                req.amount(), req.liableParty(), req.remark());
        return Result.ok();
    }
}
