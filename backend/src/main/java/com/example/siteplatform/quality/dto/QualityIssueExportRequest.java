package com.example.siteplatform.quality.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class QualityIssueExportRequest {
    @NotNull
    private Long projectId;

    @NotNull
    private LocalDate startDate;

    @NotNull
    private LocalDate endDate;

    private String source = "ALL";
    private String status = "ALL";

    @Size(max = 100, message = "关键词不能超过100个字符")
    private String keyword;
}
