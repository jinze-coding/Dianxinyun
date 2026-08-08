package com.example.siteplatform.project.service;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.log.mapper.OperationLogMapper;
import com.example.siteplatform.project.dto.ProjectMemberRequest;
import com.example.siteplatform.project.dto.ProjectMemberStatusRequest;
import com.example.siteplatform.project.entity.SysUserProject;
import com.example.siteplatform.project.mapper.SysUserProjectMapper;
import com.example.siteplatform.project.mapper.SysUserProjectRoleMapper;
import com.example.siteplatform.quality.service.QualityAssigneeService;
import com.example.siteplatform.system.entity.SystemPermission;
import com.example.siteplatform.system.entity.SystemRole;
import com.example.siteplatform.system.mapper.SystemPermissionMapper;
import com.example.siteplatform.system.mapper.SystemRoleBusinessModuleMapper;
import com.example.siteplatform.system.mapper.SystemRoleMapper;
import com.example.siteplatform.system.service.ResponsibilityReleaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectMemberSessionInvalidationTest {

    @Mock private SysUserMapper userMapper;
    @Mock private SysUserProjectMapper userProjectMapper;
    @Mock private SysUserProjectRoleMapper userProjectRoleMapper;
    @Mock private ProjectPermissionService projectPermissionService;
    @Mock private OperationLogMapper operationLogMapper;
    @Mock private AuthService authService;
    @Mock private SystemRoleMapper systemRoleMapper;
    @Mock private SystemRoleBusinessModuleMapper roleBusinessModuleMapper;
    @Mock private SystemPermissionMapper systemPermissionMapper;
    @Mock private QualityAssigneeService qualityAssigneeService;
    @Mock private ResponsibilityReleaseService responsibilityReleaseService;

    private ProjectMemberService service;
    private SysUser operator;

    @BeforeEach
    void setUp() {
        service = new ProjectMemberService();
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "userProjectMapper", userProjectMapper);
        ReflectionTestUtils.setField(service, "userProjectRoleMapper", userProjectRoleMapper);
        ReflectionTestUtils.setField(service, "projectPermissionService", projectPermissionService);
        ReflectionTestUtils.setField(service, "operationLogMapper", operationLogMapper);
        ReflectionTestUtils.setField(service, "authService", authService);
        ReflectionTestUtils.setField(service, "systemRoleMapper", systemRoleMapper);
        ReflectionTestUtils.setField(service, "roleBusinessModuleMapper", roleBusinessModuleMapper);
        ReflectionTestUtils.setField(service, "systemPermissionMapper", systemPermissionMapper);
        ReflectionTestUtils.setField(service, "qualityAssigneeService", qualityAssigneeService);
        ReflectionTestUtils.setField(service, "responsibilityReleaseService", responsibilityReleaseService);
        lenient().when(userProjectMapper.insert(any(SysUserProject.class))).thenReturn(1);
        lenient().when(userProjectMapper.updateById(any(SysUserProject.class))).thenReturn(1);
        lenient().when(userProjectMapper.deleteById(anyLong())).thenReturn(1);
        lenient().when(userProjectRoleMapper.insert(any())).thenReturn(1);
        operator = user(1L);
    }

    @Test
    void multipleProjectRolesCanBeAssignedWithoutRestoringDisabledAccessAndRevokesSessions() {
        ProjectMemberRequest request = new ProjectMemberRequest();
        request.setProjectId(9L);
        request.setUserId(2L);
        request.setRoleIds(List.of(30L, 31L));
        when(projectPermissionService.canManageProjectMembers(1L, 9L)).thenReturn(true);
        when(userMapper.selectById(2L)).thenReturn(user(2L));
        when(systemRoleMapper.selectById(30L)).thenReturn(projectRole(30L, "CUSTOM_REVIEWER"));
        when(systemRoleMapper.selectById(31L)).thenReturn(projectRole(31L, "DOCUMENT_REVIEWER"));
        SysUserProject existing = member(5L, "DISABLED");
        when(userProjectMapper.selectOne(any())).thenReturn(existing);
        when(userProjectMapper.selectMembersByProjectId(9L)).thenReturn(List.of());

        service.saveMember(request, operator);

        assertEquals("CUSTOM_REVIEWER", existing.getProjectRoleCode());
        assertEquals("DISABLED", existing.getStatus());
        assertEquals(null, existing.getInspectionPermissionTemplateId());
        verify(userProjectMapper).updateById(existing);
        verify(userProjectRoleMapper).deleteByUserAndProject(2L, 9L);
        verify(userProjectRoleMapper, org.mockito.Mockito.times(2)).insert(any());
        verify(authService).logout(2L);
        verify(authService).repeatLogoutAfterCommit(2L);
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
        when(userProjectMapper.selectByProjectAndUserForUpdate(9L, 2L)).thenReturn(existing);

        service.removeMember(9L, 2L, operator);

        verify(userProjectMapper).deleteById(5L);
        verify(userProjectRoleMapper).deleteByUserAndProject(2L, 9L);
        verify(responsibilityReleaseService).releaseAll(9L, 2L);
        verify(authService).logout(2L);
    }

    @Test
    void memberWithOpenQualityAssignmentsCannotBeRemoved() {
        when(projectPermissionService.canManageProjectMembers(1L, 9L)).thenReturn(true);
        when(userProjectMapper.selectByProjectAndUserForUpdate(9L, 2L)).thenReturn(member(5L, "ACTIVE"));
        when(qualityAssigneeService.countOpenAssignments(9L, 2L)).thenReturn(2L);

        var exception = assertThrows(BusinessException.class,
                () -> service.removeMember(9L, 2L, operator));

        assertTrue(exception.getMessage().contains("2项未闭环质量整改"));
        assertTrue(exception.getMessage().contains("先改派或关闭"));
        verify(userProjectMapper, never()).deleteById(anyLong());
    }

    @Test
    void changingProjectAccessStatusRevokesAllSessions() {
        when(projectPermissionService.canManageProjectMembers(1L, 9L)).thenReturn(true);
        SysUserProject existing = member(5L, "ACTIVE");
        when(userProjectMapper.selectByProjectAndUserForUpdate(9L, 2L)).thenReturn(existing);
        when(userProjectMapper.selectMembersByProjectId(9L)).thenReturn(List.of());
        ProjectMemberStatusRequest request = new ProjectMemberStatusRequest();
        request.setStatus("DISABLED");
        request.setReason("调离项目");

        service.updateMemberStatus(9L, 2L, request, operator);

        assertEquals("DISABLED", existing.getStatus());
        verify(responsibilityReleaseService).releaseAll(9L, 2L);
        verify(authService).logout(2L);
    }

    @Test
    void accessStatusWriteFailureReturnsConflictWithoutRevokingSessionsOrAuditingSuccess() {
        when(projectPermissionService.canManageProjectMembers(1L, 9L)).thenReturn(true);
        SysUserProject existing = member(5L, "ACTIVE");
        when(userProjectMapper.selectByProjectAndUserForUpdate(9L, 2L)).thenReturn(existing);
        when(userProjectMapper.updateById(existing)).thenReturn(0);
        ProjectMemberStatusRequest request = new ProjectMemberStatusRequest();
        request.setStatus("DISABLED");
        request.setReason("调离项目");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateMemberStatus(9L, 2L, request, operator));

        assertEquals(409, exception.getCode());
        verify(authService, never()).logout(2L);
        verify(operationLogMapper, never()).insert(any());
        verify(responsibilityReleaseService, never()).releaseAll(anyLong(), anyLong());
    }

    @Test
    void memberDeleteFailureReturnsConflictWithoutRevokingSessionsOrAuditingSuccess() {
        when(projectPermissionService.canManageProjectMembers(1L, 9L)).thenReturn(true);
        when(userProjectMapper.selectMembersByProjectId(9L)).thenReturn(List.of());
        SysUserProject existing = member(5L, "ACTIVE");
        when(userProjectMapper.selectByProjectAndUserForUpdate(9L, 2L)).thenReturn(existing);
        when(userProjectMapper.deleteById(5L)).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.removeMember(9L, 2L, operator));

        assertEquals(409, exception.getCode());
        verify(authService, never()).logout(2L);
        verify(operationLogMapper, never()).insert(any());
        verify(responsibilityReleaseService, never()).releaseAll(anyLong(), anyLong());
    }

    @Test
    void memberRoleSaveFailureReturnsConflictBeforeReplacingRoles() {
        ProjectMemberRequest request = new ProjectMemberRequest();
        request.setProjectId(9L);
        request.setUserId(2L);
        request.setRoleIds(List.of(30L));
        when(projectPermissionService.canManageProjectMembers(1L, 9L)).thenReturn(true);
        when(userMapper.selectById(2L)).thenReturn(user(2L));
        when(systemRoleMapper.selectById(30L)).thenReturn(projectRole(30L, "CUSTOM_REVIEWER"));
        SysUserProject existing = member(5L, "ACTIVE");
        when(userProjectMapper.selectOne(any())).thenReturn(existing);
        when(userProjectMapper.updateById(existing)).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.saveMember(request, operator));

        assertEquals(409, exception.getCode());
        verify(userProjectRoleMapper, never()).deleteByUserAndProject(2L, 9L);
        verify(authService, never()).logout(2L);
    }

    @Test
    void memberWithOpenQualityAssignmentsCannotBePaused() {
        when(projectPermissionService.canManageProjectMembers(1L, 9L)).thenReturn(true);
        when(userProjectMapper.selectByProjectAndUserForUpdate(9L, 2L)).thenReturn(member(5L, "ACTIVE"));
        when(qualityAssigneeService.countOpenAssignments(9L, 2L)).thenReturn(1L);
        ProjectMemberStatusRequest request = new ProjectMemberStatusRequest();
        request.setStatus("DISABLED");
        request.setReason("调离项目");

        var exception = assertThrows(BusinessException.class,
                () -> service.updateMemberStatus(9L, 2L, request, operator));

        assertTrue(exception.getMessage().contains("1项未闭环质量整改"));
        assertTrue(exception.getMessage().contains("暂停访问"));
        verify(userProjectMapper, never()).updateById(any());
    }

    @Test
    void roleChangeCannotRemoveEligibilityWhileQualityAssignmentsRemainOpen() {
        ProjectMemberRequest request = new ProjectMemberRequest();
        request.setProjectId(9L);
        request.setUserId(2L);
        request.setRoleIds(List.of(30L));
        when(projectPermissionService.canManageProjectMembers(1L, 9L)).thenReturn(true);
        when(userMapper.selectById(2L)).thenReturn(user(2L));
        when(systemRoleMapper.selectById(30L)).thenReturn(projectRole(30L, "DOCUMENT_ONLY"));
        when(userProjectMapper.selectOne(any())).thenReturn(member(5L, "ACTIVE"));
        when(qualityAssigneeService.countOpenAssignments(9L, 2L)).thenReturn(1L);
        when(qualityAssigneeService.isEligibleAssignee(2L, 9L)).thenReturn(false);

        var exception = assertThrows(BusinessException.class,
                () -> service.saveMember(request, operator));

        assertTrue(exception.getMessage().contains("不能取消其质量模块、查看或整改权限"));
        verify(authService, never()).logout(2L);
    }

    @Test
    void projectManagerCannotGrantProtectedProjectManagerRole() {
        ProjectMemberRequest request = new ProjectMemberRequest();
        request.setProjectId(9L);
        request.setUserId(2L);
        request.setRoleIds(List.of(40L));
        SystemRole managerRole = projectRole(40L, "PROJECT_ADMIN");
        managerRole.setProjectManagerRole(1);
        when(projectPermissionService.canManageProjectMembers(1L, 9L)).thenReturn(true);
        when(userMapper.selectById(2L)).thenReturn(user(2L));
        when(systemRoleMapper.selectById(40L)).thenReturn(managerRole);
        when(userProjectMapper.selectOne(any())).thenReturn(null);

        var exception = assertThrows(RuntimeException.class, () -> service.saveMember(request, operator));

        assertTrue(exception.getMessage().contains("项目经理角色只能由系统管理员"));
        verify(userProjectRoleMapper, never()).insert(any());
    }

    @Test
    void projectManagerCannotRemoveOrPauseAnotherProjectManager() {
        SysUserProject managerMember = member(5L, "ACTIVE");
        when(projectPermissionService.canManageProjectMembers(1L, 9L)).thenReturn(true);
        when(userProjectMapper.selectByProjectAndUserForUpdate(9L, 2L)).thenReturn(managerMember);
        when(userProjectRoleMapper.countEnabledProjectManagerRoles(2L, 9L)).thenReturn(1L);

        var exception = assertThrows(RuntimeException.class, () -> service.removeMember(9L, 2L, operator));

        assertTrue(exception.getMessage().contains("不能调整项目经理"));
        verify(userProjectMapper, never()).deleteById(anyLong());
    }

    @Test
    void projectManagerCanSelectEnabledExistingSystemAccountsWithoutContactDetails() {
        when(projectPermissionService.canManageProjectMembers(1L, 9L)).thenReturn(true);
        when(userMapper.selectList(any())).thenReturn(List.of(user(2L)));

        var options = service.listUserOptions(9L, null, operator);

        assertEquals(1, options.size());
        assertEquals(2L, options.get(0).getId());
        assertEquals("user_2", options.get(0).getUsername());
    }

    @Test
    void projectManagerCanSearchEnabledAccountsEvenWhenProjectHasNoMembers() {
        when(projectPermissionService.canManageProjectMembers(1L, 9L)).thenReturn(true);
        when(userMapper.selectList(any())).thenReturn(List.of(user(3L)));

        assertEquals(1, service.listUserOptions(9L, null, operator).size());

        verify(userMapper).selectList(any());
    }

    @Test
    void assignableRoleCatalogContainsModuleAndPermissionNameSummariesWithoutRawPermissionIds() {
        SystemRole role = projectRole(30L, "USER");
        role.setRoleName("项目成员");
        SystemPermission permission = new SystemPermission();
        permission.setId(41L);
        permission.setPermissionName("查看资料");
        permission.setEnabled(1);
        permission.setDeleted(0);
        when(projectPermissionService.canManageProjectMembers(1L, 9L)).thenReturn(true);
        when(systemRoleMapper.selectList(any())).thenReturn(List.of(role));
        when(systemRoleMapper.selectPermissionIds(30L)).thenReturn(List.of(41L));
        when(roleBusinessModuleMapper.selectModuleCodesByRoleId(30L)).thenReturn(List.of("DOCUMENT"));
        when(systemPermissionMapper.selectBatchIds(List.of(41L))).thenReturn(List.of(permission));

        List<SystemRole> roles = service.listAssignableRoles(9L, operator);

        assertEquals(List.of("DOCUMENT"), roles.get(0).getBusinessModuleCodes());
        assertEquals(List.of("查看资料"), roles.get(0).getPermissionNames());
        assertNull(roles.get(0).getPermissionIds());
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
        return projectRole(30L, code);
    }

    private SystemRole projectRole(Long id, String code) {
        SystemRole role = new SystemRole();
        role.setId(id);
        role.setRoleCode(code);
        role.setScopeType("PROJECT");
        role.setEnabled(1);
        role.setDeleted(0);
        return role;
    }
}
