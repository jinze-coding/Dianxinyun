package com.example.siteplatform.inspection.general.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class EdgeInspectionPointSaveRequest {
    @NotNull private Long projectId;
    @NotBlank private String pointName;
    @NotBlank private String pointTypeCode;
    private String buildingName;
    private String floorName;
    private String locationDesc;
    private Integer expectedVersion;
}
