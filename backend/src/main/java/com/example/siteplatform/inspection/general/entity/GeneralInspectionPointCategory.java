package com.example.siteplatform.inspection.general.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("general_inspection_point_category")
public class GeneralInspectionPointCategory {
    @TableId(type = IdType.AUTO) private Long id;
    private Long projectId;
    private String categoryCode;
    private String categoryName;
    private Integer builtin;
    private Integer enabled;
    private Long createdById;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
