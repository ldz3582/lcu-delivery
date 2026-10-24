package com.lcu.delivery.modules.admin;

import com.lcu.delivery.common.Result;
import com.lcu.delivery.modules.settle.RiderFinanceService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 骑手处罚：超时自动生成 + 平台手动开罚单。 */
@RestController
@RequestMapping("/api/admin")
public class AdminPenaltyController {

    private final RiderFinanceService finance;

    public AdminPenaltyController(RiderFinanceService finance) {
        this.finance = finance;
    }

    @GetMapping("/penalties")
    public Result<List<Map<String, Object>>> list(@RequestParam(required = false) Long riderId) {
        return Result.ok(finance.allPenalties(riderId));
    }

    public record PenaltyReq(Integer type, Integer amount, String remark, Integer orderId) {
    }

    @PostMapping("/riders/{riderId}/penalty")
    public Result<Long> create(@PathVariable Long riderId, @RequestBody PenaltyReq req) {
        return Result.ok(finance.addPenalty(riderId, req.orderId() == null ? null : req.orderId().longValue(),
                req.type() == null ? 3 : req.type(),
                req.amount() == null ? 0 : req.amount(), req.remark()));
    }
}
