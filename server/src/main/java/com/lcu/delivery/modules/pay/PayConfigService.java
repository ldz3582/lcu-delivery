package com.lcu.delivery.modules.pay;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lcu.delivery.common.BizException;
import com.lcu.delivery.common.Crypto;
import com.lcu.delivery.common.ErrorCode;
import com.lcu.delivery.config.WechatProperties;
import com.lcu.delivery.modules.admin.entity.SysConfig;
import com.lcu.delivery.modules.admin.mapper.SysConfigMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 支付配置：后台可视化配置 + 加密落库 + 运行时热生效。
 *
 * <p>配置存在 sys_config（group_key='pay'），敏感项（APIv3 密钥、商户私钥）用
 * {@link Crypto} 做 AES-GCM 加密后写入，读出来才解密。库里没有配置时回落环境变量，
 * 保证老的部署方式（ops/.env）继续可用。
 *
 * <p>生效时机：{@link PayGatewayProvider} 每次下单前比对配置指纹，变了就重建微信 SDK
 * 客户端，所以后台保存后**不用重启**。
 */
@Service
public class PayConfigService {

    private static final Logger log = LoggerFactory.getLogger(PayConfigService.class);

    static final String G_MODE = "wxpay.mode";
    static final String G_APPID = "wxpay.app_id";
    static final String G_MP_APPID = "wxpay.mp_app_id";
    static final String G_MCHID = "wxpay.mch_id";
    static final String G_SERIAL = "wxpay.serial_no";
    static final String G_V3KEY = "wxpay.api_v3_key_enc";
    static final String G_PRIKEY = "wxpay.private_key_enc";
    static final String G_NOTIFY = "wxpay.notify_url";

    private static final String GROUP = "pay";

    private final SysConfigMapper mapper;
    private final Crypto crypto;
    private final WechatProperties env;

    public PayConfigService(SysConfigMapper mapper, Crypto crypto, WechatProperties env) {
        this.mapper = mapper;
        this.crypto = crypto;
        this.env = env;
    }

    @PostConstruct
    public void ensureRows() {
        // 首次启动把配置项建出来，后台页面才有东西可编辑（值留空，敏感项空串）
        upsertIfAbsent(G_MODE, "mock", "string", "支付模式：mock 模拟 / real 真实扣款");
        upsertIfAbsent(G_APPID, "", "string", "小程序 AppID（JSAPI 下单必需）");
        upsertIfAbsent(G_MP_APPID, "", "string", "公众号 AppID（微信内 H5 支付用，可空）");
        upsertIfAbsent(G_MCHID, "", "string", "微信支付商户号");
        upsertIfAbsent(G_SERIAL, "", "string", "商户 API 证书序列号");
        upsertIfAbsent(G_V3KEY, "", "secret", "APIv3 密钥（32 字节，加密存储）");
        upsertIfAbsent(G_PRIKEY, "", "secret", "商户 API 私钥 apiclient_key.pem 内容（加密存储）");
        upsertIfAbsent(G_NOTIFY, "", "string", "支付回调地址，必须 https");
    }

    // ---------------------------------------------------------------- 读

    /** 有效配置：库优先，库为空回落环境变量。 */
    public WechatPaySettings effective() {
        String mode = str(G_MODE);
        WechatProperties.Pay p = env.pay();
        WechatProperties.Miniapp m = env.miniapp();
        WechatProperties.Official o = env.official();

        if (mode == null) {
            // 库里没有任何模式设置 → 完全按环境变量走（老部署）
            boolean envMock = !hasEnvCredentials();
            return new WechatPaySettings(
                    envMock ? "mock" : "real",
                    m == null ? null : m.appid(),
                    o == null ? null : o.appid(),
                    p == null ? null : p.mchId(),
                    p == null ? null : p.serialNo(),
                    p == null ? null : p.apiV3Key(),
                    envPrivateKeyContent(),
                    p == null ? null : p.notifyUrl(),
                    "env");
        }

        boolean fromDb = !blank(str(G_MCHID)) || !blank(str(G_APPID));
        return new WechatPaySettings(
                mode,
                firstNonBlank(str(G_APPID), m == null ? null : m.appid()),
                firstNonBlank(str(G_MP_APPID), o == null ? null : o.appid()),
                firstNonBlank(str(G_MCHID), p == null ? null : p.mchId()),
                firstNonBlank(str(G_SERIAL), p == null ? null : p.serialNo()),
                firstNonBlank(decryptOrNull(str(G_V3KEY)), p == null ? null : p.apiV3Key()),
                firstNonBlank(decryptOrNull(str(G_PRIKEY)), envPrivateKeyContent()),
                firstNonBlank(str(G_NOTIFY), p == null ? null : p.notifyUrl()),
                fromDb ? "db" : "env");
    }

    /** 给后台页面用的脱敏状态。 */
    public Map<String, Object> status() {
        WechatPaySettings s = effective();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mode", s.mode());
        m.put("source", s.source());
        m.put("mock", s.mock());
        m.put("appId", s.appId());
        m.put("mpAppId", s.mpAppId());
        m.put("mchId", mask(s.mchId(), 4));
        m.put("serialNo", mask(s.serialNo(), 6));
        m.put("apiV3KeySet", !blank(s.apiV3Key()));
        m.put("apiV3KeyMask", blank(s.apiV3Key()) ? "" : "****（已配置，" + s.apiV3Key().length() + " 字节）");
        m.put("privateKeySet", !blank(s.privateKeyPem()));
        m.put("privateKeyMask", blank(s.privateKeyPem()) ? "" : "****（已配置，" + s.privateKeyPem().length() + " 字符）");
        m.put("notifyUrl", s.notifyUrl());
        m.put("missing", s.missing());
        m.put("ready", s.mock() || s.missing().isEmpty());
        m.put("updatedAt", str("wxpay.updated_at"));
        return m;
    }

    // ---------------------------------------------------------------- 写

    public record SaveReq(
            String mode,
            String appId,
            String mpAppId,
            String mchId,
            String serialNo,
            String apiV3Key,
            String privateKeyPem,
            String notifyUrl
    ) {
    }

    /**
     * 保存配置。敏感字段留空 = 不修改（避免前端脱敏后回写把密钥冲掉）。
     */
    public synchronized void save(SaveReq req, Long operatorId) {
        if (req == null) {
            throw new BizException(ErrorCode.PARAM, "缺少配置内容");
        }
        String mode = req.mode() == null ? "mock" : req.mode().trim().toLowerCase();
        if (!mode.equals("mock") && !mode.equals("real")) {
            throw new BizException(ErrorCode.PARAM, "mode 只能是 mock 或 real");
        }
        if (!blank(req.apiV3Key()) && req.apiV3Key().length() != 32) {
            throw new BizException(ErrorCode.PARAM, "APIv3 密钥必须正好 32 字节");
        }
        if (!blank(req.notifyUrl()) && !req.notifyUrl().startsWith("https://")) {
            throw new BizException(ErrorCode.PARAM, "回调地址必须是 https");
        }
        if (!blank(req.privateKeyPem()) && !req.privateKeyPem().contains("PRIVATE KEY")) {
            throw new BizException(ErrorCode.PARAM, "私钥格式不对，应包含 -----BEGIN PRIVATE KEY-----");
        }

        upsert(G_MODE, mode, "string", operatorId);
        upsert(G_APPID, nz(req.appId()), "string", operatorId);
        upsert(G_MP_APPID, nz(req.mpAppId()), "string", operatorId);
        upsert(G_MCHID, nz(req.mchId()), "string", operatorId);
        upsert(G_SERIAL, nz(req.serialNo()), "string", operatorId);
        upsert(G_NOTIFY, nz(req.notifyUrl()), "string", operatorId);
        if (!blank(req.apiV3Key())) {
            upsert(G_V3KEY, crypto.encrypt(req.apiV3Key().trim()), "secret", operatorId);
        }
        if (!blank(req.privateKeyPem())) {
            upsert(G_PRIKEY, crypto.encrypt(req.privateKeyPem().trim()), "secret", operatorId);
        }
        upsert("wxpay.updated_at", LocalDateTime.now().toString(), "string", operatorId);

        WechatPaySettings after = effective();
        log.info("支付配置已更新：mode={} source={} missing={}", after.mode(), after.source(), after.missing());
    }

    // ---------------------------------------------------------------- 内部

    private boolean hasEnvCredentials() {
        WechatProperties.Pay p = env.pay();
        return p != null && !blank(p.mchId()) && !blank(p.serialNo())
                && !blank(p.apiV3Key()) && !blank(p.privateKeyPath());
    }

    private String envPrivateKeyContent() {
        WechatProperties.Pay p = env.pay();
        if (p == null || blank(p.privateKeyPath())) {
            return null;
        }
        try {
            return java.nio.file.Files.readString(java.nio.file.Path.of(p.privateKeyPath()));
        } catch (Exception e) {
            log.warn("读取环境变量指定的私钥失败：{}", p.privateKeyPath());
            return null;
        }
    }

    private String str(String key) {
        SysConfig c = mapper.selectOne(Wrappers.<SysConfig>lambdaQuery().eq(SysConfig::getConfigKey, key));
        return c == null ? null : c.getConfigValue();
    }

    private void upsertIfAbsent(String key, String value, String type, String remark) {
        SysConfig c = mapper.selectOne(Wrappers.<SysConfig>lambdaQuery().eq(SysConfig::getConfigKey, key));
        if (c != null) {
            return;
        }
        SysConfig n = new SysConfig();
        n.setConfigKey(key);
        n.setConfigValue(value);
        n.setValueType(type);
        n.setGroupKey(GROUP);
        n.setRemark(remark);
        n.setUpdatedAt(LocalDateTime.now());
        try {
            mapper.insert(n);
        } catch (Exception e) {
            log.debug("配置项 {} 已存在，跳过", key);
        }
    }

    private void upsert(String key, String value, String type, Long operatorId) {
        SysConfig c = mapper.selectOne(Wrappers.<SysConfig>lambdaQuery().eq(SysConfig::getConfigKey, key));
        if (c == null) {
            SysConfig n = new SysConfig();
            n.setConfigKey(key);
            n.setConfigValue(value == null ? "" : value);
            n.setValueType(type);
            n.setGroupKey(GROUP);
            n.setUpdatedBy(operatorId);
            n.setUpdatedAt(LocalDateTime.now());
            mapper.insert(n);
            return;
        }
        c.setConfigValue(value == null ? "" : value);
        c.setUpdatedBy(operatorId);
        c.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(c);
    }

    private String decryptOrNull(String cipher) {
        if (blank(cipher)) {
            return null;
        }
        try {
            return crypto.decrypt(cipher);
        } catch (Exception e) {
            log.warn("支付密钥解密失败，可能是 APP_CRYPTO_KEY 变更导致", e);
            return null;
        }
    }

    private static String firstNonBlank(String a, String b) {
        return blank(a) ? b : a;
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String mask(String s, int keep) {
        if (blank(s)) {
            return "";
        }
        if (s.length() <= keep) {
            return "****";
        }
        return s.substring(0, keep) + "****" + s.substring(s.length() - Math.min(4, s.length() - keep));
    }

    /** 诊断用：列出缺失项（中文）。 */
    public List<String> missingItems() {
        return new ArrayList<>(effective().missing());
    }
}
