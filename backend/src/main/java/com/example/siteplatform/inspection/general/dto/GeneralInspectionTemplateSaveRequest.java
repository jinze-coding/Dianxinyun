package com.example.siteplatform.inspection.general.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class GeneralInspectionTemplateSaveRequest {
    private Long projectId;
    @NotBlank private String templateName;
    private String categoryName;
    @Min(0) @Max(9) private Integer overallPhotoMin;
    @Min(0) @Max(9) private Integer overallPhotoMax;
    private Boolean overallRemarkRequired;
    private String remark;
    @NotNull private Integer expectedVersion;
    @Valid @NotEmpty @Size(max = 50) private List<GeneralInspectionTemplateItemRequest> items;
}
