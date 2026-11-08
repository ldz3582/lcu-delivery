package com.lcu.delivery.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wechat")
public record WechatProperties(Miniapp miniapp, Pay pay, Official official) {

    public record Miniapp(String appid, String secret) {
    }

    /**
     * 公众号（服务号）配置：H5 在微信内打开时，用网页授权拿 openid 再走 JSAPI 支付。
     * 未配置时 H5 只能走微信外 H5 支付（MWEB）。
     */
    public record Official(String appid, String secret) {
    }

    /** 微信支付 v3 配置契约。 */
    public record Pay(String mchId, String serialNo, String apiV3Key, String privateKeyPath, String notifyUrl) {
    }
}
