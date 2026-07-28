package com.example.siteplatform.system.service;

import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.system.mapper.SystemMenuMapper;
import com.example.siteplatform.system.mapper.SystemPermissionMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SystemPermissionServiceTest {

    @Test
    void platformAdministratorBypassesIndividualPermissionLookup() {
        SystemMenuMapper menuMapper = mock(SystemMenuMapper.class);
        SystemPermissionMapper permissionMapper = mock(SystemPermissionMapper.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SystemPermissionService service = new SystemPermissionService(menuMapper, permissionMapper, userMapper);
        when(userMapper.selectRoleCodesByUserId(12L))
                .thenReturn(List.of(ProjectPermissionService.ROLE_PLATFORM_ADMIN));

        assertThat(service.hasPermission(12L, "document.manage")).isTrue();
        verify(permissionMapper, never()).selectCodesByUserId(12L);
    }

    @Test
    void firstNumericUserIdDoesNotBypassRoleBasedPermissions() {
        SystemMenuMapper menuMapper = mock(SystemMenuMapper.class);
        SystemPermissionMapper permissionMapper = mock(SystemPermissionMapper.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SystemPermissionService service = new SystemPermissionService(menuMapper, permissionMapper, userMapper);
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
        SystemPermissionService service = new SystemPermissionService(menuMapper, permissionMapper, userMapper);
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
        SystemPermissionService service = new SystemPermissionService(menuMapper, permissionMapper, userMapper);
        when(userMapper.selectRoleCodesByUserId(7L)).thenReturn(List.of());
        when(permissionMapper.selectCodesByUserIdAndProject(7L, 101L))
                .thenReturn(List.of("document.view", "document.upload"));
        when(permissionMapper.selectCodesByUserIdAndProject(7L, 202L))
                .thenReturn(List.of("document.view"));

        assertThat(service.hasProjectPermission(7L, 101L, "document.upload")).isTrue();
        assertThat(service.hasProjectPermission(7L, 202L, "document.upload")).isFalse();
    }
}
