package com.example.siteplatform.inspection.general.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GeneralInspectionScanRequest {
    @NotBlank private String sceneCode;
}
