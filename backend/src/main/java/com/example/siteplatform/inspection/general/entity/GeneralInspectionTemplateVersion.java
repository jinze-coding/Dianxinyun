package com.example.siteplatform.inspection.general.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("general_inspection_template_version")
public class GeneralInspectionTemplateVersion {
    @TableId(type = IdType.AUTO) private Long id;
    private Long templateId;
    private Integer versionNo;
    private LocalDateTime effectiveTime;
    private String templateName;
    private String categoryName;
    private Integer overallPhotoMin;
    private Integer overallPhotoMax;
    private Integer overallRemarkRequired;
    private String remark;
    private Long publishedById;
    private String publishedByName;
    private LocalDateTime createTime;
}
