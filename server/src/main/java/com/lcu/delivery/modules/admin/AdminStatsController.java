package com.lcu.delivery.modules.admin;

import com.lcu.delivery.common.Result;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 数据看板 / 财务总览 / 订单导出。
 * 权限：/api/admin/** 已由 AuthInterceptor 按管理员统一拦截。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminStatsController {

    private final AdminStatsService stats;

    public AdminStatsController(AdminStatsService stats) {
        this.stats = stats;
    }

    /** 订单趋势（默认 7 天，最多 90 天）。 */
    @GetMapping("/stats/trend")
    public Result<List<Map<String, Object>>> trend(@RequestParam(defaultValue = "7") int days) {
        return Result.ok(stats.trend(days));
    }

    /** 商家 / 骑手排行榜。 */
    @GetMapping("/stats/ranking")
    public Result<Map<String, Object>> ranking(@RequestParam(defaultValue = "10") int limit) {
        return Result.ok(Map.of(
                "shops", stats.shopRanking(limit),
                "riders", stats.riderRanking(limit)));
    }

    /** 超时率 / 差评率 / 复购率 / 用户增长。 */
    @GetMapping("/stats/rates")
    public Result<Map<String, Object>> rates() {
        return Result.ok(stats.rates());
    }

    /** 资金总览。 */
    @GetMapping("/finance/overview")
    public Result<Map<String, Object>> financeOverview() {
        return Result.ok(stats.financeOverview());
    }

    /** 资金流水查询。 */
    @GetMapping("/finance/flows")
    public Result<Map<String, Object>> flows(@RequestParam(required = false) String bizType,
                                             @RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return Result.ok(stats.walletFlows(bizType, page, size));
    }

    /** 订单导出 CSV（Excel 可直接打开）。 */
    @GetMapping("/orders/export")
    public ResponseEntity<byte[]> exportOrders(@RequestParam(required = false) Integer status,
                                               @RequestParam(required = false) String keyword) {
        String csv = stats.exportOrdersCsv(status, keyword);
        byte[] body = csv.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String filename = "orders-" + LocalDate.now() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .body(body);
    }
}
