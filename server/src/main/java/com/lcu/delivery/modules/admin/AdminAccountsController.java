package com.lcu.delivery.modules.admin;

import com.lcu.delivery.common.BizException;
import com.lcu.delivery.common.ErrorCode;
import com.lcu.delivery.common.Result;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台账号与角色管理（需求 4.4.1 多角色 + 权限隔离）。
 * 仅 SUPER 可访问（由 AuthInterceptor 统一拦截）。
 */
@RestController
@RequestMapping("/api/admin/accounts")
public class AdminAccountsController {

    private static final List<String> ROLES = List.of("SUPER", "CS", "FINANCE", "AUDITOR", "OPERATOR");

    private final JdbcTemplate jdbc;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AdminAccountsController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record CreateReq(String username, String password, String realName, String role) {
    }

    public record UpdateReq(String realName, String role, Integer status, String password) {
    }

    @GetMapping
    public Result<List<Map<String, Object>>> list() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> r : jdbc.queryForList(
                "SELECT id, username, real_name, role, phone_mask, status, last_login_at, created_at "
                        + "FROM admin_user ORDER BY id")) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.get("id"));
            m.put("username", r.get("username"));
            m.put("realName", r.get("real_name"));
            m.put("role", r.get("role"));
            m.put("roleText", roleText(String.valueOf(r.get("role"))));
            m.put("phoneMask", r.get("phone_mask"));
            m.put("status", r.get("status"));
            m.put("statusText", ((Number) r.get("status")).intValue() == 1 ? "启用" : "停用");
            m.put("lastLoginAt", r.get("last_login_at"));
            m.put("createdAt", r.get("created_at"));
            rows.add(m);
        }
        return Result.ok(rows);
    }

    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody CreateReq req) {
        if (req.username() == null || req.username().isBlank() || req.username().trim().length() < 3) {
            throw new BizException(ErrorCode.PARAM, "账号至少 3 位");
        }
        if (req.password() == null || req.password().length() < 6) {
            throw new BizException(ErrorCode.PARAM, "密码至少 6 位");
        }
        String role = normalizeRole(req.role());
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM admin_user WHERE username = ?",
                Integer.class, req.username().trim());
        if (exists != null && exists > 0) {
            throw new BizException(ErrorCode.PARAM, "账号已存在");
        }
        jdbc.update("INSERT INTO admin_user (username, password_hash, real_name, role, status, created_at) "
                        + "VALUES (?,?,?,?,1,?)",
                req.username().trim(), encoder.encode(req.password()),
                req.realName() == null || req.realName().isBlank() ? req.username().trim() : req.realName().trim(),
                role, LocalDateTime.now());
        Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return Result.ok(Map.of("id", id, "role", role));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody UpdateReq req) {
        Map<String, Object> row = jdbc.queryForMap("SELECT role FROM admin_user WHERE id = ?", id);
        String currentRole = String.valueOf(row.get("role"));
        if ("SUPER".equals(currentRole) && req.role() != null && !"SUPER".equals(req.role())
                && jdbc.queryForObject("SELECT COUNT(*) FROM admin_user WHERE role = 'SUPER' AND status = 1",
                Integer.class) <= 1) {
            throw new BizException(ErrorCode.PARAM, "至少要保留一个启用的超级管理员");
        }
        jdbc.update("UPDATE admin_user SET real_name = COALESCE(?, real_name), role = COALESCE(?, role), "
                        + "status = COALESCE(?, status) WHERE id = ?",
                req.realName(), req.role() == null ? null : normalizeRole(req.role()), req.status(), id);
        if (req.password() != null && !req.password().isBlank()) {
            if (req.password().length() < 6) {
                throw new BizException(ErrorCode.PARAM, "密码至少 6 位");
            }
            jdbc.update("UPDATE admin_user SET password_hash = ? WHERE id = ?", encoder.encode(req.password()), id);
        }
        return Result.ok();
    }

    /** 角色清单，前端下拉用。 */
    @GetMapping("/roles")
    public Result<List<Map<String, String>>> roles() {
        List<Map<String, String>> rows = new ArrayList<>();
        for (String r : ROLES) {
            rows.add(Map.of("value", r, "label", roleText(r)));
        }
        return Result.ok(rows);
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return "OPERATOR";
        }
        String r = role.trim().toUpperCase();
        if (!ROLES.contains(r)) {
            throw new BizException(ErrorCode.PARAM, "角色不合法：" + role);
        }
        return r;
    }

    private String roleText(String role) {
        return switch (role == null ? "" : role) {
            case "SUPER" -> "超级管理员";
            case "CS" -> "订单客服";
            case "FINANCE" -> "财务";
            case "AUDITOR" -> "商家/骑手审核员";
            default -> "运营";
        };
    }
}
