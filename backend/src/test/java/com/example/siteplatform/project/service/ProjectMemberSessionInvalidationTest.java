package com.example.siteplatform.project.service;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.log.mapper.OperationLogMapper;
import com.example.siteplatform.project.dto.ProjectMemberRequest;
import com.example.siteplatform.project.dto.ProjectMemberStatusRequest;
import com.example.siteplatform.project.entity.SysUserProject;
import com.example.siteplatform.project.mapper.SysUserProjectMapper;
import com.example.siteplatform.system.entity.SystemRole;
import com.example.siteplatform.system.mapper.SystemRoleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectMemberSessionInvalidationTest {

    @Mock private SysUserMapper userMapper;
    @Mock private SysUserProjectMapper userProjectMapper;
    @Mock private ProjectPermissionService projectPermissionService;
    @Mock private InspectionPermissionTemplateService permissionTemplateService;
    @Mock private OperationLogMapper operationLogMapper;
    @Mock private AuthService authService;
    @Mock private SystemRoleMapper systemRoleMapper;

    private ProjectMemberService service;
    private SysUser operator;

    @BeforeEach
    void setUp() {
        service = new ProjectMemberService();
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "userProjectMapper", userProjectMapper);
        ReflectionTestUtils.setField(service, "projectPermissionService", projectPermissionService);
        ReflectionTestUtils.setField(service, "permissionTemplateService", permissionTemplateService);
        ReflectionTestUtils.setField(service, "operationLogMapper", operationLogMapper);
        ReflectionTestUtils.setField(service, "authService", authService);
        ReflectionTestUtils.setField(service, "systemRoleMapper", systemRoleMapper);
        operator = user(1L);
    }

    @Test
    void customProjectRoleCanBeAssignedWithoutRestoringDisabledAccessAndRevokesSessions() {
        ProjectMemberRequest request = new ProjectMemberRequest();
        request.setProjectId(9L);
        request.setUserId(2L);
        request.setProjectRoleCode("CUSTOM_REVIEWER");
        when(projectPermissionService.canManageProjectMembers(1L, 9L)).thenReturn(true);
        when(userMapper.selectById(2L)).thenReturn(user(2L));
        when(projectPermissionService.normalizeProjectRoleCode("CUSTOM_REVIEWER"))
                .thenReturn("CUSTOM_REVIEWER");
        when(systemRoleMapper.selectOne(any())).thenReturn(projectRole("CUSTOM_REVIEWER"));
        when(permissionTemplateService.defaultTemplateIdForRole("CUSTOM_REVIEWER")).thenReturn(100L);
        SysUserProject existing = member(5L, "DISABLED");
        when(userProjectMapper.selectOne(any())).thenReturn(existing);
        when(userProjectMapper.selectMembersByProjectId(9L)).thenReturn(List.of());

        service.saveMember(request, operator);

        assertEquals("CUSTOM_REVIEWER", existing.getProjectRoleCode());
        assertEquals("DISABLED", existing.getStatus());
        assertEquals(100L, existing.getInspectionPermissionTemplateId());
        verify(userProjectMapper).updateById(existing);
        verify(authService).logout(2L);
    }

    @Test
    void projectRoleNormalizationAcceptsValidatedCustomCodes() {
        ProjectPermissionService permissionService = new ProjectPermissionService();

        assertEquals("CUSTOM_REVIEWER",
                permissionService.normalizeProjectRoleCode(" custom_reviewer "));
        assertThrows(RuntimeException.class,
                () -> permissionService.normalizeProjectRoleCode("bad role"));
        assertThrows(RuntimeException.class,
                () -> permissionService.normalizeProjectRoleCode("PLATFORM_ADMIN"));
    }

    @Test
    void removingMemberRevokesAllSessions() {
        when(projectPermissionService.canManageProjectMembers(1L, 9L)).thenReturn(true);
        when(userProjectMapper.selectMembersByProjectId(9L)).thenReturn(List.of());
        SysUserProject existing = member(5L, "ACTIVE");
        when(userProjectMapper.selectOne(any())).thenReturn(existing);

        service.removeMember(9L, 2L, operator);

        verify(userProjectMapper).deleteById(5L);
        verify(authService).logout(2L);
    }

    @Test
    void changingProjectAccessStatusRevokesAllSessions() {
        when(projectPermissionService.canManageProjectMembers(1L, 9L)).thenReturn(true);
        SysUserProject existing = member(5L, "ACTIVE");
        when(userProjectMapper.selectOne(any())).thenReturn(existing);
        when(userProjectMapper.selectMembersByProjectId(9L)).thenReturn(List.of());
        ProjectMemberStatusRequest request = new ProjectMemberStatusRequest();
        request.setStatus("DISABLED");
        request.setReason("调离项目");

        service.updateMemberStatus(9L, 2L, request, operator);

        assertEquals("DISABLED", existing.getStatus());
        verify(authService).logout(2L);
    }

    @Test
    void projectManagerUserOptionsAreLimitedToExistingProjectMembers() {
        when(projectPermissionService.canManageProjectMembers(1L, 9L)).thenReturn(true);
        when(projectPermissionService.isPlatformAdmin(1L)).thenReturn(false);
        SysUserProject relation = member(5L, "ACTIVE");
        when(userProjectMapper.selectList(any())).thenReturn(List.of(relation));
        when(userMapper.selectList(any())).thenReturn(List.of(user(2L)));
        when(userProjectMapper.selectOne(any())).thenReturn(relation);
        when(userMapper.selectRoleCodesByUserId(2L)).thenReturn(List.of("USER"));

        var options = service.listUserOptions(9L, null, operator);

        assertEquals(1, options.size());
        assertEquals(2L, options.get(0).getId());
        assertEquals(true, options.get(0).getInProject());
    }

    @Test
    void projectManagerCannotBrowseUsersWhenProjectHasNoMembers() {
        when(projectPermissionService.canManageProjectMembers(1L, 9L)).thenReturn(true);
        when(projectPermissionService.isPlatformAdmin(1L)).thenReturn(false);
        when(userProjectMapper.selectList(any())).thenReturn(List.of());

        assertTrue(service.listUserOptions(9L, null, operator).isEmpty());

        verify(userMapper, never()).selectList(any());
    }

    @Test
    void ensuringNewOrUpgradedMemberRevokesSessionsButNoopDoesNot() {
        when(projectPermissionService.normalizeProjectRoleCode("CUSTOM_REVIEWER"))
                .thenReturn("CUSTOM_REVIEWER");
        when(systemRoleMapper.selectOne(any())).thenReturn(projectRole("CUSTOM_REVIEWER"));
        when(permissionTemplateService.defaultTemplateIdForRole("CUSTOM_REVIEWER")).thenReturn(100L);
        when(userProjectMapper.selectOne(any())).thenReturn(null);

        service.ensureProjectMember(9L, 2L, "CUSTOM_REVIEWER");

        verify(userProjectMapper).insert(any(SysUserProject.class));
        verify(authService).logout(2L);

        ProjectMemberService noopService = new ProjectMemberService();
        ReflectionTestUtils.setField(noopService, "userProjectMapper", userProjectMapper);
        ReflectionTestUtils.setField(noopService, "projectPermissionService", projectPermissionService);
        ReflectionTestUtils.setField(noopService, "permissionTemplateService", permissionTemplateService);
        ReflectionTestUtils.setField(noopService, "authService", authService);
        ReflectionTestUtils.setField(noopService, "systemRoleMapper", systemRoleMapper);
        SysUserProject unchanged = member(6L, "ACTIVE");
        unchanged.setProjectRoleCode("CUSTOM_REVIEWER");
        unchanged.setInspectionPermissionTemplateId(100L);
        when(userProjectMapper.selectOne(any())).thenReturn(unchanged);

        noopService.ensureProjectMember(9L, 3L, "CUSTOM_REVIEWER");

        verify(authService, never()).logout(3L);
    }

    private SysUser user(Long id) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername("user_" + id);
        user.setStatus(1);
        return user;
    }

    private SysUserProject member(Long id, String status) {
        SysUserProject member = new SysUserProject();
        member.setId(id);
        member.setProjectId(9L);
        member.setUserId(2L);
        member.setProjectRoleCode("USER");
        member.setStatus(status);
        return member;
    }

    private SystemRole projectRole(String code) {
        SystemRole role = new SystemRole();
        role.setId(30L);
        role.setRoleCode(code);
        role.setScopeType("PROJECT");
        role.setEnabled(1);
        role.setDeleted(0);
        return role;
    }
}
