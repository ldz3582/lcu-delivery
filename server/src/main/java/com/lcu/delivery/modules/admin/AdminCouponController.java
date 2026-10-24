package com.lcu.delivery.modules.admin;

import com.lcu.delivery.common.PageResult;
import com.lcu.delivery.common.Result;
import com.lcu.delivery.modules.coupon.CouponService;
import com.lcu.delivery.modules.coupon.entity.Coupon;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/** 管理后台：优惠券模板管理。 */
@RestController
@RequestMapping("/api/admin/coupons")
public class AdminCouponController {

    private final CouponService couponService;

    public AdminCouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    public record CreateReq(String name, Integer type, Integer threshold, Integer amount,
                            Integer total, Integer scope, Long shopId,
                            LocalDateTime startAt, LocalDateTime endAt) {
    }

    @GetMapping
    public Result<PageResult<Map<String, Object>>> list(@RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        return Result.ok(couponService.adminList(page, size));
    }

    @PostMapping
    public Result<Long> create(@RequestBody CreateReq req) {
        Coupon c = couponService.create(req.name(),
                req.type() == null ? 1 : req.type(),
                req.threshold() == null ? 0 : req.threshold(),
                req.amount() == null ? 0 : req.amount(),
                req.total() == null ? 0 : req.total(),
                req.scope() == null ? 1 : req.scope(),
                req.shopId(), req.startAt(), req.endAt());
        return Result.ok(c.getId());
    }

    public record StatusReq(Integer status) {
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody StatusReq req) {
        couponService.updateStatus(id, req.status() == null ? 0 : req.status());
        return Result.ok();
    }
}
