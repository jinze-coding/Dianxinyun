package com.example.siteplatform.auth.dto;

import lombok.Data;

@Data
public class WechatApplicationReviewRequest {
    private String accountMode;
    private Long userId;
    private String username;
    private String realName;
    private String projectRoleCode;
    private Long permissionTemplateId;
    private String comment;
}
