package com.example.siteplatform.inspection.vo;

import lombok.Data;

import java.time.LocalTime;

@Data
public class ProjectInspectionSettingVO {
    private Long projectId;
    private LocalTime dailyCutoffTime;
    private Integer preDueReminderMinutes;
    private Integer reviewDueHours;
    private Integer rectificationDays;
    private Boolean enabled;
}
