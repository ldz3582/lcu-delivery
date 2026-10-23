package com.lcu.delivery.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

/**
 * 敏感字段（姓名、身份证、学号）的对称加密。
 * 生产环境请把 app.crypto-key 换成 KMS 托管密钥，不要写进代码库。
 */
@Component
public class Crypto {

    private static final int IV_LEN = 12;
    private final SecretKeySpec key;

    public Crypto(@Value("${app.crypto-key:lcu-delivery-demo-crypto-key-32bytes!}") String rawKey) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawKey.getBytes(StandardCharsets.UTF_8));
        this.key = new SecretKeySpec(digest, "AES");
    }

    public String encrypt(String plain) {
        if (plain == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LEN];
            new java.security.SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] cipherText = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[IV_LEN + cipherText.length];
            System.arraycopy(iv, 0, out, 0, IV_LEN);
            System.arraycopy(cipherText, 0, out, IV_LEN, cipherText.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new BizException(500, "加密失败");
        }
    }

    public String decrypt(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        try {
            byte[] all = Base64.getDecoder().decode(encoded);
            byte[] iv = Arrays.copyOfRange(all, 0, IV_LEN);
            byte[] body = Arrays.copyOfRange(all, IV_LEN, all.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(body), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    /** 138****8000 */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
