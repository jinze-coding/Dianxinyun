package com.example.siteplatform.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.siteplatform.system.entity.SystemRoleBusinessModule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SystemRoleBusinessModuleMapper extends BaseMapper<SystemRoleBusinessModule> {
    @Select("SELECT module_code FROM sys_role_business_module WHERE role_id = #{roleId} ORDER BY module_code")
    List<String> selectModuleCodesByRoleId(@Param("roleId") Long roleId);

    /** 平台角色或当前有效项目角色在指定项目中授予的模块。 */
    @Select("""
            SELECT DISTINCT rbm.module_code
            FROM sys_role_business_module rbm
            INNER JOIN sys_role r ON r.id = rbm.role_id
            LEFT JOIN sys_user_role ur
              ON ur.role_id = r.id
             AND r.scope_type = 'PLATFORM'
             AND ur.user_id = #{userId}
            LEFT JOIN sys_user_project_role upr
              ON upr.role_id = r.id
             AND r.scope_type = 'PROJECT'
             AND upr.user_id = #{userId}
             AND upr.project_id = #{projectId}
            LEFT JOIN sys_user_project up
              ON up.user_id = upr.user_id
             AND up.project_id = upr.project_id
             AND up.status = 'ACTIVE'
            WHERE (ur.user_id IS NOT NULL OR up.user_id IS NOT NULL)
              AND r.enabled = 1
              AND r.deleted = 0
            ORDER BY rbm.module_code
            """)
    List<String> selectModuleCodesByUserIdAndProject(@Param("userId") Long userId,
                                                      @Param("projectId") Long projectId);

    /** 仅用于无 projectId 的拦截器和旧版菜单树兼容，返回任一有效范围的模块。 */
    @Select("""
            SELECT DISTINCT rbm.module_code
            FROM sys_role_business_module rbm
            INNER JOIN sys_role r ON r.id = rbm.role_id
            LEFT JOIN sys_user_role ur
              ON ur.role_id = r.id
             AND r.scope_type = 'PLATFORM'
             AND ur.user_id = #{userId}
            LEFT JOIN sys_user_project_role upr
              ON upr.role_id = r.id
             AND r.scope_type = 'PROJECT'
             AND upr.user_id = #{userId}
            LEFT JOIN sys_user_project up
              ON up.user_id = upr.user_id
             AND up.project_id = upr.project_id
             AND up.status = 'ACTIVE'
            WHERE (ur.user_id IS NOT NULL OR up.user_id IS NOT NULL)
              AND r.enabled = 1
              AND r.deleted = 0
            ORDER BY rbm.module_code
            """)
    List<String> selectModuleCodesByUserId(@Param("userId") Long userId);
}
