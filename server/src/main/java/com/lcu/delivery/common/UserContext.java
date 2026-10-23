package com.lcu.delivery.common;

public final class UserContext {

    private static final ThreadLocal<AuthUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(AuthUser user) {
        HOLDER.set(user);
    }

    public static AuthUser get() {
        return HOLDER.get();
    }

    public static AuthUser require() {
        AuthUser user = HOLDER.get();
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        return user;
    }

    public static Long userId() {
        return require().id();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
