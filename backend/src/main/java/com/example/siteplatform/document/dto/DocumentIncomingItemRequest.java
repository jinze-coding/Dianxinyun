package com.example.siteplatform.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DocumentIncomingItemRequest {
    private Long id;
    @NotNull @Positive
    private Long fileResourceId;
    private Long folderId;
    @NotBlank @Size(max = 200)
    private String title;
    @Size(max = 100)
    private String documentNo;
    @NotBlank
    private String documentType;
    @Size(max = 100)
    private String externalRevision;
    @NotBlank
    private String matchMode;
    private Long targetDocumentId;
    @Size(max = 500)
    private String duplicateRevisionReason;
    @Size(max = 500)
    private String changeNote;
    private Integer itemOrder;
}
