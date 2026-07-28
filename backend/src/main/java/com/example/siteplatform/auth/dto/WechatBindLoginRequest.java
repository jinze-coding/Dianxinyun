package com.example.siteplatform.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WechatBindLoginRequest {
    @NotBlank(message = "微信登录 code 不能为空")
    @Size(max = 512)
    private String code;
    @NotBlank(message = "账号不能为空")
    @Size(max = 50)
    private String username;
    @NotBlank(message = "密码不能为空")
    @Size(max = 72)
    private String password;
}
