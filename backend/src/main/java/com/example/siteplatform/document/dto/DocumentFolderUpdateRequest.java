package com.example.siteplatform.document.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DocumentFolderUpdateRequest {
    @NotBlank
    private String folderName;
}
