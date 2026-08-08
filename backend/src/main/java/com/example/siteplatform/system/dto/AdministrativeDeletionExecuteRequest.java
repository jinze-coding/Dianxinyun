package com.example.siteplatform.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class AdministrativeDeletionExecuteRequest {
    @NotBlank
    private String targetType;

    @NotNull
    @Positive
    private Long targetId;

    @NotBlank
    private String confirmationToken;

    private String confirmationText;

    private boolean acknowledged;
}
