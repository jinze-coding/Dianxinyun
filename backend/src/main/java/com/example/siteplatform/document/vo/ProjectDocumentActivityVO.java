package com.example.siteplatform.document.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProjectDocumentActivityVO {
    private Long id;
    private Long documentId;
    private String operationType;
    private String operationLabel;
    private String description;
    private Long operatorId;
    private String operatorName;
    private LocalDateTime createTime;
}
