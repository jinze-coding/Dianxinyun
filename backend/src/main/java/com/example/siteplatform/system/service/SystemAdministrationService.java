package com.example.siteplatform.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.auth.service.PasswordCredentialService;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.common.PageResult;
import com.example.siteplatform.log.entity.OperationLog;
import com.example.siteplatform.log.mapper.OperationLogMapper;
import com.example.siteplatform.project.entity.SysUserProject;
import com.example.siteplatform.project.mapper.SysUserProjectMapper;
import com.example.siteplatform.project.service.InspectionPermissionTemplateService;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.registration.dto.RegistrationReviewRequest;
import com.example.siteplatform.system.constant.SystemPermissionCodes;
import com.example.siteplatform.system.dto.RoleSaveRequest;
import com.example.siteplatform.system.entity.SystemMenu;
import com.example.siteplatform.system.entity.SystemPermission;
import com.example.siteplatform.system.entity.SystemRole;
import com.example.siteplatform.system.mapper.SystemMenuMapper;
import com.example.siteplatform.system.mapper.SystemPermissionMapper;
import com.example.siteplatform.system.mapper.SystemRoleMapper;
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

@Service
public class SystemAdministrationService {

    private final SysUserMapper userMapper;
    private final SysUserProjectMapper userProjectMapper;
    private final SystemRoleMapper roleMapper;
    private final SystemMenuMapper menuMapper;
    private final SystemPermissionMapper permissionMapper;
    private final OperationLogMapper operationLogMapper;
    private final InspectionPermissionTemplateService inspectionTemplateService;
    private final ProjectPermissionService projectPermissionService;
    private final AuthService authService;
    private final PasswordCredentialService passwordCredentialService;

    public SystemAdministrationService(SysUserMapper userMapper, SysUserProjectMapper userProjectMapper,
                                       SystemRoleMapper roleMapper, SystemMenuMapper menuMapper,
                                       SystemPermissionMapper permissionMapper, OperationLogMapper operationLogMapper,
                                       InspectionPermissionTemplateService inspectionTemplateService,
                                       ProjectPermissionService projectPermissionService, AuthService authService,
                                       PasswordCredentialService passwordCredentialService) {
        this.userMapper = userMapper;
        this.userProjectMapper = userProjectMapper;
        this.roleMapper = roleMapper;
        this.menuMapper = menuMapper;
        this.permissionMapper = permissionMapper;
        this.operationLogMapper = operationLogMapper;
        this.inspectionTemplateService = inspectionTemplateService;
        this.projectPermissionService = projectPermissionService;
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
        SysUser user = requireUser(userId);
        if (status == 0 && isEffectivePlatformAdministrator(user)
                && activePlatformAdministratorCount() <= 1) {
            throw new BusinessException("不能停用最后一个可用的平台管理员");
        }
        user.setStatus(status);
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
        authService.logout(userId);
        record(operator, status == 1 ? "ENABLE_USER" : "DISABLE_USER", "SYS_USER", userId,
                (status == 1 ? "启用账号" : "停用账号") + reasonSuffix(reason));
    }

    @Transactional
    public void resetPassword(Long userId, String newPassword, SysUser operator) {
        SysUser user = requireUser(userId);
        authService.changePassword(user, newPassword);
        record(operator, "RESET_PASSWORD", "SYS_USER", userId, "管理员重置账号密码");
    }

    @Transactional
    public void assignAccess(Long userId, RegistrationReviewRequest request, SysUser operator) {
        lockPlatformAdministratorMutex();
        SysUser targetUser = requireUser(userId);
        if (Objects.equals(userId, operator.getId())) {
            throw new BusinessException("不能通过此接口修改自己的角色和项目权限");
        }
        boolean removingPlatformAdmin = isPlatformAdministrator(userId)
                && !containsPlatformAdministratorRole(request == null ? null : request.getRoleIds());
        if (removingPlatformAdmin && isEffectivePlatformAdministrator(targetUser)
                && activePlatformAdministratorCount() <= 1) {
            throw new BusinessException("不能移除最后一个可用平台管理员的角色");
        }
        userMapper.deleteUserRoles(userId);
        if (request != null && request.getRoleIds() != null) {
            for (Long roleId : request.getRoleIds().stream().distinct().toList()) {
                SystemRole role = roleMapper.selectById(roleId);
                if (role == null || !"PLATFORM".equalsIgnoreCase(role.getScopeType())
                        || Integer.valueOf(0).equals(role.getEnabled())) {
                    throw new BusinessException("平台角色不存在：" + roleId);
                }
                userMapper.insertUserRole(userId, roleId);
            }
        }
        if (userMapper.selectRoleCodesByUserId(userId).isEmpty()) {
            SystemRole userRole = roleMapper.selectOne(new LambdaQueryWrapper<SystemRole>()
                    .eq(SystemRole::getRoleCode, ProjectPermissionService.ROLE_USER)
                    .eq(SystemRole::getScopeType, "PLATFORM").last("LIMIT 1"));
            if (userRole != null) userMapper.insertUserRole(userId, userRole.getId());
        }
        userProjectMapper.delete(new LambdaQueryWrapper<SysUserProject>().eq(SysUserProject::getUserId, userId));
        if (request != null && request.getProjectAssignments() != null) {
            for (RegistrationReviewRequest.ProjectAssignment assignment : request.getProjectAssignments()) {
                insertProjectAssignment(userId, assignment);
            }
        }
        projectPermissionService.clearUserProjectsCache(userId);
        authService.logout(userId);
        record(operator, "ASSIGN_USER_ACCESS", "SYS_USER", userId, "更新用户角色和项目授权");
    }

    public List<SystemRole> roles() {
        List<SystemRole> roles = roleMapper.selectList(new LambdaQueryWrapper<SystemRole>()
                .orderByAsc(SystemRole::getScopeType).orderByAsc(SystemRole::getRoleCode));
        roles.forEach(role -> {
            role.setMenuIds(roleMapper.selectMenuIds(role.getId()));
            role.setPermissionIds(roleMapper.selectPermissionIds(role.getId()));
        });
        return roles;
    }

    @Transactional
    public SystemRole saveRole(Long id, RoleSaveRequest request, SysUser operator) {
        String code = request.getRoleCode().trim().toUpperCase(Locale.ROOT);
        SystemRole role = id == null ? new SystemRole() : roleMapper.selectByIdForUpdate(id);
        if (id != null && role == null) throw BusinessException.notFound("角色不存在");
        String previousRoleCode = role.getRoleCode();
        String previousScopeType = role.getScopeType();
        if (id != null && Integer.valueOf(1).equals(role.getBuiltin()) && !code.equals(role.getRoleCode())) {
            throw new BusinessException("内置角色编码不能修改");
        }
        String scopeType = StringUtils.hasText(request.getScopeType())
                ? request.getScopeType().trim().toUpperCase(Locale.ROOT)
                : (role.getScopeType() == null ? "PLATFORM" : role.getScopeType());
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
        int enabled = request.getEnabled() == null ? 1 : request.getEnabled();
        if (isProtectedPlatformAdministratorRole(role)) {
            if (!"PLATFORM".equals(scopeType)) {
                throw new BusinessException("内置平台管理员角色范围不能修改");
            }
            if (enabled != 1) {
                throw new BusinessException("内置平台管理员角色不能停用");
            }
        }
        if (ProjectPermissionService.ROLE_PLATFORM_ADMIN.equals(code) && !"PLATFORM".equals(scopeType)) {
            throw new BusinessException("PLATFORM_ADMIN 只能作为平台角色");
        }
        Long duplicate = roleMapper.selectCount(new LambdaQueryWrapper<SystemRole>()
                .eq(SystemRole::getRoleCode, code)
                .eq(SystemRole::getScopeType, scopeType)
                .ne(id != null, SystemRole::getId, id));
        if (duplicate != null && duplicate > 0) throw new BusinessException("角色编码已存在");
        validateRolePermissions(scopeType, request.getPermissionIds());
        validateRoleMenus(scopeType, request.getMenuIds());
        role.setRoleName(request.getRoleName().trim());
        role.setRoleCode(code);
        role.setDescription(trim(request.getDescription()));
        role.setScopeType(scopeType);
        role.setEnabled(enabled);
        role.setBuiltin(role.getBuiltin() == null ? 0 : role.getBuiltin());
        role.setDeleted(0);
        role.setUpdateTime(LocalDateTime.now());
        if (role.getId() == null) {
            role.setCreateTime(LocalDateTime.now());
            roleMapper.insert(role);
        } else roleMapper.updateById(role);
        roleMapper.deleteMenus(role.getId());
        if (request.getMenuIds() != null) {
            request.getMenuIds().stream().distinct().forEach(menuId -> roleMapper.insertMenu(role.getId(), menuId));
        }
        roleMapper.deletePermissions(role.getId());
        if (request.getPermissionIds() != null) {
            request.getPermissionIds().stream().distinct()
                    .forEach(permissionId -> roleMapper.insertPermission(role.getId(), permissionId));
        }
        Set<Long> affectedUserIds = affectedRoleUserIds(role.getId(), previousScopeType, previousRoleCode);
        affectedUserIds.addAll(affectedRoleUserIds(role.getId(), role.getScopeType(), role.getRoleCode()));
        logoutUsers(affectedUserIds);
        record(operator, "SAVE_ROLE", "SYS_ROLE", role.getId(), "保存角色" + code);
        role.setMenuIds(roleMapper.selectMenuIds(role.getId()));
        role.setPermissionIds(roleMapper.selectPermissionIds(role.getId()));
        return role;
    }

    @Transactional
    public void deleteRole(Long roleId, SysUser operator) {
        SystemRole role = roleMapper.selectByIdForUpdate(roleId);
        if (role == null) throw BusinessException.notFound("角色不存在");
        if (isProtectedPlatformAdministratorRole(role)) {
            throw new BusinessException("内置平台管理员角色不能删除");
        }
        if (Integer.valueOf(1).equals(role.getBuiltin())) {
            throw new BusinessException("内置角色不能删除");
        }
        List<Long> assignedUserIds = userMapper.selectUserIdsByRoleId(roleId);
        List<Long> projectUserIds = "PROJECT".equalsIgnoreCase(role.getScopeType())
                ? userProjectMapper.selectUserIdsByProjectRoleCode(role.getRoleCode()) : List.of();
        if ((assignedUserIds != null && !assignedUserIds.isEmpty())
                || (projectUserIds != null && !projectUserIds.isEmpty())) {
            throw BusinessException.of(409, "角色仍有用户使用，不能删除");
        }
        roleMapper.deleteMenus(roleId);
        roleMapper.deletePermissions(roleId);
        if (roleMapper.deleteById(roleId) != 1) {
            throw BusinessException.of(409, "角色状态已变化，请刷新后重试");
        }
        record(operator, "DELETE_ROLE", "SYS_ROLE", roleId, "删除角色" + role.getRoleCode());
    }

    @Transactional
    public void updateRolePermissions(Long roleId, List<Long> permissionIds, List<Long> menuIds, SysUser operator) {
        SystemRole role = roleMapper.selectByIdForUpdate(roleId);
        if (role == null) throw BusinessException.notFound("角色不存在");
        validateRolePermissions(role.getScopeType(), permissionIds);
        validateRoleMenus(role.getScopeType(), menuIds);
        roleMapper.deletePermissions(roleId);
        if (permissionIds != null) {
            permissionIds.stream().distinct().forEach(id -> roleMapper.insertPermission(roleId, id));
        }
        if (menuIds != null) {
            roleMapper.deleteMenus(roleId);
            menuIds.stream().distinct().forEach(id -> roleMapper.insertMenu(roleId, id));
        }
        logoutUsers(affectedRoleUserIds(role.getId(), role.getScopeType(), role.getRoleCode()));
        record(operator, "UPDATE_ROLE_PERMISSIONS", "SYS_ROLE", roleId, "更新角色操作权限");
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
                        || Integer.valueOf(0).equals(role.getEnabled())) {
                    throw new BusinessException("平台角色不存在：" + roleId);
                }
                userMapper.insertUserRole(userId, roleId);
            }
        }
        if (userMapper.selectRoleCodesByUserId(userId).isEmpty()) {
            SystemRole userRole = roleMapper.selectOne(new LambdaQueryWrapper<SystemRole>()
                    .eq(SystemRole::getRoleCode, ProjectPermissionService.ROLE_USER)
                    .eq(SystemRole::getScopeType, "PLATFORM")
                    .eq(SystemRole::getEnabled, 1)
                    .last("LIMIT 1"));
            if (userRole == null) throw new BusinessException("系统默认用户角色不存在");
            userMapper.insertUserRole(userId, userRole.getId());
        }
        projectPermissionService.clearUserProjectsCache(userId);
        authService.logout(userId);
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
        List<Long> affectedRoleIds = roleMapper.selectRoleIdsByMenuId(id);
        int enabled = normalizeEnabled(rawStatus);
        menu.setEnabled(enabled);
        menu.setUpdateTime(LocalDateTime.now());
        menuMapper.updateById(menu);
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
            menuMapper.insert(request);
        } else menuMapper.updateById(request);
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
            permissionMapper.insert(request);
        } else permissionMapper.updateById(request);
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
        String roleCode = ProjectPermissionService.ROLE_USER;
        if (assignment.getRoleIds() != null && !assignment.getRoleIds().isEmpty()) {
            if (assignment.getRoleIds().size() > 1) {
                throw new BusinessException("同一项目只能分配一个项目角色");
            }
            SystemRole role = roleMapper.selectById(assignment.getRoleIds().get(0));
            if (role == null || !"PROJECT".equalsIgnoreCase(role.getScopeType())
                    || Integer.valueOf(0).equals(role.getEnabled())) {
                throw new BusinessException("项目角色不存在：" + assignment.getRoleIds().get(0));
            }
            roleCode = role.getRoleCode();
        }
        SysUserProject relation = new SysUserProject();
        relation.setUserId(userId);
        relation.setProjectId(assignment.getProjectId());
        relation.setProjectRoleCode(roleCode);
        relation.setInspectionPermissionTemplateId(inspectionTemplateService.defaultTemplateIdForRole(roleCode));
        relation.setStatus("ACTIVE");
        relation.setCreateTime(LocalDateTime.now());
        relation.setUpdateTime(LocalDateTime.now());
        userProjectMapper.insert(relation);
    }

    private SysUser requireUser(Long userId) {
        SysUser user = userMapper.selectById(userId);
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
        result.put("projectRoles", userProjectMapper.selectUserProjectRoles(user.getId()));
        result.put("createTime", user.getCreateTime());
        return result;
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

    private boolean isRoleAssigned(Long roleId, String scopeType, String roleCode) {
        List<Long> directUserIds = userMapper.selectUserIdsByRoleId(roleId);
        if (directUserIds != null && !directUserIds.isEmpty()) {
            return true;
        }
        if ("PROJECT".equalsIgnoreCase(scopeType) && StringUtils.hasText(roleCode)) {
            List<Long> projectUserIds = userProjectMapper.selectUserIdsByProjectRoleCode(roleCode);
            return projectUserIds != null && !projectUserIds.isEmpty();
        }
        return false;
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
            if ("PROJECT".equalsIgnoreCase(scopeType)
                    && StringUtils.hasText(permissionCode)
                    && permissionCode.startsWith("system.")
                    && !SystemPermissionCodes.PROJECT_MANAGE.equals(permissionCode)) {
                throw BusinessException.of(400, "项目角色不能配置平台管理权限：" + permissionCode);
            }
        }
    }

    private void validateRoleMenus(String scopeType, List<Long> menuIds) {
        if (!"PROJECT".equalsIgnoreCase(scopeType) || menuIds == null || menuIds.isEmpty()) return;
        for (Long menuId : menuIds.stream().filter(Objects::nonNull).distinct().toList()) {
            SystemMenu menu = menuMapper.selectById(menuId);
            if (menu == null || Integer.valueOf(1).equals(menu.getDeleted())
                    || Integer.valueOf(0).equals(menu.getEnabled())) {
                throw new BusinessException("菜单不存在或已停用：" + menuId);
            }
            String menuCode = menu.getMenuCode();
            if (StringUtils.hasText(menuCode)
                    && menuCode.startsWith("SYSTEM_")
                    && !"SYSTEM_PROJECT".equals(menuCode)) {
                throw BusinessException.of(400, "项目角色不能配置平台管理菜单：" + menuCode);
            }
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
        if ("PROJECT".equalsIgnoreCase(scopeType) && StringUtils.hasText(roleCode)) {
            List<Long> projectUserIds = userProjectMapper.selectUserIdsByProjectRoleCode(roleCode);
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
        userIds.stream().filter(Objects::nonNull).forEach(authService::logout);
    }
}
