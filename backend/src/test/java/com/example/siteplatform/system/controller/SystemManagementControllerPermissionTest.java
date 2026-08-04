package com.example.siteplatform.system.controller;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.auth.service.WechatUserManagementService;
import com.example.siteplatform.registration.service.RegistrationApplicationService;
import com.example.siteplatform.project.dto.UserProjectRoleBatchRequest;
import com.example.siteplatform.project.service.ProjectMemberService;
import com.example.siteplatform.system.constant.SystemPermissionCodes;
import com.example.siteplatform.system.entity.SystemMenu;
import com.example.siteplatform.system.dto.RoleMenuUpdateRequest;
import com.example.siteplatform.system.dto.RoleOperationPermissionUpdateRequest;
import com.example.siteplatform.system.service.SystemAdministrationService;
import com.example.siteplatform.system.service.SystemPermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemManagementControllerPermissionTest {

    @Mock private AuthService authService;
    @Mock private SystemPermissionService permissionService;
    @Mock private SystemAdministrationService administrationService;
    @Mock private RegistrationApplicationService registrationService;
    @Mock private WechatUserManagementService wechatUserService;
    @Mock private ProjectMemberService projectMemberService;

    private SystemManagementController controller;
    private MockHttpServletRequest request;
    private SysUser user;

    @BeforeEach
    void setUp() {
        controller = new SystemManagementController(authService, permissionService,
                administrationService, registrationService, wechatUserService, projectMemberService);
        request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer delegated-token");
        user = new SysUser();
        user.setId(9L);
        when(authService.getCurrentUser("delegated-token")).thenReturn(user);
    }

    @Test
    void delegatedRoleManagerCanReadMenusNeededForRoleConfiguration() {
        when(administrationService.menus(null)).thenReturn(List.of());

        controller.menus(null, request);

        verify(permissionService).requireAnyPlatformPermission(user,
                SystemPermissionCodes.USER_VIEW,
                SystemPermissionCodes.ROLE_MANAGE,
                SystemPermissionCodes.MENU_MANAGE);
    }

    @Test
    void delegatedRoleManagerCanReadPermissionCatalogNeededForRoleConfiguration() {
        when(administrationService.permissions()).thenReturn(List.of());

        controller.permissions(request);

        verify(permissionService).requireAnyPlatformPermission(user,
                SystemPermissionCodes.USER_VIEW,
                SystemPermissionCodes.ROLE_MANAGE,
                SystemPermissionCodes.MENU_MANAGE);
    }

    @Test
    void menuWritesStillRequireMenuManagementPermission() {
        SystemMenu menu = new SystemMenu();
        when(administrationService.saveMenu(7L, menu, user)).thenReturn(menu);

        controller.updateMenu(7L, menu, request);

        verify(permissionService).requirePlatformPermission(user, SystemPermissionCodes.MENU_MANAGE);
    }

    @Test
    void roleMenuAndOperationPermissionWritesRemainPlatformProtected() {
        RoleMenuUpdateRequest menuRequest = new RoleMenuUpdateRequest();
        menuRequest.setMenuIds(List.of(3L));
        menuRequest.setBusinessModuleCodes(List.of("DOCUMENT"));
        RoleOperationPermissionUpdateRequest permissionRequest = new RoleOperationPermissionUpdateRequest();
        permissionRequest.setPermissionIds(List.of(8L));

        controller.updateRoleMenus(7L, menuRequest, request);
        controller.updateRoleOperationPermissions(7L, permissionRequest, request);

        verify(permissionService, org.mockito.Mockito.times(2))
                .requirePlatformPermission(user, SystemPermissionCodes.ROLE_MANAGE);
        verify(administrationService).updateRoleMenus(7L, List.of(3L), List.of("DOCUMENT"), user);
        verify(administrationService).updateRoleOperationPermissions(7L, List.of(8L), user);
    }

    @Test
    void crossProjectRoleAssignmentRequiresPlatformUserManagementPermission() {
        UserProjectRoleBatchRequest body = new UserProjectRoleBatchRequest();
        UserProjectRoleBatchRequest.Change change = new UserProjectRoleBatchRequest.Change();
        change.setProjectId(3L);
        change.setOperation("UPSERT");
        change.setRoleIds(List.of(8L));
        body.setChanges(List.of(change));
        when(projectMemberService.batchUpdateUserProjectAssignments(7L, body, user)).thenReturn(List.of());

        controller.updateUserProjectRoleAssignments(7L, body, request);

        verify(permissionService).requirePlatformPermission(user, SystemPermissionCodes.USER_MANAGE);
        verify(projectMemberService).batchUpdateUserProjectAssignments(7L, body, user);
    }
}
