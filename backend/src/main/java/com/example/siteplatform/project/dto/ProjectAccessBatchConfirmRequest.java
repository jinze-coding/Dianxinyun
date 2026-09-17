package com.example.siteplatform.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ProjectAccessBatchConfirmRequest {
    @NotBlank @Pattern(regexp = "[a-f0-9-]{36}")
    private String confirmationToken;
    private boolean confirmResponsibilityRelease;
}
