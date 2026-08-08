package com.example.siteplatform.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.auth.service.PasswordCredentialService;
import com.example.siteplatform.auth.dto.UserProjectRoleVO;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.common.PageResult;
import com.example.siteplatform.log.entity.OperationLog;
import com.example.siteplatform.log.mapper.OperationLogMapper;
import com.example.siteplatform.project.entity.SysUserProject;
import com.example.siteplatform.project.entity.SysUserProjectRole;
import com.example.siteplatform.project.mapper.SysUserProjectMapper;
import com.example.siteplatform.project.mapper.SysUserProjectRoleMapper;
import com.example.siteplatform.project.service.InspectionPermissionTemplateService;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.registration.dto.RegistrationReviewRequest;
import com.example.siteplatform.system.constant.BusinessModuleCodes;
import com.example.siteplatform.system.constant.SystemPermissionCodes;
import com.example.siteplatform.system.dto.RoleSaveRequest;
import com.example.siteplatform.system.entity.SystemMenu;
import com.example.siteplatform.system.entity.SystemPermission;
import com.example.siteplatform.system.entity.SystemRole;
import com.example.siteplatform.system.entity.SystemRoleBusinessModule;
import com.example.siteplatform.system.mapper.SystemMenuMapper;
import com.example.siteplatform.system.mapper.SystemPermissionMapper;
import com.example.siteplatform.system.mapper.SystemRoleMapper;
import com.example.siteplatform.system.mapper.SystemRoleBusinessModuleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class SystemAdministrationService {

    private static final Set<String> INSPECTION_LEDGER_PERMISSION_CODES = Set.of(
            "BOX_VIEW", "BOX_MANAGE", "BOX_QR_MANAGE", "BOX_PUBLIC_ACCESS");
    private static final Set<String> INSPECTION_RECORD_PERMISSION_CODES = Set.of(
            "INSPECTION_DAILY_SUBMIT", "INSPECTION_RECORD_VIEW", "SUMMARY_VIEW", "SUMMARY_EXPORT");
    private static final Set<String> INSPECTION_RECTIFICATION_PERMISSION_CODES = Set.of(
            "INSPECTION.RECTIFY", "INSPECTION.REVIEW");
    private static final String RETIRED_PROJECT_MEMBER_MENU = "SYSTEM_PROJECT";
    private static final String RETIRED_PROJECT_MEMBER_PERMISSION = "project.member.manage";
    private static final String GENERATED_ROLE_CODE_PREFIX = "ROLE_";
    private static final int GENERATED_ROLE_CODE_ATTEMPTS = 10;
    private static final Pattern ROLE_CODE_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_-]{1,49}$");

    private final SysUserMapper userMapper;
    private final SysUserProjectMapper userProjectMapper;
    private final SystemRoleMapper roleMapper;
    private final SystemMenuMapper menuMapper;
    private final SystemPermissionMapper permissionMapper;
    private final OperationLogMapper operationLogMapper;
    private final InspectionPermissionTemplateService inspectionTemplateService;
    private final ProjectPermissionService projectPermissionService;
    private final ResponsibilityReleaseService responsibilityReleaseService;
    private final AuthService authService;
    private final PasswordCredentialService passwordCredentialService;

    @org.springframework.beans.factory.annotation.Autowired
    private SysUserProjectRoleMapper userProjectRoleMapper;

    @org.springframework.beans.factory.annotation.Autowired
    private SystemRoleBusinessModuleMapper roleBusinessModuleMapper;

    public SystemAdministrationService(SysUserMapper userMapper, SysUserProjectMapper userProjectMapper,
                                       SystemRoleMapper roleMapper, SystemMenuMapper menuMapper,
                                       SystemPermissionMapper permissionMapper, OperationLogMapper operationLogMapper,
                                       InspectionPermissionTemplateService inspectionTemplateService,
                                       ProjectPermissionService projectPermissionService,
                                       ResponsibilityReleaseService responsibilityReleaseService,
                                       AuthService authService,
                                       PasswordCredentialService passwordCredentialService) {
        this.userMapper = userMapper;
        this.userProjectMapper = userProjectMapper;
        this.roleMapper = roleMapper;
        this.menuMapper = menuMapper;
        this.permissionMapper = permissionMapper;
        this.operationLogMapper = operationLogMapper;
        this.inspectionTemplateService = inspectionTemplateService;
        this.projectPermissionService = projectPermissionService;
        this.responsibilityReleaseService = responsibilityReleaseService;
        this.authService = authService;
        this.passwordCredentialService = passwordCredentialService;
    }

    public PageResult<Map<String, Object>> users(String keyword, Integer status, Integer pageNo, Integer pageSize) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .orderByAsc(SysUser::getStatus).orderByDesc(SysUser::getCreateTime);
        if (status != null) wrapper.eq(SysUser::getStatus, status);
        if (StringUtils.hasText(keyword)) {
            String value = keyword.trim();
            wrapper.and(w -> w.like(SysUser::getUsername, value)
                    .or().like(SysUser::getRealName, value)
                    .or().like(SysUser::getPhone, value));
        }
        List<Map<String, Object>> all = userMapper.selectList(wrapper).stream().map(this::userMap).toList();
        return page(all, pageNo, pageSize);
    }

    public Map<String, Object> user(Long userId) {
        return userMap(requireUser(userId));
    }

    @Transactional
    public void changeUserStatus(Long userId, Object rawStatus, String reason, SysUser operator) {
        Integer status = normalizeUserStatus(rawStatus);
        if (Objects.equals(userId, operator.getId()) && status == 0) throw new BusinessException("不能停用自己的账号");
        if (status == 0) lockPlatformAdministratorMutex();
        SysUser user = requireUserForUpdate(userId);
        if (status == 0 && isEffectivePlatformAdministrator(user)
                && activePlatformAdministratorCount() <= 1) {
            throw new BusinessException("不能停用最后一个可用的平台管理员");
        }
        user.setStatus(status);
        user.setUpdateTime(LocalDateTime.now());
        requireSingleWrite(userMapper.updateById(user), "账号状态已变化，请刷新后重试");
        if (status == 0) {
            // Account disablement invalidates every project-scoped responsibility,
            // including direct-user approval configuration and pending seal tasks.
            // The release remains in this transaction, so a failure also rolls the
            // status write back instead of leaving a half-disabled account.
            List<SysUserProject> memberships = userProjectMapper.selectList(
                    new LambdaQueryWrapper<SysUserProject>().eq(SysUserProject::getUserId, userId));
            if (memberships != null) {
                memberships.stream().map(SysUserProject::getProjectId)
                        .filter(Objects::nonNull).distinct()
                        .forEach(projectId -> responsibilityReleaseService.releaseAll(projectId, userId));
            }
        }
        authService.logout(userId);
        authService.repeatLogoutAfterCommit(userId);
        record(operator, status == 1 ? "ENABLE_USER" : "DISABLE_USER", "SYS_USER", userId,
                (status == 1 ? "启用账号" : "停用账号") + reasonSuffix(reason));
    }

    @Transactional
    public void resetPassword(Long userId, String newPassword, SysUser operator) {
        SysUser user = requireUser(userId);
        authService.changePassword(user, newPassword);
        authService.repeatLogoutAfterCommit(userId);
        record(operator, "RESET_PASSWORD", "SYS_USER", userId, "管理员重置账号密码");
    }

    @Transactional
    public void assignAccess(Long userId, RegistrationReviewRequest request, SysUser operator) {
        throw BusinessException.of(410,
                "旧版整包授权接口已停用，请使用平台角色接口和项目角色分配接口，并先完成责任影响预览");
    }

    public List<SystemRole> roles() {
        List<SystemRole> roles = roleMapper.selectList(new LambdaQueryWrapper<SystemRole>()
                .and(wrapper -> wrapper.eq(SystemRole::getScopeType, "PROJECT")
                        .or().eq(SystemRole::getRoleCode, ProjectPermissionService.ROLE_PLATFORM_ADMIN)
                        .eq(SystemRole::getScopeType, "PLATFORM"))
                .orderByAsc(SystemRole::getScopeType).orderByAsc(SystemRole::getRoleCode));
        roles.forEach(role -> {
            role.setMenuIds(roleMapper.selectMenuIds(role.getId()));
            role.setPermissionIds(roleMapper.selectPermissionIds(role.getId()));
            role.setBusinessModuleCodes(roleBusinessModuleMapper.selectModuleCodesByRoleId(role.getId()));
        });
        return roles;
    }

    @Transactional
    public SystemRole saveRole(Long id, RoleSaveRequest request, SysUser operator) {
        String normalizedRoleName = trim(request.getRoleName());
        if (!StringUtils.hasText(normalizedRoleName)) {
            throw new BusinessException("角色名称不能为空");
        }
        // Serialize role definition writes across application instances so the
        // following name check cannot be bypassed by concurrent create requests.
        lockPlatformAdministratorMutex();
        SystemRole role = id == null ? new SystemRole() : roleMapper.selectByIdForUpdate(id);
        if (id != null && role == null) throw BusinessException.notFound("角色不存在");
        Long duplicateName = roleMapper.selectCount(new LambdaQueryWrapper<SystemRole>()
                .apply("LOWER(TRIM(role_name)) = LOWER({0})", normalizedRoleName)
                .ne(id != null, SystemRole::getId, id));
        if (duplicateName != null && duplicateName > 0) {
            throw BusinessException.of(409, "角色名称已存在，请使用其他名称");
        }
        String previousRoleCode = role.getRoleCode();
        String previousScopeType = role.getScopeType();
        String scopeType = id == null ? "PROJECT" : role.getScopeType();
        if (id != null && isProtectedPlatformAdministratorRole(role)) {
            scopeType = "PLATFORM";
        }
        String code = resolveRoleCode(id, request.getRoleCode(), role, scopeType);
        if (id != null && Integer.valueOf(1).equals(role.getBuiltin()) && !code.equals(role.getRoleCode())) {
            throw new BusinessException("内置角色编码不能修改");
        }
        if (id != null && StringUtils.hasText(request.getScopeType())
                && !request.getScopeType().trim().equalsIgnoreCase(previousScopeType)) {
            if (isRoleAssigned(role.getId(), previousScopeType, previousRoleCode)) {
                throw BusinessException.of(409, "已分配给用户的角色不能修改编码或作用域");
            }
            throw new BusinessException("已存在角色的作用域不能修改");
        }
        if (id != null && Integer.valueOf(1).equals(role.getBuiltin())
                && !Objects.equals(role.getScopeType(), scopeType)) {
            throw new BusinessException("内置角色范围不能修改");
        }
        if (id != null
                && (!Objects.equals(previousRoleCode, code)
                || !Objects.equals(previousScopeType, scopeType))
                && isRoleAssigned(role.getId(), previousScopeType, previousRoleCode)) {
            throw BusinessException.of(409, "已分配给用户的角色不能修改编码或作用域");
        }
        int enabled = request.getEnabled() == null
                ? (id == null || role.getEnabled() == null ? 1 : role.getEnabled())
                : request.getEnabled();
        if (isProtectedPlatformAdministratorRole(role)) {
            if (!"PLATFORM".equals(scopeType)) {
                throw new BusinessException("内置平台管理员角色范围不能修改");
            }
            if (enabled != 1) {
                throw new BusinessException("内置平台管理员角色不能停用");
            }
        }
        if (isProtectedProjectManagerRole(role) && enabled != 1) {
            throw new BusinessException("项目经理角色不能停用");
        }
        if (Set.of(ProjectPermissionService.ROLE_ELECTRICIAN,
                ProjectPermissionService.ROLE_SAFETY_OFFICER).contains(code) && enabled != 1) {
            throw new BusinessException("巡检闭环业务角色不能停用");
        }
        if (!"PLATFORM".equals(scopeType) && !"PROJECT".equals(scopeType)) {
            throw new BusinessException("角色范围只支持 PLATFORM 或 PROJECT");
        }
        if ("PLATFORM".equals(scopeType) && !ProjectPermissionService.ROLE_PLATFORM_ADMIN.equals(code)) {
            throw new BusinessException("仅系统管理员可作为平台角色");
        }
        if (ProjectPermissionService.ROLE_PLATFORM_ADMIN.equals(code) && !"PLATFORM".equals(scopeType)) {
            throw new BusinessException("PLATFORM_ADMIN 只能作为平台角色");
        }
        Long duplicate = roleMapper.selectCount(new LambdaQueryWrapper<SystemRole>()
                .eq(SystemRole::getRoleCode, code)
                .eq(SystemRole::getScopeType, scopeType)
                .ne(id != null, SystemRole::getId, id));
        if (duplicate != null && duplicate > 0) throw new BusinessException("角色编码已存在");
        boolean projectManagerRole = isProtectedProjectManagerRole(role)
                || ("PROJECT".equals(scopeType) && ProjectPermissionService.ROLE_PROJECT_ADMIN.equals(code));
        boolean authorizationSupplied = request.getMenuIds() != null
                || request.getPermissionIds() != null
                || request.getBusinessModuleCodes() != null;
        Set<String> businessModuleCodes = authorizationSupplied || id == null
                ? resolveBusinessModuleCodes(role, request.getBusinessModuleCodes(), request.getMenuIds())
                : new LinkedHashSet<>(safeList(roleBusinessModuleMapper.selectModuleCodesByRoleId(role.getId())));
        List<Long> normalizedMenuIds = authorizationSupplied || id == null
                ? normalizeRoleMenuIds(request.getMenuIds(), businessModuleCodes)
                : safeList(roleMapper.selectMenuIds(role.getId()));
        List<Long> normalizedPermissionIds = authorizationSupplied || id == null
                ? normalizeRolePermissionIds(request.getPermissionIds())
                : safeList(roleMapper.selectPermissionIds(role.getId()));
        if (authorizationSupplied || id == null) {
            validateRolePermissions(scopeType, normalizedPermissionIds);
            validateRoleMenus(scopeType, normalizedMenuIds);
            validateMenuHierarchy(normalizedMenuIds, false);
            validatePermissionsMatchMenus(normalizedPermissionIds, normalizedMenuIds,
                    businessModuleCodes, false);
        }
        role.setRoleName(normalizedRoleName);
        role.setRoleCode(code);
        role.setDescription(trim(request.getDescription()));
        role.setScopeType(scopeType);
        role.setProjectManagerRole(projectManagerRole ? 1 : 0);
        role.setEnabled(enabled);
        role.setBuiltin(role.getBuiltin() == null ? 0 : role.getBuiltin());
        role.setDeleted(0);
        role.setUpdateTime(LocalDateTime.now());
        if (role.getId() == null) {
            role.setCreateTime(LocalDateTime.now());
            requireSingleWrite(roleMapper.insert(role), "角色保存失败，请重试");
        } else {
            requireSingleWrite(roleMapper.updateById(role), "角色状态已变化，请刷新后重试");
        }
        if (authorizationSupplied || id == null) {
            replaceRoleMenus(role.getId(), normalizedMenuIds, businessModuleCodes);
            replaceRolePermissions(role.getId(), normalizedPermissionIds);
        }
        Set<Long> affectedUserIds = affectedRoleUserIds(role.getId(), previousScopeType, previousRoleCode);
        affectedUserIds.addAll(affectedRoleUserIds(role.getId(), role.getScopeType(), role.getRoleCode()));
        logoutUsers(affectedUserIds);
        record(operator, "SAVE_ROLE", "SYS_ROLE", role.getId(), "保存角色" + code);
        role.setMenuIds(roleMapper.selectMenuIds(role.getId()));
        role.setPermissionIds(roleMapper.selectPermissionIds(role.getId()));
        role.setBusinessModuleCodes(roleBusinessModuleMapper.selectModuleCodesByRoleId(role.getId()));
        return role;
    }

    @Transactional
    public void deleteRole(Long roleId, SysUser operator) {
        SystemRole role = roleMapper.selectByIdForUpdate(roleId);
        if (role == null) throw BusinessException.notFound("角色不存在");
        if (isProtectedPlatformAdministratorRole(role)) {
            throw new BusinessException("内置平台管理员角色不能删除");
        }
        List<Long> assignedUserIds = userMapper.selectUserIdsByRoleId(roleId);
        List<Long> projectUserIds = "PROJECT".equalsIgnoreCase(role.getScopeType())
                ? userProjectRoleMapper.selectUserIdsByRoleId(role.getId()) : List.of();
        if ((assignedUserIds != null && !assignedUserIds.isEmpty())
                || (projectUserIds != null && !projectUserIds.isEmpty())) {
            throw BusinessException.of(409, "角色仍有用户使用，不能删除");
        }
        roleMapper.deleteMenus(roleId);
        roleMapper.deletePermissions(roleId);
        roleBusinessModuleMapper.delete(new LambdaQueryWrapper<SystemRoleBusinessModule>()
                .eq(SystemRoleBusinessModule::getRoleId, roleId));
        if (roleMapper.deleteById(roleId) != 1) {
            throw BusinessException.of(409, "角色状态已变化，请刷新后重试");
        }
        record(operator, "DELETE_ROLE", "SYS_ROLE", roleId, "删除角色" + role.getRoleCode());
    }

    @Transactional
    public void updateRolePermissions(Long roleId, List<Long> permissionIds, List<Long> menuIds,
                                      List<String> requestedBusinessModuleCodes, SysUser operator) {
        SystemRole role = roleMapper.selectByIdForUpdate(roleId);
        if (role == null) throw BusinessException.notFound("角色不存在");
        requireRoleAuthorizationEditable(role);
        Set<String> businessModuleCodes = resolveBusinessModuleCodes(role, requestedBusinessModuleCodes, menuIds);
        List<Long> normalizedMenuIds = normalizeRoleMenuIds(menuIds, businessModuleCodes);
        List<Long> normalizedPermissionIds = normalizeRolePermissionIds(permissionIds);
        validateRolePermissions(role.getScopeType(), normalizedPermissionIds);
        validateRoleMenus(role.getScopeType(), normalizedMenuIds);
        validateMenuHierarchy(normalizedMenuIds, false);
        validatePermissionsMatchMenus(normalizedPermissionIds, normalizedMenuIds, businessModuleCodes, false);
        replaceRolePermissions(roleId, normalizedPermissionIds);
        replaceRoleMenus(roleId, normalizedMenuIds, businessModuleCodes);
        logoutUsers(affectedRoleUserIds(role.getId(), role.getScopeType(), role.getRoleCode()));
        record(operator, "UPDATE_ROLE_PERMISSIONS", "SYS_ROLE", roleId, "更新角色业务模块、菜单和操作权限");
    }

    @Transactional
    public void updateRoleMenus(Long roleId, List<Long> menuIds, List<String> requestedBusinessModuleCodes,
                                SysUser operator) {
        SystemRole role = roleMapper.selectByIdForUpdate(roleId);
        if (role == null) throw BusinessException.notFound("角色不存在");
        requireRoleAuthorizationEditable(role);
        Set<String> businessModuleCodes = resolveBusinessModuleCodes(role, requestedBusinessModuleCodes, menuIds);
        List<Long> normalizedMenuIds = normalizeRoleMenuIds(menuIds, businessModuleCodes);
        validateRoleMenus(role.getScopeType(), normalizedMenuIds);
        validateMenuHierarchy(normalizedMenuIds, true);

        List<Long> currentPermissionIds = normalizeRolePermissionIds(roleMapper.selectPermissionIds(roleId));
        List<Long> retainedPermissionIds = filterPermissionsForMenus(
                currentPermissionIds, normalizedMenuIds, businessModuleCodes, true);
        replaceRoleMenus(roleId, normalizedMenuIds, businessModuleCodes);
        replaceRolePermissions(roleId, retainedPermissionIds);
        logoutUsers(affectedRoleUserIds(role.getId(), role.getScopeType(), role.getRoleCode()));
        record(operator, "UPDATE_ROLE_MENUS", "SYS_ROLE", roleId, "更新角色菜单并清理失效操作权限");
    }

    @Transactional
    public void updateRoleOperationPermissions(Long roleId, List<Long> permissionIds, SysUser operator) {
        SystemRole role = roleMapper.selectByIdForUpdate(roleId);
        if (role == null) throw BusinessException.notFound("角色不存在");
        requireRoleAuthorizationEditable(role);
        List<Long> menuIds = safeList(roleMapper.selectMenuIds(roleId));
        Set<String> businessModuleCodes = new LinkedHashSet<>(
                safeList(roleBusinessModuleMapper.selectModuleCodesByRoleId(roleId)));
        List<Long> normalizedPermissionIds = normalizeRolePermissionIds(permissionIds);
        validateRolePermissions(role.getScopeType(), normalizedPermissionIds);
        validatePermissionsMatchMenus(normalizedPermissionIds, menuIds, businessModuleCodes, true);
        replaceRolePermissions(roleId, normalizedPermissionIds);
        logoutUsers(affectedRoleUserIds(role.getId(), role.getScopeType(), role.getRoleCode()));
        record(operator, "UPDATE_ROLE_OPERATION_PERMISSIONS", "SYS_ROLE", roleId, "更新角色操作权限");
    }

    /** 保留给旧服务调用的兼容入口；未传模块时保持该角色现有模块选择。 */
    @Transactional
    public void updateRolePermissions(Long roleId, List<Long> permissionIds, List<Long> menuIds, SysUser operator) {
        updateRolePermissions(roleId, permissionIds, menuIds, null, operator);
    }

    @Transactional
    public void updateUserRoles(Long userId, List<Long> roleIds, SysUser operator) {
        lockPlatformAdministratorMutex();
        SysUser targetUser = requireUser(userId);
        if (Objects.equals(userId, operator.getId())) {
            throw new BusinessException("不能通过此接口修改自己的平台角色");
        }
        boolean removingPlatformAdmin = isPlatformAdministrator(userId)
                && !containsPlatformAdministratorRole(roleIds);
        if (removingPlatformAdmin && isEffectivePlatformAdministrator(targetUser)
                && activePlatformAdministratorCount() <= 1) {
            throw new BusinessException("不能移除最后一个可用平台管理员的角色");
        }
        userMapper.deleteUserRoles(userId);
        if (roleIds != null) {
            for (Long roleId : roleIds.stream().filter(Objects::nonNull).distinct().toList()) {
                SystemRole role = roleMapper.selectById(roleId);
                if (role == null || !"PLATFORM".equalsIgnoreCase(role.getScopeType())
                        || !ProjectPermissionService.ROLE_PLATFORM_ADMIN.equals(role.getRoleCode())
                        || Integer.valueOf(0).equals(role.getEnabled())) {
                    throw new BusinessException("仅可分配系统管理员平台角色：" + roleId);
                }
                userMapper.insertUserRole(userId, roleId);
            }
        }
        projectPermissionService.clearUserProjectsCache(userId);
        authService.logout(userId);
        authService.repeatLogoutAfterCommit(userId);
        record(operator, "UPDATE_USER_ROLES", "SYS_USER", userId, "更新用户平台角色");
    }

    public List<SystemMenu> menus() {
        return menuMapper.selectList(new LambdaQueryWrapper<SystemMenu>()
                .orderByAsc(SystemMenu::getSortOrder).orderByAsc(SystemMenu::getId));
    }

    public List<SystemMenu> menus(String clientType) {
        LambdaQueryWrapper<SystemMenu> wrapper = new LambdaQueryWrapper<SystemMenu>()
                .orderByAsc(SystemMenu::getSortOrder).orderByAsc(SystemMenu::getId);
        if (StringUtils.hasText(clientType)) wrapper.eq(SystemMenu::getClientType, clientType.trim().toUpperCase(Locale.ROOT));
        return menuMapper.selectList(wrapper);
    }

    @Transactional
    public SystemMenu updateMenuStatus(Long id, Object rawStatus, SysUser operator) {
        SystemMenu menu = menuMapper.selectById(id);
        if (menu == null) throw BusinessException.notFound("菜单不存在");
        rejectRetiredMenu(menu.getMenuCode());
        List<Long> affectedRoleIds = roleMapper.selectRoleIdsByMenuId(id);
        int enabled = normalizeEnabled(rawStatus);
        menu.setEnabled(enabled);
        menu.setUpdateTime(LocalDateTime.now());
        requireSingleWrite(menuMapper.updateById(menu), "菜单状态已变化，请刷新后重试");
        logoutUsersForRoles(affectedRoleIds);
        record(operator, "UPDATE_MENU_STATUS", "SYS_MENU", id, enabled == 1 ? "启用菜单" : "停用菜单");
        return menu;
    }

    @Transactional
    public SystemMenu saveMenu(Long id, SystemMenu request, SysUser operator) {
        SystemMenu menu = id == null ? new SystemMenu() : menuMapper.selectById(id);
        if (id != null && menu == null) throw BusinessException.notFound("菜单不存在");
        List<Long> affectedRoleIds = id == null ? List.of() : roleMapper.selectRoleIdsByMenuId(id);
        if (id != null && !StringUtils.hasText(request.getMenuCode())
                && !StringUtils.hasText(request.getMenuName())) {
            Object status = request.getEnabled() == null ? request.getStatus() : request.getEnabled();
            return updateMenuStatus(id, status, operator);
        }
        if (!StringUtils.hasText(request.getMenuCode()) || !StringUtils.hasText(request.getMenuName())) {
            throw new BusinessException("菜单编码和名称不能为空");
        }
        rejectRetiredMenu(request.getMenuCode());
        if (id != null && Integer.valueOf(1).equals(menu.getBuiltin())
                && !menu.getMenuCode().equals(request.getMenuCode())) {
            throw new BusinessException("内置菜单编码不能修改");
        }
        request.setId(id);
        request.setMenuCode(request.getMenuCode().trim().toUpperCase(Locale.ROOT));
        request.setEnabled(request.getEnabled() == null ? 1 : request.getEnabled());
        request.setVisible(request.getVisible() == null ? 1 : request.getVisible());
        request.setBuiltin(menu.getBuiltin() == null ? 0 : menu.getBuiltin());
        request.setDeleted(0);
        request.setUpdateTime(LocalDateTime.now());
        if (id == null) {
            request.setCreateTime(LocalDateTime.now());
            requireSingleWrite(menuMapper.insert(request), "菜单保存失败，请重试");
        } else {
            requireSingleWrite(menuMapper.updateById(request), "菜单状态已变化，请刷新后重试");
        }
        logoutUsersForRoles(affectedRoleIds);
        record(operator, "SAVE_MENU", "SYS_MENU", request.getId(), "保存菜单" + request.getMenuCode());
        return request;
    }

    public List<SystemPermission> permissions() {
        return permissionMapper.selectList(new LambdaQueryWrapper<SystemPermission>()
                .orderByAsc(SystemPermission::getModuleCode).orderByAsc(SystemPermission::getPermissionCode));
    }

    @Transactional
    public SystemPermission savePermission(Long id, SystemPermission request, SysUser operator) {
        SystemPermission permission = id == null ? new SystemPermission() : permissionMapper.selectById(id);
        if (id != null && permission == null) throw BusinessException.notFound("权限不存在");
        List<Long> affectedRoleIds = id == null ? List.of() : roleMapper.selectRoleIdsByPermissionId(id);
        if (!StringUtils.hasText(request.getPermissionCode()) || !StringUtils.hasText(request.getPermissionName())) {
            throw new BusinessException("权限编码和名称不能为空");
        }
        rejectRetiredPermission(request.getPermissionCode());
        if (id != null && Integer.valueOf(1).equals(permission.getBuiltin())
                && !permission.getPermissionCode().equals(request.getPermissionCode())) {
            throw new BusinessException("内置权限编码不能修改");
        }
        request.setId(id);
        request.setPermissionCode(request.getPermissionCode().trim().toLowerCase(Locale.ROOT));
        request.setEnabled(request.getEnabled() == null ? 1 : request.getEnabled());
        request.setBuiltin(permission.getBuiltin() == null ? 0 : permission.getBuiltin());
        request.setDeleted(0);
        request.setUpdateTime(LocalDateTime.now());
        if (id == null) {
            request.setCreateTime(LocalDateTime.now());
            requireSingleWrite(permissionMapper.insert(request), "操作权限保存失败，请重试");
        } else {
            requireSingleWrite(permissionMapper.updateById(request), "操作权限状态已变化，请刷新后重试");
        }
        logoutUsersForRoles(affectedRoleIds);
        record(operator, "SAVE_PERMISSION", "SYS_PERMISSION", request.getId(),
                "保存权限" + request.getPermissionCode());
        return request;
    }

    public PageResult<OperationLog> auditLogs(String keyword, Integer pageNo, Integer pageSize) {
        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<OperationLog>()
                .orderByDesc(OperationLog::getCreateTime);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(OperationLog::getUsername, keyword.trim())
                    .or().like(OperationLog::getOperationType, keyword.trim())
                    .or().like(OperationLog::getOperationDesc, keyword.trim()));
        }
        return page(operationLogMapper.selectList(wrapper), pageNo, pageSize);
    }

    private void insertProjectAssignment(Long userId, RegistrationReviewRequest.ProjectAssignment assignment) {
        if (assignment == null || assignment.getProjectId() == null) throw new BusinessException("项目授权不能为空");
        if (assignment.getRoleIds() == null || assignment.getRoleIds().isEmpty()) {
            throw new BusinessException("项目至少需要分配一个项目角色");
        }
        List<SystemRole> roles = new ArrayList<>();
        for (Long roleId : assignment.getRoleIds().stream().filter(Objects::nonNull).distinct().toList()) {
            SystemRole role = roleMapper.selectById(roleId);
            if (role == null || !"PROJECT".equalsIgnoreCase(role.getScopeType())
                    || Integer.valueOf(0).equals(role.getEnabled())) {
                throw new BusinessException("项目角色不存在：" + roleId);
            }
            roles.add(role);
        }
        SysUserProject relation = new SysUserProject();
        relation.setUserId(userId);
        relation.setProjectId(assignment.getProjectId());
        relation.setProjectRoleCode(roles.stream()
                .sorted(java.util.Comparator.comparing((SystemRole role) -> Integer.valueOf(1).equals(role.getProjectManagerRole())).reversed()
                        .thenComparing(SystemRole::getRoleCode))
                .map(SystemRole::getRoleCode).findFirst().orElse(null));
        relation.setInspectionPermissionTemplateId(null);
        relation.setStatus("ACTIVE");
        relation.setCreateTime(LocalDateTime.now());
        relation.setUpdateTime(LocalDateTime.now());
        requireSingleWrite(userProjectMapper.insert(relation), "项目授权保存失败，请重试");
        for (SystemRole role : roles) {
            SysUserProjectRole roleRelation = new SysUserProjectRole();
            roleRelation.setUserId(userId);
            roleRelation.setProjectId(assignment.getProjectId());
            roleRelation.setRoleId(role.getId());
            roleRelation.setCreateTime(LocalDateTime.now());
            requireSingleWrite(userProjectRoleMapper.insert(roleRelation), "项目角色保存失败，请重试");
        }
    }

    private SysUser requireUser(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) throw BusinessException.notFound("用户不存在");
        return user;
    }

    private SysUser requireUserForUpdate(Long userId) {
        SysUser user = userId == null ? null : userMapper.selectByIdForUpdate(userId);
        if (user == null) throw BusinessException.notFound("用户不存在");
        return user;
    }

    private Map<String, Object> userMap(SysUser user) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", user.getId());
        result.put("username", user.getUsername());
        result.put("realName", user.getRealName());
        result.put("phone", user.getPhone());
        result.put("email", user.getEmail());
        result.put("status", user.getStatus());
        result.put("passwordLoginEnabled", user.getPasswordLoginEnabled());
        result.put("passwordResetRequired", user.getPasswordResetRequired());
        result.put("roles", userMapper.selectRoleCodesByUserId(user.getId()));
        result.put("projectRoles", managementProjectRoles(user.getId()));
        result.put("createTime", user.getCreateTime());
        return result;
    }

    /**
     * 用户管理需要展示每一个项目以及该项目下的全部角色，不能只使用旧字段
     * projectRoleCode。登录态仍由 selectUserProjectRoles() 过滤 ACTIVE 项目，
     * 避免管理页展示规则影响实际可访问项目范围。
     */
    private List<UserProjectRoleVO> managementProjectRoles(Long userId) {
        List<UserProjectRoleVO> assignments = userProjectMapper.selectUserProjectRolesForManagement(userId);
        if (assignments == null || assignments.isEmpty()) return List.of();
        assignments.forEach(assignment -> {
            if (assignment.getProjectId() == null) {
                assignment.setProjectRoles(List.of());
                return;
            }
            List<SystemRole> assignedRoles = userProjectRoleMapper.selectAssignedRoles(userId, assignment.getProjectId());
            assignment.setProjectRoles(assignedRoles == null ? List.of() : assignedRoles);
        });
        return assignments;
    }

    private <T> PageResult<T> page(List<T> all, Integer pageNo, Integer pageSize) {
        int currentPage = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int size = pageSize == null ? 20 : Math.max(1, Math.min(pageSize, 100));
        int from = Math.min((currentPage - 1) * size, all.size());
        int to = Math.min(from + size, all.size());
        return PageResult.of(currentPage, size, (long) all.size(), new ArrayList<>(all.subList(from, to)));
    }

    private void record(SysUser operator, String type, String businessType, Long businessId, String description) {
        OperationLog log = new OperationLog();
        log.setUserId(operator.getId());
        log.setUsername(operator.getUsername());
        log.setOperationType(type);
        log.setOperationDesc(description);
        log.setBusinessType(businessType);
        log.setBusinessId(businessId);
        log.setCreateTime(LocalDateTime.now());
        operationLogMapper.insert(log);
    }

    private String reasonSuffix(String reason) {
        return StringUtils.hasText(reason) ? "，原因：" + reason.trim() : "";
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String resolveRoleCode(Long id, String requestedCode, SystemRole role, String scopeType) {
        if (StringUtils.hasText(requestedCode)) {
            String normalized = requestedCode.trim().toUpperCase(Locale.ROOT);
            if (!ROLE_CODE_PATTERN.matcher(normalized).matches()) {
                throw new BusinessException("角色编码格式不正确");
            }
            return normalized;
        }
        if (id != null) {
            if (!StringUtils.hasText(role.getRoleCode())) {
                throw BusinessException.of(409, "角色编码缺失，请联系系统管理员处理");
            }
            return role.getRoleCode();
        }
        for (int attempt = 0; attempt < GENERATED_ROLE_CODE_ATTEMPTS; attempt++) {
            String generated = GENERATED_ROLE_CODE_PREFIX
                    + UUID.randomUUID().toString().replace("-", "")
                    .substring(0, 12).toUpperCase(Locale.ROOT);
            Long duplicate = roleMapper.selectCount(new LambdaQueryWrapper<SystemRole>()
                    .eq(SystemRole::getRoleCode, generated)
                    .eq(SystemRole::getScopeType, scopeType));
            if (duplicate == null || duplicate == 0) {
                return generated;
            }
        }
        throw BusinessException.of(409, "角色编码生成失败，请重试");
    }

    private Integer normalizeUserStatus(Object value) {
        if (value instanceof Number number) {
            int status = number.intValue();
            if (status == 0 || status == 1) return status;
        }
        if (value != null) {
            String status = String.valueOf(value).trim().toUpperCase(Locale.ROOT);
            if ("ACTIVE".equals(status) || "ENABLED".equals(status) || "1".equals(status)) return 1;
            if ("DISABLED".equals(status) || "0".equals(status)) return 0;
        }
        throw new BusinessException("账号状态只支持 ACTIVE/DISABLED 或 1/0");
    }

    private int normalizeEnabled(Object value) {
        if (value instanceof Number number) return number.intValue() == 1 ? 1 : 0;
        String status = value == null ? "" : String.valueOf(value).trim().toUpperCase(Locale.ROOT);
        return "ACTIVE".equals(status) || "ENABLED".equals(status) || "1".equals(status)
                || "TRUE".equals(status) ? 1 : 0;
    }

    private boolean isPlatformAdministrator(Long userId) {
        List<String> roles = userMapper.selectRoleCodesByUserId(userId);
        return roles != null && roles.contains(ProjectPermissionService.ROLE_PLATFORM_ADMIN);
    }

    private boolean containsPlatformAdministratorRole(List<Long> roleIds) {
        if (roleIds == null) return false;
        for (Long roleId : roleIds) {
            SystemRole role = roleMapper.selectById(roleId);
            if (role != null
                    && ProjectPermissionService.ROLE_PLATFORM_ADMIN.equals(role.getRoleCode())
                    && "PLATFORM".equalsIgnoreCase(role.getScopeType())
                    && !Integer.valueOf(0).equals(role.getEnabled())
                    && !Integer.valueOf(1).equals(role.getDeleted())) {
                return true;
            }
        }
        return false;
    }

    private long activePlatformAdministratorCount() {
        Long count = userMapper.countActivePlatformAdministrators();
        return count == null ? 0 : count;
    }

    private SystemRole lockPlatformAdministratorMutex() {
        SystemRole role = roleMapper.selectPlatformAdministratorForUpdate();
        if (role == null) {
            throw BusinessException.of(409, "平台管理员角色不存在，禁止执行可能导致系统锁定的操作");
        }
        return role;
    }

    private boolean isProtectedPlatformAdministratorRole(SystemRole role) {
        return role != null
                && ProjectPermissionService.ROLE_PLATFORM_ADMIN.equals(role.getRoleCode())
                && "PLATFORM".equalsIgnoreCase(role.getScopeType())
                && Integer.valueOf(1).equals(role.getBuiltin());
    }

    private boolean isProtectedProjectManagerRole(SystemRole role) {
        return role != null && "PROJECT".equalsIgnoreCase(role.getScopeType())
                && Integer.valueOf(1).equals(role.getProjectManagerRole());
    }

    private boolean isRoleAssigned(Long roleId, String scopeType, String roleCode) {
        List<Long> directUserIds = userMapper.selectUserIdsByRoleId(roleId);
        if (directUserIds != null && !directUserIds.isEmpty()) {
            return true;
        }
        if ("PROJECT".equalsIgnoreCase(scopeType) && roleId != null) {
            List<Long> projectUserIds = userProjectRoleMapper.selectUserIdsByRoleId(roleId);
            return projectUserIds != null && !projectUserIds.isEmpty();
        }
        return false;
    }

    /**
     * 角色页面只传正式业务模块编码；底层同步对应客户端菜单，以兼容现有 Web / 小程序路由。
     * 旧调用没有模块字段时不意外清空已有开关，新角色则从旧菜单数组推导一次。
     */
    private Set<String> resolveBusinessModuleCodes(SystemRole role, List<String> requestedCodes,
                                                   List<Long> requestedMenuIds) {
        if (requestedCodes != null) {
            Set<String> result = new LinkedHashSet<>();
            for (String code : requestedCodes) {
                if (!BusinessModuleCodes.isBusinessModule(code)) {
                    throw new BusinessException("不支持的业务模块：" + code);
                }
                result.add(code.trim().toUpperCase(Locale.ROOT));
            }
            return result;
        }
        if (role != null && role.getId() != null) {
            List<String> existing = roleBusinessModuleMapper.selectModuleCodesByRoleId(role.getId());
            if (existing != null && !existing.isEmpty()) {
                return existing.stream().filter(BusinessModuleCodes::isBusinessModule)
                        .map(code -> code.trim().toUpperCase(Locale.ROOT))
                        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            }
        }
        return deriveBusinessModuleCodesFromMenuIds(requestedMenuIds);
    }

    private Set<String> deriveBusinessModuleCodesFromMenuIds(List<Long> menuIds) {
        if (menuIds == null || menuIds.isEmpty()) return new LinkedHashSet<>();
        Set<Long> requested = new LinkedHashSet<>(menuIds.stream().filter(Objects::nonNull).toList());
        Set<String> result = new LinkedHashSet<>();
        for (SystemMenu menu : menuMapper.selectList(new LambdaQueryWrapper<SystemMenu>()
                .in(SystemMenu::getId, requested))) {
            String moduleCode = BusinessModuleCodes.fromMenuCode(menu.getMenuCode());
            if (moduleCode != null) result.add(moduleCode);
        }
        return result;
    }

    private List<Long> normalizeRoleMenuIds(List<Long> requestedMenuIds, Set<String> businessModuleCodes) {
        Set<Long> normalized = new LinkedHashSet<>();
        if (requestedMenuIds != null) {
            normalized.addAll(requestedMenuIds.stream().filter(Objects::nonNull).toList());
        }
        List<SystemMenu> businessMenus = menuMapper.selectList(new LambdaQueryWrapper<SystemMenu>()
                .in(SystemMenu::getMenuCode, List.of(
                        "WEB_SITE_ACCESS",
                        "WEB_DOCUMENT", "MINI_DOCUMENT", "WEB_INSPECTION", "MINI_INSPECTION",
                        "WEB_QUALITY", "MINI_QUALITY")));
        for (SystemMenu menu : businessMenus) {
            String moduleCode = BusinessModuleCodes.fromMenuCode(menu.getMenuCode());
            if (moduleCode == null) continue;
            normalized.remove(menu.getId());
            if (businessModuleCodes.contains(moduleCode)) {
                normalized.add(menu.getId());
            }
        }
        return List.copyOf(normalized);
    }

    private List<Long> normalizeRolePermissionIds(List<Long> requestedPermissionIds) {
        Set<Long> normalizedIds = new LinkedHashSet<>();
        Map<String, SystemPermission> selectedByCode = new LinkedHashMap<>();
        for (Long permissionId : safeList(requestedPermissionIds).stream()
                .filter(Objects::nonNull).distinct().toList()) {
            SystemPermission permission = permissionMapper.selectById(permissionId);
            if (permission == null || Integer.valueOf(1).equals(permission.getDeleted())
                    || Integer.valueOf(0).equals(permission.getEnabled())) {
                throw new BusinessException("操作权限不存在或已停用：" + permissionId);
            }
            normalizedIds.add(permissionId);
            selectedByCode.put(normalizePermissionCode(permission.getPermissionCode()), permission);
        }

        Set<String> requiredCodes = new LinkedHashSet<>();
        for (String code : selectedByCode.keySet()) {
            if ("BOX_VIEW".equals(code)) requiredCodes.add("INSPECTION.VIEW");
            if ("BOX_MANAGE".equals(code)) {
                requiredCodes.add("BOX_VIEW");
                requiredCodes.add("INSPECTION.VIEW");
                requiredCodes.add("INSPECTION.MANAGE");
            }
            if (Set.of("BOX_QR_MANAGE", "BOX_PUBLIC_ACCESS").contains(code)) {
                requiredCodes.add("BOX_VIEW");
                requiredCodes.add("INSPECTION.VIEW");
            }
            if ("INSPECTION_DAILY_SUBMIT".equals(code)) requiredCodes.add("INSPECTION.SUBMIT");
            if (Set.of("INSPECTION.RECTIFY", "INSPECTION.REVIEW").contains(code)) {
                requiredCodes.add("INSPECTION.VIEW");
            }
            if (Set.of("INSPECTION_RECORD_VIEW", "SUMMARY_VIEW").contains(code)) {
                requiredCodes.add("INSPECTION.VIEW");
            }
            if ("SUMMARY_EXPORT".equals(code)) {
                requiredCodes.add("SUMMARY_VIEW");
                requiredCodes.add("INSPECTION.VIEW");
                requiredCodes.add("INSPECTION.EXPORT");
            }
            if (Set.of("DOCUMENT.UPLOAD", "DOCUMENT.MANAGE").contains(code)) {
                requiredCodes.add("DOCUMENT.VIEW");
            }
            if (Set.of("SEAL.MANAGE", "SEAL.EXPORT").contains(code)) {
                requiredCodes.add("SEAL.VIEW");
            }
            if (Set.of("QUALITY.MANAGE", "QUALITY.RECTIFY", "QUALITY.REVIEW").contains(code)) {
                requiredCodes.add("QUALITY.VIEW");
            }
            if (Set.of("SITE_ACCESS.MANAGE", "SITE_ACCESS.EXPORT").contains(code)) {
                requiredCodes.add("SITE_ACCESS.VIEW");
            }
        }
        if (!requiredCodes.isEmpty()) {
            Map<String, SystemPermission> enabledByCode = new LinkedHashMap<>();
            permissionMapper.selectList(new LambdaQueryWrapper<SystemPermission>()
                    .eq(SystemPermission::getEnabled, 1)).forEach(permission ->
                    enabledByCode.put(normalizePermissionCode(permission.getPermissionCode()), permission));
            for (String code : requiredCodes) {
                SystemPermission required = enabledByCode.get(code);
                if (required == null || Integer.valueOf(1).equals(required.getDeleted())) {
                    throw new BusinessException("操作权限前置项不存在或已停用：" + code);
                }
                normalizedIds.add(required.getId());
            }
        }
        return List.copyOf(normalizedIds);
    }

    private void validateMenuHierarchy(List<Long> menuIds, boolean strictTabs) {
        List<SystemMenu> allMenus = menuMapper.selectList(new LambdaQueryWrapper<SystemMenu>()
                .eq(SystemMenu::getEnabled, 1));
        Map<Long, SystemMenu> menuById = new LinkedHashMap<>();
        Set<String> catalogCodes = new LinkedHashSet<>();
        for (SystemMenu menu : allMenus) {
            if (Integer.valueOf(1).equals(menu.getDeleted())) continue;
            menuById.put(menu.getId(), menu);
            catalogCodes.add(normalizeMenuCode(menu.getMenuCode()));
        }
        Set<Long> selectedIds = new LinkedHashSet<>(safeList(menuIds));
        Set<String> selectedCodes = selectedIds.stream().map(menuById::get).filter(Objects::nonNull)
                .map(menu -> normalizeMenuCode(menu.getMenuCode()))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        for (Long menuId : selectedIds) {
            SystemMenu menu = menuById.get(menuId);
            if (menu == null) throw new BusinessException("菜单不存在或已停用：" + menuId);
            if (menu.getParentId() != null && !selectedIds.contains(menu.getParentId())) {
                throw BusinessException.of(400, "子菜单必须同时选择上级菜单：" + menu.getMenuCode());
            }
        }
        if (!strictTabs) return;
        requireSelectedPageWhenCatalogExists("WEB_DOCUMENT",
                Set.of("DOCUMENT_LIBRARY", "DOCUMENT_SEAL", "DOCUMENT_RECYCLE"), selectedCodes, catalogCodes);
        requireSelectedPageWhenCatalogExists("WEB_INSPECTION",
                Set.of("INSPECTION_LEDGER", "INSPECTION_RECORDS", "INSPECTION_RECTIFICATIONS"), selectedCodes, catalogCodes);
        requireSelectedPageWhenCatalogExists("WEB_QUALITY",
                Set.of("QUALITY_ISSUES", "QUALITY_DOCUMENTS"), selectedCodes, catalogCodes);
        requireSelectedPageWhenCatalogExists("WEB_SITE_ACCESS",
                Set.of("SITE_VISITOR"), selectedCodes, catalogCodes);
        if (selectedCodes.contains("WEB_SYSTEM")
                && catalogCodes.stream().anyMatch(code -> code.startsWith("SYSTEM_"))
                && selectedCodes.stream().noneMatch(code -> code.startsWith("SYSTEM_"))) {
            throw BusinessException.of(400, "系统管理至少需要选择一个页面");
        }
    }

    private void requireSelectedPageWhenCatalogExists(String parentCode, Set<String> pageCodes,
                                                       Set<String> selectedCodes, Set<String> catalogCodes) {
        if (selectedCodes.contains(parentCode)
                && pageCodes.stream().anyMatch(catalogCodes::contains)
                && pageCodes.stream().noneMatch(selectedCodes::contains)) {
            throw BusinessException.of(400, parentCode + " 至少需要选择一个页面");
        }
    }

    private void validatePermissionsMatchMenus(List<Long> permissionIds, List<Long> menuIds,
                                               Set<String> businessModuleCodes, boolean strictTabs) {
        Set<String> selectedMenuCodes = selectedMenuCodes(menuIds);
        Set<String> catalogMenuCodes = menuMapper.selectList(new LambdaQueryWrapper<SystemMenu>()
                        .eq(SystemMenu::getEnabled, 1)).stream()
                .filter(menu -> !Integer.valueOf(1).equals(menu.getDeleted()))
                .map(menu -> normalizeMenuCode(menu.getMenuCode()))
                .collect(java.util.stream.Collectors.toSet());
        Set<String> normalizedModules = safeList(new ArrayList<>(businessModuleCodes)).stream()
                .map(this::normalizeMenuCode).collect(java.util.stream.Collectors.toSet());
        for (Long permissionId : safeList(permissionIds)) {
            SystemPermission permission = permissionMapper.selectById(permissionId);
            if (permission == null || !permissionAllowedByMenus(permission, selectedMenuCodes,
                    normalizedModules, catalogMenuCodes, strictTabs)) {
                String code = permission == null ? String.valueOf(permissionId) : permission.getPermissionCode();
                throw BusinessException.of(400, "操作权限不属于已分配菜单：" + code);
            }
        }
    }

    private List<Long> filterPermissionsForMenus(List<Long> permissionIds, List<Long> menuIds,
                                                 Set<String> businessModuleCodes, boolean strictTabs) {
        Set<String> selectedMenuCodes = selectedMenuCodes(menuIds);
        Set<String> catalogMenuCodes = menuMapper.selectList(new LambdaQueryWrapper<SystemMenu>()
                        .eq(SystemMenu::getEnabled, 1)).stream()
                .filter(menu -> !Integer.valueOf(1).equals(menu.getDeleted()))
                .map(menu -> normalizeMenuCode(menu.getMenuCode()))
                .collect(java.util.stream.Collectors.toSet());
        Set<String> normalizedModules = businessModuleCodes.stream().map(this::normalizeMenuCode)
                .collect(java.util.stream.Collectors.toSet());
        List<Long> retained = new ArrayList<>();
        for (Long permissionId : safeList(permissionIds)) {
            SystemPermission permission = permissionMapper.selectById(permissionId);
            if (permission != null && permissionAllowedByMenus(permission, selectedMenuCodes,
                    normalizedModules, catalogMenuCodes, strictTabs)) {
                retained.add(permissionId);
            }
        }
        return retained.stream().distinct().toList();
    }

    private boolean permissionAllowedByMenus(SystemPermission permission, Set<String> selectedMenuCodes,
                                             Set<String> businessModuleCodes, Set<String> catalogMenuCodes,
                                             boolean strictTabs) {
        String module = normalizeMenuCode(permission.getModuleCode());
        String code = normalizePermissionCode(permission.getPermissionCode());
        if ("WEB_DOCUMENT".equals(module)) {
            if (!businessModuleCodes.contains("DOCUMENT")) return false;
            if (!strictTabs || !catalogHasAny(catalogMenuCodes,
                    "DOCUMENT_LIBRARY", "DOCUMENT_SEAL", "DOCUMENT_RECYCLE")) return true;
            if (Set.of("SEAL.VIEW", "SEAL.MANAGE", "SEAL.EXPORT").contains(code)) {
                return selectedMenuCodes.contains("DOCUMENT_SEAL");
            }
            if ("DOCUMENT.UPLOAD".equals(code)) return selectedMenuCodes.contains("DOCUMENT_LIBRARY");
            return selectedMenuCodes.contains("DOCUMENT_LIBRARY") || selectedMenuCodes.contains("DOCUMENT_RECYCLE");
        }
        if ("WEB_INSPECTION".equals(module)) {
            if (!businessModuleCodes.contains("INSPECTION")) return false;
            if (!strictTabs || !catalogHasAny(catalogMenuCodes,
                    "INSPECTION_LEDGER", "INSPECTION_RECORDS", "INSPECTION_RECTIFICATIONS")) return true;
            if (INSPECTION_LEDGER_PERMISSION_CODES.contains(code) || "INSPECTION.MANAGE".equals(code)) {
                return selectedMenuCodes.contains("INSPECTION_LEDGER");
            }
            if (INSPECTION_RECORD_PERMISSION_CODES.contains(code)
                    || Set.of("INSPECTION.SUBMIT", "INSPECTION.EXPORT").contains(code)) {
                return selectedMenuCodes.contains("INSPECTION_RECORDS");
            }
            if (INSPECTION_RECTIFICATION_PERMISSION_CODES.contains(code)) {
                return selectedMenuCodes.contains("INSPECTION_RECTIFICATIONS");
            }
            return selectedMenuCodes.contains("INSPECTION_LEDGER")
                    || selectedMenuCodes.contains("INSPECTION_RECORDS")
                    || selectedMenuCodes.contains("INSPECTION_RECTIFICATIONS");
        }
        if ("WEB_QUALITY".equals(module)) {
            if (!businessModuleCodes.contains("QUALITY")) return false;
            if (!strictTabs || !catalogHasAny(catalogMenuCodes, "QUALITY_ISSUES", "QUALITY_DOCUMENTS")) return true;
            if (Set.of("QUALITY.RECTIFY", "QUALITY.REVIEW").contains(code)) {
                return selectedMenuCodes.contains("QUALITY_ISSUES");
            }
            return selectedMenuCodes.contains("QUALITY_ISSUES") || selectedMenuCodes.contains("QUALITY_DOCUMENTS");
        }
        if ("WEB_SITE_ACCESS".equals(module)) {
            if (!businessModuleCodes.contains("SITE_ACCESS")) return false;
            if (!strictTabs || !catalogHasAny(catalogMenuCodes, "SITE_VISITOR")) return true;
            return selectedMenuCodes.contains("SITE_VISITOR");
        }
        if (StringUtils.hasText(module)) {
            return selectedMenuCodes.contains(module);
        }
        return false;
    }

    private Set<String> selectedMenuCodes(List<Long> menuIds) {
        Set<String> result = new LinkedHashSet<>();
        for (Long menuId : safeList(menuIds)) {
            SystemMenu menu = menuMapper.selectById(menuId);
            if (menu != null && !Integer.valueOf(1).equals(menu.getDeleted())
                    && !Integer.valueOf(0).equals(menu.getEnabled())) {
                result.add(normalizeMenuCode(menu.getMenuCode()));
            }
        }
        return result;
    }

    private boolean catalogHasAny(Set<String> codes, String... expected) {
        return java.util.Arrays.stream(expected).anyMatch(codes::contains);
    }

    private String normalizePermissionCode(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeMenuCode(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }

    private void replaceRoleMenus(Long roleId, List<Long> menuIds, Set<String> businessModuleCodes) {
        roleMapper.deleteMenus(roleId);
        safeList(menuIds).stream().filter(Objects::nonNull).distinct()
                .forEach(menuId -> roleMapper.insertMenu(roleId, menuId));
        replaceBusinessModules(roleId, businessModuleCodes);
    }

    private void replaceRolePermissions(Long roleId, List<Long> permissionIds) {
        roleMapper.deletePermissions(roleId);
        safeList(permissionIds).stream().filter(Objects::nonNull).distinct()
                .forEach(permissionId -> roleMapper.insertPermission(roleId, permissionId));
    }

    private void requireRoleAuthorizationEditable(SystemRole role) {
        if (isProtectedPlatformAdministratorRole(role)) {
            throw new BusinessException("内置平台管理员权限由系统保护，不能在业务界面修改");
        }
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private void replaceBusinessModules(Long roleId, Set<String> moduleCodes) {
        roleBusinessModuleMapper.delete(new LambdaQueryWrapper<SystemRoleBusinessModule>()
                .eq(SystemRoleBusinessModule::getRoleId, roleId));
        for (String moduleCode : moduleCodes) {
            SystemRoleBusinessModule relation = new SystemRoleBusinessModule();
            relation.setRoleId(roleId);
            relation.setModuleCode(moduleCode);
            relation.setCreateTime(LocalDateTime.now());
            requireSingleWrite(roleBusinessModuleMapper.insert(relation), "角色业务模块保存失败，请重试");
        }
    }

    private void requireSingleWrite(int affectedRows, String message) {
        if (affectedRows != 1) {
            throw BusinessException.of(409, message);
        }
    }

    private void validateRolePermissions(String scopeType, List<Long> permissionIds) {
        if (permissionIds == null || permissionIds.isEmpty()) return;
        for (Long permissionId : permissionIds.stream().filter(Objects::nonNull).distinct().toList()) {
            SystemPermission permission = permissionMapper.selectById(permissionId);
            if (permission == null || Integer.valueOf(1).equals(permission.getDeleted())
                    || Integer.valueOf(0).equals(permission.getEnabled())) {
                throw new BusinessException("操作权限不存在或已停用：" + permissionId);
            }
            String permissionCode = permission.getPermissionCode();
            rejectRetiredPermission(permissionCode);
            if ("PROJECT".equalsIgnoreCase(scopeType) && StringUtils.hasText(permissionCode)) {
                if (permissionCode.startsWith("system.")) {
                    throw BusinessException.of(400, "项目角色不能配置平台管理权限：" + permissionCode);
                }
            }
        }
    }

    private void validateRoleMenus(String scopeType, List<Long> menuIds) {
        if (menuIds == null || menuIds.isEmpty()) return;
        for (Long menuId : menuIds.stream().filter(Objects::nonNull).distinct().toList()) {
            SystemMenu menu = menuMapper.selectById(menuId);
            if (menu == null || Integer.valueOf(1).equals(menu.getDeleted())
                    || Integer.valueOf(0).equals(menu.getEnabled())) {
                throw new BusinessException("菜单不存在或已停用：" + menuId);
            }
            String menuCode = menu.getMenuCode();
            rejectRetiredMenu(menuCode);
            if (!"PROJECT".equalsIgnoreCase(scopeType)) continue;
            if (StringUtils.hasText(menuCode) && menuCode.startsWith("SYSTEM_")) {
                throw BusinessException.of(400, "项目角色不能配置平台管理菜单：" + menuCode);
            }
            if ("WEB_SYSTEM".equals(menuCode)) {
                throw BusinessException.of(400, "项目角色不能配置平台管理入口");
            }
        }
    }

    private void rejectRetiredMenu(String menuCode) {
        if (RETIRED_PROJECT_MEMBER_MENU.equalsIgnoreCase(trim(menuCode))) {
            throw BusinessException.of(400, "项目成员与权限菜单已退役，请使用注册审核或用户管理分配项目与角色");
        }
    }

    private void rejectRetiredPermission(String permissionCode) {
        if (RETIRED_PROJECT_MEMBER_PERMISSION.equalsIgnoreCase(trim(permissionCode))) {
            throw BusinessException.of(400, "项目成员管理权限已退役，请使用注册审核或用户管理分配项目与角色");
        }
    }

    private boolean isEffectivePlatformAdministrator(SysUser user) {
        return user != null
                && Integer.valueOf(1).equals(user.getStatus())
                && Integer.valueOf(0).equals(user.getDeleted())
                && Integer.valueOf(1).equals(user.getPasswordLoginEnabled())
                && Integer.valueOf(0).equals(user.getPasswordResetRequired())
                && passwordCredentialService.isBcrypt(user.getPassword())
                && isPlatformAdministrator(user.getId());
    }

    private Set<Long> affectedRoleUserIds(Long roleId, String scopeType, String roleCode) {
        Set<Long> result = new LinkedHashSet<>();
        List<Long> globalUserIds = userMapper.selectUserIdsByRoleId(roleId);
        if (globalUserIds != null) result.addAll(globalUserIds);
        if ("PROJECT".equalsIgnoreCase(scopeType) && roleId != null) {
            List<Long> projectUserIds = userProjectRoleMapper.selectUserIdsByRoleId(roleId);
            if (projectUserIds != null) result.addAll(projectUserIds);
        }
        return result;
    }

    private void logoutUsersForRoles(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) return;
        Set<Long> affectedUserIds = new LinkedHashSet<>();
        for (Long roleId : roleIds.stream().filter(Objects::nonNull).distinct().toList()) {
            SystemRole role = roleMapper.selectById(roleId);
            if (role != null) {
                affectedUserIds.addAll(affectedRoleUserIds(
                        role.getId(), role.getScopeType(), role.getRoleCode()));
            }
        }
        logoutUsers(affectedUserIds);
    }

    private void logoutUsers(Set<Long> userIds) {
        userIds.stream().filter(Objects::nonNull).forEach(userId -> {
            projectPermissionService.clearUserProjectsCache(userId);
            authService.logout(userId);
            authService.repeatLogoutAfterCommit(userId);
        });
    }
}
