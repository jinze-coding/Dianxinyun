package com.example.siteplatform.inspection.general.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class GeneralInspectionCorrectionRequest {
    @NotNull private Integer expectedVersion;
    @NotBlank private String reason;
    @Size(max = 9) private List<Long> overallPhotoFileIds;
    private String remark;
    private String publicRemark;
    @Valid @NotEmpty @Size(max = 50) private List<GeneralInspectionTaskSubmitRequest.ItemResult> items;
}
