package com.example.siteplatform.inspection.general.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class GeneralInspectionPointRequest {
    @NotNull private Long projectId;
    @NotBlank private String pointCode;
    @NotBlank private String pointName;
    private Long categoryId;
    private String areaName;
    private String buildingName;
    private String floorName;
    private String locationDesc;
    private String riskNote;
    @Size(max = 9) private List<Long> referencePhotoFileIds;
    private Boolean qrEnabled;
    private Boolean publicAccessEnabled;
    private Integer expectedVersion;
}
