package com.example.siteplatform.project.dto;

import lombok.Data;

@Data
public class CreateProjectUserRequest {
    private Long projectId;
    private String username;
    private String password;
    private Integer passwordLoginEnabled;
    private String realName;
    private String phone;
    private String email;
    private String globalRoleCode;
    private String projectRoleCode;
    private Long permissionTemplateId;
}
