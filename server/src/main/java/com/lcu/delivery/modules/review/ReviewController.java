package com.lcu.delivery.modules.review;

import com.lcu.delivery.common.PageResult;
import com.lcu.delivery.common.Result;
import com.lcu.delivery.common.UserContext;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 评价相关接口。路径前缀混用是为了跟既有鉴权规则对齐：
 * /api/shops/** 公开、/api/merchant/** 需商家、/api/admin/** 需管理员。
 */
@RestController
@RequestMapping("/api")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping("/reviews")
    public Result<Long> create(@RequestBody ReviewService.CreateReq req) {
        return Result.ok(reviewService.create(UserContext.userId(), req));
    }

    @GetMapping("/reviews/mine")
    public Result<PageResult<Map<String, Object>>> mine(@RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "10") int size) {
        return Result.ok(reviewService.mine(UserContext.userId(), page, size));
    }

    @GetMapping("/reviews/order/{orderId}")
    public Result<Map<String, Object>> byOrder(@PathVariable Long orderId) {
        return Result.ok(reviewService.byOrder(UserContext.userId(), orderId));
    }

    /** 店铺详情页评价列表（公开，配合 /api/shops/** 免登录规则）。 */
    @GetMapping("/shops/{shopId}/reviews")
    public Result<PageResult<Map<String, Object>>> listByShop(@PathVariable Long shopId,
                                                              @RequestParam(defaultValue = "1") int page,
                                                              @RequestParam(defaultValue = "10") int size) {
        return Result.ok(reviewService.listByShop(shopId, page, size));
    }

    public record ReplyReq(String reply) {
    }

    @PostMapping("/merchant/reviews/{id}/reply")
    public Result<Void> reply(@PathVariable Long id, @RequestBody ReplyReq req) {
        reviewService.reply(UserContext.userId(), id, req.reply());
        return Result.ok();
    }

    @GetMapping("/admin/reviews")
    public Result<PageResult<Map<String, Object>>> adminList(@RequestParam(required = false) Integer status,
                                                             @RequestParam(required = false) Integer score,
                                                             @RequestParam(defaultValue = "1") int page,
                                                             @RequestParam(defaultValue = "20") int size) {
        return Result.ok(reviewService.adminList(status, score, page, size));
    }

    public record StatusReq(Integer status) {
    }

    @PutMapping("/admin/reviews/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody StatusReq req) {
        reviewService.updateStatus(id, req.status() == null ? 1 : req.status());
        return Result.ok();
    }
}
