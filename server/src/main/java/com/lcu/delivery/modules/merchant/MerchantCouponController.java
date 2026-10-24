package com.lcu.delivery.modules.merchant;

import com.lcu.delivery.common.Result;
import com.lcu.delivery.common.UserContext;
import com.lcu.delivery.modules.coupon.CouponService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 商家自建店铺券（满减 / 折扣），只能作用于本店。 */
@RestController
@RequestMapping("/api/merchant/coupons")
public class MerchantCouponController {

    private final CouponService couponService;

    public MerchantCouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    @GetMapping
    public Result<List<Map<String, Object>>> list() {
        return Result.ok(couponService.merchantList(UserContext.userId()));
    }

    public record CreateReq(String name, Integer type, Integer threshold, Integer amount,
                            Integer total, LocalDateTime startAt, LocalDateTime endAt) {
    }

    @PostMapping
    public Result<Long> create(@RequestBody CreateReq req) {
        return Result.ok(couponService.merchantCreate(UserContext.userId(), req.name(),
                req.type() == null ? 1 : req.type(),
                req.threshold() == null ? 0 : req.threshold(),
                req.amount() == null ? 0 : req.amount(),
                req.total() == null ? 0 : req.total(),
                req.startAt(), req.endAt()));
    }

    public record StatusReq(Integer status) {
    }

    @PutMapping("/{id}/status")
    public Result<Void> status(@PathVariable Long id, @RequestBody StatusReq req) {
        couponService.merchantToggle(UserContext.userId(), id, req.status() == null ? 1 : req.status());
        return Result.ok();
    }
}
