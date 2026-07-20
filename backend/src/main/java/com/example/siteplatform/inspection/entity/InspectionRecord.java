package com.example.siteplatform.inspection.entity;

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
@TableName("inspection_record")
public class InspectionRecord {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;
    private Long electricBoxId;
    private String templateCode;
    private String source;
    private String problemCategory;
    private LocalDate checkDate;
    private Long inspectorId;
    private String inspectorName;
    private String status;
    private String reviewStatus;
    private Long reviewerId;
    private String reviewerName;
    private LocalDateTime reviewTime;
    private LocalDateTime reviewDueTime;
    private Long assignedReviewerId;
    private String assignedReviewerName;
    private String reviewComment;
    private Integer reviewOverdue;
    private String outerPhotoFileIds;
    private String innerPhotoFileIds;
    private Integer abnormalCount;
    private String remark;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
