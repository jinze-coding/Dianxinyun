package com.example.siteplatform.project.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.log.mapper.OperationLogMapper;
import com.example.siteplatform.log.entity.OperationLog;
import com.example.siteplatform.project.dto.*;
import com.example.siteplatform.project.dto.ProjectAccessBatchRequest.*;
import com.example.siteplatform.project.entity.*;
import com.example.siteplatform.project.mapper.*;
import com.example.siteplatform.system.constant.BusinessModuleCodes;
import com.example.siteplatform.system.entity.*;
import com.example.siteplatform.system.mapper.*;
import com.example.siteplatform.system.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProjectAccessBatchServiceTest {
    final SysUserMapper users = mock(SysUserMapper.class);
    final SysUserProjectMapper memberships = mock(SysUserProjectMapper.class);
    final SysUserProjectRoleMapper assignments = mock(SysUserProjectRoleMapper.class);
    final ProjectInfoMapper projects = mock(ProjectInfoMapper.class);
    final SystemRoleMapper roles = mock(SystemRoleMapper.class);
    final SystemPermissionMapper permissions = mock(SystemPermissionMapper.class);
    final SystemRoleBusinessModuleMapper roleModules = mock(SystemRoleBusinessModuleMapper.class);
    final SystemPermissionService authorization = mock(SystemPermissionService.class);
    final ProjectBusinessModuleService modules = mock(ProjectBusinessModuleService.class);
    final ProjectMemberService members = mock(ProjectMemberService.class);
    final ResponsibilityReleaseService responsibilities = mock(ResponsibilityReleaseService.class);
    final OperationLogMapper audit = mock(OperationLogMapper.class);
    final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    final ValueOperations<String,String> values = mock(ValueOperations.class);
    final Map<String,String> tokens = new HashMap<>();
    final Map<String,SysUserProject> relations = new HashMap<>();
    final Map<String,List<Long>> roleIds = new HashMap<>();
    final Map<Long,SystemRole> roleCatalog = new HashMap<>();
    ProjectAccessBatchService service;
    SysUser operator;

    @BeforeEach void setup() {
        var builder = new MapperBuilderAssistant(new MybatisConfiguration(), "batch-test");
        TableInfoHelper.initTableInfo(builder, SysUserProject.class);
        TableInfoHelper.initTableInfo(builder, SysUserProjectRole.class);
        when(users.selectById(any())).thenAnswer(i -> user(i.getArgument(0)));
        when(users.selectByIdForUpdate(any())).thenAnswer(i -> user(i.getArgument(0)));
        operator = user(1L);
        when(projects.selectById(any())).thenAnswer(i -> project(i.getArgument(0)));
        when(projects.selectByIdForUpdate(any())).thenAnswer(i -> project(i.getArgument(0)));
        when(modules.state(any())).thenAnswer(i -> new ProjectBusinessModuleService.State(i.getArgument(0), BusinessModuleCodes.ALL, 1));
        when(memberships.selectOne(any())).thenAnswer(i -> relations.get(key(i.getArgument(0))));
        when(assignments.selectList(any())).thenAnswer(i -> roleIds.getOrDefault(key(i.getArgument(0)), List.of()).stream().map(id -> {
            SysUserProjectRole value = new SysUserProjectRole(); value.setRoleId(id); return value;
        }).toList());
        for (long id : List.of(10L, 20L, 30L)) {
            SystemRole role = new SystemRole(); role.setId(id); role.setRoleCode("ROLE_" + id); role.setRoleName("角色" + id);
            role.setScopeType("PROJECT"); role.setEnabled(1); role.setDeleted(0); roleCatalog.put(id, role);
        }
        when(roles.selectById(any())).thenAnswer(i -> roleCatalog.get(i.getArgument(0)));
        when(roles.selectPermissionIds(any())).thenReturn(List.of());
        when(roles.selectMenuIds(any())).thenReturn(List.of());
        when(roleModules.selectModuleCodesByRoleId(any())).thenReturn(BusinessModuleCodes.ALL);
        when(audit.insert(any())).thenReturn(1);
        when(redis.opsForValue()).thenReturn(values);
        doAnswer(i -> { tokens.put(i.getArgument(0), i.getArgument(1)); return null; }).when(values).set(anyString(), anyString(), any(Duration.class));
        when(values.get(anyString())).thenAnswer(i -> tokens.get(i.getArgument(0)));
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenAnswer(i -> {
            String key = ((List<String>) i.getArgument(1)).get(0);
            String expected = (String) i.getArgument(2);
            return tokens.remove(key, expected) ? 1L : 0L;
        });
        when(responsibilities.impact(any(), any())).thenAnswer(i -> {
            var impact = new ResponsibilityImpactVO(); impact.setProjectId(i.getArgument(0)); impact.setUserId(i.getArgument(1)); impact.setSealApprovalConfigCount(1); return impact;
        });
        service = new ProjectAccessBatchService(users, memberships, assignments, projects, roles, permissions, roleModules,
                authorization, modules, members, responsibilities, audit, redis, new ObjectMapper().findAndRegisterModules());
    }

    @Test void addingRolesUnionsEveryUserAndDoesNotTouchSealOrStaleResponsibilities() {
        member(2, 9, "ACTIVE", 10); member(3, 9, "ACTIVE", 30);
        var request = request(Operation.ADD_ROLES, 2L, 3L); request.setRoleIds(List.of(20L));
        var preview = service.preview(request, operator);
        assertEquals(2, preview.getChangedUserCount()); assertEquals(0, preview.getResponsibilityCount());
        assertEquals(List.of(10L,20L), afterIds(preview,0)); assertEquals(List.of(20L,30L), afterIds(preview,1));
        service.confirm(confirm(preview, false), operator);
        verifyNoInteractions(responsibilities);
        verify(members).invalidateUsers(Set.of(2L,3L));
        verify(members, times(2)).applyValidatedBatchChange(eq(9L), anyLong(), anyList(), any(), eq(false), eq("ACTIVE"), eq(operator));
    }

    @Test void copyMergesExistingTargetAndPreservesAccessPause() {
        member(2, 9, "ACTIVE", 10); member(2, 8, "DISABLED", 30);
        var preview = service.preview(request(Operation.COPY_PROJECT, 2L), operator);
        assertEquals(List.of(10L,30L), preview.getUsers().get(0).getProjects().get(1).getAfterRoles().stream().map(ProjectAccessBatchPreview.Role::id).toList());
        assertEquals("DISABLED", preview.getUsers().get(0).getProjects().get(1).getAfterStatus());
        service.confirm(confirm(preview, false), operator);
        verify(members, never()).applyValidatedBatchChange(eq(9L), any(), anyList(), any(), anyBoolean(), any(), any());
        verifyNoInteractions(responsibilities);
    }

    @Test void auditKeepsSupplementaryCharactersIntactAcrossEveryChunk() throws Exception {
        for (String prefix : List.of("", "工")) {
            clearInvocations(audit);
            ProjectInfo project = project(9L);
            project.setProjectName(prefix + "𠮷".repeat(180));
            when(projects.selectById(9L)).thenReturn(project);
            var preview = service.preview(request(Operation.ADD_ROLES, 2L), operator);
            service.confirm(confirm(preview, false), operator);
            var captured = org.mockito.ArgumentCaptor.forClass(OperationLog.class);
            verify(audit, atLeast(2)).insert(captured.capture());
            StringBuilder restored = new StringBuilder();
            for (OperationLog log : captured.getAllValues()) {
                String description = log.getOperationDesc();
                assertTrue(java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(description));
                assertTrue(description.codePointCount(0, description.length()) <= 500);
                restored.append(description.substring(description.indexOf(" 分段=")).split(" ", 3)[2]);
            }
            assertEquals(project.getProjectName(), new ObjectMapper().readTree(restored.toString()).get("projectName").asText());
        }
    }

    @Test void newTargetInheritsSourcePauseAndSelectedRolesOverrideRoleSourceOnly() {
        member(2, 9, "DISABLED", 10);
        var request = request(Operation.COPY_PROJECT, 2L); request.setRoleSource(RoleSource.SELECTED); request.setRoleIds(List.of(20L));
        var preview = service.preview(request, operator);
        var target = preview.getUsers().get(0).getProjects().get(1);
        assertEquals("DISABLED", target.getAfterStatus()); assertEquals(List.of(20L), target.getAfterRoles().stream().map(ProjectAccessBatchPreview.Role::id).toList());
        service.confirm(confirm(preview, false), operator);
        verify(members).applyValidatedBatchChange(eq(8L), eq(2L), anyList(), isNull(), eq(false), eq("DISABLED"), eq(operator));
    }

    @Test void moveRequiresAcknowledgementAndGrantsTargetBeforeRemovingSource() {
        member(2, 9, "ACTIVE", 10);
        var preview = service.preview(request(Operation.MOVE_PROJECT, 2L), operator);
        assertEquals(1, preview.getRemovedCount()); assertEquals(1, preview.getAddedCount()); assertEquals(1, preview.getResponsibilityCount());
        assertEquals(409, assertThrows(BusinessException.class, () -> service.confirm(confirm(preview,false),operator)).getCode());
        verifyNoInteractions(members);
        service.confirm(confirm(preview,true),operator);
        var order = inOrder(members);
        order.verify(members).applyValidatedBatchChange(eq(8L), eq(2L), anyList(), isNull(), eq(false), eq("ACTIVE"), eq(operator));
        order.verify(members).applyValidatedBatchChange(eq(9L), eq(2L), eq(List.of()), any(), eq(true), isNull(), eq(operator));
        verify(responsibilities).releaseAll(9L,2L);
    }

    @Test void lastRoleAndAnyInvalidUserBlockWholeBatch() {
        member(2, 9, "ACTIVE", 10); member(3, 9, "ACTIVE", 10,20);
        var request = request(Operation.REMOVE_ROLES, 2L,3L); request.setRoleIds(List.of(10L));
        var preview = service.preview(request,operator);
        assertEquals(1,preview.getBlockedUserCount()); assertNull(preview.getConfirmationToken()); assertTrue(tokens.isEmpty());
        assertTrue(preview.getUsers().get(0).getErrors().get(0).contains("不会自动移出"));
    }

    @Test void disabledAndPlatformUsersAreReportedWithoutGrantingAnything() {
        var disabled = user(2L); disabled.setStatus(0); when(users.selectById(2L)).thenReturn(disabled);
        when(authorization.isPlatformAdmin(3L)).thenReturn(true);
        var preview=service.preview(request(Operation.ADD_ROLES,2L,3L),operator);
        assertEquals(2,preview.getBlockedUserCount()); assertNull(preview.getConfirmationToken()); verifyNoInteractions(members);
    }

    @Test void sourceMembershipAndRetiredRolesCannotBeSilentlySkipped() {
        member(2,9,"ACTIVE",10); roleCatalog.get(10L).setEnabled(0);
        var preview=service.preview(request(Operation.COPY_PROJECT,2L,3L),operator);
        assertEquals(2,preview.getBlockedUserCount()); assertNull(preview.getConfirmationToken());
    }

    @Test void unchangedRoleSetDoesNotIssueTokenOrInvalidateSessions() {
        member(2,9,"ACTIVE",20);
        var preview=service.preview(request(Operation.ADD_ROLES,2L),operator);
        assertEquals(1,preview.getUnchangedUserCount()); assertNull(preview.getConfirmationToken()); verifyNoInteractions(members);
    }

    @Test void changedMembershipAfterPreviewRejectsEntireBatch() {
        member(2,9,"ACTIVE",10);
        var preview=service.preview(request(Operation.ADD_ROLES,2L),operator);
        member(2,9,"DISABLED",10);
        assertEquals(409, assertThrows(BusinessException.class,()->service.confirm(confirm(preview,false),operator)).getCode());
        verifyNoInteractions(members);
    }

    @Test void changedRoleDefinitionAndProjectModulesInvalidatePreview() {
        member(2,9,"ACTIVE",10);
        var preview=service.preview(request(Operation.ADD_ROLES,2L),operator);
        roleCatalog.get(20L).setRoleName("并发改名");
        assertEquals(409,assertThrows(BusinessException.class,()->service.confirm(confirm(preview,false),operator)).getCode());
        var next=service.preview(request(Operation.ADD_ROLES,2L),operator);
        when(modules.state(9L)).thenReturn(new ProjectBusinessModuleService.State(9L,List.of(),2));
        assertEquals(409,assertThrows(BusinessException.class,()->service.confirm(confirm(next,false),operator)).getCode());
        verifyNoInteractions(members);
    }

    @Test void changedResponsibilityCountInvalidatesMigrationPreview() {
        member(2,9,"ACTIVE",10);
        var preview=service.preview(request(Operation.MOVE_PROJECT,2L),operator);
        var impact=new ResponsibilityImpactVO(); impact.setProjectId(9L); impact.setUserId(2L); impact.setSealApprovalConfigCount(2);
        when(responsibilities.impact(9L,2L)).thenReturn(impact);
        assertThrows(BusinessException.class,()->service.confirm(confirm(preview,true),operator)); verifyNoInteractions(members);
    }

    @Test void confirmationIsBoundToOperatorAndConsumedOnce() {
        member(2,9,"ACTIVE",10);
        var preview=service.preview(request(Operation.ADD_ROLES,2L),operator);
        assertEquals(403,assertThrows(BusinessException.class,()->service.confirm(confirm(preview,false),user(4L))).getCode());
        service.confirm(confirm(preview,false),operator);
        assertEquals(409,assertThrows(BusinessException.class,()->service.confirm(confirm(preview,false),operator)).getCode());
        verify(members,times(1)).invalidateUsers(anySet());
    }

    @Test void missingTokenAndRevokedOperatorCannotExecute() {
        var confirm=new ProjectAccessBatchConfirmRequest(); confirm.setConfirmationToken(UUID.randomUUID().toString());
        assertThrows(BusinessException.class,()->service.confirm(confirm,operator));
        doThrow(BusinessException.forbidden("无权")).when(authorization).requirePlatformAdmin(operator);
        assertEquals(403,assertThrows(BusinessException.class,()->service.preview(request(Operation.ADD_ROLES,2L),operator)).getCode());
    }

    @Test void invalidBatchShapeIsRejectedBeforeAnyPlan() {
        assertThrows(BusinessException.class,()->service.preview(request(Operation.ADD_ROLES,2L,2L),operator));
        var request=request(Operation.MOVE_PROJECT,2L); request.setTargetProjectId(9L);
        assertThrows(BusinessException.class,()->service.preview(request,operator));
        request.setTargetProjectId(8L); request.setUserIds(java.util.stream.LongStream.rangeClosed(1,201).boxed().toList());
        assertThrows(BusinessException.class,()->service.preview(request,operator));
    }

    @Test void roleReplacementReportsOnlyActualLostCapabilitiesAndNeverDirectSealAssignment() {
        member(2,9,"ACTIVE",10);
        SystemPermission permission=new SystemPermission(); permission.setId(100L); permission.setPermissionCode("quality.rectify"); permission.setPermissionName("质量整改"); permission.setEnabled(1); permission.setDeleted(0);
        when(roles.selectPermissionIds(10L)).thenReturn(List.of(100L)); when(permissions.selectBatchIds(any())).thenReturn(List.of(permission));
        when(responsibilities.impact(9L,2L)).thenAnswer(i->{var result=new ResponsibilityImpactVO(); result.setProjectId(9L); result.setUserId(2L); result.setOpenQualityIssueCount(2); result.setSealApprovalConfigCount(1); result.setOpenGeneralRectificationCount(3);return result;});
        var preview=service.preview(request(Operation.REPLACE_ROLES,2L),operator);
        assertEquals(2,preview.getResponsibilityCount());
        assertEquals(List.of("质量整改"),preview.getUsers().get(0).getProjects().get(0).getRemovedPermissions());
        service.confirm(confirm(preview,true),operator);
        verify(responsibilities).releasePreviewedImpact(argThat(i->i.getOpenQualityIssueCount()==2 && i.getSealApprovalConfigCount()==0 && i.getOpenGeneralRectificationCount()==0));
        verify(responsibilities,never()).releaseAll(any(),any());
    }

    private String key(LambdaQueryWrapper<?> wrapper) { wrapper.getSqlSegment(); var p=wrapper.getParamNameValuePairs(); return p.get("MPGENVAL1")+":"+p.get("MPGENVAL2"); }
    private void member(long user,long project,String status,long... ids) { var value=new SysUserProject();value.setId(project*100+user);value.setUserId(user);value.setProjectId(project);value.setStatus(status);relations.put(project+":"+user,value);roleIds.put(project+":"+user,Arrays.stream(ids).sorted().boxed().toList()); }
    private SysUser user(Long id) {var value=new SysUser();value.setId(id);value.setStatus(1);value.setDeleted(0);value.setUsername("synthetic-"+id);value.setRealName("测试"+id);return value;}
    private ProjectInfo project(Long id) {var value=new ProjectInfo();value.setId(id);value.setProjectName("测试项目"+id);return value;}
    private ProjectAccessBatchRequest request(Operation op,Long... ids) {var value=new ProjectAccessBatchRequest();value.setUserIds(List.of(ids));value.setOperation(op);value.setProjectId(9L);value.setSourceProjectId(9L);value.setTargetProjectId(8L);value.setRoleIds(List.of(20L));return value;}
    private ProjectAccessBatchConfirmRequest confirm(ProjectAccessBatchPreview preview,boolean acknowledged) {var value=new ProjectAccessBatchConfirmRequest();value.setConfirmationToken(preview.getConfirmationToken());value.setConfirmResponsibilityRelease(acknowledged);return value;}
    private List<Long> afterIds(ProjectAccessBatchPreview preview,int index) {return preview.getUsers().get(index).getProjects().get(0).getAfterRoles().stream().map(ProjectAccessBatchPreview.Role::id).toList();}
}
