package com.example.siteplatform.quality.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.time.LocalDate;

@Data
public class QualityWeeklyDraftCreateRequest {
    @NotNull(message = "项目ID不能为空")
    @Positive(message = "项目ID必须为正数")
    private Long projectId;

    @NotNull(message = "周起始日不能为空")
    private LocalDate weekStart;
}
