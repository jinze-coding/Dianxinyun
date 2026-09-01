package com.example.siteplatform.inspection.general.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class GeneralInspectionExportRequest {
    @NotNull private Long projectId;
    @NotNull private LocalDate startDate;
    @NotNull private LocalDate endDate;
    @Size(max = 500) private List<Long> pointIds;
}
