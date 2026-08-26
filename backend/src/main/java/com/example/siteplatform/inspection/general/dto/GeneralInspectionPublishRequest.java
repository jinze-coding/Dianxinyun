package com.example.siteplatform.inspection.general.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class GeneralInspectionPublishRequest {
    @NotNull private Integer expectedVersion;
    @NotNull private LocalDateTime effectiveTime;
}
