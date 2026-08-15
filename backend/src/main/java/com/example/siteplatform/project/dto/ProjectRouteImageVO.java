package com.example.siteplatform.project.dto;

import lombok.Data;

@Data
public class ProjectRouteImageVO {
    private Long fileId;
    private String fileName;
    private Long fileSize;
    private String mimeType;
}
