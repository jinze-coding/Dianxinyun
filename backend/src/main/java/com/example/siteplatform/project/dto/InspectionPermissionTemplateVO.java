package com.example.siteplatform.project.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class InspectionPermissionTemplateVO {
    private Long id;
    private String templateName;
    private String templateCode;
    private String description;
    private List<String> permissionCodes;
    private Integer enabled;
    private Integer builtin;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
