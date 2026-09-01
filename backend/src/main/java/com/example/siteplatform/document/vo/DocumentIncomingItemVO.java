package com.example.siteplatform.document.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DocumentIncomingItemVO {
    private Long id;
    private Long fileResourceId;
    private Long folderId;
    private Integer itemOrder;
    private String title;
    private String documentNo;
    private String documentType;
    private String externalRevision;
    private String matchMode;
    private Long targetDocumentId;
    private String duplicateRevisionReason;
    private String changeNote;
    private Long publishedDocumentId;
    private Long publishedVersionId;
    private String status;
    private String fileName;
    private Long fileSize;
    private String sha256;
    private LocalDateTime createTime;
}
