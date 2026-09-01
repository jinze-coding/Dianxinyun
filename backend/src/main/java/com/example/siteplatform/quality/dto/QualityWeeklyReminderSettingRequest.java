package com.example.siteplatform.quality.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.time.LocalTime;

@Data
public class QualityWeeklyReminderSettingRequest {
    @NotNull(message = "提醒开关不能为空")
    private Boolean enabled;

    @NotNull(message = "提醒星期不能为空")
    @Min(value = 1, message = "提醒星期必须在1到7之间")
    @Max(value = 7, message = "提醒星期必须在1到7之间")
    private Integer dayOfWeek;

    @NotNull(message = "提醒时间不能为空")
    private LocalTime triggerTime;

    @Positive(message = "责任人ID必须为正数")
    private Long responsibleUserId;

    @NotNull(message = "expectedVersion不能为空")
    @PositiveOrZero(message = "expectedVersion不能为负数")
    private Integer expectedVersion;
}
