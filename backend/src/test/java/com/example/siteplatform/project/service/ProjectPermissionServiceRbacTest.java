package com.example.siteplatform.project.service;

import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.project.entity.SysUserProject;
import com.example.siteplatform.project.mapper.SysUserProjectMapper;
import com.example.siteplatform.system.service.SystemPermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectPermissionServiceRbacTest {

    @Mock private SysUserMapper userMapper;
    @Mock private SysUserProjectMapper userProjectMapper;
    @Mock private SystemPermissionService systemPermissionService;

    private ProjectPermissionService service;

    @BeforeEach
    void setUp() {
        service = new ProjectPermissionService();
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "userProjectMapper", userProjectMapper);
        ReflectionTestUtils.setField(service, "systemPermissionService", systemPermissionService);
        when(userMapper.selectRoleCodesByUserId(7L)).thenReturn(List.of());
    }

    @Test
    void customProjectRolePermissionControlsMemberManagementForThatProject() {
        when(userProjectMapper.selectOne(any())).thenReturn(activeMembership("CUSTOM_MANAGER"));
        when(systemPermissionService.permissionCodes(7L)).thenReturn(List.of());
        when(systemPermissionService.projectRolePermissionCodes("CUSTOM_MANAGER"))
                .thenReturn(List.of("system.project.manage"));

        assertTrue(service.canManageProjectMembers(7L, 2L));
    }

    @Test
    void legacyInspectionTemplateCannotBypassRevokedRbacPermission() {
        SysUserProject membership = activeMembership("USER");
        membership.setInspectionPermissionTemplateId(88L);
        when(userProjectMapper.selectOne(any())).thenReturn(membership);
        when(systemPermissionService.permissionCodes(7L)).thenReturn(List.of());
        when(systemPermissionService.projectRolePermissionCodes("USER")).thenReturn(List.of());

        assertFalse(service.canManageProjectMembers(7L, 2L));
    }

    @Test
    void platformProjectManagePermissionStillRequiresProjectMembership() {
        when(userProjectMapper.selectOne(any())).thenReturn(null);

        assertFalse(service.canManageProjectMembers(7L, 2L));
    }

    private SysUserProject activeMembership(String roleCode) {
        SysUserProject membership = new SysUserProject();
        membership.setUserId(7L);
        membership.setProjectId(2L);
        membership.setProjectRoleCode(roleCode);
        membership.setStatus("ACTIVE");
        return membership;
    }
}
