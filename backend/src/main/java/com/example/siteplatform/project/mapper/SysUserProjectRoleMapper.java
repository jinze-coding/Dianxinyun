package com.example.siteplatform.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.siteplatform.project.entity.SysUserProjectRole;
import com.example.siteplatform.system.entity.SystemRole;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysUserProjectRoleMapper extends BaseMapper<SysUserProjectRole> {

    @Select("""
            SELECT r.*
            FROM sys_user_project_role upr
            INNER JOIN sys_role r ON r.id = upr.role_id
            WHERE upr.user_id = #{userId}
              AND upr.project_id = #{projectId}
              AND r.scope_type = 'PROJECT'
              AND r.deleted = 0
            ORDER BY r.project_manager_role DESC, r.role_name, r.id
            """)
    List<SystemRole> selectAssignedRoles(@Param("userId") Long userId,
                                         @Param("projectId") Long projectId);

    @Select("""
            SELECT r.*
            FROM sys_user_project_role upr
            INNER JOIN sys_role r ON r.id = upr.role_id
            WHERE upr.user_id = #{userId}
              AND upr.project_id = #{projectId}
              AND r.scope_type = 'PROJECT'
              AND r.enabled = 1
              AND r.deleted = 0
            ORDER BY r.project_manager_role DESC, r.role_name, r.id
            """)
    List<SystemRole> selectEnabledRoles(@Param("userId") Long userId,
                                        @Param("projectId") Long projectId);

    @Select("""
            SELECT DISTINCT upr.user_id
            FROM sys_user_project_role upr
            WHERE upr.role_id = #{roleId}
            ORDER BY upr.user_id
            """)
    List<Long> selectUserIdsByRoleId(@Param("roleId") Long roleId);

    @Select("""
            SELECT COUNT(1)
            FROM sys_user_project_role upr
            INNER JOIN sys_role r ON r.id = upr.role_id
            WHERE upr.user_id = #{userId}
              AND upr.project_id = #{projectId}
              AND r.project_manager_role = 1
              AND r.enabled = 1
              AND r.deleted = 0
            """)
    long countEnabledProjectManagerRoles(@Param("userId") Long userId,
                                         @Param("projectId") Long projectId);

    @Select("""
            SELECT COUNT(1)
            FROM sys_user_project_role upr
            INNER JOIN sys_role r ON r.id = upr.role_id
            WHERE upr.user_id = #{userId}
              AND upr.project_id = #{projectId}
              AND r.scope_type = 'PROJECT'
              AND r.enabled = 1
              AND r.deleted = 0
            """)
    long countEnabledRoles(@Param("userId") Long userId, @Param("projectId") Long projectId);

    @Delete("DELETE FROM sys_user_project_role WHERE user_id = #{userId} AND project_id = #{projectId}")
    void deleteByUserAndProject(@Param("userId") Long userId, @Param("projectId") Long projectId);

    @Delete("DELETE FROM sys_user_project_role WHERE user_id = #{userId}")
    void deleteByUserId(@Param("userId") Long userId);
}
