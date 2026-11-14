package com.lcu.delivery.modules.pay;

import com.lcu.delivery.config.WechatProperties;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.http.DefaultHttpClientBuilder;
import com.wechat.pay.java.core.http.HttpClient;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.service.payments.jsapi.JsapiServiceExtension;
import com.wechat.pay.java.service.refund.RefundService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 微信支付 v3 客户端装配（仅当 app.mock-pay=false 时生效）。
 *
 * <p>配置不全直接启动失败（fail-fast），避免线上跑起来才发现通道是空的。
 * 需要的环境变量：
 * <pre>
 * WX_USER_APPID          小程序 AppID（JSAPI 下单必填）
 * WXPAY_MCH_ID           商户号
 * WXPAY_MCH_SERIAL_NO    商户 API 证书序列号
 * WXPAY_API_V3_KEY       APIv3 密钥（32 字节）
 * WXPAY_PRIVATE_KEY_PATH 商户 API 私钥 apiclient_key.pem 路径
 * WXPAY_NOTIFY_URL       支付回调地址，必须公网 HTTPS
 * </pre>
 */
@Configuration
@ConditionalOnProperty(name = "app.mock-pay", havingValue = "false")
public class WechatPayConfig {

    private static final Logger log = LoggerFactory.getLogger(WechatPayConfig.class);

    private final WechatProperties wechat;

    public WechatPayConfig(WechatProperties wechat) {
        this.wechat = wechat;
    }

    @Bean
    public RSAAutoCertificateConfig wechatPayConfig() {
        WechatProperties.Pay pay = wechat.pay();
        String appId = wechat.miniapp() == null ? null : wechat.miniapp().appid();
        require(appId, "WX_USER_APPID");
        require(pay == null ? null : pay.mchId(), "WXPAY_MCH_ID");
        require(pay.serialNo(), "WXPAY_MCH_SERIAL_NO");
        require(pay.apiV3Key(), "WXPAY_API_V3_KEY");
        require(pay.privateKeyPath(), "WXPAY_PRIVATE_KEY_PATH");
        require(pay.notifyUrl(), "WXPAY_NOTIFY_URL");
        if (!pay.notifyUrl().startsWith("https://")) {
            throw new IllegalStateException("WXPAY_NOTIFY_URL 必须是 https 地址，当前：" + pay.notifyUrl());
        }
        Path key = Path.of(pay.privateKeyPath());
        if (!Files.isReadable(key)) {
            throw new IllegalStateException("微信支付私钥不可读：" + key.toAbsolutePath());
        }
        if (pay.apiV3Key().length() != 32) {
            throw new IllegalStateException("APIv3 密钥必须是 32 字节，当前长度：" + pay.apiV3Key().length());
        }
        log.info("微信支付 v3 初始化：mchId={} appId={} serial={}", pay.mchId(), appId,
                pay.serialNo().substring(0, 8) + "***");
        // 平台证书自动下载并轮换，无需手工放置证书文件
        return new RSAAutoCertificateConfig.Builder()
                .merchantId(pay.mchId())
                .privateKeyFromPath(pay.privateKeyPath())
                .merchantSerialNumber(pay.serialNo())
                .apiV3Key(pay.apiV3Key())
                .build();
    }

    /** JSAPI 下单 + 直接产出小程序 wx.requestPayment 需要的签名参数。 */
    @Bean
    public JsapiServiceExtension jsapiServiceExtension(RSAAutoCertificateConfig config) {
        return new JsapiServiceExtension.Builder().config(config).build();
    }

    @Bean
    public RefundService refundService(RSAAutoCertificateConfig config) {
        return new RefundService.Builder().config(config).build();
    }

    /** 微信外浏览器 H5 支付（MWEB）。 */
    @Bean
    public com.wechat.pay.java.service.payments.h5.H5Service h5Service(RSAAutoCertificateConfig config) {
        return new com.wechat.pay.java.service.payments.h5.H5Service.Builder().config(config).build();
    }

    /** PC 扫码支付（Native）。 */
    @Bean
    public com.wechat.pay.java.service.payments.nativepay.NativePayService nativePayService(
            RSAAutoCertificateConfig config) {
        return new com.wechat.pay.java.service.payments.nativepay.NativePayService.Builder().config(config).build();
    }

    /** 回调验签 + AES-GCM 解密。 */
    @Bean
    public NotificationParser notificationParser(RSAAutoCertificateConfig config) {
        return new NotificationParser(config);
    }

    /** 通用 v3 HTTP 客户端（自动签名 + 验签），用于商家转账这类 SDK 未封装成 Service 的接口。 */
    @Bean
    public HttpClient wechatHttpClient(RSAAutoCertificateConfig config) {
        return new DefaultHttpClientBuilder().config(config).build();
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("app.mock-pay=false 时必须配置 " + name
                    + "（真实微信支付通道缺这项无法下单）");
        }
    }
}
