package com.example.siteplatform.quality.mapper;

import com.example.siteplatform.auth.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface QualityAssigneeMapper {

    /**
     * 先收敛到当前项目的有效成员及平台管理员，再由服务层复用正式权限模型，
     * 继续校验质量模块、查看权限和整改权限。
     */
    @Select("""
            SELECT DISTINCT u.id,
                            u.username,
                            u.real_name,
                            u.status,
                            u.deleted
            FROM sys_user u
            WHERE u.status = 1
              AND u.deleted = 0
              AND (
                    EXISTS (
                        SELECT 1
                        FROM sys_user_project sup
                        WHERE sup.user_id = u.id
                          AND sup.project_id = #{projectId}
                          AND sup.status = 'ACTIVE'
                    )
                    OR EXISTS (
                        SELECT 1
                        FROM sys_user_role ur
                        INNER JOIN sys_role r ON r.id = ur.role_id
                        WHERE ur.user_id = u.id
                          AND r.role_code = 'PLATFORM_ADMIN'
                          AND r.scope_type = 'PLATFORM'
                          AND r.enabled = 1
                          AND r.deleted = 0
                    )
                  )
            ORDER BY COALESCE(NULLIF(TRIM(u.real_name), ''), u.username), u.username, u.id
            """)
    List<SysUser> selectPotentialAssignees(@Param("projectId") Long projectId);
}
