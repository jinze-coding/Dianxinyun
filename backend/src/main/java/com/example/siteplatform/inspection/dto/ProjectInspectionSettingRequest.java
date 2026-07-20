package com.example.siteplatform.inspection.dto;

import lombok.Data;

import java.time.LocalTime;

@Data
public class ProjectInspectionSettingRequest {
    private LocalTime dailyCutoffTime;
    private Integer preDueReminderMinutes;
    private Integer reviewDueHours;
    private Integer rectificationDays;
    private Boolean enabled;
}
