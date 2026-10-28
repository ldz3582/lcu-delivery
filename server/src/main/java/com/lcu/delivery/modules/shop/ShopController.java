package com.lcu.delivery.modules.shop;

import com.lcu.delivery.common.PageResult;
import com.lcu.delivery.common.Result;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ShopController {

    private final ShopService shopService;

    public ShopController(ShopService shopService) {
        this.shopService = shopService;
    }

    @GetMapping("/categories")
    public Result<List<Map<String, Object>>> categories() {
        return Result.ok(shopService.categories());
    }

    @GetMapping("/shops")
    public Result<PageResult<Map<String, Object>>> shops(
            @RequestParam(required = false) Integer campus,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) BigDecimal lng,
            @RequestParam(required = false) BigDecimal lat,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(shopService.list(campus, categoryId, keyword, lng, lat, page, size));
    }

    @GetMapping("/shops/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        // 公开接口：登录了就带上 userId，用于标记店铺券「已领」
        com.lcu.delivery.common.AuthUser u = com.lcu.delivery.common.UserContext.get();
        return Result.ok(shopService.detail(id, u == null ? null : u.id()));
    }

    @GetMapping("/shops/{id}/products")
    public Result<List<Map<String, Object>>> products(@PathVariable Long id) {
        return Result.ok(shopService.products(id));
    }

    /** 首页「限时特价」专区 */
    @GetMapping("/specials")
    public Result<List<Map<String, Object>>> specials(@RequestParam(defaultValue = "10") int limit) {
        return Result.ok(shopService.specials(limit));
    }
}
