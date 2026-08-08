package com.example.siteplatform.seal.service;

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

/** Dedicated key boundary for opaque seal-entry scenes. */
@Service
public class SealSceneCryptoService {
    private static final String VERSION = "v1";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String DEVELOPMENT_KEY = "dianxinyun-local-seal-scene-key-v1";

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public SealSceneCryptoService(@Value("${seal.scene-encryption-key:}") String configuredKey,
                                  Environment environment) {
        String configured = StringUtils.hasText(configuredKey) ? configuredKey.trim() : null;
        if (isProduction(environment) && (configured == null
                || DEVELOPMENT_KEY.equals(configured)
                || configured.getBytes(StandardCharsets.UTF_8).length < 32)) {
            throw new IllegalStateException("生产环境必须配置至少32字节的 SEAL_SCENE_ENCRYPTION_KEY");
        }
        String source = configured == null ? DEVELOPMENT_KEY : configured;
        this.key = new SecretKeySpec(sha256(source.getBytes(StandardCharsets.UTF_8)), "AES");
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
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
            throw new IllegalStateException("用印二维码密钥加密失败", exception);
        }
    }

    public String decrypt(String ciphertext) {
        if (ciphertext == null || !ciphertext.startsWith(VERSION + ":")) {
            throw new IllegalStateException("用印二维码密文版本不受支持");
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
            throw new IllegalStateException("用印二维码密文解密失败", exception);
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
            throw new IllegalStateException("无法生成用印二维码摘要", exception);
        }
    }

    private boolean isProduction(Environment environment) {
        boolean development = Arrays.stream(environment.getActiveProfiles()).anyMatch(profile ->
                "dev".equalsIgnoreCase(profile) || "local".equalsIgnoreCase(profile)
                        || "test".equalsIgnoreCase(profile));
        boolean production = Arrays.stream(environment.getActiveProfiles()).anyMatch(profile ->
                "prod".equalsIgnoreCase(profile) || "production".equalsIgnoreCase(profile));
        return production || !development;
    }
}
