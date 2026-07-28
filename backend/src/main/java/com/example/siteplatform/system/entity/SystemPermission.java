package com.example.siteplatform.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_permission")
public class SystemPermission {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String permissionCode;
    private String permissionName;
    private String moduleCode;
    private String description;
    private Integer enabled;
    private Integer builtin;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
