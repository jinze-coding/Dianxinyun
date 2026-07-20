package com.example.siteplatform.auth.dto;

import lombok.Data;

@Data
public class WechatSessionResponse {
    private String bindingStatus;
    private String applicationStatus;
    private String token;
    private CurrentUserVO user;
    private String wechatSessionToken;
    private Long projectId;
    private Long sourceId;
    private String message;
}
