package com.example.siteplatform.system.userimport;

import com.example.siteplatform.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/** Only unused temporary credentials are recoverable, for the 24-hour handout window. */
@Component
public class UserImportCredentialCipher {
    private final byte[] key;
    private final SecureRandom random = new SecureRandom();
    public UserImportCredentialCipher(@Value("${user-import.credential-key:}") String configured) {
        byte[] parsed;
        try { parsed = Base64.getDecoder().decode(configured.trim()); }
        catch (IllegalArgumentException e) { parsed = new byte[0]; }
        key = parsed.length == 32 ? parsed : null;
    }
    public void requireConfigured() {
        if (key == null) throw BusinessException.of(503, "批量导入尚未配置独立的账号发放加密密钥，请联系管理员");
    }
    public String encrypt(String text, String context) {
        requireConfigured();
        try {
            byte[] nonce = new byte[12]; random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(nonce) + "." + Base64.getEncoder().encodeToString(cipher.doFinal(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw BusinessException.of(503, "账号发放凭据加密失败"); }
    }
    public String decrypt(String encoded, String context) {
        requireConfigured();
        try {
            String[] pieces = encoded.split("\\.", -1);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, Base64.getDecoder().decode(pieces[0])));
            cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(Base64.getDecoder().decode(pieces[1])), StandardCharsets.UTF_8);
        } catch (Exception e) { throw BusinessException.of(409, "账号发放凭据不可用，请为未激活用户重新设置临时密码"); }
    }
}
