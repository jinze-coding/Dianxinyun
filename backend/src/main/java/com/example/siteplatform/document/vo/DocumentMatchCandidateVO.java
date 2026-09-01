package com.example.siteplatform.document.vo;

import lombok.Data;

@Data
public class DocumentMatchCandidateVO {
    private Long documentId;
    private String documentNo;
    private String title;
    private String documentType;
    private Long currentVersionId;
    private Integer currentVersionNo;
    private String currentExternalRevision;
}
