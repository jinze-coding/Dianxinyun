package com.example.siteplatform.project.dto;

import lombok.Data;

import java.util.List;

@Data
public class InspectionPermissionTemplateRequest {
    private String templateName;
    private String templateCode;
    private String description;
    private List<String> permissionCodes;
    private Integer enabled;
}
