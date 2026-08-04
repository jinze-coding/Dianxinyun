package com.example.siteplatform.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.siteplatform.auth.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;

import java.util.List;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
    @Select("SELECT * FROM sys_user WHERE id = #{userId} FOR UPDATE")
    SysUser selectByIdForUpdate(@Param("userId") Long userId);

    @Select("""
            SELECT r.role_code
            FROM sys_role r
            INNER JOIN sys_user_role ur ON ur.role_id = r.id
            WHERE ur.user_id = #{userId}
              AND r.scope_type = 'PLATFORM'
              AND r.enabled = 1
              AND r.deleted = 0
            """)
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);

    @Select("""
            SELECT project_id
            FROM sys_user_project
            WHERE user_id = #{userId}
              AND status = 'ACTIVE'
            """)
    List<Long> selectProjectIdsByUserId(@Param("userId") Long userId);

    @Select("""
            SELECT id
            FROM sys_role
            WHERE role_code = #{roleCode}
              AND scope_type = #{scopeType}
              AND enabled = 1
              AND deleted = 0
            ORDER BY id
            LIMIT 1
            """)
    Long selectRoleIdByCode(@Param("roleCode") String roleCode,
                            @Param("scopeType") String scopeType);

    @Insert("""
            INSERT INTO sys_user_role (user_id, role_id)
            VALUES (#{userId}, #{roleId})
            """)
    void insertUserRole(@Param("userId") Long userId, @Param("roleId") Long roleId);

    @Delete("DELETE FROM sys_user_role WHERE user_id = #{userId}")
    void deleteUserRoles(@Param("userId") Long userId);

    @Select("SELECT user_id FROM sys_user_role WHERE role_id = #{roleId}")
    List<Long> selectUserIdsByRoleId(@Param("roleId") Long roleId);

    @Select("""
            SELECT COUNT(DISTINCT u.id)
            FROM sys_user u
            INNER JOIN sys_user_role ur ON ur.user_id = u.id
            INNER JOIN sys_role r ON r.id = ur.role_id
            WHERE u.status = 1 AND u.deleted = 0
              AND u.password_login_enabled = 1
              AND u.password_reset_required = 0
              AND u.password REGEXP '^[$]2[aby][$][0-9]{2}[$].{53}$'
              AND r.role_code = 'PLATFORM_ADMIN'
              AND r.scope_type = 'PLATFORM'
              AND r.enabled = 1
              AND r.deleted = 0
            """)
    Long countActivePlatformAdministrators();

    @Select("""
            SELECT DISTINCT u.*
            FROM sys_user u
            INNER JOIN sys_user_role ur ON ur.user_id = u.id
            INNER JOIN sys_role r ON r.id = ur.role_id
            WHERE u.status = 1 AND u.deleted = 0
              AND u.password_login_enabled = 1
              AND u.password_reset_required = 0
              AND u.password REGEXP '^[$]2[aby][$][0-9]{2}[$].{53}$'
              AND r.role_code = 'PLATFORM_ADMIN'
              AND r.scope_type = 'PLATFORM'
              AND r.enabled = 1
              AND r.deleted = 0
            ORDER BY u.id
            """)
    List<SysUser> selectActivePlatformAdministrators();
}
