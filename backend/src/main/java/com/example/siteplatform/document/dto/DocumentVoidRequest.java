package com.example.siteplatform.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DocumentVoidRequest {
    @NotBlank @Size(max = 500)
    private String reason;
}
