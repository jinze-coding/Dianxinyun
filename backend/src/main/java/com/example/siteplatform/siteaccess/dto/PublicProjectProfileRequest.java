package com.example.siteplatform.siteaccess.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PublicProjectProfileRequest {
    @NotBlank
    @Size(max = 64)
    private String inviteToken;
}
