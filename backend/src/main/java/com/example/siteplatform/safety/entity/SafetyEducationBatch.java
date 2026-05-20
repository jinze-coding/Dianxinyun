package com.example.siteplatform.safety.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("safety_education_batch")
public class SafetyEducationBatch {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;
    private String batchName;
    private String eduType;
    private LocalDateTime trainingTime;
    private String trainingPlace;
    private String trainer;
    private String status;
    private String remark;

    @TableField("course_hours")
    private Integer courseHours;

    @TableField("exam_type")
    private String examType;

    @TableField("training_material")
    private String trainingMaterial;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
