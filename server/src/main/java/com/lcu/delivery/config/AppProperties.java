package com.lcu.delivery.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String jwtSecret,
        int tokenExpireHours,
        boolean mockLogin,
        boolean mockPay,
        boolean fenceEnforce,
        int settleFreezeDays
) {
}
