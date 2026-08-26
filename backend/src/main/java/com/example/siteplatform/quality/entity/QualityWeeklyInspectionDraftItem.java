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
@TableName("quality_weekly_inspection_draft_item")
public class QualityWeeklyInspectionDraftItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long inspectionId;
    private String itemKey;
    private Integer itemOrder;
    private String title;
    private String location;
    private String description;
    private String severity;
    private Long assigneeId;
    private String assigneeName;
    private LocalDate deadline;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
