package com.example.siteplatform.inspection.general.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class EdgeInspectionRectificationReassignRequest {
    @NotNull private Integer expectedVersion;
    private Long assigneeId;
    private Long reviewerId;
    private LocalDate deadline;
    @NotBlank @Size(max = 500) private String reason;
}
