package com.example.siteplatform.auth.dto;

import lombok.Data;

@Data
public class WechatPhoneRequest {
    private String wechatSessionToken;
    private String phoneCode;
    private String phone;
    private String realName;
    private String scene;
}
