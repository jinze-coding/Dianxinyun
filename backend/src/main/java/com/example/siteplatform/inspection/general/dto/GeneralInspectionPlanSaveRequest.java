package com.example.siteplatform.inspection.general.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GeneralInspectionPlanSaveRequest {
    @NotNull private Long projectId;
    @NotNull private Long templateId;
    @NotBlank private String planName;
    @NotNull private Integer expectedVersion;
    @Valid @NotNull private GeneralInspectionPlanConfig config;
}
