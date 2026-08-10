package com.example.siteplatform.project.dto;

import lombok.Data;

@Data
public class ProjectProfileImageVO {
    private Long fileId;
    private String fileName;
    private String mimeType;
    private Long fileSize;
    private Integer sortOrder;
    private Boolean cover;
}
