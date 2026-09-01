package com.example.siteplatform.document.service;

import com.example.siteplatform.common.BusinessException;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
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
import java.util.HashMap;
import java.util.Map;

@Service
public class DocumentSceneService {
    private static final String VERSION = "v1";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String DEVELOPMENT_KEY = "dianxinyun-local-document-circulation-scene-key-v1";
    private static final String QR_PREFIX = "DXY:DOCUMENT_DISTRIBUTION:";

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public DocumentSceneService(
            @Value("${document.circulation.scene-encryption-key:}") String configuredKey,
            Environment environment) {
        String configured = StringUtils.hasText(configuredKey) ? configuredKey.trim() : null;
        if (isProduction(environment) && (configured == null || DEVELOPMENT_KEY.equals(configured)
                || configured.getBytes(StandardCharsets.UTF_8).length < 32)) {
            throw new IllegalStateException(
                    "生产环境必须配置至少32字节的 DOCUMENT_CIRCULATION_SCENE_ENCRYPTION_KEY");
        }
        this.key = new SecretKeySpec(sha256((configured == null ? DEVELOPMENT_KEY : configured)
                .getBytes(StandardCharsets.UTF_8)), "AES");
    }

    public String newScene() {
        byte[] value = new byte[32];
        random.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    public String normalizeScene(String raw) {
        String value = StringUtils.hasText(raw) ? raw.trim() : "";
        if (value.startsWith(QR_PREFIX)) value = value.substring(QR_PREFIX.length());
        if (!value.matches("^[A-Za-z0-9_-]{40,100}$")) {
            throw new BusinessException("图纸领取二维码无效");
        }
        return value;
    }

    public String digest(String value) {
        return java.util.HexFormat.of().formatHex(sha256(normalizeScene(value)
                .getBytes(StandardCharsets.UTF_8)));
    }

    public String encrypt(String value) {
        String normalized = normalizeScene(value);
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(VERSION.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(normalized.getBytes(StandardCharsets.UTF_8));
            byte[] envelope = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, envelope, 0, iv.length);
            System.arraycopy(encrypted, 0, envelope, iv.length, encrypted.length);
            return VERSION + ":" + Base64.getUrlEncoder().withoutPadding().encodeToString(envelope);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("图纸领取二维码加密失败", exception);
        }
    }

    public String decrypt(String ciphertext) {
        if (!StringUtils.hasText(ciphertext) || !ciphertext.startsWith(VERSION + ":")) {
            throw new IllegalStateException("图纸领取二维码密文版本不受支持");
        }
        try {
            byte[] envelope = Base64.getUrlDecoder().decode(ciphertext.substring(VERSION.length() + 1));
            if (envelope.length <= IV_BYTES) throw new GeneralSecurityException("invalid envelope");
            byte[] iv = Arrays.copyOfRange(envelope, 0, IV_BYTES);
            byte[] encrypted = Arrays.copyOfRange(envelope, IV_BYTES, envelope.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(VERSION.getBytes(StandardCharsets.UTF_8));
            return normalizeScene(new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8));
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("图纸领取二维码解密失败", exception);
        }
    }

    public String qrSvg(String scene) {
        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 1);
            BitMatrix matrix = new QRCodeWriter().encode(QR_PREFIX + normalizeScene(scene),
                    BarcodeFormat.QR_CODE, 0, 0, hints);
            int module = 8;
            int size = matrix.getWidth() * module;
            StringBuilder svg = new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ")
                    .append(size).append(' ').append(size)
                    .append("\" width=\"").append(size).append("\" height=\"").append(size)
                    .append("\" role=\"img\" aria-label=\"图纸领取二维码\"><rect width=\"100%\" height=\"100%\" fill=\"#fff\"/>");
            for (int y = 0; y < matrix.getHeight(); y++) {
                for (int x = 0; x < matrix.getWidth(); x++) {
                    if (matrix.get(x, y)) {
                        svg.append("<rect x=\"").append(x * module).append("\" y=\"")
                                .append(y * module).append("\" width=\"").append(module)
                                .append("\" height=\"").append(module).append("\" fill=\"#111\"/>");
                    }
                }
            }
            return svg.append("</svg>").toString();
        } catch (WriterException exception) {
            throw new BusinessException("图纸领取二维码生成失败");
        }
    }

    private byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("无法生成图纸领取二维码摘要", exception);
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
