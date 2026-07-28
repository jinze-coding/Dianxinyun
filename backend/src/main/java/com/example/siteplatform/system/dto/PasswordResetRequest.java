package com.example.siteplatform.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PasswordResetRequest {
    @NotBlank
    private String newPassword;
}
