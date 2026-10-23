package com.lcu.delivery.common;

/**
 * 当前登录者。role: USER / MERCHANT / RIDER / ADMIN
 */
public record AuthUser(Long id, String role) {

    public boolean is(String expected) {
        return expected.equals(role);
    }
}
