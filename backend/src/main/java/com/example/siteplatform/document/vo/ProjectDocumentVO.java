package com.example.siteplatform.document.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProjectDocumentVO {
    private Long id;
    private Long projectId;
    private Long folderId;
    private String folderName;
    private String documentNo;
    private String title;
    private String category;
    private String status;
    private String remark;
    private Long createdBy;
    private String createdByName;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private ProjectDocumentVersionVO currentVersion;
    private Boolean canEdit;
    private Boolean canManage;
}
