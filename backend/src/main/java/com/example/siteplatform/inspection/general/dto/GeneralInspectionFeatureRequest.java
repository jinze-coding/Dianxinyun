package com.example.siteplatform.inspection.general.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GeneralInspectionFeatureRequest {
    @NotNull private Boolean enabled;
    @NotNull private Integer expectedVersion;
}
