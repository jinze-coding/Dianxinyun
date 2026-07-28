package com.example.siteplatform.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WechatSelfUnbindRequest {
    @NotBlank(message = "密码不能为空")
    @Size(max = 72)
    private String password;
}
