package com.example.siteplatform.quality.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class QualityAssignRequest {
    @Positive(message = "整改负责人ID必须为正数")
    private Long assigneeId;

    @FutureOrPresent(message = "闭环期限不能早于今天")
    private LocalDate deadline;

    @Size(max = 1000, message = "改派说明长度不能超过1000个字符")
    private String comment;
}
