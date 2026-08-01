package com.example.siteplatform.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 微信快捷注册账号的首次密码设置请求。 */
@Data
public class InitialPasswordRequest {
    @NotBlank
    @Size(min = 8, max = 72)
    private String newPassword;
}
