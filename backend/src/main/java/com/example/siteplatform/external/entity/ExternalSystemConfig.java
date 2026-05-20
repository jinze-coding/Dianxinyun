package com.example.siteplatform.external.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("external_system_config")
public class ExternalSystemConfig {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;
    private String systemName;
    private String systemType;
    private String accessUrl;
    private String status;
    private String description;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
