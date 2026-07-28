package com.example.siteplatform.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("sys_role")
public class SystemRole {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String roleName;
    private String roleCode;
    private String description;
    private String scopeType;
    private Integer builtin;
    private Integer enabled;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableField(exist = false)
    private List<Long> menuIds;
    @TableField(exist = false)
    private List<Long> permissionIds;

    public String getCode() { return roleCode; }
    public String getName() { return roleName; }
}
