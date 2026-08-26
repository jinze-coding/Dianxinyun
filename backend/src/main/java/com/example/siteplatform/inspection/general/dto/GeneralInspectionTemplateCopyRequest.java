package com.example.siteplatform.inspection.general.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GeneralInspectionTemplateCopyRequest {
    @NotNull private Long projectId;
    @NotBlank private String templateName;
}
