package com.example.siteplatform.document.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lombok.Data;

@Data
public class DocumentRecipientCopyRequest {
    private Long incomingItemId;
    private Long versionId;
    @Min(0) @Max(999)
    private Integer paperCopyCount;
}
