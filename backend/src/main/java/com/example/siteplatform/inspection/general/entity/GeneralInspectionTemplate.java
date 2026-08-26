package com.example.siteplatform.inspection.general.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("general_inspection_template")
public class GeneralInspectionTemplate {
    @TableId(type = IdType.AUTO) private Long id;
    private String scopeType;
    private Long projectId;
    private Long sourceTemplateId;
    private String templateCode;
    private String templateName;
    private String categoryName;
    private String status;
    private Long currentVersionId;
    private Integer overallPhotoMin;
    private Integer overallPhotoMax;
    private Integer overallRemarkRequired;
    private String remark;
    private Integer version;
    private Long createdById;
    private String createdByName;
    private Long updatedById;
    private String updatedByName;
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
