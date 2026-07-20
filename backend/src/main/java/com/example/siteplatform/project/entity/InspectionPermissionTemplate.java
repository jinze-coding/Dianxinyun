package com.example.siteplatform.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("inspection_permission_template")
public class InspectionPermissionTemplate {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String templateName;
    private String templateCode;
    private String description;
    private String permissionCodes;
    private Integer enabled;
    private Integer builtin;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
