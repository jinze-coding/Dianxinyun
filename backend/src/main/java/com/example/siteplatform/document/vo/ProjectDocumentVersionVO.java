package com.example.siteplatform.document.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProjectDocumentVersionVO {
    private Long id;
    private Integer versionNo;
    private String versionLabel;
    private Long fileResourceId;
    private String fileName;
    private String mimeType;
    private String fileExtension;
    private Long fileSize;
    private String sha256;
    private String changeNote;
    private Long createdBy;
    private String createdByName;
    private LocalDateTime createTime;
}
