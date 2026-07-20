package com.example.siteplatform.inspection.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("inspection_rectification_review_log")
public class InspectionRectificationReviewLog {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long rectificationId;
    private Long projectId;
    private Long electricBoxId;
    private Long inspectionRecordId;
    private String actionType;
    private String fromStatus;
    private String toStatus;
    private Long operatorId;
    private String operatorName;
    private String comment;
    private String photoFileIds;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
