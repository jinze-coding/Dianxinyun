package com.example.siteplatform.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DocumentScanRequest {
    @NotBlank @Size(max = 300)
    private String scene;
}
