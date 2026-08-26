package com.example.siteplatform.inspection.general.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GeneralInspectionTemplateItemRequest {
    private String itemKey;
    @NotBlank private String itemName;
    private String guidance;
    private String standardReference;
    private Boolean allowNa;
    @Min(0) @Max(9) private Integer normalPhotoMin;
    @Min(0) @Max(9) private Integer abnormalPhotoMin;
    @Min(0) @Max(9) private Integer photoMax;
    private Boolean normalDescriptionRequired;
    private Boolean abnormalDescriptionRequired;
}
