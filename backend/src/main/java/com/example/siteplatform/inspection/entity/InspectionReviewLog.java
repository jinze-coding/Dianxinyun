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
@TableName("inspection_review_log")
public class InspectionReviewLog {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long recordId;
    private Long projectId;
    private Long electricBoxId;
    private String actionType;
    private Long fromReviewerId;
    private String fromReviewerName;
    private Long toReviewerId;
    private String toReviewerName;
    private Long operatorId;
    private String operatorName;
    private String comment;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
