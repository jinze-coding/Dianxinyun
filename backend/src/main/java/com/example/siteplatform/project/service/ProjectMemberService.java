package com.example.siteplatform.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.dto.UserProjectRoleVO;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.common.PageResult;
import com.example.siteplatform.log.entity.OperationLog;
import com.example.siteplatform.log.mapper.OperationLogMapper;
import com.example.siteplatform.project.dto.CreateProjectUserRequest;
import com.example.siteplatform.project.dto.ProjectMemberRequest;
import com.example.siteplatform.project.dto.ProjectMemberAssignmentOptionVO;
import com.example.siteplatform.project.dto.ProjectMemberBatchRequest;
import com.example.siteplatform.project.dto.ProjectMemberStatusRequest;
import com.example.siteplatform.project.dto.ProjectMemberVO;
import com.example.siteplatform.project.dto.ProjectUserOptionVO;
import com.example.siteplatform.project.dto.ResponsibilityImpactVO;
import com.example.siteplatform.project.dto.UserProjectRoleBatchRequest;
import com.example.siteplatform.project.constant.InspectionPermissionCodes;
import com.example.siteplatform.project.entity.ProjectInfo;
import com.example.siteplatform.project.entity.SysUserProject;
import com.example.siteplatform.project.entity.SysUserProjectRole;
import com.example.siteplatform.project.mapper.SysUserProjectMapper;
import com.example.siteplatform.project.mapper.SysUserProjectRoleMapper;
import com.example.siteplatform.project.mapper.ProjectInfoMapper;
import com.example.siteplatform.quality.service.QualityAssigneeService;
import com.example.siteplatform.system.entity.SystemRole;
import com.example.siteplatform.system.entity.SystemPermission;
import com.example.siteplatform.system.constant.SystemPermissionCodes;
import com.example.siteplatform.system.mapper.SystemRoleBusinessModuleMapper;
import com.example.siteplatform.system.mapper.SystemRoleMapper;
import com.example.siteplatform.system.mapper.SystemPermissionMapper;
import com.example.siteplatform.system.service.ResponsibilityReleaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
    @Autowired private ProjectInfoMapper projectInfoMapper;
    @Autowired private ResponsibilityReleaseService responsibilityReleaseService;

    private record PreparedChange(Long projectId, Long userId, String operation,
                                  List<SystemRole> roles, SysUserProject existing,
                                  ProjectMemberVO existingMember) {
    }

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

    public PageResult<ProjectMemberAssignmentOptionVO> listAssignmentOptions(
            Long projectId, String keyword, String membership, Integer pageNo, Integer pageSize,
            SysUser currentUser) {
        requireProjectId(projectId);
        requireManageMembers(currentUser, projectId, "查看分配树");
        String normalizedMembership = normalizeMembership(membership);
        List<ProjectMemberVO> members = userProjectMapper.selectMembersByProjectId(projectId);
        Map<Long, ProjectMemberVO> memberByUserId = new LinkedHashMap<>();
        for (ProjectMemberVO member : members == null ? List.<ProjectMemberVO>of() : members) {
            if (member.getUserId() != null) memberByUserId.put(member.getUserId(), member);
        }
        Map<Long, List<SystemRole>> assignedRolesByUserId = new LinkedHashMap<>();
        for (Long memberUserId : memberByUserId.keySet()) {
            assignedRolesByUserId.put(memberUserId,
                    safeList(userProjectRoleMapper.selectAssignedRoles(memberUserId, projectId)));
        }

        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getDeleted, 0)
                .orderByAsc(SysUser::getRealName)
                .orderByAsc(SysUser::getUsername)
                .orderByAsc(SysUser::getId);
        if (memberByUserId.isEmpty()) {
            wrapper.eq(SysUser::getStatus, 1);
        } else {
            wrapper.and(item -> item.eq(SysUser::getStatus, 1)
                    .or().in(SysUser::getId, memberByUserId.keySet()));
        }
        List<SysUser> candidates = userMapper.selectList(wrapper).stream()
                .filter(user -> matchesMembership(memberByUserId.containsKey(user.getId()), normalizedMembership))
                .filter(user -> matchesAssignmentKeyword(user,
                        assignedRolesByUserId.getOrDefault(user.getId(), List.of()), keyword))
                .sorted(Comparator
                        .comparing((SysUser user) -> !memberByUserId.containsKey(user.getId()))
                        .thenComparing(user -> Objects.toString(user.getRealName(), ""))
                        .thenComparing(user -> Objects.toString(user.getUsername(), ""))
                        .thenComparing(SysUser::getId))
                .toList();
        int normalizedPage = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int normalizedSize = pageSize == null ? 100 : Math.max(1, Math.min(pageSize, 100));
        int from = Math.min((normalizedPage - 1) * normalizedSize, candidates.size());
        int to = Math.min(from + normalizedSize, candidates.size());
        boolean platformAdmin = projectPermissionService.isPlatformAdmin(currentUser.getId());
        List<ProjectMemberAssignmentOptionVO> records = candidates.subList(from, to).stream()
                .map(user -> toAssignmentOption(user, memberByUserId.get(user.getId()),
                        assignedRolesByUserId.getOrDefault(user.getId(), List.of()), currentUser, platformAdmin))
                .toList();
        return PageResult.of(normalizedPage, normalizedSize, (long) candidates.size(), records);
    }

    @Transactional
    public List<ProjectMemberVO> batchUpdateProjectAssignments(
            Long projectId, ProjectMemberBatchRequest request, SysUser currentUser) {
        requireProjectId(projectId);
        requireManageMembers(currentUser, projectId, "批量授权");
        List<ProjectMemberBatchRequest.Change> changes = validateProjectBatch(request);
        requireProjectExistsForUpdate(projectId);
        for (ProjectMemberBatchRequest.Change change : changes.stream()
                .sorted(Comparator.comparing(ProjectMemberBatchRequest.Change::getUserId)).toList()) {
            userMapper.selectByIdForUpdate(change.getUserId());
        }
        List<PreparedChange> prepared = new ArrayList<>();
        for (ProjectMemberBatchRequest.Change change : changes.stream()
                .sorted(Comparator.comparing(ProjectMemberBatchRequest.Change::getUserId)).toList()) {
            prepared.add(prepareChange(projectId, change.getUserId(), change.getOperation(),
                    change.getRoleIds(), currentUser, false));
        }
        applyPreparedChanges(prepared, currentUser);
        Set<Long> affectedUsers = prepared.stream()
                .filter(change -> change.existing() != null || "UPSERT".equals(change.operation()))
                .map(PreparedChange::userId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        invalidateUsers(affectedUsers);
        recordBatchOperation(currentUser, "BATCH_UPDATE_PROJECT_MEMBERS", "PROJECT_MEMBER",
                projectId, batchDescription(prepared));
        return listMembers(projectId, currentUser);
    }

    @Transactional
    public List<UserProjectRoleVO> batchUpdateUserProjectAssignments(
            Long userId, UserProjectRoleBatchRequest request, SysUser currentUser) {
        requirePlatformAdministrator(currentUser);
        List<UserProjectRoleBatchRequest.Change> changes = validateUserProjectBatch(request);
        List<UserProjectRoleBatchRequest.Change> sortedChanges = changes.stream()
                .sorted(Comparator.comparing(UserProjectRoleBatchRequest.Change::getProjectId)).toList();
        for (UserProjectRoleBatchRequest.Change change : sortedChanges) {
            requireProjectExistsForUpdate(change.getProjectId());
        }
        SysUser targetUser = userMapper.selectByIdForUpdate(userId);
        if (targetUser == null || Integer.valueOf(1).equals(targetUser.getDeleted())) {
            throw BusinessException.notFound("用户不存在");
        }
        List<PreparedChange> prepared = new ArrayList<>();
        for (UserProjectRoleBatchRequest.Change change : sortedChanges) {
            prepared.add(prepareChange(change.getProjectId(), userId, change.getOperation(),
                    change.getRoleIds(), currentUser, true));
        }
        List<ResponsibilityImpactVO> impacts = prepared.stream()
                .filter(change -> change.existing() != null)
                .map(change -> responsibilityImpactForChange(change))
                .filter(impact -> impact.getTotalCount() > 0)
                .toList();
        if (!impacts.isEmpty() && !request.isConfirmResponsibilityRelease()) {
            throw BusinessException.of(409, "该用户仍有关联电箱或未完成任务，请预览影响并确认解除责任绑定");
        }
        applyPreparedChanges(prepared, currentUser);
        for (PreparedChange change : prepared) {
            if (change.existing() == null) continue;
            if ("REMOVE".equals(change.operation())) {
                responsibilityReleaseService.releaseAll(change.projectId(), change.userId());
            } else {
                responsibilityReleaseService.releaseForCapabilityLoss(change.projectId(), change.userId());
            }
        }
        if (prepared.stream().anyMatch(change -> change.existing() != null || "UPSERT".equals(change.operation()))) {
            invalidateUsers(Set.of(userId));
        }
        recordBatchOperation(currentUser, "BATCH_UPDATE_USER_PROJECT_ROLES", "SYS_USER",
                userId, batchDescription(prepared));
        return userProjectAssignments(userId);
    }

    public List<ResponsibilityImpactVO> previewUserProjectAssignmentImpact(
            Long userId, UserProjectRoleBatchRequest request, SysUser currentUser) {
        requirePlatformAdministrator(currentUser);
        List<UserProjectRoleBatchRequest.Change> changes = validateUserProjectBatch(request);
        SysUser targetUser = userMapper.selectById(userId);
        if (targetUser == null || Integer.valueOf(1).equals(targetUser.getDeleted())) {
            throw BusinessException.notFound("用户不存在");
        }
        List<ResponsibilityImpactVO> impacts = new ArrayList<>();
        for (UserProjectRoleBatchRequest.Change change : changes) {
            if (findUserProject(change.getProjectId(), userId) == null) continue;
            String operation = normalizeOperation(change.getOperation());
            List<SystemRole> roles = "REMOVE".equals(operation)
                    ? List.of() : requireEnabledProjectRoles(change.getRoleIds());
            PreparedChange prepared = new PreparedChange(change.getProjectId(), userId, operation,
                    roles, findUserProject(change.getProjectId(), userId), null);
            ResponsibilityImpactVO impact = responsibilityImpactForChange(prepared);
            if (impact.getTotalCount() > 0) impacts.add(impact);
        }
        return impacts;
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
        SysUserProject existing = userProjectMapper.selectByProjectAndUserForUpdate(projectId, userId);
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
        responsibilityReleaseService.releaseAll(projectId, userId);
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
        SysUserProject existing = userProjectMapper.selectByProjectAndUserForUpdate(projectId, userId);
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
        if ("DISABLED".equals(status)) {
            responsibilityReleaseService.releaseAll(projectId, userId);
        }
        invalidateUserAccess(userId);
        recordMemberOperation(projectId, userId, currentUser,
                "ACTIVE".equals(status) ? "RESTORE_PROJECT_ACCESS" : "DISABLE_PROJECT_ACCESS",
                "项目访问状态变更为" + status + reasonSuffix(request.getReason()));
        return findMember(projectId, userId);
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

    private ProjectMemberAssignmentOptionVO toAssignmentOption(
            SysUser user, ProjectMemberVO member, List<SystemRole> assignedRoles,
            SysUser currentUser, boolean platformAdmin) {
        ProjectMemberAssignmentOptionVO option = new ProjectMemberAssignmentOptionVO();
        option.setUserId(user.getId());
        option.setUsername(user.getUsername());
        option.setRealName(user.getRealName());
        option.setAccountStatus(user.getStatus());
        option.setAssigned(member != null);
        option.setAccessStatus(member == null ? null : member.getAccessStatus());
        option.setStatusReason(member == null ? null : member.getStatusReason());
        List<SystemRole> normalizedRoles = member == null ? List.of() : safeList(assignedRoles);
        option.setProjectRoles(normalizedRoles);
        boolean protectedManager = normalizedRoles.stream().anyMatch(this::isProjectManagerRole);
        boolean ownAccount = Objects.equals(currentUser.getId(), user.getId());
        option.setProtectedManager(protectedManager);
        option.setRoleEditable(platformAdmin || (!ownAccount && !protectedManager));
        option.setRemovable(!ownAccount && (platformAdmin || !protectedManager));
        return option;
    }

    private boolean matchesAssignmentKeyword(SysUser user, List<SystemRole> assignedRoles, String keyword) {
        if (!StringUtils.hasText(keyword)) return true;
        String value = keyword.trim().toLowerCase(Locale.ROOT);
        if (Objects.toString(user.getUsername(), "").toLowerCase(Locale.ROOT).contains(value)
                || Objects.toString(user.getRealName(), "").toLowerCase(Locale.ROOT).contains(value)) {
            return true;
        }
        return safeList(assignedRoles).stream().anyMatch(role ->
                Objects.toString(role.getRoleName(), "").toLowerCase(Locale.ROOT).contains(value)
                        || Objects.toString(role.getRoleCode(), "").toLowerCase(Locale.ROOT).contains(value));
    }

    private List<ProjectMemberBatchRequest.Change> validateProjectBatch(ProjectMemberBatchRequest request) {
        if (request == null || request.getChanges() == null || request.getChanges().isEmpty()) {
            throw new BusinessException("请至少提交一项成员变更");
        }
        if (request.getChanges().size() > 200) throw new BusinessException("单次最多修改200名成员");
        Set<Long> userIds = new LinkedHashSet<>();
        for (ProjectMemberBatchRequest.Change change : request.getChanges()) {
            if (change == null || change.getUserId() == null || change.getUserId() <= 0) {
                throw new BusinessException("用户ID不能为空");
            }
            if (!userIds.add(change.getUserId())) throw new BusinessException("同一用户不能重复提交");
            validateChangeShape(change.getOperation(), change.getRoleIds());
        }
        return request.getChanges();
    }

    private List<UserProjectRoleBatchRequest.Change> validateUserProjectBatch(UserProjectRoleBatchRequest request) {
        if (request == null || request.getChanges() == null || request.getChanges().isEmpty()) {
            throw new BusinessException("请至少提交一项项目变更");
        }
        if (request.getChanges().size() > 200) throw new BusinessException("单次最多修改200个项目");
        Set<Long> projectIds = new LinkedHashSet<>();
        for (UserProjectRoleBatchRequest.Change change : request.getChanges()) {
            if (change == null || change.getProjectId() == null || change.getProjectId() <= 0) {
                throw new BusinessException("项目ID不能为空");
            }
            if (!projectIds.add(change.getProjectId())) throw new BusinessException("同一项目不能重复提交");
            validateChangeShape(change.getOperation(), change.getRoleIds());
        }
        return request.getChanges();
    }

    private void validateChangeShape(String operation, List<Long> roleIds) {
        String normalized = normalizeOperation(operation);
        if ("UPSERT".equals(normalized)) {
            if (roleIds == null || roleIds.isEmpty()) throw new BusinessException("加入或调整成员时请至少选择一个角色");
            if (roleIds.size() > 100) throw new BusinessException("单个成员最多选择100个角色");
        } else if (roleIds != null && !roleIds.isEmpty()) {
            throw new BusinessException("移出成员时不能同时提交角色");
        }
    }

    private PreparedChange prepareChange(Long projectId, Long userId, String rawOperation,
                                         List<Long> roleIds, SysUser currentUser,
                                         boolean confirmResponsibilityRelease) {
        String operation = normalizeOperation(rawOperation);
        SysUserProject existing = userProjectMapper.selectByProjectAndUserForUpdate(projectId, userId);
        ProjectMemberVO existingMember = existing == null ? null : findMember(projectId, userId);
        if ("REMOVE".equals(operation)) {
            if (existing != null) validateRemoval(projectId, userId, existingMember, currentUser,
                    confirmResponsibilityRelease);
            return new PreparedChange(projectId, userId, operation, List.of(), existing, existingMember);
        }

        SysUser targetUser = userMapper.selectById(userId);
        if (targetUser == null || Integer.valueOf(1).equals(targetUser.getDeleted())) {
            throw BusinessException.notFound("用户不存在：" + userId);
        }
        if (!Integer.valueOf(1).equals(targetUser.getStatus())) {
            throw new BusinessException("只能将已启用账号加入项目：" + userId);
        }
        List<SystemRole> roles = requireEnabledProjectRoles(roleIds);
        assertProjectManagerCanChangeRoles(currentUser, projectId, userId, existing, roles);
        if (!confirmResponsibilityRelease) {
            requireRequestedRolesKeepOpenQualityServiceable(projectId, userId, existing, roles);
        }
        return new PreparedChange(projectId, userId, operation, roles, existing, existingMember);
    }

    private void validateRemoval(Long projectId, Long userId, ProjectMemberVO member, SysUser currentUser,
                                 boolean allowResponsibilityRelease) {
        if (Objects.equals(currentUser.getId(), userId)) throw new BusinessException("不能移除自己的项目授权");
        assertProjectManagerCanChangeTarget(currentUser, projectId, userId);
        if (allowResponsibilityRelease && projectPermissionService.isPlatformAdmin(currentUser.getId())) return;
        requireNoOpenQualityAssignments(projectId, userId, "移除");
        if (member != null && ((member.getResponsibleBoxCount() != null && member.getResponsibleBoxCount() > 0)
                || (member.getPendingRectificationCount() != null && member.getPendingRectificationCount() > 0))) {
            throw new BusinessException("该成员仍负责电箱或待整改任务，请先调整后再移除");
        }
    }

    private void requireRequestedRolesKeepOpenQualityServiceable(
            Long projectId, Long userId, SysUserProject existing, List<SystemRole> roles) {
        long openCount = qualityAssigneeService.countOpenAssignments(projectId, userId);
        if (openCount == 0) return;
        boolean activeAccess = existing != null && "ACTIVE".equalsIgnoreCase(existing.getStatus());
        if (!activeAccess || (!projectPermissionService.isPlatformAdmin(userId)
                && !rolesProvideQualityAssigneeAccess(roles))) {
            throw new BusinessException("该成员仍有" + openCount
                    + "项未闭环质量整改，不能取消其质量模块、查看或整改权限，请先改派或关闭");
        }
    }

    private boolean rolesProvideQualityAssigneeAccess(List<SystemRole> roles) {
        boolean qualityModule = roles.stream().anyMatch(role -> safeList(
                roleBusinessModuleMapper.selectModuleCodesByRoleId(role.getId())).stream()
                .anyMatch(code -> "QUALITY".equalsIgnoreCase(code)));
        if (!qualityModule) return false;
        Set<Long> permissionIds = new LinkedHashSet<>();
        for (SystemRole role : roles) permissionIds.addAll(safeList(systemRoleMapper.selectPermissionIds(role.getId())));
        if (permissionIds.isEmpty()) return false;
        Set<String> codes = systemPermissionMapper.selectBatchIds(permissionIds).stream()
                .filter(permission -> permission != null && Integer.valueOf(1).equals(permission.getEnabled())
                        && !Integer.valueOf(1).equals(permission.getDeleted()))
                .map(SystemPermission::getPermissionCode)
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toSet());
        return codes.contains(SystemPermissionCodes.QUALITY_VIEW)
                && codes.contains(SystemPermissionCodes.QUALITY_RECTIFY);
    }

    private ResponsibilityImpactVO responsibilityImpactForChange(PreparedChange change) {
        ResponsibilityImpactVO impact = responsibilityReleaseService.impact(change.projectId(), change.userId());
        if ("REMOVE".equals(change.operation())) return impact;
        if (projectPermissionService.isPlatformAdmin(change.userId())) {
            clearResponsibilityCounts(impact);
            return impact;
        }
        Set<String> codes = permissionCodesForRoles(change.userId(), change.roles());
        if (codes.contains(InspectionPermissionCodes.INSPECTION_DAILY_SUBMIT)
                || codes.contains(SystemPermissionCodes.INSPECTION_SUBMIT)) {
            impact.setResponsibleElectricBoxCount(0);
        }
        if (codes.contains(InspectionPermissionCodes.INSPECTION_REVIEW)
                || codes.contains(SystemPermissionCodes.INSPECTION_MANAGE)) {
            impact.setSafetyManagedElectricBoxCount(0);
            impact.setPendingInspectionReviewCount(0);
        }
        if (codes.contains(SystemPermissionCodes.INSPECTION_RECTIFY)) {
            impact.setOpenRectificationCount(0);
        }
        if (codes.contains(SystemPermissionCodes.QUALITY_RECTIFY)) {
            impact.setOpenQualityIssueCount(0);
        }
        return impact;
    }

    private Set<String> permissionCodesForRoles(Long userId, List<SystemRole> roles) {
        Set<String> codes = new LinkedHashSet<>(safeList(systemPermissionMapper.selectPlatformCodesByUserId(userId)));
        Set<Long> permissionIds = new LinkedHashSet<>();
        for (SystemRole role : safeList(roles)) {
            permissionIds.addAll(safeList(systemRoleMapper.selectPermissionIds(role.getId())));
        }
        if (!permissionIds.isEmpty()) {
            systemPermissionMapper.selectBatchIds(permissionIds).stream()
                    .filter(permission -> permission != null
                            && Integer.valueOf(1).equals(permission.getEnabled())
                            && !Integer.valueOf(1).equals(permission.getDeleted()))
                    .map(SystemPermission::getPermissionCode)
                    .filter(StringUtils::hasText)
                    .forEach(codes::add);
        }
        return codes;
    }

    private void clearResponsibilityCounts(ResponsibilityImpactVO impact) {
        impact.setResponsibleElectricBoxCount(0);
        impact.setSafetyManagedElectricBoxCount(0);
        impact.setPendingInspectionReviewCount(0);
        impact.setOpenRectificationCount(0);
        impact.setOpenQualityIssueCount(0);
    }

    private void applyPreparedChanges(List<PreparedChange> changes, SysUser currentUser) {
        LocalDateTime now = LocalDateTime.now();
        for (PreparedChange change : changes) {
            if ("REMOVE".equals(change.operation())) {
                if (change.existing() == null) continue;
                userProjectRoleMapper.deleteByUserAndProject(change.userId(), change.projectId());
                requireSingleWrite(userProjectMapper.deleteById(change.existing().getId()));
                continue;
            }
            SysUserProject relation = change.existing();
            if (relation == null) {
                relation = new SysUserProject();
                relation.setProjectId(change.projectId());
                relation.setUserId(change.userId());
                relation.setStatus("ACTIVE");
                relation.setCreateTime(now);
            }
            relation.setProjectRoleCode(primaryRoleCode(change.roles()));
            relation.setInspectionPermissionTemplateId(null);
            relation.setUpdateTime(now);
            if (relation.getId() == null) requireSingleWrite(userProjectMapper.insert(relation));
            else requireSingleWrite(userProjectMapper.updateById(relation));
            replaceRoles(change.projectId(), change.userId(), change.roles(), currentUser.getId());
        }
    }

    private List<UserProjectRoleVO> userProjectAssignments(Long userId) {
        List<UserProjectRoleVO> assignments = safeList(userProjectMapper.selectUserProjectRolesForManagement(userId));
        assignments.forEach(assignment -> assignment.setProjectRoles(assignment.getProjectId() == null
                ? List.of() : safeList(userProjectRoleMapper.selectAssignedRoles(userId, assignment.getProjectId()))));
        return assignments;
    }

    private void invalidateUsers(Set<Long> userIds) {
        for (Long userId : userIds) invalidateUserAccess(userId);
    }

    private void requireProjectExistsForUpdate(Long projectId) {
        ProjectInfo project = projectInfoMapper.selectByIdForUpdate(projectId);
        if (project == null || Integer.valueOf(1).equals(project.getDeleted())) {
            throw BusinessException.notFound("项目不存在：" + projectId);
        }
    }

    private void requirePlatformAdministrator(SysUser currentUser) {
        if (currentUser == null || !projectPermissionService.isPlatformAdmin(currentUser.getId())) {
            throw BusinessException.forbidden("仅系统管理员可跨项目批量分配用户角色");
        }
    }

    private String normalizeOperation(String operation) {
        String normalized = StringUtils.hasText(operation)
                ? operation.trim().toUpperCase(Locale.ROOT) : "";
        if (!"UPSERT".equals(normalized) && !"REMOVE".equals(normalized)) {
            throw new BusinessException("操作只支持 UPSERT 或 REMOVE");
        }
        return normalized;
    }

    private String normalizeMembership(String membership) {
        String normalized = StringUtils.hasText(membership)
                ? membership.trim().toUpperCase(Locale.ROOT) : "ALL";
        if (!Set.of("ALL", "ASSIGNED", "UNASSIGNED").contains(normalized)) {
            throw new BusinessException("成员筛选只支持 ALL、ASSIGNED 或 UNASSIGNED");
        }
        return normalized;
    }

    private boolean matchesMembership(boolean assigned, String membership) {
        return "ALL".equals(membership)
                || ("ASSIGNED".equals(membership) && assigned)
                || ("UNASSIGNED".equals(membership) && !assigned);
    }

    private String batchDescription(List<PreparedChange> changes) {
        long added = changes.stream().filter(change -> "UPSERT".equals(change.operation()) && change.existing() == null).count();
        long updated = changes.stream().filter(change -> "UPSERT".equals(change.operation()) && change.existing() != null).count();
        long removed = changes.stream().filter(change -> "REMOVE".equals(change.operation()) && change.existing() != null).count();
        return "批量更新项目成员授权：新增" + added + "，调整" + updated + "，移出" + removed;
    }

    private void recordBatchOperation(SysUser operator, String operationType, String businessType,
                                      Long businessId, String detail) {
        OperationLog log = new OperationLog();
        log.setUserId(operator.getId());
        log.setUsername(operator.getUsername());
        log.setOperationType(operationType);
        log.setOperationDesc(detail);
        log.setBusinessType(businessType);
        log.setBusinessId(businessId);
        log.setCreateTime(LocalDateTime.now());
        operationLogMapper.insert(log);
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
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
