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
@TableName("quality_issue_export_job_item")
public class QualityIssueExportJobItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long jobId;
    private Long issueId;
    private Integer itemOrder;
    private LocalDate recordDate;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
