package com.example.siteplatform.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.log.entity.OperationLog;
import com.example.siteplatform.log.mapper.OperationLogMapper;
import com.example.siteplatform.project.dto.CreateProjectUserRequest;
import com.example.siteplatform.project.dto.ProjectMemberRequest;
import com.example.siteplatform.project.dto.ProjectMemberStatusRequest;
import com.example.siteplatform.project.dto.ProjectMemberVO;
import com.example.siteplatform.project.dto.ProjectUserOptionVO;
import com.example.siteplatform.project.entity.SysUserProject;
import com.example.siteplatform.project.entity.SysUserProjectRole;
import com.example.siteplatform.project.mapper.SysUserProjectMapper;
import com.example.siteplatform.project.mapper.SysUserProjectRoleMapper;
import com.example.siteplatform.quality.service.QualityAssigneeService;
import com.example.siteplatform.system.entity.SystemRole;
import com.example.siteplatform.system.entity.SystemPermission;
import com.example.siteplatform.system.mapper.SystemRoleBusinessModuleMapper;
import com.example.siteplatform.system.mapper.SystemRoleMapper;
import com.example.siteplatform.system.mapper.SystemPermissionMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** 项目成员、项目访问状态和项目多角色授权。 */
@Service
public class ProjectMemberService {

    @Autowired private SysUserMapper userMapper;
    @Autowired private SysUserProjectMapper userProjectMapper;
    @Autowired private SysUserProjectRoleMapper userProjectRoleMapper;
    @Autowired private ProjectPermissionService projectPermissionService;
    @Autowired private OperationLogMapper operationLogMapper;
    @Autowired private AuthService authService;
    @Autowired private SystemRoleMapper systemRoleMapper;
    @Autowired private SystemRoleBusinessModuleMapper roleBusinessModuleMapper;
    @Autowired private SystemPermissionMapper systemPermissionMapper;
    @Autowired private QualityAssigneeService qualityAssigneeService;

    public List<ProjectMemberVO> listMembers(Long projectId, SysUser currentUser) {
        requireProjectId(projectId);
        requireManageMembers(currentUser, projectId, "查看");
        List<ProjectMemberVO> members = userProjectMapper.selectMembersByProjectId(projectId);
        members.forEach(this::fillMemberRoles);
        return members;
    }

    /** 项目经理可从全部启用账号中选择，但不泄露手机号、邮箱或其他项目关系。 */
    public List<ProjectUserOptionVO> listUserOptions(Long projectId, String keyword, SysUser currentUser) {
        requireProjectId(projectId);
        requireManageMembers(currentUser, projectId, "选择");
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getDeleted, 0)
                .eq(SysUser::getStatus, 1)
                .orderByAsc(SysUser::getRealName)
                .orderByAsc(SysUser::getUsername)
                .last("LIMIT 80");
        if (StringUtils.hasText(keyword)) {
            String value = keyword.trim();
            wrapper.and(w -> w.like(SysUser::getUsername, value).or().like(SysUser::getRealName, value));
        }
        return userMapper.selectList(wrapper).stream().map(this::toUserOption).toList();
    }

    public List<SystemRole> listAssignableRoles(Long projectId, SysUser currentUser) {
        requireProjectId(projectId);
        requireManageMembers(currentUser, projectId, "分配角色");
        LambdaQueryWrapper<SystemRole> wrapper = new LambdaQueryWrapper<SystemRole>()
                .eq(SystemRole::getScopeType, "PROJECT")
                .eq(SystemRole::getEnabled, 1)
                .eq(SystemRole::getDeleted, 0)
                .orderByDesc(SystemRole::getProjectManagerRole)
                .orderByAsc(SystemRole::getRoleName);
        if (!projectPermissionService.isPlatformAdmin(currentUser.getId())) {
            wrapper.eq(SystemRole::getProjectManagerRole, 0);
        }
        List<SystemRole> roles = systemRoleMapper.selectList(wrapper);
        roles.forEach(this::fillRoleSummary);
        return roles;
    }

    /**
     * 项目经理只能读取可分配角色的摘要，不获得系统管理权限目录；前端据此按角色名、模块和操作权限选择，
     * 不再让用户手工输入角色 ID。
     */
    private void fillRoleSummary(SystemRole role) {
        List<Long> permissionIds = systemRoleMapper.selectPermissionIds(role.getId());
        role.setBusinessModuleCodes(roleBusinessModuleMapper.selectModuleCodesByRoleId(role.getId()));
        if (permissionIds == null || permissionIds.isEmpty()) {
            role.setPermissionNames(List.of());
            return;
        }
        role.setPermissionNames(systemPermissionMapper.selectBatchIds(permissionIds).stream()
                .filter(permission -> Integer.valueOf(1).equals(permission.getEnabled())
                        && !Integer.valueOf(1).equals(permission.getDeleted()))
                .map(SystemPermission::getPermissionName)
                .filter(StringUtils::hasText)
                .sorted()
                .toList());
    }

    public ProjectMemberVO createUserAndJoinProject(CreateProjectUserRequest request, SysUser currentUser) {
        throw BusinessException.of(410, "新账号必须通过 Web 或小程序提交统一注册申请并由系统管理员审核");
    }

    @Transactional
    public ProjectMemberVO saveMember(ProjectMemberRequest request, SysUser currentUser) {
        validateRequest(request);
        requireManageMembers(currentUser, request.getProjectId(), "授权");
        SysUser targetUser = userMapper.selectById(request.getUserId());
        if (targetUser == null || Integer.valueOf(1).equals(targetUser.getDeleted())) {
            throw BusinessException.notFound("用户不存在");
        }
        if (!Integer.valueOf(1).equals(targetUser.getStatus())) {
            throw new BusinessException("只能将已启用账号加入项目");
        }
        List<SystemRole> roles = requireEnabledProjectRoles(request.getRoleIds());
        SysUserProject existing = findUserProject(request.getProjectId(), request.getUserId());
        assertProjectManagerCanChangeRoles(currentUser, request.getProjectId(), request.getUserId(), existing, roles);

        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            existing = new SysUserProject();
            existing.setProjectId(request.getProjectId());
            existing.setUserId(request.getUserId());
            existing.setProjectRoleCode(primaryRoleCode(roles)); // 历史兼容展示，不参与鉴权。
            existing.setInspectionPermissionTemplateId(null);
            existing.setStatus("ACTIVE");
            existing.setCreateTime(now);
            existing.setUpdateTime(now);
            requireSingleWrite(userProjectMapper.insert(existing));
        } else {
            existing.setProjectRoleCode(primaryRoleCode(roles));
            existing.setInspectionPermissionTemplateId(null);
            existing.setUpdateTime(now);
            requireSingleWrite(userProjectMapper.updateById(existing));
        }
        replaceRoles(request.getProjectId(), request.getUserId(), roles, currentUser.getId());
        requireOpenQualityAssignmentsRemainServiceable(request.getProjectId(), request.getUserId());
        invalidateUserAccess(request.getUserId());
        recordMemberOperation(request.getProjectId(), request.getUserId(), currentUser,
                "SAVE_PROJECT_MEMBER_ROLES", "更新项目成员角色：" + roleNames(roles));
        return findMember(request.getProjectId(), request.getUserId());
    }

    @Transactional
    public void removeMember(Long projectId, Long userId, SysUser currentUser) {
        requireProjectId(projectId);
        if (userId == null) throw new BusinessException("用户ID不能为空");
        requireManageMembers(currentUser, projectId, "移除");
        if (Objects.equals(currentUser.getId(), userId)) throw new BusinessException("不能移除自己的项目授权");
        SysUserProject existing = findUserProject(projectId, userId);
        if (existing == null) return;
        assertProjectManagerCanChangeTarget(currentUser, projectId, userId);
        requireNoOpenQualityAssignments(projectId, userId, "移除");
        ProjectMemberVO member = findMember(projectId, userId);
        if (member != null && ((member.getResponsibleBoxCount() != null && member.getResponsibleBoxCount() > 0)
                || (member.getPendingRectificationCount() != null && member.getPendingRectificationCount() > 0))) {
            throw new BusinessException("该成员仍负责电箱或待整改任务，请先调整后再移除");
        }
        userProjectRoleMapper.deleteByUserAndProject(userId, projectId);
        requireSingleWrite(userProjectMapper.deleteById(existing.getId()));
        invalidateUserAccess(userId);
        recordMemberOperation(projectId, userId, currentUser, "REMOVE_PROJECT_MEMBER", "移出项目成员");
    }

    @Transactional
    public ProjectMemberVO updateMemberStatus(Long projectId, Long userId, ProjectMemberStatusRequest request, SysUser currentUser) {
        requireProjectId(projectId);
        if (userId == null) throw new BusinessException("用户ID不能为空");
        requireManageMembers(currentUser, projectId, "调整访问状态");
        if (request == null || !StringUtils.hasText(request.getStatus())) throw new BusinessException("项目访问状态不能为空");
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
        assertProjectManagerCanChangeTarget(currentUser, projectId, userId);
        if ("DISABLED".equals(status)) {
            requireNoOpenQualityAssignments(projectId, userId, "暂停访问");
        }
        if ("ACTIVE".equals(status) && userProjectRoleMapper.countEnabledRoles(userId, projectId) == 0) {
            throw new BusinessException("恢复项目访问前必须至少保留一个启用中的项目角色");
        }
        existing.setStatus(status);
        existing.setStatusReason(StringUtils.hasText(request.getReason()) ? request.getReason().trim() : null);
        existing.setStatusChangedBy(currentUser.getId());
        existing.setStatusChangedTime(LocalDateTime.now());
        existing.setUpdateTime(LocalDateTime.now());
        requireSingleWrite(userProjectMapper.updateById(existing));
        invalidateUserAccess(userId);
        recordMemberOperation(projectId, userId, currentUser,
                "ACTIVE".equals(status) ? "RESTORE_PROJECT_ACCESS" : "DISABLE_PROJECT_ACCESS",
                "项目访问状态变更为" + status + reasonSuffix(request.getReason()));
        return findMember(projectId, userId);
    }

    /** 供电箱职责等既有业务自动补齐成员关系；只追加指定角色，不覆盖已有角色。 */
    @Transactional
    public void ensureProjectMember(Long projectId, Long userId, String roleCode) {
        if (projectId == null || userId == null) return;
        SystemRole role = requireEnabledProjectRoleByCode(roleCode);
        SysUserProject existing = findUserProject(projectId, userId);
        if (existing == null) {
            existing = new SysUserProject();
            existing.setProjectId(projectId);
            existing.setUserId(userId);
            existing.setProjectRoleCode(role.getRoleCode());
            existing.setStatus("ACTIVE");
            existing.setCreateTime(LocalDateTime.now());
            existing.setUpdateTime(LocalDateTime.now());
            requireSingleWrite(userProjectMapper.insert(existing));
        }
        boolean assigned = userProjectRoleMapper.selectAssignedRoles(userId, projectId).stream()
                .anyMatch(item -> Objects.equals(item.getId(), role.getId()));
        if (!assigned) {
            SysUserProjectRole relation = new SysUserProjectRole();
            relation.setProjectId(projectId);
            relation.setUserId(userId);
            relation.setRoleId(role.getId());
            relation.setCreateTime(LocalDateTime.now());
            requireSingleWrite(userProjectRoleMapper.insert(relation));
            invalidateUserAccess(userId);
        }
    }

    private void replaceRoles(Long projectId, Long userId, List<SystemRole> roles, Long operatorId) {
        userProjectRoleMapper.deleteByUserAndProject(userId, projectId);
        for (SystemRole role : roles) {
            SysUserProjectRole relation = new SysUserProjectRole();
            relation.setProjectId(projectId);
            relation.setUserId(userId);
            relation.setRoleId(role.getId());
            relation.setCreatedBy(operatorId);
            relation.setCreateTime(LocalDateTime.now());
            requireSingleWrite(userProjectRoleMapper.insert(relation));
        }
    }

    private ProjectUserOptionVO toUserOption(SysUser user) {
        ProjectUserOptionVO option = new ProjectUserOptionVO();
        option.setId(user.getId());
        option.setUsername(user.getUsername());
        option.setRealName(user.getRealName());
        return option;
    }

    private ProjectMemberVO findMember(Long projectId, Long userId) {
        ProjectMemberVO member = userProjectMapper.selectMembersByProjectId(projectId).stream()
                .filter(item -> Objects.equals(item.getUserId(), userId)).findFirst().orElse(null);
        if (member != null) fillMemberRoles(member);
        return member;
    }

    private SysUserProject findUserProject(Long projectId, Long userId) {
        return userProjectMapper.selectOne(new LambdaQueryWrapper<SysUserProject>()
                .eq(SysUserProject::getProjectId, projectId)
                .eq(SysUserProject::getUserId, userId).last("LIMIT 1"));
    }

    private void fillMemberRoles(ProjectMemberVO member) {
        List<SystemRole> roles = userProjectRoleMapper.selectAssignedRoles(member.getUserId(), member.getProjectId());
        member.setProjectRoles(roles);
        if (StringUtils.hasText(member.getProjectRoleCode()) || !roles.isEmpty()) {
            member.setProjectRoleCode(primaryRoleCode(roles));
        }
        member.setPermissionTemplateId(null);
        member.setPermissionTemplateName(null);
        member.setPermissionTemplateCode(null);
        member.setPermissionCodeText(null);
        member.setGlobalRoles(userMapper.selectRoleCodesByUserId(member.getUserId()));
        member.setPermissionCodes(projectPermissionService.getInspectionPermissionCodes(member.getUserId(), member.getProjectId()));
        if (member.getResponsibleBoxCount() == null) member.setResponsibleBoxCount(0);
        if (member.getPendingRectificationCount() == null) member.setPendingRectificationCount(0);
    }

    private List<SystemRole> requireEnabledProjectRoles(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) throw new BusinessException("请至少分配一个项目角色");
        List<SystemRole> roles = new ArrayList<>();
        for (Long roleId : roleIds.stream().filter(Objects::nonNull).distinct().toList()) {
            SystemRole role = systemRoleMapper.selectById(roleId);
            if (role == null || !"PROJECT".equalsIgnoreCase(role.getScopeType())
                    || Integer.valueOf(0).equals(role.getEnabled()) || Integer.valueOf(1).equals(role.getDeleted())) {
                throw new BusinessException("项目角色不存在或已停用：" + roleId);
            }
            roles.add(role);
        }
        if (roles.isEmpty()) throw new BusinessException("请至少分配一个项目角色");
        return roles;
    }

    private SystemRole requireEnabledProjectRoleByCode(String roleCode) {
        String code = projectPermissionService.normalizeProjectRoleCode(roleCode);
        SystemRole role = systemRoleMapper.selectOne(new LambdaQueryWrapper<SystemRole>()
                .eq(SystemRole::getRoleCode, code).eq(SystemRole::getScopeType, "PROJECT")
                .eq(SystemRole::getEnabled, 1).eq(SystemRole::getDeleted, 0).last("LIMIT 1"));
        if (role == null) throw new BusinessException("项目角色不存在或已停用：" + code);
        return role;
    }

    private void requireManageMembers(SysUser currentUser, Long projectId, String action) {
        if (currentUser == null || !projectPermissionService.canManageProjectMembers(currentUser.getId(), projectId)) {
            throw BusinessException.forbidden("无项目成员" + action + "权限");
        }
    }

    private void assertProjectManagerCanChangeRoles(SysUser operator, Long projectId, Long targetUserId,
                                                     SysUserProject existing, List<SystemRole> requestedRoles) {
        if (projectPermissionService.isPlatformAdmin(operator.getId())) return;
        assertProjectManagerCanChangeTarget(operator, projectId, targetUserId);
        if (requestedRoles.stream().anyMatch(this::isProjectManagerRole)) {
            throw BusinessException.forbidden("项目经理角色只能由系统管理员授予或撤销");
        }
        if (existing != null && userProjectRoleMapper.selectAssignedRoles(targetUserId, projectId).stream()
                .anyMatch(this::isProjectManagerRole)) {
            throw BusinessException.forbidden("项目经理角色只能由系统管理员授予或撤销");
        }
    }

    private void assertProjectManagerCanChangeTarget(SysUser operator, Long projectId, Long targetUserId) {
        if (projectPermissionService.isPlatformAdmin(operator.getId())) return;
        if (Objects.equals(operator.getId(), targetUserId)
                || userProjectRoleMapper.countEnabledProjectManagerRoles(targetUserId, projectId) > 0) {
            throw BusinessException.forbidden("项目经理不能调整项目经理角色或其项目访问");
        }
    }

    private boolean isProjectManagerRole(SystemRole role) {
        return role != null && Integer.valueOf(1).equals(role.getProjectManagerRole());
    }

    private String primaryRoleCode(List<SystemRole> roles) {
        if (roles == null || roles.isEmpty()) return null;
        return roles.stream().sorted(Comparator.comparing(this::isProjectManagerRole).reversed()
                        .thenComparing(SystemRole::getRoleCode, Comparator.nullsLast(String::compareTo)))
                .map(SystemRole::getRoleCode).findFirst().orElse(null);
    }

    private String roleNames(List<SystemRole> roles) {
        return roles.stream().map(SystemRole::getRoleName).filter(StringUtils::hasText).reduce((a, b) -> a + "、" + b).orElse("未命名角色");
    }

    private void invalidateUserAccess(Long userId) {
        projectPermissionService.clearUserProjectsCache(userId);
        authService.logout(userId);
        authService.repeatLogoutAfterCommit(userId);
    }

    private void requireNoOpenQualityAssignments(Long projectId, Long userId, String action) {
        long openCount = qualityAssigneeService.countOpenAssignments(projectId, userId);
        if (openCount > 0) {
            throw new BusinessException("该成员仍有" + openCount
                    + "项未闭环质量整改，请先改派或关闭后再" + action);
        }
    }

    private void requireOpenQualityAssignmentsRemainServiceable(Long projectId, Long userId) {
        long openCount = qualityAssigneeService.countOpenAssignments(projectId, userId);
        if (openCount > 0 && !qualityAssigneeService.isEligibleAssignee(userId, projectId)) {
            throw new BusinessException("该成员仍有" + openCount
                    + "项未闭环质量整改，不能取消其质量模块、查看或整改权限，请先改派或关闭");
        }
    }

    private void requireProjectId(Long projectId) {
        if (projectId == null) throw new BusinessException("项目ID不能为空");
    }

    private void requireSingleWrite(int affectedRows) {
        if (affectedRows != 1) {
            throw BusinessException.of(409, "项目成员授权状态已变化，请刷新后重试");
        }
    }

    private void validateRequest(ProjectMemberRequest request) {
        if (request == null) throw new BusinessException("成员信息不能为空");
        requireProjectId(request.getProjectId());
        if (request.getUserId() == null) throw new BusinessException("用户ID不能为空");
    }

    private String reasonSuffix(String reason) {
        return StringUtils.hasText(reason) ? "，原因：" + reason.trim() : "";
    }

    private void recordMemberOperation(Long projectId, Long userId, SysUser operator, String operationType, String detail) {
        OperationLog log = new OperationLog();
        log.setUserId(operator.getId());
        log.setUsername(operator.getUsername());
        log.setOperationType(operationType);
        log.setOperationDesc("项目" + projectId + "用户" + userId + "：" + detail);
        log.setBusinessType("PROJECT_MEMBER");
        log.setBusinessId(userId);
        log.setCreateTime(LocalDateTime.now());
        operationLogMapper.insert(log);
    }
}
