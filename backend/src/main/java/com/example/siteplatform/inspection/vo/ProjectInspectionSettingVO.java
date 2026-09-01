package com.example.siteplatform.inspection.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Data
public class ProjectInspectionSettingVO {
    private Long projectId;
    private LocalTime dailyCutoffTime;
    private Integer preDueReminderMinutes;
    private Integer reviewDueHours;
    private Integer rectificationDays;
    private Boolean enabled;
    private Boolean submissionReminderEnabled;
    private LocalDateTime reminderEffectiveTime;
    private LocalDateTime nextReminderTime;
    private Integer version;
    private Boolean reminderConfigurationHealthy;
    private Integer invalidReminderBoxCount;
    private List<InspectionReminderConfigurationIssueVO> reminderConfigurationIssues;
}
