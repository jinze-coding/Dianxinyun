package com.example.siteplatform.project.service;

import com.example.siteplatform.auth.dto.UserProjectRoleVO;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.log.mapper.OperationLogMapper;
import com.example.siteplatform.project.dto.ProjectMemberBatchRequest;
import com.example.siteplatform.project.dto.ProjectMemberVO;
import com.example.siteplatform.project.dto.ResponsibilityImpactVO;
import com.example.siteplatform.project.dto.UserProjectRoleBatchRequest;
import com.example.siteplatform.project.entity.ProjectInfo;
import com.example.siteplatform.project.entity.SysUserProject;
import com.example.siteplatform.project.mapper.ProjectInfoMapper;
import com.example.siteplatform.project.mapper.SysUserProjectMapper;
import com.example.siteplatform.project.mapper.SysUserProjectRoleMapper;
import com.example.siteplatform.quality.service.QualityAssigneeService;
import com.example.siteplatform.system.entity.SystemRole;
import com.example.siteplatform.system.mapper.SystemPermissionMapper;
import com.example.siteplatform.system.mapper.SystemRoleBusinessModuleMapper;
import com.example.siteplatform.system.mapper.SystemRoleMapper;
import com.example.siteplatform.system.service.ResponsibilityReleaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectMemberBatchAssignmentTest {

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
    @Mock private ProjectInfoMapper projectInfoMapper;
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
        ReflectionTestUtils.setField(service, "projectInfoMapper", projectInfoMapper);
        ReflectionTestUtils.setField(service, "responsibilityReleaseService", responsibilityReleaseService);
        lenient().when(responsibilityReleaseService.impact(any(), any()))
                .thenReturn(new com.example.siteplatform.project.dto.ResponsibilityImpactVO());
        lenient().when(projectPermissionService.canManageProjectMembers(1L, 9L)).thenReturn(true);
        lenient().when(projectPermissionService.isPlatformAdmin(1L)).thenReturn(true);
        lenient().when(projectInfoMapper.selectById(any())).thenAnswer(invocation -> project(invocation.getArgument(0)));
        lenient().when(projectInfoMapper.selectByIdForUpdate(any())).thenAnswer(invocation -> project(invocation.getArgument(0)));
        lenient().when(userMapper.selectByIdForUpdate(any())).thenAnswer(invocation -> user(invocation.getArgument(0), 1));
        lenient().when(userProjectMapper.insert(any(SysUserProject.class))).thenReturn(1);
        lenient().when(userProjectMapper.updateById(any(SysUserProject.class))).thenReturn(1);
        lenient().when(userProjectMapper.deleteById(any(Long.class))).thenReturn(1);
        lenient().when(userProjectRoleMapper.insert(any())).thenReturn(1);
        operator = user(1L, 1);
    }

    @Test
    void assignmentOptionsIncludeDisabledExistingMembersAndEnabledCandidates() {
        ProjectMemberVO existing = member(5L, 9L, 2L, "DISABLED");
        when(userProjectMapper.selectMembersByProjectId(9L)).thenReturn(List.of(existing));
        when(userMapper.selectList(any())).thenReturn(List.of(user(2L, 0), user(3L, 1)));
        when(userProjectRoleMapper.selectAssignedRoles(2L, 9L)).thenReturn(List.of(role(30L, "USER", 0)));

        var result = service.listAssignmentOptions(9L, null, "ALL", 1, 100, operator);

        assertEquals(2L, result.getTotal());
        assertTrue(result.getRecords().get(0).isAssigned());
        assertEquals("DISABLED", result.getRecords().get(0).getAccessStatus());
        assertEquals(0, result.getRecords().get(0).getAccountStatus());
        assertFalse(result.getRecords().get(1).isAssigned());
    }

    @Test
    void assignmentOptionsSearchesAssignedRoleNameWithoutReturningPrivateFields() {
        ProjectMemberVO existing = member(5L, 9L, 2L, "ACTIVE");
        SystemRole qualityRole = role(31L, "QUALITY_EDITOR", 0);
        qualityRole.setRoleName("质量编辑员");
        when(userProjectMapper.selectMembersByProjectId(9L)).thenReturn(List.of(existing));
        when(userMapper.selectList(any())).thenReturn(List.of(user(2L, 1), user(3L, 1)));
        when(userProjectRoleMapper.selectAssignedRoles(2L, 9L)).thenReturn(List.of(qualityRole));

        var result = service.listAssignmentOptions(9L, "质量", "ALL", 1, 100, operator);

        assertEquals(1L, result.getTotal());
        assertEquals(2L, result.getRecords().get(0).getUserId());
        assertEquals("质量编辑员", result.getRecords().get(0).getProjectRoles().get(0).getRoleName());
    }

    @Test
    void projectBatchAddsAndRemovesMembersThenInvalidatesEachUserOnce() {
        SysUserProject removed = relation(6L, 9L, 3L, "ACTIVE");
        when(userProjectMapper.selectByProjectAndUserForUpdate(9L, 2L)).thenReturn(null);
        when(userProjectMapper.selectByProjectAndUserForUpdate(9L, 3L)).thenReturn(removed);
        when(userProjectMapper.selectMembersByProjectId(9L)).thenReturn(List.of(member(6L, 9L, 3L, "ACTIVE")));
        when(userMapper.selectById(2L)).thenReturn(user(2L, 1));
        when(systemRoleMapper.selectById(30L)).thenReturn(role(30L, "USER", 0));

        var result = service.batchUpdateProjectAssignments(9L, projectBatch(
                projectChange(2L, "UPSERT", List.of(30L)),
                projectChange(3L, "REMOVE", List.of())), operator);

        assertEquals(1, result.size());
        verify(userProjectMapper).insert(any(SysUserProject.class));
        verify(userProjectMapper).deleteById(6L);
        verify(authService, times(1)).logout(2L);
        verify(authService, times(1)).logout(3L);
        verify(operationLogMapper, times(1)).insert(any());
        InOrder lockOrder = inOrder(projectInfoMapper, userMapper, userProjectMapper);
        lockOrder.verify(projectInfoMapper).selectByIdForUpdate(9L);
        lockOrder.verify(userMapper).selectByIdForUpdate(2L);
        lockOrder.verify(userMapper).selectByIdForUpdate(3L);
        lockOrder.verify(userProjectMapper).selectByProjectAndUserForUpdate(9L, 2L);
        lockOrder.verify(userProjectMapper).selectByProjectAndUserForUpdate(9L, 3L);
    }

    @Test
    void userBatchChangesMultipleProjectsButInvalidatesTargetOnce() {
        SysUserProject removed = relation(7L, 10L, 2L, "ACTIVE");
        when(userMapper.selectByIdForUpdate(2L)).thenReturn(user(2L, 1));
        when(userMapper.selectById(2L)).thenReturn(user(2L, 1));
        when(userProjectMapper.selectByProjectAndUserForUpdate(9L, 2L)).thenReturn(null);
        when(userProjectMapper.selectByProjectAndUserForUpdate(10L, 2L)).thenReturn(removed);
        when(systemRoleMapper.selectById(30L)).thenReturn(role(30L, "USER", 0));
        when(userProjectMapper.selectMembersByProjectId(10L)).thenReturn(List.of(member(7L, 10L, 2L, "ACTIVE")));
        when(userProjectMapper.selectUserProjectRolesForManagement(2L)).thenReturn(List.of());

        List<UserProjectRoleVO> result = service.batchUpdateUserProjectAssignments(2L, userBatch(
                userChange(9L, "UPSERT", List.of(30L)),
                userChange(10L, "REMOVE", List.of())), operator);

        assertTrue(result.isEmpty());
        verify(authService, times(1)).logout(2L);
        verify(authService, times(1)).repeatLogoutAfterCommit(2L);
        verify(operationLogMapper, times(1)).insert(any());
    }

    @Test
    void completeProjectRemovalRequiresImpactConfirmationThenReleasesResponsibilities() {
        SysUserProject existing = relation(7L, 10L, 2L, "ACTIVE");
        when(userMapper.selectByIdForUpdate(2L)).thenReturn(user(2L, 1));
        when(userProjectMapper.selectByProjectAndUserForUpdate(10L, 2L)).thenReturn(existing);
        when(userProjectMapper.selectMembersByProjectId(10L)).thenReturn(List.of(member(7L, 10L, 2L, "ACTIVE")));
        when(responsibilityReleaseService.impact(10L, 2L)).thenReturn(responsibilityImpact(10L, 2L, 3));

        BusinessException blocked = assertThrows(BusinessException.class,
                () -> service.batchUpdateUserProjectAssignments(2L,
                        userBatch(userChange(10L, "REMOVE", List.of())), operator));

        assertEquals(409, blocked.getCode());
        verify(userProjectMapper, never()).deleteById(7L);
        verify(responsibilityReleaseService, never()).releaseAll(10L, 2L);

        UserProjectRoleBatchRequest confirmed = userBatch(userChange(10L, "REMOVE", List.of()));
        confirmed.setConfirmResponsibilityRelease(true);
        service.batchUpdateUserProjectAssignments(2L, confirmed, operator);

        verify(userProjectMapper).deleteById(7L);
        verify(responsibilityReleaseService).releaseAll(10L, 2L);
        verify(authService).logout(2L);
        verify(authService).repeatLogoutAfterCommit(2L);
    }

    @Test
    void partialRoleRevocationRechecksCapabilitiesAndKeepsBusinessRecords() {
        SysUserProject existing = relation(8L, 9L, 2L, "ACTIVE");
        when(userMapper.selectByIdForUpdate(2L)).thenReturn(user(2L, 1));
        when(userMapper.selectById(2L)).thenReturn(user(2L, 1));
        when(userProjectMapper.selectByProjectAndUserForUpdate(9L, 2L)).thenReturn(existing);
        when(userProjectMapper.selectMembersByProjectId(9L)).thenReturn(List.of(member(8L, 9L, 2L, "ACTIVE")));
        when(systemRoleMapper.selectById(30L)).thenReturn(role(30L, "USER", 0));
        when(responsibilityReleaseService.impact(9L, 2L)).thenReturn(responsibilityImpact(9L, 2L, 1));
        UserProjectRoleBatchRequest confirmed = userBatch(userChange(9L, "UPSERT", List.of(30L)));
        confirmed.setConfirmResponsibilityRelease(true);

        service.batchUpdateUserProjectAssignments(2L, confirmed, operator);

        verify(userProjectMapper).updateById(existing);
        verify(responsibilityReleaseService).releaseForCapabilityLoss(9L, 2L);
        verify(responsibilityReleaseService, never()).releaseAll(9L, 2L);
        verify(userProjectMapper, never()).deleteById(8L);
    }

    @Test
    void projectManagerCannotGrantProtectedManagerRoleInBatch() {
        when(projectPermissionService.isPlatformAdmin(1L)).thenReturn(false);
        when(userMapper.selectById(2L)).thenReturn(user(2L, 1));
        when(systemRoleMapper.selectById(40L)).thenReturn(role(40L, "PROJECT_ADMIN", 1));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.batchUpdateProjectAssignments(9L, projectBatch(
                        projectChange(2L, "UPSERT", List.of(40L))), operator));

        assertTrue(exception.getMessage().contains("项目经理角色只能由系统管理员"));
        verify(userProjectMapper, never()).insert(any(SysUserProject.class));
    }

    @Test
    void roleBatchPreservesPausedProjectAccessStatus() {
        SysUserProject paused = relation(8L, 9L, 2L, "DISABLED");
        when(userProjectMapper.selectByProjectAndUserForUpdate(9L, 2L)).thenReturn(paused);
        when(userProjectMapper.selectMembersByProjectId(9L)).thenReturn(List.of(member(8L, 9L, 2L, "DISABLED")));
        when(userMapper.selectById(2L)).thenReturn(user(2L, 1));
        when(systemRoleMapper.selectById(30L)).thenReturn(role(30L, "USER", 0));

        service.batchUpdateProjectAssignments(9L,
                projectBatch(projectChange(2L, "UPSERT", List.of(30L))), operator);

        verify(userProjectMapper).updateById(argThat(relation -> "DISABLED".equals(relation.getStatus())));
    }

    @Test
    void nonPlatformAdministratorCannotUseCrossProjectBatch() {
        when(projectPermissionService.isPlatformAdmin(1L)).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.batchUpdateUserProjectAssignments(2L,
                        userBatch(userChange(9L, "UPSERT", List.of(30L))), operator));

        assertEquals(403, exception.getCode());
        verify(projectInfoMapper, never()).selectByIdForUpdate(any());
        verify(userProjectMapper, never()).insert(any(SysUserProject.class));
    }

    @Test
    void blockedRemovalPreventsEveryWriteInBatch() {
        when(userProjectMapper.selectByProjectAndUserForUpdate(9L, 2L))
                .thenReturn(relation(5L, 9L, 2L, "ACTIVE"));
        when(userProjectMapper.selectByProjectAndUserForUpdate(9L, 3L))
                .thenReturn(relation(6L, 9L, 3L, "ACTIVE"));
        when(userProjectMapper.selectMembersByProjectId(9L)).thenReturn(List.of(
                member(5L, 9L, 2L, "ACTIVE"), member(6L, 9L, 3L, "ACTIVE")));
        when(qualityAssigneeService.countOpenAssignments(9L, 2L)).thenReturn(0L);
        when(qualityAssigneeService.countOpenAssignments(9L, 3L)).thenReturn(1L);

        assertThrows(BusinessException.class, () -> service.batchUpdateProjectAssignments(9L, projectBatch(
                projectChange(2L, "REMOVE", List.of()),
                projectChange(3L, "REMOVE", List.of())), operator));

        verify(userProjectMapper, never()).deleteById(any(Long.class));
        verify(authService, never()).logout(any());
        verify(operationLogMapper, never()).insert(any());
    }

    @Test
    void duplicateBatchNodeIsRejectedBeforeLockingOrWriting() {
        assertThrows(BusinessException.class, () -> service.batchUpdateProjectAssignments(9L, projectBatch(
                projectChange(2L, "REMOVE", List.of()),
                projectChange(2L, "REMOVE", List.of())), operator));

        verify(userProjectMapper, never()).selectByProjectAndUserForUpdate(any(), any());
        verify(userProjectMapper, never()).deleteById(any(Long.class));
    }

    private ProjectMemberBatchRequest projectBatch(ProjectMemberBatchRequest.Change... changes) {
        ProjectMemberBatchRequest request = new ProjectMemberBatchRequest();
        request.setChanges(List.of(changes));
        return request;
    }

    private ProjectMemberBatchRequest.Change projectChange(Long userId, String operation, List<Long> roleIds) {
        ProjectMemberBatchRequest.Change change = new ProjectMemberBatchRequest.Change();
        change.setUserId(userId);
        change.setOperation(operation);
        change.setRoleIds(roleIds);
        return change;
    }

    private UserProjectRoleBatchRequest userBatch(UserProjectRoleBatchRequest.Change... changes) {
        UserProjectRoleBatchRequest request = new UserProjectRoleBatchRequest();
        request.setChanges(List.of(changes));
        return request;
    }

    private UserProjectRoleBatchRequest.Change userChange(Long projectId, String operation, List<Long> roleIds) {
        UserProjectRoleBatchRequest.Change change = new UserProjectRoleBatchRequest.Change();
        change.setProjectId(projectId);
        change.setOperation(operation);
        change.setRoleIds(roleIds);
        return change;
    }

    private SysUser user(Long id, int status) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername("user_" + id);
        user.setRealName("用户" + id);
        user.setStatus(status);
        user.setDeleted(0);
        return user;
    }

    private ProjectInfo project(Long id) {
        ProjectInfo project = new ProjectInfo();
        project.setId(id);
        project.setProjectName("项目" + id);
        project.setDeleted(0);
        return project;
    }

    private SystemRole role(Long id, String code, int manager) {
        SystemRole role = new SystemRole();
        role.setId(id);
        role.setRoleCode(code);
        role.setRoleName(code);
        role.setScopeType("PROJECT");
        role.setProjectManagerRole(manager);
        role.setEnabled(1);
        role.setDeleted(0);
        return role;
    }

    private SysUserProject relation(Long id, Long projectId, Long userId, String status) {
        SysUserProject relation = new SysUserProject();
        relation.setId(id);
        relation.setProjectId(projectId);
        relation.setUserId(userId);
        relation.setStatus(status);
        return relation;
    }

    private ProjectMemberVO member(Long memberId, Long projectId, Long userId, String accessStatus) {
        ProjectMemberVO member = new ProjectMemberVO();
        member.setMemberId(memberId);
        member.setProjectId(projectId);
        member.setUserId(userId);
        member.setUsername("user_" + userId);
        member.setRealName("用户" + userId);
        member.setAccessStatus(accessStatus);
        member.setResponsibleBoxCount(0);
        member.setPendingRectificationCount(0);
        return member;
    }

    private ResponsibilityImpactVO responsibilityImpact(Long projectId, Long userId, long openQualityIssues) {
        ResponsibilityImpactVO impact = new ResponsibilityImpactVO();
        impact.setProjectId(projectId);
        impact.setUserId(userId);
        impact.setProjectName("项目" + projectId);
        impact.setOpenQualityIssueCount(openQualityIssues);
        return impact;
    }
}
