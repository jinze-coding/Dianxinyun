package com.example.siteplatform.auth.dto;

import lombok.Data;

@Data
public class UserProjectRoleVO {
    private Long projectId;
    private String projectName;
    private String shortName;
    private String projectRoleCode;
    private Long permissionTemplateId;
    private String permissionTemplateName;
    private String permissionTemplateCode;
    private String permissionCodeText;
    private String accessStatus;
    private String statusReason;
    private java.util.List<String> permissionCodes;
}
