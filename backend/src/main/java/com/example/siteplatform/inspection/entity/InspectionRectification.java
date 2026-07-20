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
@TableName("inspection_rectification")
public class InspectionRectification {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;
    private Long electricBoxId;
    private Long inspectionRecordId;
    private Long recordItemId;
    private String boxCode;
    private String problemDesc;
    private String problemCategory;
    private String requirement;
    private Long assigneeId;
    private String assigneeName;
    private LocalDate deadline;
    private String status;
    private String feedback;
    private String rectificationPhotoFileIds;
    private LocalDateTime completedTime;
    private Long reviewerId;
    private String reviewerName;
    private LocalDateTime reviewTime;
    private String reviewComment;
    private Integer rejectCount;
    private LocalDate recheckDeadline;
    private String escalationStatus;
    private LocalDateTime escalationTime;
    private String escalationNote;
    private LocalDateTime closeTime;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
