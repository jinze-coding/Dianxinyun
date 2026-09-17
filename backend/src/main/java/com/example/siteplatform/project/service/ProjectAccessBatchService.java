package com.example.siteplatform.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.log.entity.OperationLog;
import com.example.siteplatform.log.mapper.OperationLogMapper;
import com.example.siteplatform.project.constant.InspectionPermissionCodes;
import com.example.siteplatform.project.dto.*;
import com.example.siteplatform.project.dto.ProjectAccessBatchPreview.*;
import com.example.siteplatform.project.dto.ProjectAccessBatchRequest.*;
import com.example.siteplatform.project.entity.*;
import com.example.siteplatform.project.mapper.*;
import com.example.siteplatform.system.constant.*;
import com.example.siteplatform.system.entity.*;
import com.example.siteplatform.system.mapper.*;
import com.example.siteplatform.system.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/** 多用户授权变更：服务端生成计划，确认时重新校验，整批事务保存。 */
@Service
@RequiredArgsConstructor
public class ProjectAccessBatchService {
    private static final String PREFIX = "project-access-batch:preview:";
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final DefaultRedisScript<Long> CONSUME = new DefaultRedisScript<>(
            "if redis.call('GET',KEYS[1]) == ARGV[1] then return redis.call('DEL',KEYS[1]) else return 0 end", Long.class);
    private final SysUserMapper users;
    private final SysUserProjectMapper memberships;
    private final SysUserProjectRoleMapper assignments;
    private final ProjectInfoMapper projects;
    private final SystemRoleMapper roles;
    private final SystemPermissionMapper permissions;
    private final SystemRoleBusinessModuleMapper roleModules;
    private final SystemPermissionService authorization;
    private final ProjectBusinessModuleService projectModules;
    private final ProjectMemberService members;
    private final ResponsibilityReleaseService responsibilities;
    private final OperationLogMapper audit;
    private final StringRedisTemplate redis;
    private final ObjectMapper json;

    public record TokenPayload(Long operatorId, ProjectAccessBatchRequest request, String signature) {}
    private record Grant(SystemRole role, List<SystemPermission> permissions, List<String> modules, List<Long> menus) {}
    private record Membership(SysUserProject row, List<Long> roleIds) {}
    private record Change(Long userId, Membership before, List<SystemRole> afterRoles, ProjectChange view) {}
    private record Evaluation(ProjectAccessBatchPreview preview, String signature, List<Change> changes) {}

    @Transactional(readOnly = true)
    public ProjectAccessBatchPreview preview(ProjectAccessBatchRequest request, SysUser operator) {
        requireOperator(operator);
        normalize(request);
        Evaluation result = evaluate(request, operator);
        ProjectAccessBatchPreview preview = result.preview();
        if (preview.getBlockedUserCount() == 0 && preview.getChangedUserCount() > 0) {
            String token = UUID.randomUUID().toString();
            redis.opsForValue().set(PREFIX + token,
                    encode(new TokenPayload(operator.getId(), request, result.signature())), TTL);
            preview.setConfirmationToken(token);
            preview.setExpiresAt(Instant.now().plus(TTL).toString());
        }
        return preview;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ProjectAccessBatchPreview confirm(ProjectAccessBatchConfirmRequest request, SysUser operator) {
        requireOperator(operator);
        if (request == null || request.getConfirmationToken() == null
                || !request.getConfirmationToken().matches("[a-f0-9-]{36}")) throw conflict();
        String key = PREFIX + request.getConfirmationToken();
        String raw = redis.opsForValue().get(key);
        if (raw == null) throw conflict();
        TokenPayload token;
        try { token = json.readValue(raw, TokenPayload.class); }
        catch (Exception failure) { throw conflict(); }
        if (!Objects.equals(token.operatorId(), operator.getId())) throw BusinessException.forbidden("请使用生成本次预览的管理员账号");
        ProjectAccessBatchRequest input = token.request();
        normalize(input);
        // 与既有成员写入保持项目、用户的固定锁顺序，避免跨项目对向迁移互锁。
        for (Long id : projectIds(input)) {
            if (projects.selectByIdForUpdate(id) == null) throw conflict();
        }
        Set<Long> lockedUsers = new TreeSet<>(input.getUserIds());
        lockedUsers.add(operator.getId());
        for (Long id : lockedUsers) users.selectByIdForUpdate(id);
        requireOperator(operator);
        Set<Long> lockedRoles = new TreeSet<>(input.getRoleIds());
        for (Long userId : input.getUserIds()) for (Long projectId : projectIds(input)) {
            memberships.selectByProjectAndUserForUpdate(projectId, userId);
            lockedRoles.addAll(readRoleIds(projectId, userId));
        }
        for (Long id : lockedRoles) roles.selectByIdForUpdate(id);
        Evaluation current = evaluate(input, operator);
        if (current.preview().getBlockedUserCount() > 0 || !current.signature().equals(token.signature())) throw conflict();
        if (current.preview().getResponsibilityCount() > 0 && !request.isConfirmResponsibilityRelease()) {
            throw BusinessException.of(409, "请先核对并确认本次需要解除的责任绑定");
        }
        if (!Long.valueOf(1).equals(redis.execute(CONSUME, List.of(key), raw))) throw conflict();

        List<Change> changed = current.changes().stream().filter(c -> !"UNCHANGED".equals(c.view().getAction())).toList();
        // 先授予目标项目，再移出来源项目；任一步失败会回滚整个批次。
        for (Change change : changed.stream().sorted(Comparator.comparing(c -> "REMOVED".equals(c.view().getAction()))).toList()) {
            members.applyValidatedBatchChange(change.view().getProjectId(), change.userId(), change.afterRoles(),
                    change.before().row(), "REMOVED".equals(change.view().getAction()), change.view().getAfterStatus(), operator);
        }
        for (Change change : changed) {
            if ("REMOVED".equals(change.view().getAction())) {
                responsibilities.releaseAll(change.view().getProjectId(), change.userId());
            } else if (change.view().getResponsibilityImpact().getTotalCount() > 0) {
                responsibilities.releasePreviewedImpact(change.view().getResponsibilityImpact());
            }
            record(operator, request.getConfirmationToken(), input.getOperation(), change);
        }
        members.invalidateUsers(changed.stream().map(Change::userId).collect(Collectors.toCollection(LinkedHashSet::new)));
        return current.preview();
    }

    private Evaluation evaluate(ProjectAccessBatchRequest request, SysUser operator) {
        Map<Long, ProjectInfo> projectMap = new TreeMap<>();
        Map<Long, ProjectBusinessModuleService.State> moduleStates = new TreeMap<>();
        List<Object> snapshot = new ArrayList<>();
        for (Long id : projectIds(request)) {
            ProjectInfo project = projects.selectById(id);
            if (project == null) throw BusinessException.notFound("项目不存在：" + id);
            projectMap.put(id, project);
            moduleStates.put(id, projectModules.state(id));
            snapshot.add(Arrays.asList(id, project.getProjectName(), moduleStates.get(id)));
        }
        Map<Long, Grant> grants = new TreeMap<>();
        ProjectAccessBatchPreview result = new ProjectAccessBatchPreview();
        List<Change> changes = new ArrayList<>();
        for (Long userId : request.getUserIds()) {
            UserChange view = new UserChange();
            view.setUserId(userId);
            result.getUsers().add(view);
            SysUser user = users.selectById(userId);
            if (user == null) { view.getErrors().add("用户不存在或已删除"); continue; }
            view.setUsername(user.getUsername()); view.setRealName(user.getRealName());
            snapshot.add(Arrays.asList(userId, user.getStatus(), user.getDeleted(), user.getUpdateTime()));
            if (!Integer.valueOf(1).equals(user.getStatus())) { view.getErrors().add("账号已停用，请先启用账号"); continue; }
            if (authorization.isPlatformAdmin(userId)) { view.getErrors().add("平台管理员不参与项目授权批量迁移"); continue; }
            try {
                Long targetId = request.isTransfer() ? request.getTargetProjectId() : request.getProjectId();
                Membership target = readMembership(targetId, userId);
                snapshot.add(Arrays.asList(userId, targetId, target));
                List<Long> selected = request.getRoleIds();
                if (request.isTransfer()) {
                    Membership source = readMembership(request.getSourceProjectId(), userId);
                    snapshot.add(Arrays.asList(userId, request.getSourceProjectId(), source));
                    if (source.row() == null) throw new BusinessException("该用户不属于来源项目");
                    resolveRoles(source.roleIds(), grants);
                    selected = request.getRoleSource() == RoleSource.SOURCE ? source.roleIds() : selected;
                    if (selected.isEmpty()) throw new BusinessException("来源项目没有可沿用角色，请指定目标角色");
                    if (request.getOperation() == Operation.MOVE_PROJECT) {
                        if (Objects.equals(userId, operator.getId())) throw new BusinessException("不能移除自己的项目授权");
                        addChange(userId, source, List.of(), null, request.getSourceProjectId(), true, grants, projectMap, moduleStates, view, changes);
                    } else {
                        addChange(userId, source, source.roleIds(), source.row().getStatus(), request.getSourceProjectId(), false, grants, projectMap, moduleStates, view, changes);
                    }
                    String initialStatus = target.row() == null ? source.row().getStatus() : target.row().getStatus();
                    addChange(userId, target, union(target.roleIds(), selected), initialStatus, targetId, false, grants, projectMap, moduleStates, view, changes);
                } else {
                    if (request.getOperation() != Operation.ADD_ROLES && target.row() == null) throw new BusinessException("该用户尚未加入所选项目");
                    List<Long> after = switch (request.getOperation()) {
                        case ADD_ROLES -> union(target.roleIds(), selected);
                        case REMOVE_ROLES -> target.roleIds().stream().filter(id -> !request.getRoleIds().contains(id)).toList();
                        case REPLACE_ROLES -> selected;
                        default -> throw new BusinessException("不支持的批量操作");
                    };
                    if (after.isEmpty()) throw new BusinessException("移除后将没有任何角色，请调整选择；不会自动移出项目");
                    addChange(userId, target, after, target.row() == null ? "ACTIVE" : target.row().getStatus(), targetId, false, grants, projectMap, moduleStates, view, changes);
                }
            } catch (BusinessException invalid) { view.getErrors().add(invalid.getMessage()); }
        }
        for (UserChange user : result.getUsers()) {
            if (!user.getErrors().isEmpty()) { result.setBlockedUserCount(result.getBlockedUserCount() + 1); continue; }
            boolean changed = false;
            for (ProjectChange project : user.getProjects()) {
                switch (project.getAction()) {
                    case "ADDED" -> { result.setAddedCount(result.getAddedCount() + 1); changed = true; }
                    case "UPDATED" -> { result.setUpdatedCount(result.getUpdatedCount() + 1); changed = true; }
                    case "REMOVED" -> { result.setRemovedCount(result.getRemovedCount() + 1); changed = true; }
                    default -> { }
                }
                result.setResponsibilityCount(result.getResponsibilityCount() + project.getResponsibilityImpact().getTotalCount());
            }
            if (changed) result.setChangedUserCount(result.getChangedUserCount() + 1);
            else result.setUnchangedUserCount(result.getUnchangedUserCount() + 1);
        }
        snapshot.add(grants);
        snapshot.add(result);
        return new Evaluation(result, digest(snapshot), changes);
    }

    private void addChange(Long userId, Membership before, List<Long> afterIds, String afterStatus, Long projectId,
                           boolean remove, Map<Long, Grant> grants, Map<Long, ProjectInfo> projectMap,
                           Map<Long, ProjectBusinessModuleService.State> moduleStates, UserChange userView, List<Change> changes) {
        List<SystemRole> beforeRoles = resolveRoles(before.roleIds(), grants);
        List<SystemRole> afterRoles = resolveRoles(afterIds, grants);
        if (!remove && afterRoles.size() > 100) throw new BusinessException("合并后角色超过100个，请调整选择");
        ProjectChange view = new ProjectChange();
        view.setProjectId(projectId); view.setProjectName(projectMap.get(projectId).getProjectName());
        view.setBeforeStatus(before.row() == null ? null : before.row().getStatus());
        view.setAfterStatus(remove ? null : afterStatus);
        view.setBeforeRoles(roleViews(beforeRoles)); view.setAfterRoles(roleViews(afterRoles));
        view.setAction(remove ? "REMOVED" : before.row() == null ? "ADDED" : before.roleIds().equals(afterIds) ? "UNCHANGED" : "UPDATED");
        Set<String> oldCodes = codes(before.roleIds(), view.getBeforeStatus(), moduleStates.get(projectId), grants);
        Set<String> newCodes = codes(afterIds, view.getAfterStatus(), moduleStates.get(projectId), grants);
        view.setRemovedPermissions(oldCodes.stream().filter(code -> !newCodes.contains(code)).sorted().map(code -> grants.values().stream()
                .flatMap(grant -> grant.permissions().stream()).filter(p -> code.equals(p.getPermissionCode()))
                .map(SystemPermission::getPermissionName).filter(Objects::nonNull).findFirst().orElse(code)).toList());
        ResponsibilityImpactVO impact = new ResponsibilityImpactVO(); impact.setProjectId(projectId); impact.setUserId(userId); impact.setProjectName(view.getProjectName());
        if (remove) impact = responsibilities.impact(projectId, userId);
        else if (!view.getRemovedPermissions().isEmpty()) {
            impact = responsibilities.impact(projectId, userId);
            keepOnlyLostResponsibilities(impact, oldCodes, newCodes);
        }
        view.setResponsibilityImpact(impact);
        userView.getProjects().add(view);
        changes.add(new Change(userId, before, afterRoles, view));
    }

    static void keepOnlyLostResponsibilities(ResponsibilityImpactVO impact, Set<String> before, Set<String> after) {
        if (!lost(before, after, InspectionPermissionCodes.INSPECTION_DAILY_SUBMIT, SystemPermissionCodes.INSPECTION_SUBMIT)) impact.setResponsibleElectricBoxCount(0);
        if (!lost(before, after, InspectionPermissionCodes.INSPECTION_REVIEW, SystemPermissionCodes.INSPECTION_MANAGE)) {
            impact.setSafetyManagedElectricBoxCount(0); impact.setPendingInspectionReviewCount(0);
        }
        if (!lost(before, after, SystemPermissionCodes.INSPECTION_RECTIFY)) impact.setOpenRectificationCount(0);
        if (!lost(before, after, InspectionPermissionCodes.EDGE_INSPECTION_SUBMIT)) impact.setPendingGeneralInspectionTaskCount(0);
        if (!lost(before, after, InspectionPermissionCodes.EDGE_INSPECTION_RECTIFY)) impact.setOpenGeneralRectificationCount(0);
        if (!lost(before, after, InspectionPermissionCodes.EDGE_INSPECTION_REVIEW)) impact.setPendingGeneralReviewCount(0);
        if (!lost(before, after, SystemPermissionCodes.QUALITY_RECTIFY)
                && !lost(before, after, SystemPermissionCodes.QUALITY_VIEW)) impact.setOpenQualityIssueCount(0);
        if (!lost(before, after, SystemPermissionCodes.QUALITY_MANAGE)) impact.setQualityWeeklyReminderSettingCount(0);
        impact.setPendingSealApprovalCount(0); impact.setSealApprovalConfigCount(0);
    }

    private static boolean lost(Set<String> before, Set<String> after, String... codes) {
        return Arrays.stream(codes).anyMatch(before::contains) && Arrays.stream(codes).noneMatch(after::contains);
    }

    private Set<String> codes(List<Long> ids, String status, ProjectBusinessModuleService.State project, Map<Long, Grant> grants) {
        if (!"ACTIVE".equals(status)) return Set.of();
        Set<String> modules = ids.stream().flatMap(id -> grants.get(id).modules().stream()).collect(Collectors.toSet());
        modules.retainAll(project.enabledBusinessModules());
        return ids.stream().flatMap(id -> grants.get(id).permissions().stream())
                .filter(p -> Integer.valueOf(1).equals(p.getEnabled()) && !Integer.valueOf(1).equals(p.getDeleted()))
                .map(SystemPermission::getPermissionCode).filter(Objects::nonNull)
                .filter(code -> BusinessModuleCodes.fromPermissionCode(code) == null || modules.contains(BusinessModuleCodes.fromPermissionCode(code)))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private List<SystemRole> resolveRoles(List<Long> ids, Map<Long, Grant> grants) {
        List<SystemRole> result = new ArrayList<>();
        for (Long id : ids) {
            Grant grant = grants.get(id);
            if (grant == null) {
                SystemRole role = roles.selectById(id);
                if (role == null || !"PROJECT".equals(role.getScopeType()) || !Integer.valueOf(1).equals(role.getEnabled()))
                    throw new BusinessException("项目角色不存在或已停用：" + id);
                List<Long> permissionIds = roles.selectPermissionIds(id);
                List<SystemPermission> permissionList = permissionIds.isEmpty() ? List.of() : permissions.selectBatchIds(permissionIds)
                        .stream().sorted(Comparator.comparing(SystemPermission::getId)).toList();
                grant = new Grant(role, permissionList, roleModules.selectModuleCodesByRoleId(id).stream().sorted().toList(), roles.selectMenuIds(id));
                grants.put(id, grant);
            }
            result.add(grant.role());
        }
        return result;
    }

    private Membership readMembership(Long projectId, Long userId) {
        return new Membership(memberships.selectOne(new LambdaQueryWrapper<SysUserProject>()
                .eq(SysUserProject::getProjectId, projectId).eq(SysUserProject::getUserId, userId)), readRoleIds(projectId, userId));
    }
    private List<Long> readRoleIds(Long projectId, Long userId) {
        return assignments.selectList(new LambdaQueryWrapper<SysUserProjectRole>()
                .eq(SysUserProjectRole::getProjectId, projectId).eq(SysUserProjectRole::getUserId, userId))
                .stream().map(SysUserProjectRole::getRoleId).distinct().sorted().toList();
    }
    private List<Role> roleViews(List<SystemRole> values) { return values.stream().map(r -> new Role(r.getId(), r.getRoleName())).toList(); }
    private static List<Long> union(List<Long> first, List<Long> second) { Set<Long> ids = new TreeSet<>(first); ids.addAll(second); return List.copyOf(ids); }
    private List<Long> projectIds(ProjectAccessBatchRequest request) { return request.isTransfer()
            ? new TreeSet<>(List.of(request.getSourceProjectId(), request.getTargetProjectId())).stream().toList() : List.of(request.getProjectId()); }

    private void normalize(ProjectAccessBatchRequest request) {
        if (request == null || request.getOperation() == null) throw new BusinessException("请选择批量操作");
        if (request.getUserIds() == null || request.getUserIds().isEmpty() || request.getUserIds().size() > 200
                || request.getUserIds().stream().anyMatch(id -> id == null || id <= 0)) throw new BusinessException("请选择1至200名用户");
        if (new HashSet<>(request.getUserIds()).size() != request.getUserIds().size()) throw new BusinessException("同一用户不能重复选择");
        request.setUserIds(request.getUserIds().stream().sorted().toList());
        if (request.getRoleIds() == null) request.setRoleIds(List.of());
        if (request.getRoleIds().size() > 100 || request.getRoleIds().stream().anyMatch(id -> id == null || id <= 0)) throw new BusinessException("角色选择无效");
        request.setRoleIds(request.getRoleIds().stream().distinct().sorted().toList());
        if (request.isTransfer()) {
            if (request.getSourceProjectId() == null || request.getTargetProjectId() == null || request.getSourceProjectId() <= 0 || request.getTargetProjectId() <= 0
                    || request.getSourceProjectId().equals(request.getTargetProjectId())) throw new BusinessException("请选择不同的来源和目标项目");
            if (request.getRoleSource() == null) throw new BusinessException("请选择目标角色来源");
            request.setProjectId(null);
            if (request.getRoleSource() == RoleSource.SOURCE) request.setRoleIds(List.of());
        } else {
            if (request.getProjectId() == null || request.getProjectId() <= 0) throw new BusinessException("请选择项目");
            request.setSourceProjectId(null); request.setTargetProjectId(null); request.setRoleSource(RoleSource.SELECTED);
        }
        if (request.getRoleSource() == RoleSource.SELECTED && request.getRoleIds().isEmpty()) throw new BusinessException("请至少选择一个项目角色");
    }
    private void requireOperator(SysUser operator) {
        authorization.requirePlatformAdmin(operator);
        SysUser current = users.selectById(operator.getId());
        if (current == null || !Integer.valueOf(1).equals(current.getStatus())) throw BusinessException.forbidden("管理员账号已失效");
    }
    private BusinessException conflict() { return BusinessException.of(409, "预览已过期、已使用或授权状态已变化，请重新预览"); }
    private String encode(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception failure) { throw new IllegalStateException("授权预览序列化失败", failure); }
    }
    private String digest(Object value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encode(value).getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException failure) { throw new IllegalStateException(failure); }
    }
    private void record(SysUser operator, String batch, Operation operation, Change change) {
        String details = encode(change.view());
        // 现有审计描述列为500字符，分片留全量前后角色，不截断关键授权证据。
        for (int from = 0, part = 1; from < details.length(); part++) {
            int end = details.offsetByCodePoints(from, Math.min(360, details.codePointCount(from, details.length())));
            OperationLog log = new OperationLog();
            log.setUserId(operator.getId()); log.setUsername(operator.getUsername());
            log.setOperationType("BATCH_PROJECT_ACCESS"); log.setBusinessType("SYS_USER"); log.setBusinessId(change.userId());
            log.setOperationDesc("批次=" + batch + " 操作=" + operation + " 分段=" + part + " " + details.substring(from, end));
            log.setCreateTime(LocalDateTime.now());
            if (audit.insert(log) != 1) throw BusinessException.of(409, "授权审计保存失败，整批变更已回滚");
            from = end;
        }
    }
}
