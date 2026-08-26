package com.example.siteplatform.inspection.general.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GeneralInspectionCorrectionNoteRequest {
    @NotNull private Integer expectedVersion;
    @NotBlank private String reason;
    private String remark;
    private String publicRemark;
}
