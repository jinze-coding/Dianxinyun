package com.example.siteplatform.project.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import com.example.siteplatform.system.entity.SystemRole;

@Data
public class ProjectMemberVO {
    private Long memberId;
    private Long projectId;
    private Long userId;
    private String username;
    private String realName;
    private String phone;
    private String email;
    private Integer status;
    private List<SystemRole> projectRoles;
    private String projectRoleCode;
    private Long permissionTemplateId;
    private String accessStatus;
    private String statusReason;
    private Long statusChangedBy;
    private LocalDateTime statusChangedTime;
    private String permissionTemplateName;
    private String permissionTemplateCode;
    private String permissionCodeText;
    private List<String> permissionCodes;
    private List<String> globalRoles;
    private Integer responsibleBoxCount;
    private Integer pendingRectificationCount;
    private LocalDateTime authorizedAt;
    private LocalDateTime lastOperationTime;
}
