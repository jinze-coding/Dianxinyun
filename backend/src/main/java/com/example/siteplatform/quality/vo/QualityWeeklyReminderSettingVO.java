package com.example.siteplatform.quality.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
public class QualityWeeklyReminderSettingVO {
    private Long id;
    private Long projectId;
    private Boolean enabled;
    private Integer dayOfWeek;
    private LocalTime triggerTime;
    private Long responsibleUserId;
    private String responsibleUserName;
    private LocalDateTime reminderEffectiveTime;
    private LocalDateTime nextReminderTime;
    private Integer version;
    private Long updatedById;
    private String updatedByName;
    private LocalDateTime updateTime;
}
