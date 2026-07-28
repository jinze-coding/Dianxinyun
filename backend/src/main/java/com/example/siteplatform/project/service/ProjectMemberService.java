package com.example.siteplatform.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.project.dto.CreateProjectUserRequest;
import com.example.siteplatform.project.dto.ProjectMemberRequest;
import com.example.siteplatform.project.dto.ProjectMemberStatusRequest;
import com.example.siteplatform.project.dto.ProjectMemberVO;
import com.example.siteplatform.project.dto.ProjectUserOptionVO;
import com.example.siteplatform.project.entity.InspectionPermissionTemplate;
import com.example.siteplatform.project.entity.SysUserProject;
import com.example.siteplatform.project.mapper.SysUserProjectMapper;
import com.example.siteplatform.log.entity.OperationLog;
import com.example.siteplatform.log.mapper.OperationLogMapper;
import com.example.siteplatform.system.entity.SystemRole;
import com.example.siteplatform.system.mapper.SystemRoleMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class ProjectMemberService {

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private SysUserProjectMapper userProjectMapper;

    @Autowired
    private ProjectPermissionService projectPermissionService;

    @Autowired
    private InspectionPermissionTemplateService permissionTemplateService;

    @Autowired
    private OperationLogMapper operationLogMapper;

    @Autowired
    private AuthService authService;

    @Autowired
    private SystemRoleMapper systemRoleMapper;

    public List<ProjectMemberVO> listMembers(Long projectId, SysUser currentUser) {
        requireProjectId(projectId);
        if (!projectPermissionService.canManageProjectMembers(currentUser.getId(), projectId)) {
            throw BusinessException.forbidden("无项目成员查看权限");
        }
        List<ProjectMemberVO> members = userProjectMapper.selectMembersByProjectId(projectId);
        members.forEach(this::fillGlobalRoles);
        return members;
    }

    public List<ProjectUserOptionVO> listUserOptions(Long projectId, String keyword, SysUser currentUser) {
        requireProjectId(projectId);
        if (!projectPermissionService.canManageProjectMembers(currentUser.getId(), projectId)) {
            throw BusinessException.forbidden("无项目成员选择权限");
        }
        boolean platformAdmin = projectPermissionService.isPlatformAdmin(currentUser.getId());
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getDeleted, 0)
                .orderByAsc(SysUser::getRealName)
                .orderByAsc(SysUser::getUsername)
                .last("LIMIT 80");
        if (!platformAdmin) {
            List<Long> memberUserIds = userProjectMapper.selectList(new LambdaQueryWrapper<SysUserProject>()
                            .eq(SysUserProject::getProjectId, projectId))
                    .stream().map(SysUserProject::getUserId).filter(Objects::nonNull).distinct().toList();
            if (memberUserIds.isEmpty()) return List.of();
            wrapper.in(SysUser::getId, memberUserIds);
        }
        if (StringUtils.hasText(keyword)) {
            String value = keyword.trim();
            wrapper.and(w -> w.like(SysUser::getUsername, value)
                    .or()
                    .like(SysUser::getRealName, value)
                    .or()
                    .like(SysUser::getPhone, value));
        }
        return userMapper.selectList(wrapper).stream()
                .map(user -> toUserOption(user, projectId))
                .toList();
    }

    @Transactional
    public ProjectMemberVO saveMember(ProjectMemberRequest request, SysUser currentUser) {
        validateRequest(request);
        if (!projectPermissionService.canManageProjectMembers(currentUser.getId(), request.getProjectId())) {
            throw BusinessException.forbidden("无项目成员授权管理权限");
        }
        SysUser user = userMapper.selectById(request.getUserId());
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }
        String roleCode = requireEnabledProjectRole(request.getProjectRoleCode());
        Long permissionTemplateId = resolveTemplateId(request.getPermissionTemplateId(), roleCode);
        SysUserProject existing = findUserProject(request.getProjectId(), request.getUserId());
        if (existing == null) {
            SysUserProject userProject = new SysUserProject();
            userProject.setProjectId(request.getProjectId());
            userProject.setUserId(request.getUserId());
            userProject.setProjectRoleCode(roleCode);
            userProject.setInspectionPermissionTemplateId(permissionTemplateId);
            userProject.setStatus("ACTIVE");
            userProject.setCreateTime(LocalDateTime.now());
            userProject.setUpdateTime(LocalDateTime.now());
            userProjectMapper.insert(userProject);
        } else {
            existing.setProjectRoleCode(roleCode);
            existing.setInspectionPermissionTemplateId(permissionTemplateId);
            if (!StringUtils.hasText(existing.getStatus())) existing.setStatus("ACTIVE");
            existing.setUpdateTime(LocalDateTime.now());
            userProjectMapper.updateById(existing);
        }
        projectPermissionService.clearUserProjectsCache(request.getUserId());
        authService.logout(request.getUserId());
        return findMember(request.getProjectId(), request.getUserId());
    }

    public ProjectMemberVO createUserAndJoinProject(CreateProjectUserRequest request, SysUser currentUser) {
        throw BusinessException.of(410, "新账号必须通过 Web 或小程序提交统一注册申请并由平台审核");
    }

    @Transactional
    public void removeMember(Long projectId, Long userId, SysUser currentUser) {
        requireProjectId(projectId);
        if (userId == null) {
            throw new BusinessException("用户ID不能为空");
        }
        if (!projectPermissionService.canManageProjectMembers(currentUser.getId(), projectId)) {
            throw BusinessException.forbidden("无项目成员移除权限");
        }
        if (Objects.equals(currentUser.getId(), userId)) {
            throw new BusinessException("不能移除自己的项目授权");
        }
        ProjectMemberVO member = findMember(projectId, userId);
        if (member != null
                && ((member.getResponsibleBoxCount() != null && member.getResponsibleBoxCount() > 0)
                || (member.getPendingRectificationCount() != null && member.getPendingRectificationCount() > 0))) {
            throw new BusinessException("该成员仍负责电箱或待整改任务，请先调整后再移除");
        }
        SysUserProject existing = findUserProject(projectId, userId);
        if (existing != null) {
            userProjectMapper.deleteById(existing.getId());
            projectPermissionService.clearUserProjectsCache(userId);
            authService.logout(userId);
        }
    }

    @Transactional
    public ProjectMemberVO updateMemberStatus(Long projectId, Long userId, ProjectMemberStatusRequest request, SysUser currentUser) {
        requireProjectId(projectId);
        if (userId == null) throw new BusinessException("用户ID不能为空");
        if (!projectPermissionService.canManageProjectMembers(currentUser.getId(), projectId)) {
            throw BusinessException.forbidden("无项目成员授权管理权限");
        }
        if (request == null || !StringUtils.hasText(request.getStatus())) {
            throw new BusinessException("项目访问状态不能为空");
        }
        String status = request.getStatus().trim().toUpperCase();
        if (!"ACTIVE".equals(status) && !"DISABLED".equals(status)) {
            throw new BusinessException("项目访问状态只支持 ACTIVE 或 DISABLED");
        }
        if ("DISABLED".equals(status) && !StringUtils.hasText(request.getReason())) {
            throw new BusinessException("暂停项目访问原因不能为空");
        }
        if (Objects.equals(currentUser.getId(), userId) && "DISABLED".equals(status)) {
            throw new BusinessException("不能暂停自己的项目访问权限");
        }
        SysUserProject existing = findUserProject(projectId, userId);
        if (existing == null) throw BusinessException.notFound("项目成员授权不存在");
        existing.setStatus(status);
        existing.setStatusReason(StringUtils.hasText(request.getReason()) ? request.getReason().trim() : null);
        existing.setStatusChangedBy(currentUser.getId());
        existing.setStatusChangedTime(LocalDateTime.now());
        existing.setUpdateTime(LocalDateTime.now());
        userProjectMapper.updateById(existing);
        projectPermissionService.clearUserProjectsCache(userId);
        authService.logout(userId);
        recordMemberOperation(existing, currentUser, "ACTIVE".equals(status) ? "RESTORE_PROJECT_ACCESS" : "DISABLE_PROJECT_ACCESS");
        return findMember(projectId, userId);
    }

    @Transactional
    public void ensureProjectMember(Long projectId, Long userId, String roleCode) {
        if (projectId == null || userId == null) {
            return;
        }
        String normalized = requireEnabledProjectRole(roleCode);
        Long permissionTemplateId = permissionTemplateService.defaultTemplateIdForRole(normalized);
        SysUserProject existing = findUserProject(projectId, userId);
        if (existing == null) {
            SysUserProject userProject = new SysUserProject();
            userProject.setProjectId(projectId);
            userProject.setUserId(userId);
            userProject.setProjectRoleCode(normalized);
            userProject.setInspectionPermissionTemplateId(permissionTemplateId);
            userProject.setStatus("ACTIVE");
            userProject.setCreateTime(LocalDateTime.now());
            userProject.setUpdateTime(LocalDateTime.now());
            userProjectMapper.insert(userProject);
            projectPermissionService.clearUserProjectsCache(userId);
            authService.logout(userId);
            return;
        }
        if (roleRank(normalized) > roleRank(existing.getProjectRoleCode())) {
            existing.setProjectRoleCode(normalized);
            existing.setInspectionPermissionTemplateId(permissionTemplateId);
            existing.setUpdateTime(LocalDateTime.now());
            userProjectMapper.updateById(existing);
            projectPermissionService.clearUserProjectsCache(userId);
            authService.logout(userId);
        } else if (existing.getInspectionPermissionTemplateId() == null && permissionTemplateId != null) {
            existing.setInspectionPermissionTemplateId(permissionTemplateId);
            existing.setUpdateTime(LocalDateTime.now());
            userProjectMapper.updateById(existing);
            projectPermissionService.clearUserProjectsCache(userId);
            authService.logout(userId);
        }
    }

    private ProjectUserOptionVO toUserOption(SysUser user, Long projectId) {
        ProjectUserOptionVO option = new ProjectUserOptionVO();
        BeanUtils.copyProperties(user, option);
        SysUserProject userProject = findUserProject(projectId, user.getId());
        option.setInProject(userProject != null);
        option.setAccessStatus(userProject == null ? null : userProject.getStatus());
        option.setProjectRoleCode(userProject == null ? null : userProject.getProjectRoleCode());
        if (userProject != null) {
            option.setPermissionTemplateId(userProject.getInspectionPermissionTemplateId());
            fillTemplateFields(option, userProject);
            option.setPermissionCodes(projectPermissionService.getInspectionPermissionCodes(user.getId(), projectId));
        }
        option.setGlobalRoles(userMapper.selectRoleCodesByUserId(user.getId()));
        return option;
    }

    private ProjectMemberVO findMember(Long projectId, Long userId) {
        ProjectMemberVO member = userProjectMapper.selectMembersByProjectId(projectId).stream()
                .filter(item -> Objects.equals(item.getUserId(), userId))
                .findFirst()
                .orElse(null);
        if (member != null) {
            fillGlobalRoles(member);
        }
        return member;
    }

    private SysUserProject findUserProject(Long projectId, Long userId) {
        return userProjectMapper.selectOne(new LambdaQueryWrapper<SysUserProject>()
                .eq(SysUserProject::getProjectId, projectId)
                .eq(SysUserProject::getUserId, userId)
                .last("LIMIT 1"));
    }

    private void fillGlobalRoles(ProjectMemberVO member) {
        member.setGlobalRoles(userMapper.selectRoleCodesByUserId(member.getUserId()));
        member.setPermissionCodes(projectPermissionService.getInspectionPermissionCodes(member.getUserId(), member.getProjectId()));
        if (member.getResponsibleBoxCount() == null) {
            member.setResponsibleBoxCount(0);
        }
        if (member.getPendingRectificationCount() == null) {
            member.setPendingRectificationCount(0);
        }
    }

    private void validateRequest(ProjectMemberRequest request) {
        if (request == null) {
            throw new BusinessException("成员信息不能为空");
        }
        requireProjectId(request.getProjectId());
        if (request.getUserId() == null) {
            throw new BusinessException("用户ID不能为空");
        }
    }

    private void validateCreateUserRequest(CreateProjectUserRequest request) {
        if (request == null) {
            throw new BusinessException("用户信息不能为空");
        }
        requireProjectId(request.getProjectId());
        if (!StringUtils.hasText(request.getUsername())) {
            throw new BusinessException("用户名不能为空");
        }
        if (!request.getUsername().trim().matches("^[A-Za-z0-9_\\-]{3,40}$")) {
            throw new BusinessException("用户名需为3-40位字母、数字、下划线或横线");
        }
        if (!StringUtils.hasText(request.getRealName())) {
            throw new BusinessException("姓名不能为空");
        }
    }

    private String normalizeGlobalRoleCode(String roleCode) {
        if (!StringUtils.hasText(roleCode)) {
            return ProjectPermissionService.ROLE_USER;
        }
        String normalized = roleCode.trim().toUpperCase();
        if (ProjectPermissionService.ROLE_PLATFORM_ADMIN.equals(normalized)
                || ProjectPermissionService.ROLE_PROJECT_ADMIN.equals(normalized)
                || ProjectPermissionService.ROLE_SAFETY_ADMIN.equals(normalized)
                || ProjectPermissionService.ROLE_USER.equals(normalized)) {
            return normalized;
        }
        throw BusinessException.of(400, "全局角色只支持 PLATFORM_ADMIN、PROJECT_ADMIN、SAFETY_ADMIN、USER");
    }

    private Long resolveTemplateId(Long permissionTemplateId, String roleCode) {
        if (permissionTemplateId != null) {
            permissionTemplateService.requireEnabledTemplate(permissionTemplateId);
            return permissionTemplateId;
        }
        return permissionTemplateService.defaultTemplateIdForRole(roleCode);
    }

    private void fillTemplateFields(ProjectUserOptionVO option, SysUserProject userProject) {
        if (userProject.getInspectionPermissionTemplateId() == null) {
            return;
        }
        try {
            InspectionPermissionTemplate template = permissionTemplateService.requireEnabledTemplate(userProject.getInspectionPermissionTemplateId());
            option.setPermissionTemplateName(template.getTemplateName());
            option.setPermissionTemplateCode(template.getTemplateCode());
            option.setPermissionCodeText(template.getPermissionCodes());
        } catch (BusinessException ignored) {
            option.setPermissionTemplateId(null);
        }
    }

    private void requireProjectId(Long projectId) {
        if (projectId == null) {
            throw new BusinessException("项目ID不能为空");
        }
    }

    private int roleRank(String roleCode) {
        String normalized = projectPermissionService.normalizeProjectRoleCode(roleCode);
        if (ProjectPermissionService.ROLE_PROJECT_ADMIN.equals(normalized)) {
            return 3;
        }
        if (ProjectPermissionService.ROLE_SAFETY_ADMIN.equals(normalized)) {
            return 2;
        }
        return 1;
    }

    private String requireEnabledProjectRole(String roleCode) {
        String normalized = projectPermissionService.normalizeProjectRoleCode(roleCode);
        SystemRole role = systemRoleMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SystemRole>()
                .eq(SystemRole::getRoleCode, normalized)
                .eq(SystemRole::getScopeType, "PROJECT")
                .eq(SystemRole::getEnabled, 1)
                .last("LIMIT 1"));
        if (role == null) {
            throw new BusinessException("项目角色不存在或已停用：" + normalized);
        }
        return normalized;
    }

    private void recordMemberOperation(SysUserProject member, SysUser operator, String operationType) {
        OperationLog log = new OperationLog();
        log.setUserId(operator.getId());
        log.setUsername(operator.getUsername());
        log.setOperationType(operationType);
        log.setOperationDesc("项目" + member.getProjectId() + "用户" + member.getUserId() + "授权状态变更为" + member.getStatus()
                + (StringUtils.hasText(member.getStatusReason()) ? "，原因：" + member.getStatusReason() : ""));
        log.setBusinessType("WECHAT_PROJECT_ACCESS");
        log.setBusinessId(member.getUserId());
        log.setCreateTime(LocalDateTime.now());
        operationLogMapper.insert(log);
    }
}
