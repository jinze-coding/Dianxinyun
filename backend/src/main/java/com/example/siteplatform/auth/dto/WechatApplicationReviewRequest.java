package com.example.siteplatform.auth.dto;

import lombok.Data;

import java.util.List;

@Data
public class WechatApplicationReviewRequest {
    private String accountMode;
    private Long userId;
    private String username;
    private String realName;
    private List<Long> roleIds;
    @Deprecated
    private String projectRoleCode;
    @Deprecated
    private Long permissionTemplateId;
    private String comment;
}
