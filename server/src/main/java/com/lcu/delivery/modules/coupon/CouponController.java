package com.lcu.delivery.modules.coupon;

import com.lcu.delivery.common.Result;
import com.lcu.delivery.common.UserContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 用户端：领券中心、我的券。 */
@RestController
@RequestMapping("/api/coupons")
public class CouponController {

    private final CouponService couponService;

    public CouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    /** 还能领的券；带 shopId 时额外返回该店铺的专属券。 */
    @GetMapping("/available")
    public Result<List<Map<String, Object>>> available(@RequestParam(required = false) Long shopId) {
        return Result.ok(couponService.available(UserContext.userId(), shopId));
    }

    @PostMapping("/{id}/claim")
    public Result<Void> claim(@PathVariable Long id) {
        couponService.claim(UserContext.userId(), id);
        return Result.ok();
    }

    /** 我的券。shopId + amount（商品金额，分）用来算这一单能不能用。 */
    @GetMapping("/mine")
    public Result<List<Map<String, Object>>> mine(@RequestParam(required = false) Integer status,
                                                 @RequestParam(required = false) Long shopId,
                                                 @RequestParam(required = false) Integer amount) {
        return Result.ok(couponService.mine(UserContext.userId(), status, shopId, amount));
    }
}
