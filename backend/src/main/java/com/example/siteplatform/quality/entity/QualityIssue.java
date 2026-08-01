package com.example.siteplatform.quality.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("quality_issue")
public class QualityIssue {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String issueNo;
    private String requestKey;
    private String title;
    private String location;
    private String description;
    private String severity;
    private String status;
    private Long assigneeId;
    private String assigneeName;
    private LocalDate deadline;
    private String rectificationDescription;
    private String rectificationPhotoFileIds;
    private LocalDateTime rectifiedTime;
    private Long reviewerId;
    private String reviewerName;
    private String reviewComment;
    private LocalDateTime reviewTime;
    private Long createdById;
    private String createdByName;
    private Integer version;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
