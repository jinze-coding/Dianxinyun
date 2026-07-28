package com.example.siteplatform.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.siteplatform.system.entity.SystemRole;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SystemRoleMapper extends BaseMapper<SystemRole> {
    @Select("""
            SELECT *
            FROM sys_role
            WHERE id = #{roleId}
              AND deleted = 0
            FOR UPDATE
            """)
    SystemRole selectByIdForUpdate(@Param("roleId") Long roleId);

    @Select("""
            SELECT *
            FROM sys_role
            WHERE role_code = 'PLATFORM_ADMIN'
              AND scope_type = 'PLATFORM'
              AND deleted = 0
            ORDER BY builtin DESC, id
            LIMIT 1
            FOR UPDATE
            """)
    SystemRole selectPlatformAdministratorForUpdate();

    @Delete("DELETE FROM sys_role_menu WHERE role_id = #{roleId}")
    void deleteMenus(@Param("roleId") Long roleId);

    @Insert("INSERT INTO sys_role_menu(role_id, menu_id) VALUES(#{roleId}, #{menuId})")
    void insertMenu(@Param("roleId") Long roleId, @Param("menuId") Long menuId);

    @Delete("DELETE FROM sys_role_permission WHERE role_id = #{roleId}")
    void deletePermissions(@Param("roleId") Long roleId);

    @Insert("INSERT INTO sys_role_permission(role_id, permission_id) VALUES(#{roleId}, #{permissionId})")
    void insertPermission(@Param("roleId") Long roleId, @Param("permissionId") Long permissionId);

    @Select("SELECT menu_id FROM sys_role_menu WHERE role_id = #{roleId} ORDER BY menu_id")
    List<Long> selectMenuIds(@Param("roleId") Long roleId);

    @Select("SELECT permission_id FROM sys_role_permission WHERE role_id = #{roleId} ORDER BY permission_id")
    List<Long> selectPermissionIds(@Param("roleId") Long roleId);

    @Select("SELECT role_id FROM sys_role_menu WHERE menu_id = #{menuId} ORDER BY role_id")
    List<Long> selectRoleIdsByMenuId(@Param("menuId") Long menuId);

    @Select("SELECT role_id FROM sys_role_permission WHERE permission_id = #{permissionId} ORDER BY role_id")
    List<Long> selectRoleIdsByPermissionId(@Param("permissionId") Long permissionId);
}
