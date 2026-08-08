package com.example.siteplatform.seal.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SealApplicationFileVO {
    private Long id;
    private String fileRole;
    private Long itemId;
    private String fileName;
    private String originalFileName;
    private Long fileSize;
    private String mimeType;
    private String fileExtension;
    private Long uploaderId;
    private String uploaderName;
    private Long archivedDocumentId;
    private Long archivedVersionId;
    private LocalDateTime createTime;
    private Boolean canPreview;
    private Boolean canDelete;
}
