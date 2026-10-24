package com.lcu.delivery.modules.favorite;

import com.lcu.delivery.common.Result;
import com.lcu.delivery.common.UserContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 收藏店铺：切换、检查、我的收藏列表。 */
@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @GetMapping
    public Result<List<Map<String, Object>>> list() {
        return Result.ok(favoriteService.list(UserContext.userId()));
    }

    @GetMapping("/check")
    public Result<Map<String, Object>> check(@RequestParam Long shopId) {
        return Result.ok(Map.of("shopId", shopId, "favorited", favoriteService.check(UserContext.userId(), shopId)));
    }

    /** 收藏 / 取消收藏（同一个接口切换）。 */
    @PostMapping("/{shopId}/toggle")
    public Result<Map<String, Object>> toggle(@PathVariable Long shopId) {
        boolean favorited = favoriteService.toggle(UserContext.userId(), shopId);
        return Result.ok(Map.of("shopId", shopId, "favorited", favorited));
    }

    @DeleteMapping("/{shopId}")
    public Result<Void> remove(@PathVariable Long shopId) {
        favoriteService.remove(UserContext.userId(), shopId);
        return Result.ok();
    }
}
