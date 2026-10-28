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
 * 平台公告：后台维护 + 用户端展示（需求 4.4.6「平台公告维护」、4.1.5「平台公告」）。
 */
@RestController
public class AnnouncementController {

    private final JdbcTemplate jdbc;

    public AnnouncementController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record AnnouncementReq(String title, String content, Integer position,
                                  Integer status, String startAt, String endAt) {
    }

    /** 用户端：只返回启用中且在有效期内的公告。 */
    @GetMapping("/api/announcements")
    public Result<List<Map<String, Object>>> publicList(@RequestParam(required = false) Integer position) {
        String sql = "SELECT id, title, content, position, created_at FROM announcement WHERE status = 1 "
                + "AND (start_at IS NULL OR start_at <= NOW()) AND (end_at IS NULL OR end_at >= NOW()) "
                + (position == null ? "" : "AND position = ? ")
                + "ORDER BY id DESC LIMIT 20";
        List<Map<String, Object>> rows = new ArrayList<>();
        List<Map<String, Object>> raw = position == null
                ? jdbc.queryForList(sql)
                : jdbc.queryForList(sql, position);
        for (Map<String, Object> r : raw) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.get("id"));
            m.put("title", r.get("title"));
            m.put("content", r.get("content"));
            m.put("position", r.get("position"));
            m.put("createdAt", r.get("created_at"));
            rows.add(m);
        }
        return Result.ok(rows);
    }

    @GetMapping("/api/admin/announcements")
    public Result<List<Map<String, Object>>> list(@RequestParam(required = false) Integer status) {
        String sql = "SELECT id, title, content, position, status, start_at, end_at, created_at "
                + "FROM announcement " + (status == null ? "" : "WHERE status = ? ") + "ORDER BY id DESC LIMIT 200";
        List<Map<String, Object>> raw = status == null ? jdbc.queryForList(sql) : jdbc.queryForList(sql, status);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> r : raw) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.get("id"));
            m.put("title", r.get("title"));
            m.put("content", r.get("content"));
            m.put("position", r.get("position"));
            m.put("status", r.get("status"));
            m.put("statusText", ((Number) r.get("status")).intValue() == 1 ? "已发布" : "已下线");
            m.put("startAt", r.get("start_at"));
            m.put("endAt", r.get("end_at"));
            m.put("createdAt", r.get("created_at"));
            rows.add(m);
        }
        return Result.ok(rows);
    }

    @PostMapping("/api/admin/announcements")
    public Result<Map<String, Object>> create(@RequestBody AnnouncementReq req) {
        if (req.title() == null || req.title().isBlank()) {
            throw new BizException(ErrorCode.PARAM, "请填写公告标题");
        }
        if (req.content() == null || req.content().isBlank()) {
            throw new BizException(ErrorCode.PARAM, "请填写公告内容");
        }
        jdbc.update("INSERT INTO announcement (title, content, position, status, start_at, end_at, created_at) "
                        + "VALUES (?,?,?,?,?,?,?)",
                req.title().trim(), req.content().trim(),
                req.position() == null ? 1 : req.position(),
                req.status() == null ? 1 : req.status(),
                blankToNull(req.startAt()), blankToNull(req.endAt()), LocalDateTime.now());
        Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return Result.ok(Map.of("id", id));
    }

    @PutMapping("/api/admin/announcements/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody AnnouncementReq req) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM announcement WHERE id = ?", Integer.class, id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "公告不存在");
        }
        jdbc.update("UPDATE announcement SET title = COALESCE(?, title), content = COALESCE(?, content), "
                        + "position = COALESCE(?, position), status = COALESCE(?, status), "
                        + "start_at = ?, end_at = ? WHERE id = ?",
                req.title(), req.content(), req.position(), req.status(),
                blankToNull(req.startAt()), blankToNull(req.endAt()), id);
        return Result.ok();
    }

    @PutMapping("/api/admin/announcements/{id}/status")
    public Result<Void> status(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        int st = ((Number) body.getOrDefault("status", 1)).intValue();
        jdbc.update("UPDATE announcement SET status = ? WHERE id = ?", st, id);
        return Result.ok();
    }

    @DeleteMapping("/api/admin/announcements/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        jdbc.update("DELETE FROM announcement WHERE id = ?", id);
        return Result.ok();
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
