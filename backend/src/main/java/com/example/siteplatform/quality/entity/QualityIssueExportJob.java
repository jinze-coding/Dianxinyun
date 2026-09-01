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
@TableName("quality_issue_export_job")
public class QualityIssueExportJob {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long requestedById;
    private String requestedByName;
    private LocalDate startDate;
    private LocalDate endDate;
    private String issueSource;
    private String issueStatus;
    private String keyword;
    private String status;
    private Integer progress;
    private Integer issueCount;
    private Integer photoCount;
    private Long photoBytes;
    private Long fileResourceId;
    private String errorMessage;
    private LocalDateTime expiresTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
