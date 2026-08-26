package com.example.siteplatform.inspection.general.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Data
public class EdgeInspectionSettingRequest {
    @NotBlank private String frequency;
    private List<Integer> weekdays;
    @Min(1) @Max(31) private Integer monthDay;
    private Boolean monthEnd;
    @NotNull private LocalDate effectiveStart;
    @NotNull private LocalTime startTime;
    @NotNull private LocalTime dueTime;
    @NotNull private Long assigneeId;
    @NotNull private Long rectifierId;
    @NotNull private Long reviewerId;
    @NotNull @Min(0) @Max(365) private Integer rectificationDays;
    @NotNull private Boolean enabled;
    @NotNull private Integer expectedVersion;
}
