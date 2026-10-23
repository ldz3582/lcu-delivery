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
        return Result.ok(shopService.detail(id));
    }

    @GetMapping("/shops/{id}/products")
    public Result<List<Map<String, Object>>> products(@PathVariable Long id) {
        return Result.ok(shopService.products(id));
    }
}
