package com.lcu.delivery.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * 统一鉴权：解析 token -> 按路径前缀校验角色。
 * 后台再按 admin_user.role 做细粒度权限隔离（SUPER / CS / FINANCE / AUDITOR / OPERATOR）。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtService jwtService;
    private final JdbcTemplate jdbc;

    /** 只读角色可以访问的后台模块（按路径关键字匹配） */
    private static final Set<String> FINANCE_KEYS = Set.of("finance", "settlement", "withdraw", "stats", "orders/export");
    private static final Set<String> AUDITOR_KEYS = Set.of("shops", "riders", "users", "announcement", "products");
    private static final Set<String> CS_KEYS = Set.of("orders", "after-sales", "incidents", "reviews", "users",
            "announcements", "feedbacks", "announcement");
    /** 所有后台角色都要能进的公共能力：登录后落地的概览页 + 自己的账号信息 */
    private static final Set<String> COMMON_KEYS = Set.of("overview", "me");

    public AuthInterceptor(JwtService jwtService, JdbcTemplate jdbc) {
        this.jwtService = jwtService;
        this.jdbc = jdbc;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String token = bearer(request);
        if (token == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        AuthUser user = jwtService.parse(token);
        UserContext.set(user);

        String path = request.getRequestURI();
        if (path.startsWith("/api/admin/") && !user.is("ADMIN")) {
            throw new BizException(ErrorCode.FORBIDDEN, "需要管理员权限");
        }
        if (path.startsWith("/api/merchant/") && !user.is("MERCHANT")) {
            throw new BizException(ErrorCode.FORBIDDEN, "需要商家权限");
        }
        if (path.startsWith("/api/rider/") && !user.is("RIDER")) {
            throw new BizException(ErrorCode.FORBIDDEN, "需要骑手权限");
        }
        if (path.startsWith("/api/admin/") && !path.startsWith("/api/admin/login")) {
            checkAdminPermission(user.id(), path);
        }
        return true;
    }

    /**
     * 后台细粒度权限：
     *  SUPER 全通；账号管理 / 平台配置 / 围栏只有 SUPER；
     *  FINANCE 只能财务类；AUDITOR 只能审核类；CS 只能订单/售后/评价类；OPERATOR 只读常见查询。
     */
    private void checkAdminPermission(Long adminId, String path) {
        String role;
        try {
            role = jdbc.queryForObject("SELECT role FROM admin_user WHERE id = ?", String.class, adminId);
        } catch (Exception e) {
            throw new BizException(ErrorCode.FORBIDDEN, "管理员账号不存在");
        }
        if (role == null || role.isBlank()) {
            role = "OPERATOR";
        }
        if ("SUPER".equals(role)) {
            return;
        }
        String p = path.substring("/api/admin/".length());
        if (p.startsWith("accounts") || p.startsWith("configs") || p.startsWith("fence")) {
            throw new BizException(ErrorCode.FORBIDDEN, "只有超级管理员可以操作「账号 / 平台配置 / 围栏」");
        }
        // 概览页是所有角色登录后的落地页，必须放行，否则非 SUPER 一登录就 403
        if (matches(p, COMMON_KEYS)) {
            return;
        }
        boolean allowed = switch (role) {
            case "FINANCE" -> matches(p, FINANCE_KEYS);
            case "AUDITOR" -> matches(p, AUDITOR_KEYS);
            case "CS" -> matches(p, CS_KEYS);
            case "OPERATOR" -> true;
            default -> false;
        };
        if (!allowed) {
            throw new BizException(ErrorCode.FORBIDDEN, "当前角色（" + role + "）无权访问该功能");
        }
    }

    private boolean matches(String path, Set<String> keys) {
        for (String k : keys) {
            if (path.startsWith(k) || path.contains("/" + k)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }

    private String bearer(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || header.isBlank()) {
            return null;
        }
        return header.startsWith("Bearer ") ? header.substring(7) : header;
    }
}
