package com.example.siteplatform.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.siteplatform.auth.dto.UserProjectRoleVO;
import com.example.siteplatform.project.dto.ProjectMemberVO;
import com.example.siteplatform.project.entity.SysUserProject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysUserProjectMapper extends BaseMapper<SysUserProject> {

    @Select("""
            SELECT sup.id AS memberId,
                   sup.project_id AS projectId,
                   sup.user_id AS userId,
                   u.username AS username,
                   u.real_name AS realName,
                   u.phone AS phone,
                   u.email AS email,
                   u.status AS status,
                   sup.project_role_code AS projectRoleCode,
                   sup.inspection_permission_template_id AS permissionTemplateId,
                   sup.status AS accessStatus,
                   sup.status_reason AS statusReason,
                   sup.status_changed_by AS statusChangedBy,
                   sup.status_changed_time AS statusChangedTime,
                   ipt.template_name AS permissionTemplateName,
                   ipt.template_code AS permissionTemplateCode,
                   ipt.permission_codes AS permissionCodeText,
                   sup.create_time AS authorizedAt,
                   sup.update_time AS lastOperationTime,
                   (SELECT COUNT(1)
                    FROM electric_box eb
                    WHERE eb.project_id = sup.project_id
                      AND eb.responsible_electrician_id = sup.user_id
                      AND eb.deleted = 0) AS responsibleBoxCount,
                   (SELECT COUNT(1)
                    FROM inspection_rectification ir
                    WHERE ir.project_id = sup.project_id
                      AND ir.assignee_id = sup.user_id
                      AND ir.status <> 'CLOSED'
                      AND ir.deleted = 0) AS pendingRectificationCount
            FROM sys_user_project sup
            INNER JOIN sys_user u ON u.id = sup.user_id AND u.deleted = 0
            LEFT JOIN inspection_permission_template ipt ON ipt.id = sup.inspection_permission_template_id AND ipt.deleted = 0
            WHERE sup.project_id = #{projectId}
            ORDER BY FIELD(sup.project_role_code, 'PROJECT_ADMIN', 'SAFETY_ADMIN', 'USER'),
                     u.real_name,
                     u.username
            """)
    List<ProjectMemberVO> selectMembersByProjectId(@Param("projectId") Long projectId);

    @Select("""
            SELECT p.id AS projectId,
                   p.project_name AS projectName,
                   p.short_name AS shortName,
                   sup.project_role_code AS projectRoleCode,
                   sup.inspection_permission_template_id AS permissionTemplateId,
                   ipt.template_name AS permissionTemplateName,
                   ipt.template_code AS permissionTemplateCode,
                   ipt.permission_codes AS permissionCodeText
            FROM sys_user_project sup
            INNER JOIN project_info p ON p.id = sup.project_id AND p.deleted = 0
            LEFT JOIN inspection_permission_template ipt ON ipt.id = sup.inspection_permission_template_id AND ipt.deleted = 0
            WHERE sup.user_id = #{userId}
              AND sup.status = 'ACTIVE'
            ORDER BY p.id
            """)
    List<UserProjectRoleVO> selectUserProjectRoles(@Param("userId") Long userId);
}
