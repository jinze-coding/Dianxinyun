package com.example.siteplatform.auth.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class WechatUserListItemVO {
    private Long bindingId;
    private Long userId;
    private String username;
    private String realName;
    private String phone;
    private String bindingStatus;
    private LocalDateTime bindTime;
    private LocalDateTime lastLoginTime;
    private Integer projectCount;
    private Long projectId;
    private String projectName;
    private String projectRoleCode;
    private Long permissionTemplateId;
    private String permissionTemplateName;
    private String projectAccessStatus;
    private String registrationSource;
}
