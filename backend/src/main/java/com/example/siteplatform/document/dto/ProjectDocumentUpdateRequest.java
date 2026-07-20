package com.example.siteplatform.document.dto;

import lombok.Data;

@Data
public class ProjectDocumentUpdateRequest {
    private Long folderId;
    private String documentNo;
    private String title;
    private String category;
    private String remark;
}
