package com.example.siteplatform.system.service;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.dto.UserProjectRoleVO;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.auth.service.PasswordCredentialService;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.common.PageResult;
import com.example.siteplatform.log.mapper.OperationLogMapper;
import com.example.siteplatform.project.mapper.SysUserProjectMapper;
import com.example.siteplatform.project.mapper.SysUserProjectRoleMapper;
import com.example.siteplatform.project.service.InspectionPermissionTemplateService;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.system.dto.RoleSaveRequest;
import com.example.siteplatform.system.entity.SystemMenu;
import com.example.siteplatform.system.entity.SystemPermission;
import com.example.siteplatform.system.entity.SystemRole;
import com.example.siteplatform.system.mapper.SystemMenuMapper;
import com.example.siteplatform.system.mapper.SystemPermissionMapper;
import com.example.siteplatform.system.mapper.SystemRoleBusinessModuleMapper;
import com.example.siteplatform.system.mapper.SystemRoleMapper;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemAdministrationServiceSafetyTest {

    @Mock private SysUserMapper userMapper;
    @Mock private SysUserProjectMapper userProjectMapper;
    @Mock private SysUserProjectRoleMapper userProjectRoleMapper;
    @Mock private SystemRoleMapper roleMapper;
    @Mock private SystemMenuMapper menuMapper;
    @Mock private SystemPermissionMapper permissionMapper;
    @Mock private SystemRoleBusinessModuleMapper roleBusinessModuleMapper;
    @Mock private OperationLogMapper operationLogMapper;
    @Mock private InspectionPermissionTemplateService inspectionTemplateService;
    @Mock private ProjectPermissionService projectPermissionService;
    @Mock private AuthService authService;
    @Mock private PasswordCredentialService passwordCredentialService;

    private SystemAdministrationService service;

    @BeforeEach
    void setUp() {
        service = new SystemAdministrationService(
                userMapper, userProjectMapper, roleMapper, menuMapper, permissionMapper,
                operationLogMapper, inspectionTemplateService, projectPermissionService,
                authService, passwordCredentialService);
        ReflectionTestUtils.setField(service, "userProjectRoleMapper", userProjectRoleMapper);
        ReflectionTestUtils.setField(service, "roleBusinessModuleMapper", roleBusinessModuleMapper);
        lenient().when(userMapper.updateById(any(SysUser.class))).thenReturn(1);
        lenient().when(roleMapper.insert(any(SystemRole.class))).thenReturn(1);
        lenient().when(roleMapper.updateById(any(SystemRole.class))).thenReturn(1);
        lenient().when(menuMapper.insert(any(SystemMenu.class))).thenReturn(1);
        lenient().when(menuMapper.updateById(any(SystemMenu.class))).thenReturn(1);
        lenient().when(permissionMapper.insert(any(SystemPermission.class))).thenReturn(1);
        lenient().when(permissionMapper.updateById(any(SystemPermission.class))).thenReturn(1);
        lenient().when(userProjectMapper.insert(any())).thenReturn(1);
        lenient().when(userProjectRoleMapper.insert(any())).thenReturn(1);
        lenient().when(roleBusinessModuleMapper.insert(any())).thenReturn(1);
    }

    @Test
    void builtinPlatformAdministratorCannotChangeScopeOrBeDisabled() {
        SystemRole platformAdmin = platformAdminRole();
        when(roleMapper.selectByIdForUpdate(10L)).thenReturn(platformAdmin);

        RoleSaveRequest scopeChange = roleRequest("PROJECT", 1);
        BusinessException scopeError = assertThrows(BusinessException.class,
                () -> service.saveRole(10L, scopeChange, operator()));
        assertTrue(scopeError.getMessage().contains("不能修改"));

        when(roleMapper.selectByIdForUpdate(10L)).thenReturn(platformAdmin);
        RoleSaveRequest disable = roleRequest("PLATFORM", 0);
        BusinessException disableError = assertThrows(BusinessException.class,
                () -> service.saveRole(10L, disable, operator()));
        assertTrue(disableError.getMessage().contains("不能停用"));
        verify(roleMapper, never()).updateById(any());
    }

    @Test
    void builtinPlatformAdministratorCannotBeDeleted() {
        when(roleMapper.selectByIdForUpdate(10L)).thenReturn(platformAdminRole());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.deleteRole(10L, operator()));

        assertTrue(exception.getMessage().contains("不能删除"));
        verify(roleMapper, never()).deleteById(10L);
    }

    @Test
    void projectRoleInUseCannotBeDeleted() {
        SystemRole projectRole = role(30L, "CUSTOM_REVIEWER", "PROJECT", 0, 1);
        when(roleMapper.selectByIdForUpdate(30L)).thenReturn(projectRole);
        when(userMapper.selectUserIdsByRoleId(30L)).thenReturn(List.of());
        when(userProjectRoleMapper.selectUserIdsByRoleId(30L)).thenReturn(List.of(8L));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.deleteRole(30L, operator()));

        assertEquals(409, exception.getCode());
        verify(roleMapper, never()).deleteById(30L);
    }

    @Test
    void disablingLastUsableAdministratorLocksRoleBeforeCountingAndIsRejected() {
        SystemRole mutex = platformAdminRole();
        SysUser target = effectiveAdmin(2L);
        when(roleMapper.selectPlatformAdministratorForUpdate()).thenReturn(mutex);
        when(userMapper.selectById(2L)).thenReturn(target);
        when(passwordCredentialService.isBcrypt(target.getPassword())).thenReturn(true);
        when(userMapper.selectRoleCodesByUserId(2L)).thenReturn(List.of("PLATFORM_ADMIN"));
        when(userMapper.countActivePlatformAdministrators()).thenReturn(1L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.changeUserStatus(2L, 0, "离职", operator()));

        assertTrue(exception.getMessage().contains("最后一个"));
        InOrder ordered = inOrder(roleMapper, userMapper);
        ordered.verify(roleMapper).selectPlatformAdministratorForUpdate();
        ordered.verify(userMapper).selectById(2L);
        ordered.verify(userMapper).selectRoleCodesByUserId(2L);
        ordered.verify(userMapper).countActivePlatformAdministrators();
        verify(userMapper, never()).updateById(any());
        verify(authService, never()).logout(2L);
    }

    @Test
    void accountStatusWriteFailureReturnsConflictWithoutRevokingSessionsOrAuditingSuccess() {
        SysUser target = ordinaryUser(2L);
        when(userMapper.selectById(2L)).thenReturn(target);
        when(userMapper.updateById(target)).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.changeUserStatus(2L, 1, "恢复使用", operator()));

        assertEquals(409, exception.getCode());
        verify(authService, never()).logout(2L);
        verify(operationLogMapper, never()).insert(any());
    }

    @Test
    void removingLastUsableAdministratorRoleThroughRolesEndpointIsRejected() {
        SystemRole mutex = platformAdminRole();
        SysUser target = effectiveAdmin(2L);
        when(roleMapper.selectPlatformAdministratorForUpdate()).thenReturn(mutex);
        when(userMapper.selectById(2L)).thenReturn(target);
        when(userMapper.selectRoleCodesByUserId(2L)).thenReturn(List.of("PLATFORM_ADMIN"));
        when(passwordCredentialService.isBcrypt(target.getPassword())).thenReturn(true);
        when(userMapper.countActivePlatformAdministrators()).thenReturn(1L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateUserRoles(2L, List.of(), operator()));

        assertTrue(exception.getMessage().contains("最后一个"));
        InOrder ordered = inOrder(roleMapper, userMapper);
        ordered.verify(roleMapper).selectPlatformAdministratorForUpdate();
        ordered.verify(userMapper).countActivePlatformAdministrators();
        verify(userMapper, never()).deleteUserRoles(2L);
    }

    @Test
    void updatingPlatformRolesNeverRewritesProjectAssignments() {
        SystemRole mutex = platformAdminRole();
        SystemRole userRole = role(20L, "PLATFORM_ADMIN", "PLATFORM", 1, 1);
        SysUser target = ordinaryUser(2L);
        when(roleMapper.selectPlatformAdministratorForUpdate()).thenReturn(mutex);
        when(userMapper.selectById(2L)).thenReturn(target);
        when(userMapper.selectRoleCodesByUserId(2L)).thenReturn(List.of("USER"));
        when(roleMapper.selectById(20L)).thenReturn(userRole);

        service.updateUserRoles(2L, List.of(20L), operator());

        verify(userMapper).deleteUserRoles(2L);
        verify(userMapper).insertUserRole(2L, 20L);
        verify(userProjectMapper, never()).delete(any());
        verify(userProjectMapper, never()).insert(any());
        verify(userProjectMapper, never()).updateById(any());
        verify(projectPermissionService).clearUserProjectsCache(2L);
        verify(authService).logout(2L);
    }

    @Test
    void projectRolePermissionChangeRevokesGlobalAndProjectUserSessionsOnce() {
        SystemRole projectRole = role(30L, "CUSTOM_REVIEWER", "PROJECT", 0, 1);
        SystemPermission permission = permission(100L, "document.manage");
        SystemPermission viewPermission = permission(101L, "document.view");
        SystemMenu menu = menu(200L, "WEB_DOCUMENT");
        when(roleMapper.selectByIdForUpdate(30L)).thenReturn(projectRole);
        when(permissionMapper.selectById(100L)).thenReturn(permission);
        when(permissionMapper.selectById(101L)).thenReturn(viewPermission);
        when(permissionMapper.selectList(any())).thenReturn(List.of(permission, viewPermission));
        when(menuMapper.selectById(200L)).thenReturn(menu);
        when(menuMapper.selectList(any())).thenReturn(List.of(menu));
        when(userMapper.selectUserIdsByRoleId(30L)).thenReturn(List.of(2L));
        when(userProjectRoleMapper.selectUserIdsByRoleId(30L)).thenReturn(List.of(2L, 3L));

        service.updateRolePermissions(30L, List.of(100L), List.of(200L), operator());

        verify(authService, times(1)).logout(2L);
        verify(authService, times(1)).logout(3L);
        verify(authService, times(1)).repeatLogoutAfterCommit(2L);
        verify(authService, times(1)).repeatLogoutAfterCommit(3L);
        verify(projectPermissionService, times(1)).clearUserProjectsCache(2L);
        verify(projectPermissionService, times(1)).clearUserProjectsCache(3L);
    }

    @Test
    void editingRoleBasicsPreservesExistingMenusAndPermissions() {
        SystemRole projectRole = role(30L, "CUSTOM_REVIEWER", "PROJECT", 0, 1);
        projectRole.setRoleName("旧名称");
        when(roleMapper.selectByIdForUpdate(30L)).thenReturn(projectRole);
        when(roleMapper.selectMenuIds(30L)).thenReturn(List.of(200L));
        when(roleMapper.selectPermissionIds(30L)).thenReturn(List.of(100L));
        when(roleBusinessModuleMapper.selectModuleCodesByRoleId(30L)).thenReturn(List.of("DOCUMENT"));

        RoleSaveRequest request = new RoleSaveRequest();
        request.setRoleName("新名称");
        request.setScopeType("PROJECT");
        request.setEnabled(1);

        SystemRole saved = service.saveRole(30L, request, operator());

        assertEquals("新名称", saved.getRoleName());
        assertEquals("CUSTOM_REVIEWER", saved.getRoleCode());
        assertEquals(List.of(200L), saved.getMenuIds());
        assertEquals(List.of(100L), saved.getPermissionIds());
        verify(roleMapper, never()).deleteMenus(30L);
        verify(roleMapper, never()).deletePermissions(30L);
    }

    @Test
    void creatingRoleWithoutCodeGeneratesUniqueStableProjectCode() {
        when(roleMapper.insert(any(SystemRole.class))).thenAnswer(invocation -> {
            SystemRole inserted = invocation.getArgument(0);
            inserted.setId(31L);
            return 1;
        });
        when(roleMapper.selectMenuIds(31L)).thenReturn(List.of());
        when(roleMapper.selectPermissionIds(31L)).thenReturn(List.of());
        when(roleBusinessModuleMapper.selectModuleCodesByRoleId(31L)).thenReturn(List.of());
        when(userMapper.selectUserIdsByRoleId(31L)).thenReturn(List.of());
        when(userProjectRoleMapper.selectUserIdsByRoleId(31L)).thenReturn(List.of());

        RoleSaveRequest request = new RoleSaveRequest();
        request.setRoleName("材料查看员");
        request.setScopeType("PROJECT");
        request.setEnabled(1);
        request.setMenuIds(List.of());
        request.setPermissionIds(List.of());
        request.setBusinessModuleCodes(List.of());

        SystemRole saved = service.saveRole(null, request, operator());

        assertTrue(saved.getRoleCode().matches("^ROLE_[A-F0-9]{12}$"));
        assertEquals("PROJECT", saved.getScopeType());
    }

    @Test
    void menuUpdateRemovesPermissionsOutsideSelectedPages() {
        SystemRole projectRole = role(30L, "CUSTOM_REVIEWER", "PROJECT", 0, 1);
        SystemMenu webDocument = menu(200L, "WEB_DOCUMENT");
        SystemMenu miniDocument = menu(201L, "MINI_DOCUMENT");
        SystemMenu library = menu(202L, "DOCUMENT_LIBRARY");
        library.setParentId(200L);
        SystemMenu qualityIssues = menu(203L, "QUALITY_ISSUES");
        SystemPermission documentView = permission(100L, "document.view");
        documentView.setModuleCode("WEB_DOCUMENT");
        SystemPermission qualityView = permission(101L, "quality.view");
        qualityView.setModuleCode("WEB_QUALITY");
        when(roleMapper.selectByIdForUpdate(30L)).thenReturn(projectRole);
        when(roleMapper.selectPermissionIds(30L)).thenReturn(List.of(100L, 101L));
        when(permissionMapper.selectById(100L)).thenReturn(documentView);
        when(permissionMapper.selectById(101L)).thenReturn(qualityView);
        when(menuMapper.selectById(200L)).thenReturn(webDocument);
        when(menuMapper.selectById(201L)).thenReturn(miniDocument);
        when(menuMapper.selectById(202L)).thenReturn(library);
        when(menuMapper.selectList(any())).thenReturn(List.of(webDocument, miniDocument, library, qualityIssues));

        service.updateRoleMenus(30L, List.of(202L), List.of("DOCUMENT"), operator());

        verify(roleMapper).insertPermission(30L, 100L);
        verify(roleMapper, never()).insertPermission(30L, 101L);
        verify(roleMapper).insertMenu(30L, 200L);
        verify(roleMapper).insertMenu(30L, 201L);
        verify(roleMapper).insertMenu(30L, 202L);
    }

    @Test
    void inspectionSummaryExportAddsTechnicalPrerequisites() {
        SystemRole projectRole = role(30L, "CUSTOM_REVIEWER", "PROJECT", 0, 1);
        SystemMenu webInspection = menu(200L, "WEB_INSPECTION");
        SystemMenu miniInspection = menu(201L, "MINI_INSPECTION");
        SystemMenu records = menu(202L, "INSPECTION_RECORDS");
        records.setParentId(200L);
        SystemPermission summaryExport = permission(100L, "SUMMARY_EXPORT");
        SystemPermission summaryView = permission(101L, "SUMMARY_VIEW");
        SystemPermission inspectionView = permission(102L, "inspection.view");
        SystemPermission inspectionExport = permission(103L, "inspection.export");
        List<SystemPermission> permissions = List.of(summaryExport, summaryView, inspectionView, inspectionExport);
        permissions.forEach(item -> item.setModuleCode("WEB_INSPECTION"));
        when(roleMapper.selectByIdForUpdate(30L)).thenReturn(projectRole);
        when(roleMapper.selectMenuIds(30L)).thenReturn(List.of(200L, 201L, 202L));
        when(roleBusinessModuleMapper.selectModuleCodesByRoleId(30L)).thenReturn(List.of("INSPECTION"));
        permissions.forEach(item -> when(permissionMapper.selectById(item.getId())).thenReturn(item));
        when(permissionMapper.selectList(any())).thenReturn(permissions);
        when(menuMapper.selectList(any())).thenReturn(List.of(webInspection, miniInspection, records));
        when(menuMapper.selectById(200L)).thenReturn(webInspection);
        when(menuMapper.selectById(201L)).thenReturn(miniInspection);
        when(menuMapper.selectById(202L)).thenReturn(records);

        service.updateRoleOperationPermissions(30L, List.of(100L), operator());

        verify(roleMapper).insertPermission(30L, 100L);
        verify(roleMapper).insertPermission(30L, 101L);
        verify(roleMapper).insertPermission(30L, 102L);
        verify(roleMapper).insertPermission(30L, 103L);
    }

    @Test
    void inspectionQrPermissionAddsLedgerViewButNotLedgerManage() {
        SystemRole projectRole = role(30L, "CUSTOM_REVIEWER", "PROJECT", 0, 1);
        SystemMenu webInspection = menu(200L, "WEB_INSPECTION");
        SystemMenu miniInspection = menu(201L, "MINI_INSPECTION");
        SystemMenu ledger = menu(202L, "INSPECTION_LEDGER");
        ledger.setParentId(200L);
        SystemPermission qr = permission(100L, "BOX_QR_MANAGE");
        SystemPermission boxView = permission(101L, "BOX_VIEW");
        SystemPermission inspectionView = permission(102L, "inspection.view");
        SystemPermission inspectionManage = permission(103L, "inspection.manage");
        List<SystemPermission> permissions = List.of(qr, boxView, inspectionView, inspectionManage);
        permissions.forEach(item -> item.setModuleCode("WEB_INSPECTION"));
        when(roleMapper.selectByIdForUpdate(30L)).thenReturn(projectRole);
        when(roleMapper.selectMenuIds(30L)).thenReturn(List.of(200L, 201L, 202L));
        when(roleBusinessModuleMapper.selectModuleCodesByRoleId(30L)).thenReturn(List.of("INSPECTION"));
        List.of(qr, boxView, inspectionView)
                .forEach(item -> when(permissionMapper.selectById(item.getId())).thenReturn(item));
        when(permissionMapper.selectList(any())).thenReturn(permissions);
        when(menuMapper.selectList(any())).thenReturn(List.of(webInspection, miniInspection, ledger));
        when(menuMapper.selectById(200L)).thenReturn(webInspection);
        when(menuMapper.selectById(201L)).thenReturn(miniInspection);
        when(menuMapper.selectById(202L)).thenReturn(ledger);

        service.updateRoleOperationPermissions(30L, List.of(100L), operator());

        verify(roleMapper).insertPermission(30L, 100L);
        verify(roleMapper).insertPermission(30L, 101L);
        verify(roleMapper).insertPermission(30L, 102L);
        verify(roleMapper, never()).insertPermission(30L, 103L);
    }

    @Test
    void operationPermissionOutsideSelectedMenuIsRejected() {
        SystemRole projectRole = role(30L, "CUSTOM_REVIEWER", "PROJECT", 0, 1);
        SystemMenu webDocument = menu(200L, "WEB_DOCUMENT");
        SystemMenu library = menu(201L, "DOCUMENT_LIBRARY");
        library.setParentId(200L);
        SystemPermission qualityView = permission(100L, "quality.view");
        qualityView.setModuleCode("WEB_QUALITY");
        when(roleMapper.selectByIdForUpdate(30L)).thenReturn(projectRole);
        when(roleMapper.selectMenuIds(30L)).thenReturn(List.of(200L, 201L));
        when(roleBusinessModuleMapper.selectModuleCodesByRoleId(30L)).thenReturn(List.of("DOCUMENT"));
        when(permissionMapper.selectById(100L)).thenReturn(qualityView);
        when(menuMapper.selectList(any())).thenReturn(List.of(webDocument, library));
        when(menuMapper.selectById(200L)).thenReturn(webDocument);
        when(menuMapper.selectById(201L)).thenReturn(library);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateRoleOperationPermissions(30L, List.of(100L), operator()));

        assertEquals(400, exception.getCode());
        verify(roleMapper, never()).deletePermissions(30L);
        verify(roleMapper, never()).deleteMenus(30L);
    }

    @Test
    void projectRoleCannotReceivePlatformManagementPermission() {
        SystemRole projectRole = role(30L, "CUSTOM_REVIEWER", "PROJECT", 0, 1);
        when(roleMapper.selectByIdForUpdate(30L)).thenReturn(projectRole);
        when(permissionMapper.selectById(100L)).thenReturn(permission(100L, "system.role.manage"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateRolePermissions(30L, List.of(100L), List.of(), operator()));

        assertEquals(400, exception.getCode());
        verify(roleMapper, never()).deletePermissions(30L);
    }

    @Test
    void assignedProjectRoleCannotChangeCodeOrScope() {
        SystemRole projectRole = role(30L, "CUSTOM_REVIEWER", "PROJECT", 0, 1);
        when(roleMapper.selectByIdForUpdate(30L)).thenReturn(projectRole);
        when(userMapper.selectUserIdsByRoleId(30L)).thenReturn(List.of());
        when(userProjectRoleMapper.selectUserIdsByRoleId(30L)).thenReturn(List.of(8L));
        RoleSaveRequest request = new RoleSaveRequest();
        request.setRoleName("新名称");
        request.setRoleCode("CUSTOM_MANAGER");
        request.setScopeType("PLATFORM");
        request.setEnabled(1);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.saveRole(30L, request, operator()));

        assertEquals(409, exception.getCode());
        verify(roleMapper, never()).updateById(any());
    }

    @Test
    void projectRoleCannotReceivePlatformManagementMenu() {
        SystemRole projectRole = role(30L, "CUSTOM_REVIEWER", "PROJECT", 0, 1);
        when(roleMapper.selectByIdForUpdate(30L)).thenReturn(projectRole);
        when(menuMapper.selectById(200L)).thenReturn(menu(200L, "SYSTEM_USER"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateRolePermissions(30L, List.of(), List.of(200L), operator()));

        assertEquals(400, exception.getCode());
        verify(roleMapper, never()).deleteMenus(30L);
    }

    @Test
    void menuAndPermissionChangesRevokeAllUsersOfLinkedRoles() {
        SystemRole platformUserRole = role(20L, "USER", "PLATFORM", 1, 1);
        when(roleMapper.selectRoleIdsByMenuId(5L)).thenReturn(List.of(20L));
        when(roleMapper.selectRoleIdsByPermissionId(6L)).thenReturn(List.of(20L));
        when(roleMapper.selectById(20L)).thenReturn(platformUserRole);
        when(userMapper.selectUserIdsByRoleId(20L)).thenReturn(List.of(2L, 3L));
        SystemMenu menu = new SystemMenu();
        menu.setId(5L);
        menu.setEnabled(1);
        when(menuMapper.selectById(5L)).thenReturn(menu);
        SystemPermission permission = new SystemPermission();
        permission.setId(6L);
        permission.setPermissionCode("document.view");
        permission.setPermissionName("查看资料");
        permission.setEnabled(1);
        when(permissionMapper.selectById(6L)).thenReturn(permission);

        service.updateMenuStatus(5L, 0, operator());
        SystemPermission update = new SystemPermission();
        update.setPermissionCode("document.view");
        update.setPermissionName("查看资料");
        update.setEnabled(0);
        service.savePermission(6L, update, operator());

        verify(authService, times(2)).logout(2L);
        verify(authService, times(2)).logout(3L);
    }

    @Test
    void roleWriteFailureReturnsConflictBeforeReplacingPermissionsOrAuditingSuccess() {
        SystemRole projectRole = role(30L, "CUSTOM_REVIEWER", "PROJECT", 0, 1);
        when(roleMapper.selectByIdForUpdate(30L)).thenReturn(projectRole);
        when(roleMapper.updateById(projectRole)).thenReturn(0);
        RoleSaveRequest request = new RoleSaveRequest();
        request.setRoleName("自定义复查员");
        request.setRoleCode("CUSTOM_REVIEWER");
        request.setScopeType("PROJECT");
        request.setEnabled(1);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.saveRole(30L, request, operator()));

        assertEquals(409, exception.getCode());
        verify(roleMapper, never()).deleteMenus(30L);
        verify(operationLogMapper, never()).insert(any());
    }

    @Test
    void menuWriteFailureReturnsConflictBeforeRevokingSessionsOrAuditingSuccess() {
        SystemMenu menu = new SystemMenu();
        menu.setId(5L);
        menu.setEnabled(1);
        when(menuMapper.selectById(5L)).thenReturn(menu);
        when(roleMapper.selectRoleIdsByMenuId(5L)).thenReturn(List.of(20L));
        when(menuMapper.updateById(menu)).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateMenuStatus(5L, 0, operator()));

        assertEquals(409, exception.getCode());
        verify(authService, never()).logout(any());
        verify(operationLogMapper, never()).insert(any());
    }

    @Test
    void administratorCountSqlRequiresUsableBcryptCredentialsAndEnabledRole() throws Exception {
        Method method = SysUserMapper.class.getMethod("countActivePlatformAdministrators");
        String sql = String.join(" ", method.getAnnotation(Select.class).value());

        assertTrue(sql.contains("u.password_login_enabled = 1"));
        assertTrue(sql.contains("u.password_reset_required = 0"));
        assertTrue(sql.contains("u.password REGEXP"));
        assertTrue(sql.contains("r.enabled = 1"));
        assertTrue(sql.contains("r.deleted = 0"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void userManagementReturnsEveryAssignedProjectWithAllProjectRoles() {
        SysUser user = ordinaryUser(2L);
        UserProjectRoleVO assignment = new UserProjectRoleVO();
        assignment.setProjectId(9L);
        assignment.setProjectName("演示项目");
        assignment.setAccessStatus("DISABLED");
        SystemRole manager = role(30L, "PROJECT_ADMIN", "PROJECT", 1, 1);
        manager.setRoleName("项目经理");
        SystemRole reviewer = role(31L, "QUALITY_REVIEWER", "PROJECT", 0, 1);
        reviewer.setRoleName("质量复查员");

        when(userMapper.selectList(any())).thenReturn(List.of(user));
        when(userMapper.selectRoleCodesByUserId(2L)).thenReturn(List.of());
        when(userProjectMapper.selectUserProjectRolesForManagement(2L)).thenReturn(List.of(assignment));
        when(userProjectRoleMapper.selectAssignedRoles(2L, 9L)).thenReturn(List.of(manager, reviewer));

        PageResult<Map<String, Object>> result = service.users(null, null, 1, 20);
        List<UserProjectRoleVO> assignments = (List<UserProjectRoleVO>) result.getRecords().get(0).get("projectRoles");

        assertEquals(1, assignments.size());
        assertEquals("演示项目", assignments.get(0).getProjectName());
        assertEquals("DISABLED", assignments.get(0).getAccessStatus());
        assertEquals(List.of("项目经理", "质量复查员"), assignments.get(0).getProjectRoles().stream()
                .map(SystemRole::getRoleName).toList());
    }

    private RoleSaveRequest roleRequest(String scope, int enabled) {
        RoleSaveRequest request = new RoleSaveRequest();
        request.setRoleName("平台管理员");
        request.setRoleCode("PLATFORM_ADMIN");
        request.setScopeType(scope);
        request.setEnabled(enabled);
        return request;
    }

    private SystemRole platformAdminRole() {
        return role(10L, "PLATFORM_ADMIN", "PLATFORM", 1, 1);
    }

    private SystemRole role(Long id, String code, String scope, int builtin, int enabled) {
        SystemRole role = new SystemRole();
        role.setId(id);
        role.setRoleName(code);
        role.setRoleCode(code);
        role.setScopeType(scope);
        role.setBuiltin(builtin);
        role.setEnabled(enabled);
        role.setDeleted(0);
        return role;
    }

    private SystemPermission permission(Long id, String code) {
        SystemPermission permission = new SystemPermission();
        permission.setId(id);
        permission.setPermissionCode(code);
        permission.setPermissionName(code);
        String normalized = code == null ? "" : code.toLowerCase();
        if (normalized.startsWith("document.")) permission.setModuleCode("WEB_DOCUMENT");
        else if (normalized.startsWith("quality.")) permission.setModuleCode("WEB_QUALITY");
        else if (normalized.startsWith("inspection.") || normalized.startsWith("box_")
                || normalized.startsWith("summary_") || normalized.startsWith("inspection_")) {
            permission.setModuleCode("WEB_INSPECTION");
        } else if (normalized.startsWith("system.role.")) permission.setModuleCode("SYSTEM_ROLE");
        permission.setEnabled(1);
        permission.setDeleted(0);
        return permission;
    }

    private SystemMenu menu(Long id, String code) {
        SystemMenu menu = new SystemMenu();
        menu.setId(id);
        menu.setMenuCode(code);
        menu.setMenuName(code);
        menu.setEnabled(1);
        menu.setDeleted(0);
        return menu;
    }

    private SysUser effectiveAdmin(Long id) {
        SysUser user = ordinaryUser(id);
        user.setPassword("$2a$12$" + "x".repeat(53));
        user.setPasswordLoginEnabled(1);
        user.setPasswordResetRequired(0);
        return user;
    }

    private SysUser ordinaryUser(Long id) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername("user_" + id);
        user.setStatus(1);
        user.setDeleted(0);
        user.setPasswordLoginEnabled(1);
        user.setPasswordResetRequired(0);
        return user;
    }

    private SysUser operator() {
        SysUser operator = ordinaryUser(1L);
        operator.setUsername("admin");
        return operator;
    }
}
