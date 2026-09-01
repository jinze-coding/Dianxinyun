package com.example.siteplatform.document.vo;

import lombok.Data;

@Data
public class DocumentDistributionItemVO {
    private Long id;
    private Integer itemOrder;
    private Long documentId;
    private Long versionId;
    private String documentNo;
    private String title;
    private String documentType;
    private Integer systemVersionNo;
    private String externalRevision;
    private String fileName;
    private String sha256;
    private Integer paperCopyCount;
}
