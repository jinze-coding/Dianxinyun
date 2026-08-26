package com.example.siteplatform.inspection.general.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class GeneralInspectionRectificationRequest {
    @NotNull private Integer expectedVersion;
    private Long assigneeId;
    private LocalDate deadline;
    private String requirement;
    private String comment;
    @Size(max = 9) private List<Long> photoFileIds;
}
