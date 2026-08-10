package com.example.siteplatform.siteaccess.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PublicVisitorProfileRequest {
    @NotBlank
    @Size(max = 40)
    private String profileCode;
}
