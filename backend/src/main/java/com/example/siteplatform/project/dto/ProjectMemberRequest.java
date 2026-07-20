package com.example.siteplatform.project.dto;

import lombok.Data;

@Data
public class ProjectMemberRequest {
    private Long projectId;
    private Long userId;
    private String projectRoleCode;
    private Long permissionTemplateId;
}
