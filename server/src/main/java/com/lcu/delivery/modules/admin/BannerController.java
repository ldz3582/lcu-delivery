package com.lcu.delivery.modules.admin;

import com.lcu.delivery.common.BizException;
import com.lcu.delivery.common.ErrorCode;
import com.lcu.delivery.common.Result;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 首页运营位（Banner）：后台维护 + 用户端展示（需求 4.1.1「活动 Banner」）。
 */
@RestController
public class BannerController {

    private final JdbcTemplate jdbc;

    public BannerController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record BannerReq(String title, String image, Integer linkType, String linkValue,
                            Integer sort, Integer status, String startAt, String endAt) {
    }

    /** 用户端：启用中且在有效期内的 Banner，按 sort 升序。 */
    @GetMapping("/api/banners")
    public Result<List<Map<String, Object>>> publicList() {
        return Result.ok(query("WHERE status = 1 AND (start_at IS NULL OR start_at <= NOW()) "
                + "AND (end_at IS NULL OR end_at >= NOW()) ORDER BY sort ASC, id DESC"));
    }

    @GetMapping("/api/admin/banners")
    public Result<List<Map<String, Object>>> adminList() {
        return Result.ok(query("ORDER BY sort ASC, id DESC"));
    }

    @PostMapping("/api/admin/banners")
    public Result<Long> create(@RequestBody BannerReq req) {
        if (req.title() == null || req.title().isBlank()) {
            throw new BizException(ErrorCode.PARAM, "请填写 Banner 标题");
        }
        if (req.image() == null || req.image().isBlank()) {
            throw new BizException(ErrorCode.PARAM, "请上传 Banner 图片");
        }
        jdbc.update("INSERT INTO banner (title, image, link_type, link_value, sort, status, start_at, end_at, created_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?)",
                req.title().trim(), req.image().trim(),
                req.linkType() == null ? 1 : req.linkType(), req.linkValue(),
                req.sort() == null ? 0 : req.sort(), req.status() == null ? 1 : req.status(),
                blankToNull(req.startAt()), blankToNull(req.endAt()), LocalDateTime.now());
        return Result.ok(jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class));
    }

    @PutMapping("/api/admin/banners/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody BannerReq req) {
        if (countOf(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "Banner 不存在");
        }
        jdbc.update("UPDATE banner SET title = COALESCE(?, title), image = COALESCE(?, image), "
                        + "link_type = COALESCE(?, link_type), link_value = COALESCE(?, link_value), "
                        + "sort = COALESCE(?, sort), status = COALESCE(?, status), "
                        + "start_at = ?, end_at = ? WHERE id = ?",
                req.title(), req.image(), req.linkType(), req.linkValue(), req.sort(), req.status(),
                blankToNull(req.startAt()), blankToNull(req.endAt()), id);
        return Result.ok();
    }

    @PutMapping("/api/admin/banners/{id}/status")
    public Result<Void> status(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        int st = ((Number) body.getOrDefault("status", 1)).intValue() == 1 ? 1 : 0;
        jdbc.update("UPDATE banner SET status = ? WHERE id = ?", st, id);
        return Result.ok();
    }

    @DeleteMapping("/api/admin/banners/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        jdbc.update("DELETE FROM banner WHERE id = ?", id);
        return Result.ok();
    }

    private int countOf(Long id) {
        Integer c = jdbc.queryForObject("SELECT COUNT(*) FROM banner WHERE id = ?", Integer.class, id);
        return c == null ? 0 : c;
    }

    private List<Map<String, Object>> query(String tail) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> r : jdbc.queryForList(
                "SELECT id, title, image, link_type, link_value, sort, status, start_at, end_at, created_at "
                        + "FROM banner " + tail)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.get("id"));
            m.put("title", r.get("title"));
            m.put("image", r.get("image"));
            m.put("linkType", r.get("link_type"));
            m.put("linkValue", r.get("link_value"));
            m.put("sort", r.get("sort"));
            m.put("status", r.get("status"));
            m.put("startAt", r.get("start_at"));
            m.put("endAt", r.get("end_at"));
            m.put("createdAt", r.get("created_at"));
            rows.add(m);
        }
        return rows;
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
