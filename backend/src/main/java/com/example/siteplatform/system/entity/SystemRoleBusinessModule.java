package com.example.siteplatform.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 角色与正式业务模块（资料、巡检、质量）的统一关联。 */
@Data
@TableName("sys_role_business_module")
public class SystemRoleBusinessModule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long roleId;
    private String moduleCode;
    private LocalDateTime createTime;
}
