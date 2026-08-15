package com.example.siteplatform.siteaccess.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

@Data
public class SiteGuardVisitQrRotateRequest {
    @NotNull
    @PositiveOrZero
    private Integer version;
}
