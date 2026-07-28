package com.example.siteplatform.auth.service;

import com.example.siteplatform.common.BusinessException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PasswordCredentialService {

    private static final String BCRYPT_PATTERN = "^\\$2[aby]\\$\\d{2}\\$.{53}$";
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

    public String encode(String rawPassword) {
        validateStrength(rawPassword);
        return encoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String encodedPassword) {
        return StringUtils.hasText(rawPassword)
                && isBcrypt(encodedPassword)
                && encoder.matches(rawPassword, encodedPassword);
    }

    public boolean isBcrypt(String value) {
        return StringUtils.hasText(value) && value.matches(BCRYPT_PATTERN);
    }

    public void validateStrength(String rawPassword) {
        if (!StringUtils.hasText(rawPassword) || rawPassword.length() < 8 || rawPassword.length() > 72) {
            throw new BusinessException("密码长度必须为8-72位");
        }
        if (!rawPassword.matches(".*[A-Za-z].*") || !rawPassword.matches(".*\\d.*")) {
            throw new BusinessException("密码必须同时包含字母和数字");
        }
    }
}
