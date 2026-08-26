package com.example.siteplatform.inspection.general.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("general_inspection_plan")
public class GeneralInspectionPlan {
    @TableId(type = IdType.AUTO) private Long id;
    private Long projectId;
    private Long templateId;
    private String planCode;
    private String planName;
    private String status;
    private String draftConfigJson;
    private Long currentVersionId;
    private LocalDateTime generatedThroughTime;
    private Integer version;
    private Long createdById;
    private String createdByName;
    private Long updatedById;
    private String updatedByName;
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
