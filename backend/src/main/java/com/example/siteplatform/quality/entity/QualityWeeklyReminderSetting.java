package com.example.siteplatform.quality.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@TableName("quality_weekly_reminder_setting")
public class QualityWeeklyReminderSetting {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Integer enabled;
    private Integer dayOfWeek;
    private LocalTime triggerTime;
    private Long responsibleUserId;
    private String responsibleUserName;
    private LocalDateTime effectiveTime;
    private Integer version;
    private Long createdById;
    private String createdByName;
    private Long updatedById;
    private String updatedByName;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
