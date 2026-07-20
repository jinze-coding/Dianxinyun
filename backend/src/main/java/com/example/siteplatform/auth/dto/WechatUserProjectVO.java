package com.example.siteplatform.auth.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class WechatUserProjectVO {
    private Long memberId;
    private Long projectId;
    private String projectName;
    private String projectRoleCode;
    private Long permissionTemplateId;
    private String permissionTemplateName;
    private String accessStatus;
    private String statusReason;
    private LocalDateTime statusChangedTime;
}
