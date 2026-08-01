package com.example.siteplatform.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.siteplatform.system.entity.SystemMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SystemMenuMapper extends BaseMapper<SystemMenu> {
    @Select("""
            SELECT DISTINCT m.menu_code
            FROM sys_menu m
            INNER JOIN sys_role_menu rm ON rm.menu_id = m.id
            INNER JOIN sys_role r ON r.id = rm.role_id
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
              AND m.enabled = 1
              AND m.visible = 1
              AND m.deleted = 0
            ORDER BY m.menu_code
            """)
    List<String> selectEnabledCodesByUserIdAndProject(@Param("userId") Long userId,
                                                        @Param("projectId") Long projectId);

    @Select("""
            SELECT DISTINCT m.*
            FROM sys_menu m
            INNER JOIN sys_role_menu rm ON rm.menu_id = m.id
            INNER JOIN sys_role r ON r.id = rm.role_id
            LEFT JOIN sys_user_role ur
              ON ur.role_id = r.id AND r.scope_type = 'PLATFORM' AND ur.user_id = #{userId}
            LEFT JOIN sys_user_project_role upr
              ON r.scope_type = 'PROJECT'
             AND upr.user_id = #{userId}
             AND upr.role_id = r.id
            LEFT JOIN sys_user_project up
              ON up.user_id = upr.user_id
             AND up.project_id = upr.project_id
             AND up.status = 'ACTIVE'
            WHERE (ur.user_id IS NOT NULL OR up.user_id IS NOT NULL)
              AND m.enabled = 1 AND m.visible = 1 AND m.deleted = 0
              AND r.enabled = 1 AND r.deleted = 0
            ORDER BY m.sort_order, m.id
            """)
    List<SystemMenu> selectEnabledByUserId(@Param("userId") Long userId);
}
