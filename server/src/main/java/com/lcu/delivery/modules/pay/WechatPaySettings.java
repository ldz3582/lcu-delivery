package com.lcu.delivery.modules.pay;

/**
 * 支付通道的「生效配置」快照。
 *
 * <p>来源优先级：后台配置（sys_config, group=pay）&gt; 环境变量。
 * 敏感字段（APIv3 密钥、商户私钥）在库里是 AES-GCM 加密的，读取时解密成明文放进这里，
 * 这份对象只在服务端内存里流动，绝不返回给前端。
 *
 * @param mode          mock / real
 * @param appId         小程序 AppID（JSAPI 下单用）
 * @param mpAppId       公众号 AppID（微信内 H5 支付用，可空）
 * @param mchId         商户号
 * @param serialNo      商户 API 证书序列号
 * @param apiV3Key      APIv3 密钥（32 字节明文）
 * @param privateKeyPem 商户 API 私钥 PEM 内容
 * @param notifyUrl     支付回调地址（必须 https）
 * @param source        db / env，仅用于诊断展示
 */
public record WechatPaySettings(
        String mode,
        String appId,
        String mpAppId,
        String mchId,
        String serialNo,
        String apiV3Key,
        String privateKeyPem,
        String notifyUrl,
        String source
) {

    public boolean mock() {
        return !"real".equalsIgnoreCase(mode);
    }

    /** 真实通道还缺哪些项（空列表 = 齐全）。 */
    public java.util.List<String> missing() {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (blank(appId)) out.add("小程序 AppID");
        if (blank(mchId)) out.add("商户号");
        if (blank(serialNo)) out.add("证书序列号");
        if (blank(apiV3Key) || apiV3Key.length() != 32) out.add("APIv3 密钥(32字节)");
        if (blank(privateKeyPem)) out.add("商户私钥");
        if (blank(notifyUrl) || !notifyUrl.startsWith("https://")) out.add("HTTPS 回调地址");
        return out;
    }

    /** 配置指纹：任一关键项变化就重建 SDK 客户端。 */
    public String fingerprint() {
        return mode + "|" + appId + "|" + mpAppId + "|" + mchId + "|" + serialNo
                + "|" + hash(apiV3Key) + "|" + hash(privateKeyPem) + "|" + notifyUrl;
    }

    private static String hash(String s) {
        if (s == null) {
            return "-";
        }
        return Integer.toHexString(s.hashCode()) + ":" + s.length();
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
