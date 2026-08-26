package com.example.siteplatform.quality.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("quality_weekly_inspection")
public class QualityWeeklyInspection {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String inspectionNo;
    private LocalDate weekStart;
    private LocalDate inspectionDate;
    private String conclusion;
    private String status;
    private Integer submittedIssueCount;
    private Long createdById;
    private String createdByName;
    private Long lastEditedById;
    private String lastEditedByName;
    private Long submittedById;
    private String submittedByName;
    private LocalDateTime submittedTime;
    private Integer version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
