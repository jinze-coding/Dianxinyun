package com.example.siteplatform.inspection.general.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Data
public class EdgeInspectionSettingVO {
    private Long id;
    private Long projectId;
    private String frequency;
    private List<Integer> weekdays;
    private Integer monthDay;
    private Boolean monthEnd;
    private LocalDate effectiveStart;
    private LocalTime startTime;
    private LocalTime dueTime;
    private Long assigneeId;
    private String assigneeName;
    private Long rectifierId;
    private String rectifierName;
    private Long reviewerId;
    private String reviewerName;
    private Integer rectificationDays;
    private Boolean enabled;
    private Boolean submissionReminderEnabled;
    private LocalDateTime reminderEffectiveTime;
    private LocalDateTime nextReminderTime;
    private String status;
    private Integer version;
}
