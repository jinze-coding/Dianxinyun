package com.example.siteplatform.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user_project")
public class SysUserProject {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long projectId;
    private String projectRoleCode;
    private Long inspectionPermissionTemplateId;
    private String status;
    private String statusReason;
    private Long statusChangedBy;
    private LocalDateTime statusChangedTime;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
