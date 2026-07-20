package com.example.siteplatform.auth.dto;

import lombok.Data;

@Data
public class WechatSessionRequest {
    private String code;
    private String scene;
}
