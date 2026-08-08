package com.example.siteplatform.system.service;

import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.system.constant.BusinessModuleCodes;
import com.example.siteplatform.system.entity.SystemMenu;
import com.example.siteplatform.system.entity.SystemPermission;
import com.example.siteplatform.system.mapper.SystemMenuMapper;
import com.example.siteplatform.system.mapper.SystemPermissionMapper;
import com.example.siteplatform.system.mapper.SystemRoleBusinessModuleMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SystemPermissionServiceTest {

    @Test
    void platformAdministratorBypassesIndividualPermissionLookup() {
        SystemMenuMapper menuMapper = mock(SystemMenuMapper.class);
        SystemPermissionMapper permissionMapper = mock(SystemPermissionMapper.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SystemRoleBusinessModuleMapper moduleMapper = mock(SystemRoleBusinessModuleMapper.class);
        SystemPermissionService service = new SystemPermissionService(menuMapper, permissionMapper, userMapper, moduleMapper);
        when(userMapper.selectRoleCodesByUserId(12L))
                .thenReturn(List.of(ProjectPermissionService.ROLE_PLATFORM_ADMIN));
        when(moduleMapper.selectModuleCodesByUserId(12L)).thenReturn(List.of("DOCUMENT"));

        assertThat(service.hasPermission(12L, "document.manage")).isTrue();
        verify(permissionMapper, never()).selectCodesByUserId(12L);
    }

    @Test
    void platformAdministratorReceivesAllEnabledCatalogsWithoutRoleAssignments() {
        SystemMenuMapper menuMapper = mock(SystemMenuMapper.class);
        SystemPermissionMapper permissionMapper = mock(SystemPermissionMapper.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SystemRoleBusinessModuleMapper moduleMapper = mock(SystemRoleBusinessModuleMapper.class);
        SystemPermissionService service = new SystemPermissionService(menuMapper, permissionMapper, userMapper, moduleMapper);
        when(userMapper.selectRoleCodesByUserId(12L))
                .thenReturn(List.of(ProjectPermissionService.ROLE_PLATFORM_ADMIN));
        SystemPermission document = new SystemPermission();
        document.setPermissionCode("document.manage");
        SystemPermission quality = new SystemPermission();
        quality.setPermissionCode("quality.review");
        when(permissionMapper.selectList(any())).thenReturn(List.of(document, quality));
        SystemMenu root = new SystemMenu();
        root.setId(1L);
        root.setMenuCode("WEB_DOCUMENT");
        root.setMenuName("资料管理");
        when(menuMapper.selectList(any())).thenReturn(List.of(root));

        assertThat(service.permissionCodes(12L)).containsExactly("document.manage", "quality.review");
        assertThat(service.projectPermissionCodes(12L, 999L))
                .containsExactly("document.manage", "quality.review");
        assertThat(service.projectMenuCodes(12L, 999L)).containsExactly("WEB_DOCUMENT");
        assertThat(service.menuTree(12L)).extracting("menuCode").containsExactly("WEB_DOCUMENT");
        assertThat(service.businessModuleCodes(12L, 999L)).containsExactlyInAnyOrderElementsOf(BusinessModuleCodes.ALL);
        verify(moduleMapper, never()).selectModuleCodesByUserIdAndProject(12L, 999L);
    }

    @Test
    void firstNumericUserIdDoesNotBypassRoleBasedPermissions() {
        SystemMenuMapper menuMapper = mock(SystemMenuMapper.class);
        SystemPermissionMapper permissionMapper = mock(SystemPermissionMapper.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SystemRoleBusinessModuleMapper moduleMapper = mock(SystemRoleBusinessModuleMapper.class);
        SystemPermissionService service = new SystemPermissionService(menuMapper, permissionMapper, userMapper, moduleMapper);
        when(userMapper.selectRoleCodesByUserId(1L)).thenReturn(List.of());
        when(permissionMapper.selectCodesByUserId(1L)).thenReturn(List.of());

        assertThat(service.hasPermission(1L, "system.role.manage")).isFalse();
        verify(permissionMapper).selectCodesByUserId(1L);
    }

    @Test
    void projectRolePermissionDoesNotAuthorizePlatformManagement() {
        SystemMenuMapper menuMapper = mock(SystemMenuMapper.class);
        SystemPermissionMapper permissionMapper = mock(SystemPermissionMapper.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SystemRoleBusinessModuleMapper moduleMapper = mock(SystemRoleBusinessModuleMapper.class);
        SystemPermissionService service = new SystemPermissionService(menuMapper, permissionMapper, userMapper, moduleMapper);
        when(userMapper.selectRoleCodesByUserId(7L)).thenReturn(List.of());
        when(permissionMapper.selectCodesByUserId(7L)).thenReturn(List.of("system.role.manage"));
        when(permissionMapper.selectPlatformCodesByUserId(7L)).thenReturn(List.of());

        assertThat(service.hasPermission(7L, "system.role.manage")).isTrue();
        assertThat(service.hasPlatformPermission(7L, "system.role.manage")).isFalse();
        verify(permissionMapper).selectPlatformCodesByUserId(7L);
    }

    @Test
    void writePermissionFromProjectADoesNotAuthorizeProjectB() {
        SystemMenuMapper menuMapper = mock(SystemMenuMapper.class);
        SystemPermissionMapper permissionMapper = mock(SystemPermissionMapper.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SystemRoleBusinessModuleMapper moduleMapper = mock(SystemRoleBusinessModuleMapper.class);
        SystemPermissionService service = new SystemPermissionService(menuMapper, permissionMapper, userMapper, moduleMapper);
        when(userMapper.selectRoleCodesByUserId(7L)).thenReturn(List.of());
        when(moduleMapper.selectModuleCodesByUserIdAndProject(7L, 101L)).thenReturn(List.of("DOCUMENT"));
        when(moduleMapper.selectModuleCodesByUserIdAndProject(7L, 202L)).thenReturn(List.of("DOCUMENT"));
        when(permissionMapper.selectCodesByUserIdAndProject(7L, 101L))
                .thenReturn(List.of("document.view", "document.upload"));
        when(permissionMapper.selectCodesByUserIdAndProject(7L, 202L))
                .thenReturn(List.of("document.view"));

        assertThat(service.hasProjectPermission(7L, 101L, "document.upload")).isTrue();
        assertThat(service.hasProjectPermission(7L, 202L, "document.upload")).isFalse();
    }

    @Test
    void projectMenusAreReadFromTheCurrentProjectOnly() {
        SystemMenuMapper menuMapper = mock(SystemMenuMapper.class);
        SystemPermissionMapper permissionMapper = mock(SystemPermissionMapper.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SystemRoleBusinessModuleMapper moduleMapper = mock(SystemRoleBusinessModuleMapper.class);
        SystemPermissionService service = new SystemPermissionService(menuMapper, permissionMapper, userMapper, moduleMapper);
        when(userMapper.selectRoleCodesByUserId(7L)).thenReturn(List.of());
        when(moduleMapper.selectModuleCodesByUserIdAndProject(7L, 101L))
                .thenReturn(List.of("DOCUMENT", "INSPECTION"));
        when(moduleMapper.selectModuleCodesByUserIdAndProject(7L, 202L)).thenReturn(List.of("QUALITY"));
        when(menuMapper.selectEnabledCodesByUserIdAndProject(7L, 101L))
                .thenReturn(List.of("WEB_DOCUMENT", "WEB_INSPECTION"));
        when(menuMapper.selectEnabledCodesByUserIdAndProject(7L, 202L))
                .thenReturn(List.of("WEB_QUALITY"));

        assertThat(service.projectMenuCodes(7L, 101L))
                .containsExactly("WEB_DOCUMENT", "WEB_INSPECTION");
        assertThat(service.projectMenuCodes(7L, 202L)).containsExactly("WEB_QUALITY");
    }

    @Test
    void disabledBusinessModuleBlocksAnOtherwiseGrantedPermission() {
        SystemMenuMapper menuMapper = mock(SystemMenuMapper.class);
        SystemPermissionMapper permissionMapper = mock(SystemPermissionMapper.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SystemRoleBusinessModuleMapper moduleMapper = mock(SystemRoleBusinessModuleMapper.class);
        SystemPermissionService service = new SystemPermissionService(menuMapper, permissionMapper, userMapper, moduleMapper);
        when(userMapper.selectRoleCodesByUserId(7L)).thenReturn(List.of());
        when(moduleMapper.selectModuleCodesByUserIdAndProject(7L, 101L)).thenReturn(List.of("INSPECTION"));
        when(permissionMapper.selectCodesByUserIdAndProject(7L, 101L)).thenReturn(List.of("document.manage"));

        assertThat(service.hasProjectPermission(7L, 101L, "document.manage")).isFalse();
        verify(permissionMapper, never()).selectCodesByUserIdAndProject(7L, 101L);
    }
}
