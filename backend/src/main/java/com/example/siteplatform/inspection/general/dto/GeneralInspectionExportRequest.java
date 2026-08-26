package com.example.siteplatform.inspection.general.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class GeneralInspectionExportRequest {
    @NotNull private Long projectId;
    @NotNull private LocalDate startDate;
    @NotNull private LocalDate endDate;
}
