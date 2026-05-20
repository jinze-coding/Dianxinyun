package com.example.siteplatform.project.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("project_info")
public class ProjectInfo {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String projectName;
    private String shortName;
    private String area;
    private String period;
    private String phase;
    private String projectStatus;
    private String safetyGoal;
    private String qualityGoal;
    private String manager;
    private String contractor;
    private String description;
    private LocalDate startDate;
    private LocalDate endDate;

    private BigDecimal longitude;
    private BigDecimal latitude;
    private String address;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
