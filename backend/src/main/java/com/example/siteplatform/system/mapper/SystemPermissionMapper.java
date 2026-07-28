package com.example.siteplatform.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.siteplatform.system.entity.SystemPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SystemPermissionMapper extends BaseMapper<SystemPermission> {
    @Select("""
            SELECT DISTINCT p.permission_code
            FROM sys_permission p
            INNER JOIN sys_role_permission rp ON rp.permission_id = p.id
            INNER JOIN sys_role r
              ON r.id = rp.role_id
             AND r.scope_type = 'PLATFORM'
             AND r.enabled = 1
             AND r.deleted = 0
            INNER JOIN sys_user_role ur
              ON ur.role_id = r.id
             AND ur.user_id = #{userId}
            WHERE p.enabled = 1
              AND p.deleted = 0
            ORDER BY p.permission_code
            """)
    List<String> selectPlatformCodesByUserId(@Param("userId") Long userId);

    @Select("""
            SELECT DISTINCT p.permission_code
            FROM sys_permission p
            INNER JOIN sys_role_permission rp ON rp.permission_id = p.id
            INNER JOIN sys_role r ON r.id = rp.role_id
            LEFT JOIN sys_user_role ur
              ON ur.role_id = r.id AND r.scope_type = 'PLATFORM' AND ur.user_id = #{userId}
            LEFT JOIN sys_user_project up
              ON r.scope_type = 'PROJECT'
             AND up.user_id = #{userId}
             AND up.project_role_code = r.role_code
             AND up.status = 'ACTIVE'
            WHERE (ur.user_id IS NOT NULL OR up.user_id IS NOT NULL)
              AND p.enabled = 1 AND p.deleted = 0
              AND r.enabled = 1 AND r.deleted = 0
            ORDER BY p.permission_code
            """)
    List<String> selectCodesByUserId(@Param("userId") Long userId);

    @Select("""
            SELECT DISTINCT p.permission_code
            FROM sys_permission p
            INNER JOIN sys_role_permission rp ON rp.permission_id = p.id
            INNER JOIN sys_role r ON r.id = rp.role_id
            LEFT JOIN sys_user_role ur
              ON ur.role_id = r.id
             AND r.scope_type = 'PLATFORM'
             AND ur.user_id = #{userId}
            LEFT JOIN sys_user_project up
              ON r.scope_type = 'PROJECT'
             AND up.user_id = #{userId}
             AND up.project_id = #{projectId}
             AND up.project_role_code = r.role_code
             AND up.status = 'ACTIVE'
            WHERE (ur.user_id IS NOT NULL OR up.user_id IS NOT NULL)
              AND p.enabled = 1 AND p.deleted = 0
              AND r.enabled = 1 AND r.deleted = 0
            ORDER BY p.permission_code
            """)
    List<String> selectCodesByUserIdAndProject(@Param("userId") Long userId,
                                               @Param("projectId") Long projectId);

    @Select("""
            SELECT DISTINCT p.permission_code
            FROM sys_permission p
            INNER JOIN sys_role_permission rp ON rp.permission_id = p.id
            INNER JOIN sys_role r ON r.id = rp.role_id
            WHERE r.role_code = #{roleCode}
              AND r.scope_type = 'PROJECT'
              AND r.enabled = 1 AND r.deleted = 0
              AND p.enabled = 1 AND p.deleted = 0
            ORDER BY p.permission_code
            """)
    List<String> selectCodesByProjectRole(@Param("roleCode") String roleCode);
}
