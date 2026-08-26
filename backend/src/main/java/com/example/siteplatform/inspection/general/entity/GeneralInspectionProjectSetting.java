package com.example.siteplatform.inspection.general.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("general_inspection_project_setting")
public class GeneralInspectionProjectSetting {
    @TableId
    private Long projectId;
    private Integer enabled;
    private Integer version;
    private Long updatedById;
    private String updatedByName;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
