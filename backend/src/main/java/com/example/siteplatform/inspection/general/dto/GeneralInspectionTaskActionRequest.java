package com.example.siteplatform.inspection.general.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GeneralInspectionTaskActionRequest {
    @NotNull private Integer expectedVersion;
    @NotBlank private String reason;
    private Long assigneeId;
    private Long reviewerId;
}
