package com.lcu.delivery.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wechat")
public record WechatProperties(Miniapp miniapp, Pay pay) {

    public record Miniapp(String appid, String secret) {
    }

    /** 微信支付 v3 配置契约：目前只有 mchId 被使用（用于判断是否已接入），其余接 SDK 时启用。 */
    public record Pay(String mchId, String serialNo, String apiV3Key, String privateKeyPath, String notifyUrl) {
    }
}
