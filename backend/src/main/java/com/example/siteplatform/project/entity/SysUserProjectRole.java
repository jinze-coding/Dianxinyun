package com.example.siteplatform.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 项目成员与项目角色的多对多关系。
 *
 * <p>项目访问状态继续由 {@link SysUserProject} 管理；本表只描述成员在该项目
 * 拥有的角色。一个成员可以拥有多个角色，实际菜单和操作权限按并集计算。</p>
 */
@Data
@TableName("sys_user_project_role")
public class SysUserProjectRole {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long projectId;
    private Long roleId;
    private Long createdBy;
    private LocalDateTime createTime;
}
