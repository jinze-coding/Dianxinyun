package com.example.siteplatform.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DocumentFolderCreateRequest {
    @NotNull
    private Long projectId;
    private Long parentId = 0L;
    @NotBlank
    private String folderName;
}
