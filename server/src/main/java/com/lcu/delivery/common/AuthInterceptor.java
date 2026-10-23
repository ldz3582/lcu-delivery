package com.lcu.delivery.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 统一鉴权：解析 token -> 按路径前缀校验角色。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtService jwtService;

    public AuthInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
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
        return true;
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
