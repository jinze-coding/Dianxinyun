package com.example.siteplatform.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WechatSessionRequest {
    @NotBlank(message = "微信登录 code 不能为空")
    @Size(max = 512)
    private String code;
    @Size(max = 256)
    private String scene;
}
