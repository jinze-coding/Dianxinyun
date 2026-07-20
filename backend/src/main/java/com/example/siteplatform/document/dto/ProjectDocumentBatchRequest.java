package com.example.siteplatform.document.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class ProjectDocumentBatchRequest {
    @NotEmpty
    private List<Long> ids;
    @NotBlank
    private String action;
    private Long folderId;
}
