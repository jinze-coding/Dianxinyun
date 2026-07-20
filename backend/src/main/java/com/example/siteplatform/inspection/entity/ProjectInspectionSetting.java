package com.example.siteplatform.inspection.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@TableName("project_inspection_setting")
public class ProjectInspectionSetting {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private LocalTime dailyCutoffTime;
    private Integer preDueReminderMinutes;
    private Integer reviewDueHours;
    private Integer rectificationDays;
    private Integer enabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
