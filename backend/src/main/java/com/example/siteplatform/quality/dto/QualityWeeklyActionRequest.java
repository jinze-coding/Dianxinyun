package com.example.siteplatform.quality.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

@Data
public class QualityWeeklyActionRequest {
    @NotNull(message = "expectedVersion不能为空")
    @PositiveOrZero(message = "expectedVersion不能为负数")
    private Integer expectedVersion;
}
