package com.example.siteplatform.siteaccess.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SiteVisitVoidRequest {
    @NotBlank
    @Size(max = 300)
    private String reason;
}
