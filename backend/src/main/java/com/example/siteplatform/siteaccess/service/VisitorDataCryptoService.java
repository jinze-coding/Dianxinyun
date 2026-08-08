package com.example.siteplatform.siteaccess.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/** Encrypts visitor credentials and invitation secrets without ever logging raw values. */
@Service
public class VisitorDataCryptoService {
    private static final String VERSION = "v1";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String DEVELOPMENT_KEY = "dianxinyun-local-visitor-data-key-v1";

    private final SecretKeySpec key;
    private final SecureRandom secureRandom = new SecureRandom();

    public VisitorDataCryptoService(
            @Value("${site-access.encryption-key:}") String configuredKey,
            Environment environment) {
        boolean production = isProduction(environment);
        String source = StringUtils.hasText(configuredKey) ? configuredKey.trim() : DEVELOPMENT_KEY;
        if (production && (!StringUtils.hasText(configuredKey)
                || DEVELOPMENT_KEY.equals(configuredKey.trim())
                || configuredKey.trim().getBytes(StandardCharsets.UTF_8).length < 32)) {
            throw new IllegalStateException("生产环境必须配置至少32字节的 VISITOR_DATA_ENCRYPTION_KEY");
        }
        this.key = new SecretKeySpec(sha256(source.getBytes(StandardCharsets.UTF_8)), "AES");
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        byte[] iv = new byte[IV_BYTES];
        secureRandom.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(VERSION.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] envelope = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, envelope, 0, iv.length);
            System.arraycopy(encrypted, 0, envelope, iv.length, encrypted.length);
            return VERSION + ":" + Base64.getUrlEncoder().withoutPadding().encodeToString(envelope);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("外访敏感数据加密失败", exception);
        }
    }

    public String decrypt(String ciphertext) {
        if (ciphertext == null) return null;
        if (!ciphertext.startsWith(VERSION + ":")) {
            throw new IllegalStateException("外访敏感数据版本不受支持");
        }
        try {
            byte[] envelope = Base64.getUrlDecoder().decode(ciphertext.substring(VERSION.length() + 1));
            if (envelope.length <= IV_BYTES) throw new GeneralSecurityException("invalid envelope");
            byte[] iv = Arrays.copyOfRange(envelope, 0, IV_BYTES);
            byte[] encrypted = Arrays.copyOfRange(envelope, IV_BYTES, envelope.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(VERSION.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("外访敏感数据解密失败", exception);
        }
    }

    public String digest(String value) {
        if (value == null) return null;
        return java.util.HexFormat.of().formatHex(sha256(value.getBytes(StandardCharsets.UTF_8)));
    }

    private byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("无法生成外访数据摘要", exception);
        }
    }

    private boolean isProduction(Environment environment) {
        boolean developmentProfile = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> "dev".equalsIgnoreCase(profile)
                        || "local".equalsIgnoreCase(profile)
                        || "test".equalsIgnoreCase(profile));
        boolean productionProfile = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> "prod".equalsIgnoreCase(profile)
                        || "production".equalsIgnoreCase(profile));
        return productionProfile || !developmentProfile;
    }
}
