package com.example.siteplatform.inspection.general.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("general_inspection_plan_version")
public class GeneralInspectionPlanVersion {
    @TableId(type = IdType.AUTO) private Long id;
    private Long planId;
    private Integer versionNo;
    private LocalDateTime effectiveTime;
    private String configJson;
    private Long publishedById;
    private String publishedByName;
    private LocalDateTime createTime;
}
