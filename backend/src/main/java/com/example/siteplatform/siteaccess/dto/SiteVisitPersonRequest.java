package com.example.siteplatform.siteaccess.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SiteVisitPersonRequest {
    @NotBlank
    @Size(max = 50)
    private String personName;
    @NotBlank
    @Size(max = 18)
    private String idCard;
}
