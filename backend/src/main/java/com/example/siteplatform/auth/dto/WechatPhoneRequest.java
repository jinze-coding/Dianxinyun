package com.example.siteplatform.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WechatPhoneRequest {
    @NotBlank(message = "微信登录会话不能为空")
    @Size(max = 128)
    private String wechatSessionToken;
    @Size(max = 512)
    private String phoneCode;
    @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确")
    private String phone;
    @Size(max = 50)
    private String realName;
    @NotBlank(message = "巡检场景码不能为空")
    @Size(max = 256)
    private String scene;
}
