package com.lcu.delivery.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wechat")
public record WechatProperties(Miniapp miniapp, Pay pay) {

    public record Miniapp(String appid, String secret, String workAppid, String workSecret) {
    }

    public record Pay(String mchId, String serialNo, String apiV3Key, String privateKeyPath, String notifyUrl) {
    }
}
