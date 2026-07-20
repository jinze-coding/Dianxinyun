package com.example.siteplatform.project.dto;

import lombok.Data;

import java.util.List;

@Data
public class ProjectUserOptionVO {
    private Long id;
    private String username;
    private String realName;
    private String phone;
    private String email;
    private Integer status;
    private String projectRoleCode;
    private Long permissionTemplateId;
    private String permissionTemplateName;
    private String permissionTemplateCode;
    private String permissionCodeText;
    private List<String> permissionCodes;
    private Boolean inProject;
    private String accessStatus;
    private List<String> globalRoles;
}
