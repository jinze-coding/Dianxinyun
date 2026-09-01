package com.example.siteplatform.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DocumentUploadInitRequest {
    @NotNull @Positive
    private Long projectId;
    @NotNull @Positive
    private Long incomingBatchId;
    @NotBlank @Size(max = 200)
    private String fileName;
    @NotNull @Positive
    private Long totalSize;
    @Size(min = 64, max = 64)
    private String sha256;
}
