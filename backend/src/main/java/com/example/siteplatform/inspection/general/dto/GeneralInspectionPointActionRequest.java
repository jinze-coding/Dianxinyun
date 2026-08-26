package com.example.siteplatform.inspection.general.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GeneralInspectionPointActionRequest {
    @NotNull private Integer expectedVersion;
    private Boolean enabled;
    private String reason;
}
