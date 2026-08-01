package com.example.siteplatform.auth.dto;

import lombok.Data;
import com.example.siteplatform.system.entity.SystemRole;

@Data
public class UserProjectRoleVO {
    private Long projectId;
    private String projectName;
    private String shortName;
    private String projectRoleCode;
    private java.util.List<SystemRole> projectRoles;
    private java.util.List<String> menuCodes;
    private Long permissionTemplateId;
    private String permissionTemplateName;
    private String permissionTemplateCode;
    private String permissionCodeText;
    private String accessStatus;
    private String statusReason;
    private java.util.List<String> permissionCodes;
}
