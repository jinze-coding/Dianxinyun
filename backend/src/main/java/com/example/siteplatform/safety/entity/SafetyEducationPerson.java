package com.example.siteplatform.safety.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("safety_education_person")
public class SafetyEducationPerson {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long batchId;
    private Long personId;
    private String status;
    private LocalDateTime finishTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
