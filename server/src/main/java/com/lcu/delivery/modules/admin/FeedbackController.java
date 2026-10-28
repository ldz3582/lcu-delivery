package com.lcu.delivery.modules.admin;

import com.lcu.delivery.common.BizException;
import com.lcu.delivery.common.ErrorCode;
import com.lcu.delivery.common.Result;
import com.lcu.delivery.common.UserContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户反馈 / 投诉渠道（需求 4.1.5「客服中心、意见反馈」、9.5「建立用户投诉渠道」）。
 * 用户端提交与查看自己的进度；后台统一处理并回复。
 */
@RestController
public class FeedbackController {

    private static final String[] TYPE_TEXT = {"", "意见建议", "投诉", "支付问题", "其他"};
    private static final String[] STATUS_TEXT = {"待处理", "处理中", "已处理"};

    private final JdbcTemplate jdbc;

    public FeedbackController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record FeedbackReq(Integer type, String content, List<String> images, String contact) {
    }

    public record HandleReq(Integer status, String reply) {
    }

    @PostMapping("/api/feedback")
    public Result<Long> submit(@RequestBody FeedbackReq req) {
        if (req.content() == null || req.content().isBlank()) {
            throw new BizException(ErrorCode.PARAM, "请填写反馈内容");
        }
        if (req.content().trim().length() > 1000) {
            throw new BizException(ErrorCode.PARAM, "反馈内容不能超过 1000 字");
        }
        int type = req.type() == null ? 1 : req.type();
        if (type < 1 || type > 4) {
            throw new BizException(ErrorCode.PARAM, "反馈类型不合法");
        }
        jdbc.update("INSERT INTO feedback (user_id, type, content, images, contact, status, created_at) "
                        + "VALUES (?,?,?,?,?,0,?)",
                UserContext.userId(), type, req.content().trim(),
                toJson(req.images()), req.contact(), LocalDateTime.now());
        return Result.ok(jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class));
    }

    @GetMapping("/api/feedback/mine")
    public Result<List<Map<String, Object>>> mine() {
        return Result.ok(rows("WHERE user_id = ? ORDER BY id DESC LIMIT 50", UserContext.userId()));
    }

    @GetMapping("/api/admin/feedbacks")
    public Result<List<Map<String, Object>>> adminList(@RequestParam(required = false) Integer status) {
        return Result.ok(status == null
                ? rows("ORDER BY status ASC, id DESC LIMIT 200")
                : rows("WHERE status = ? ORDER BY id DESC LIMIT 200", status));
    }

    @PostMapping("/api/admin/feedbacks/{id}/handle")
    public Result<Void> handle(@PathVariable Long id, @RequestBody HandleReq req) {
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM feedback WHERE id = ?", Integer.class, id);
        if (exists == null || exists == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "反馈不存在");
        }
        int status = req.status() == null ? 2 : req.status();
        if (status < 1 || status > 2) {
            throw new BizException(ErrorCode.PARAM, "处理状态不合法（1 处理中 / 2 已处理）");
        }
        jdbc.update("UPDATE feedback SET status = ?, reply = COALESCE(?, reply), operator_id = ?, handled_at = ? "
                        + "WHERE id = ?",
                status, req.reply(), UserContext.userId(),
                status == 2 ? LocalDateTime.now() : null, id);
        return Result.ok();
    }

    private List<Map<String, Object>> rows(String tail, Object... args) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : jdbc.queryForList(
                "SELECT id, user_id, type, content, images, contact, status, reply, created_at, handled_at "
                        + "FROM feedback " + tail, args)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.get("id"));
            m.put("userId", r.get("user_id"));
            int type = ((Number) r.get("type")).intValue();
            m.put("type", type);
            m.put("typeText", type >= 1 && type <= 4 ? TYPE_TEXT[type] : "其他");
            m.put("content", r.get("content"));
            m.put("images", r.get("images"));
            m.put("contact", r.get("contact"));
            int st = ((Number) r.get("status")).intValue();
            m.put("status", st);
            m.put("statusText", st >= 0 && st <= 2 ? STATUS_TEXT[st] : "未知");
            m.put("reply", r.get("reply"));
            m.put("createdAt", r.get("created_at"));
            m.put("handledAt", r.get("handled_at"));
            out.add(m);
        }
        return out;
    }

    private String toJson(List<String> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(images.subList(0, Math.min(images.size(), 9)));
        } catch (Exception e) {
            return null;
        }
    }
}
