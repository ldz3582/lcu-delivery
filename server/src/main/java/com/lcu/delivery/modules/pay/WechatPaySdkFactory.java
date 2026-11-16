package com.lcu.delivery.modules.pay;

import com.lcu.delivery.common.BizException;
import com.lcu.delivery.common.ErrorCode;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.http.DefaultHttpClientBuilder;
import com.wechat.pay.java.core.http.HttpClient;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.service.payments.h5.H5Service;
import com.wechat.pay.java.service.payments.jsapi.JsapiServiceExtension;
import com.wechat.pay.java.service.payments.nativepay.NativePayService;
import com.wechat.pay.java.service.refund.RefundService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;

/**
 * 微信支付 v3 SDK 客户端工厂。
 *
 * <p>与旧 WechatPayConfig 的区别：不再用 {@code @Bean} 在启动时固定装配，而是由
 * {@link PayGatewayProvider} 按当前生效配置**按需构建**，配置变更时重建。
 * 好处是后台改配置不用重启，也不会因为启动时连不上微信导致整个应用起不来。
 *
 * <p>⚠️ {@link RSAAutoCertificateConfig#build()} 会向微信下载平台证书，属于网络操作，
 * 调用方应缓存结果、只在配置变化时重建。
 */
@Component
public class WechatPaySdkFactory {

    private static final Logger log = LoggerFactory.getLogger(WechatPaySdkFactory.class);

    @Value("${app.upload-dir:./uploads}")
    private String uploadDir;

    /** 一次构建出来的客户端集合。 */
    public record Sdk(
            RSAAutoCertificateConfig config,
            JsapiServiceExtension jsapi,
            RefundService refund,
            H5Service h5,
            NativePayService nativePay,
            NotificationParser parser,
            HttpClient http
    ) {
    }

    /**
     * 按配置构建一套客户端。配置不全会抛 {@link BizException}，消息直接给前台看。
     */
    public Sdk build(WechatPaySettings s) {
        var missing = s.missing();
        if (!missing.isEmpty()) {
            throw new BizException(ErrorCode.PAY_FAILED,
                    "支付配置不完整，缺少：" + String.join("、", missing) + "（请在后台「支付配置」里补齐）");
        }
        Path keyFile = materializePrivateKey(s);
        try {
            RSAAutoCertificateConfig config = new RSAAutoCertificateConfig.Builder()
                    .merchantId(s.mchId())
                    .privateKeyFromPath(keyFile.toString())
                    .merchantSerialNumber(s.serialNo())
                    .apiV3Key(s.apiV3Key())
                    .build();
            return new Sdk(
                    config,
                    new JsapiServiceExtension.Builder().config(config).build(),
                    new RefundService.Builder().config(config).build(),
                    new H5Service.Builder().config(config).build(),
                    new NativePayService.Builder().config(config).build(),
                    new NotificationParser(config),
                    new DefaultHttpClientBuilder().config(config).build());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("微信支付 SDK 初始化失败（mchId={}）", s.mchId(), e);
            throw new BizException(ErrorCode.PAY_FAILED,
                    "微信支付通道初始化失败：" + rootMessage(e)
                            + "（常见原因：商户号/证书序列号/私钥不配套，或服务器无法访问 api.mch.weixin.qq.com）");
        }
    }

    /**
     * 把私钥 PEM 落到本地文件，供 SDK 读取。
     * 放在 uploads 下的隐藏目录，权限 600（Windows 上尽力而为）。
     */
    private Path materializePrivateKey(WechatPaySettings s) {
        try {
            Path dir = Path.of(uploadDir, ".pay").toAbsolutePath().normalize();
            Files.createDirectories(dir);
            String name = "apiclient_key_" + Math.abs(s.serialNo().hashCode()) + ".pem";
            Path file = dir.resolve(name);
            String pem = s.privateKeyPem().trim();
            if (!pem.endsWith("\n")) {
                pem = pem + "\n";
            }
            Files.writeString(file, pem);
            try {
                Set<PosixFilePermission> perms = EnumSet.of(
                        PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
                Files.setPosixFilePermissions(file, perms);
            } catch (UnsupportedOperationException ignored) {
                // Windows 没有 POSIX 权限，跳过
            }
            return file;
        } catch (Exception e) {
            throw new BizException(ErrorCode.PAY_FAILED, "写入商户私钥失败：" + rootMessage(e));
        }
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        String m = t.getMessage();
        return m == null || m.isBlank() ? t.getClass().getSimpleName() : m;
    }
}
