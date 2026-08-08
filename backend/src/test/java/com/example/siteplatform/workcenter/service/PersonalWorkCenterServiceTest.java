package com.example.siteplatform.workcenter.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.common.PageResult;
import com.example.siteplatform.inspection.service.InspectionService;
import com.example.siteplatform.inspection.vo.InspectionTodoVO;
import com.example.siteplatform.notification.entity.UserNotification;
import com.example.siteplatform.notification.mapper.UserNotificationMapper;
import com.example.siteplatform.project.entity.ProjectInfo;
import com.example.siteplatform.project.entity.SysUserProject;
import com.example.siteplatform.project.mapper.ProjectInfoMapper;
import com.example.siteplatform.project.mapper.SysUserProjectMapper;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.quality.service.QualityIssueService;
import com.example.siteplatform.quality.vo.QualityTodoVO;
import com.example.siteplatform.seal.entity.SealApplication;
import com.example.siteplatform.seal.mapper.SealApplicationMapper;
import com.example.siteplatform.system.constant.SystemPermissionCodes;
import com.example.siteplatform.workcenter.vo.InboxNotificationVO;
import com.example.siteplatform.workcenter.vo.PersonalTodoVO;
import com.example.siteplatform.workcenter.vo.WorkSummaryVO;
import com.example.siteplatform.workflow.entity.WorkflowApprovalTask;
import com.example.siteplatform.workflow.entity.WorkflowCcRecipient;
import com.example.siteplatform.workflow.mapper.WorkflowApprovalTaskMapper;
import com.example.siteplatform.workflow.mapper.WorkflowCcRecipientMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersonalWorkCenterServiceTest {

    @Mock private InspectionService inspectionService;
    @Mock private QualityIssueService qualityIssueService;
    @Mock private WorkflowApprovalTaskMapper approvalTaskMapper;
    @Mock private WorkflowCcRecipientMapper ccRecipientMapper;
    @Mock private SealApplicationMapper sealApplicationMapper;
    @Mock private UserNotificationMapper notificationMapper;
    @Mock private ProjectPermissionService projectPermissionService;
    @Mock private ProjectInfoMapper projectInfoMapper;
    @Mock private SysUserProjectMapper userProjectMapper;

    private PersonalWorkCenterService service;
    private SysUser currentUser;

    @BeforeEach
    void setUp() {
        service = new PersonalWorkCenterService(
                inspectionService,
                qualityIssueService,
                approvalTaskMapper,
                ccRecipientMapper,
                sealApplicationMapper,
                notificationMapper,
                projectPermissionService,
                projectInfoMapper,
                userProjectMapper,
                new ObjectMapper());
        currentUser = enabledUser(7L);
    }

    @Test
    void stableKeyIgnoresLegacyTemporaryInspectionId() {
        ProjectInfo project = project(10L, "项目甲");
        when(projectInfoMapper.selectById(10L)).thenReturn(project);
        when(projectPermissionService.hasSystemPermission(
                7L, 10L, SystemPermissionCodes.QUALITY_VIEW)).thenReturn(true);
        when(qualityIssueService.listTodos(10L, currentUser)).thenReturn(List.of());
        when(userProjectMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(membership(7L, 10L)));

        InspectionTodoVO first = inspectionTodo(1L, 88L, "INSPECTION");
        InspectionTodoVO second = inspectionTodo(999L, 88L, "INSPECTION");
        when(inspectionService.listTodos(10L, currentUser))
                .thenReturn(List.of(first), List.of(second));

        PageResult<PersonalTodoVO> firstResult = service.listTodos(
                "PENDING", 10L, null, 1, 20, currentUser);
        PageResult<PersonalTodoVO> secondResult = service.listTodos(
                "PENDING", 10L, null, 1, 20, currentUser);

        assertEquals("INSPECTION:10:INSPECTION:88", firstResult.getRecords().get(0).getTodoKey());
        assertEquals(firstResult.getRecords().get(0).getTodoKey(),
                secondResult.getRecords().get(0).getTodoKey());
        assertEquals(88L, secondResult.getRecords().get(0).getId());
        assertEquals("INSPECTION_FORM", secondResult.getRecords().get(0).getRouteCode());
        assertEquals(88L, secondResult.getRecords().get(0).getRouteParams().get("boxId"));
    }

    @ParameterizedTest(name = "type={0}")
    @ValueSource(strings = {
            "ALL",
            "SEAL", "SEAL_APPLICATION",
            "QUALITY", "QUALITY_ISSUE",
            "INSPECTION_RECORD",
            "INSPECTION", "REVIEW", "RECTIFICATION", "RECHECK", "SEAL_APPROVAL"
    })
    void everySupportedTodoTypeReturnsSortableResults(String type) {
        stubMixedTodoSources();

        PageResult<PersonalTodoVO> result = assertDoesNotThrow(() -> service.listTodos(
                "PENDING", 10L, type, 1, 100, currentUser));

        assertFalse(result.getRecords().isEmpty());
    }

    @Test
    void unsupportedTodoTypeRemainsRejected() {
        stubMixedTodoSources();

        BusinessException error = assertThrows(BusinessException.class, () -> service.listTodos(
                "PENDING", 10L, "UNKNOWN", 1, 20, currentUser));

        assertEquals("不支持的待办类型: UNKNOWN", error.getMessage());
    }

    @Test
    void sealPendingRequiresPendingApplicationAndActiveProjectMembership() {
        ProjectInfo firstProject = project(10L, "项目甲");
        ProjectInfo secondProject = project(11L, "项目乙");
        when(projectPermissionService.getUserProjects(7L)).thenReturn(List.of(firstProject, secondProject));
        when(inspectionService.listTodos(null, currentUser)).thenReturn(List.of());
        when(qualityIssueService.listTodos(null, currentUser)).thenReturn(List.of());

        when(userProjectMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(membership(7L, 10L)));

        WorkflowApprovalTask valid = task(1L, 100L, 10L, 500L);
        WorkflowApprovalTask decidedApplication = task(2L, 101L, 10L, 501L);
        WorkflowApprovalTask noMembership = task(3L, 102L, 11L, 502L);
        when(approvalTaskMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(valid, decidedApplication, noMembership));

        SealApplication pending = application(100L, 10L, 500L, "PENDING_APPROVAL");
        SealApplication approved = application(101L, 10L, 501L, "APPROVED");
        SealApplication otherProject = application(102L, 11L, 502L, "PENDING_APPROVAL");
        when(sealApplicationMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(pending, approved, otherProject));

        PageResult<PersonalTodoVO> result = service.listTodos(
                "PENDING", null, null, 1, 20, currentUser);

        assertEquals(1L, result.getTotal());
        assertEquals("SEAL_APPROVAL:TASK:1", result.getRecords().get(0).getTodoKey());
        assertEquals(100L, result.getRecords().get(0).getTargetId());
        assertFalse(result.getRecords().get(0).getReadOnly());
    }

    @Test
    void ccInboxHidesDraftSelectionsUntilApplicationIsSubmitted() {
        when(projectInfoMapper.selectById(10L)).thenReturn(project(10L, "项目甲"));
        when(userProjectMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(membership(7L, 10L)));

        WorkflowCcRecipient draftCc = new WorkflowCcRecipient();
        draftCc.setId(30L);
        draftCc.setBusinessCode("SEAL_APPLICATION");
        draftCc.setBusinessId(100L);
        draftCc.setProjectId(10L);
        draftCc.setUserId(7L);
        draftCc.setCreateTime(LocalDateTime.now());
        WorkflowCcRecipient submittedCc = new WorkflowCcRecipient();
        submittedCc.setId(31L);
        submittedCc.setBusinessCode("SEAL_APPLICATION");
        submittedCc.setBusinessId(101L);
        submittedCc.setProjectId(10L);
        submittedCc.setUserId(7L);
        submittedCc.setCreateTime(LocalDateTime.now());
        when(ccRecipientMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(draftCc, submittedCc));
        when(sealApplicationMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                application(100L, 10L, null, "DRAFT"),
                application(101L, 10L, 501L, "PENDING_APPROVAL")));

        PageResult<PersonalTodoVO> result = service.listTodos(
                "CC", 10L, null, 1, 20, currentUser);

        assertEquals(1L, result.getTotal());
        assertEquals(101L, result.getRecords().get(0).getTargetId());
        assertTrue(result.getRecords().get(0).getReadOnly());
    }

    @Test
    void markReadIsOwnedScopedAndIdempotent() {
        ProjectInfo project = project(10L, "项目甲");
        when(projectInfoMapper.selectById(10L)).thenReturn(project);
        when(userProjectMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(membership(7L, 10L)));
        UserNotification unread = notification(20L, 7L, 10L, 0);
        when(notificationMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(unread);
        when(notificationMapper.markRead(eq(20L), eq(7L), any(LocalDateTime.class))).thenReturn(1);

        InboxNotificationVO changed = service.markRead(20L, currentUser);

        assertTrue(changed.getIsRead());
        assertEquals("READ", changed.getReadStatus());
        assertEquals(100L, changed.getRouteParams().get("applicationId"));
        ArgumentCaptor<LocalDateTime> readTime = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(notificationMapper).markRead(eq(20L), eq(7L), readTime.capture());
        assertTrue(Math.abs(Duration.between(
                readTime.getValue(), LocalDateTime.now(ZoneId.of("Asia/Shanghai"))).toSeconds()) < 5);

        UserNotification alreadyRead = notification(21L, 7L, 10L, 1);
        when(notificationMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(alreadyRead);
        InboxNotificationVO unchanged = service.markRead(21L, currentUser);
        assertTrue(unchanged.getRead());
        verify(notificationMapper, never()).markRead(eq(21L), anyLong(), any(LocalDateTime.class));
    }

    @Test
    void markReadDoesNotExposeAnotherUsersNotification() {
        when(notificationMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.markRead(99L, currentUser));

        assertEquals(404, error.getCode());
        verify(notificationMapper, never()).markRead(anyLong(), anyLong(), any(LocalDateTime.class));
    }

    @Test
    void globalNotificationCanBeReadWithoutProjectLookup() {
        UserNotification global = notification(22L, 7L, null, 1);
        when(notificationMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(global);

        InboxNotificationVO result = service.markRead(22L, currentUser);

        assertTrue(result.getRead());
        assertEquals(null, result.getProjectName());
        verify(projectInfoMapper, never()).selectById(anyLong());
    }

    @Test
    void summaryAggregatesDerivedPersistentCcAndUnreadCounts() {
        ProjectInfo project = project(10L, "项目甲");
        when(projectInfoMapper.selectById(10L)).thenReturn(project);
        when(projectPermissionService.hasSystemPermission(
                7L, 10L, SystemPermissionCodes.QUALITY_VIEW)).thenReturn(true);
        when(inspectionService.listTodos(10L, currentUser))
                .thenReturn(List.of(inspectionTodo(1L, 88L, "INSPECTION")));

        QualityTodoVO quality = new QualityTodoVO();
        quality.setId(-200L);
        quality.setTargetId(200L);
        quality.setProjectId(10L);
        quality.setProjectName("项目甲");
        quality.setType("RECTIFICATION");
        quality.setTitle("质量整改");
        quality.setBusinessType("QUALITY_ISSUE");
        quality.setPriority("danger");
        when(qualityIssueService.listTodos(10L, currentUser)).thenReturn(List.of(quality));

        when(userProjectMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(membership(7L, 10L)));

        when(approvalTaskMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(task(1L, 100L, 10L, 500L)));
        WorkflowCcRecipient cc = new WorkflowCcRecipient();
        cc.setId(30L);
        cc.setBusinessCode("SEAL_APPLICATION");
        cc.setBusinessId(100L);
        cc.setProjectId(10L);
        cc.setUserId(7L);
        cc.setCreateTime(LocalDateTime.now());
        when(ccRecipientMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(cc));
        when(sealApplicationMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(application(100L, 10L, 500L, "PENDING_APPROVAL")));
        when(notificationMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(2L);

        WorkSummaryVO summary = service.workSummary(10L, currentUser);

        assertEquals(3L, summary.getPendingCount());
        assertEquals(1L, summary.getCcCount());
        assertEquals(2L, summary.getUnreadNotificationCount());
        assertEquals(5L, summary.getBadgeCount());
        assertEquals(1L, summary.getByBusinessType().get("SEAL_APPLICATION"));
        assertEquals(1L, summary.getByBusinessType().get("QUALITY_ISSUE"));
        assertEquals(1L, summary.getByTaskType().get("SEAL_APPROVAL"));
    }

    @Test
    void markAllReadRejectsUnresolvedPartialWrite() {
        ProjectInfo project = project(10L, "项目甲");
        when(projectInfoMapper.selectById(10L)).thenReturn(project);
        when(userProjectMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(membership(7L, 10L)));
        when(notificationMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(2L, 1L);
        when(notificationMapper.markAllReadInScope(
                eq(7L), anyList(), anyBoolean(), any(LocalDateTime.class))).thenReturn(1);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.markAllRead(10L, currentUser));

        assertEquals(409, error.getCode());
    }

    @Test
    void requestedProjectRejectsStaleCachedAccessWithoutActiveMembership() {
        when(userProjectMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.listTodos("PENDING", 10L, null, 1, 20, currentUser));

        assertEquals(403, error.getCode());
        verify(projectInfoMapper, never()).selectById(10L);
        verify(inspectionService, never()).listTodos(any(), any());
    }

    @Test
    void scopedNotificationUpdateSqlParsesAsMybatisStatement() {
        MybatisConfiguration configuration = new MybatisConfiguration();

        configuration.addMapper(UserNotificationMapper.class);

        assertTrue(configuration.hasStatement(
                UserNotificationMapper.class.getName() + ".markAllReadInScope"));
    }

    private SysUser enabledUser(Long id) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername("user-" + id);
        user.setRealName("用户" + id);
        user.setStatus(1);
        user.setDeleted(0);
        return user;
    }

    private ProjectInfo project(Long id, String name) {
        ProjectInfo project = new ProjectInfo();
        project.setId(id);
        project.setProjectName(name);
        project.setShortName(name);
        project.setDeleted(0);
        return project;
    }

    private SysUserProject membership(Long userId, Long projectId) {
        SysUserProject membership = new SysUserProject();
        membership.setUserId(userId);
        membership.setProjectId(projectId);
        membership.setStatus("ACTIVE");
        return membership;
    }

    private InspectionTodoVO inspectionTodo(Long temporaryId, Long targetId, String type) {
        InspectionTodoVO todo = new InspectionTodoVO();
        todo.setId(temporaryId);
        todo.setTargetId(targetId);
        todo.setType(type);
        todo.setProjectId(10L);
        todo.setProjectName("项目甲");
        todo.setTitle("A-01 今日待巡检");
        todo.setBoxCode("A-01");
        todo.setInstallLocation("一层");
        todo.setDueText("今天");
        todo.setPriority("warning");
        return todo;
    }

    private QualityTodoVO qualityTodo(Long targetId, String type) {
        QualityTodoVO todo = new QualityTodoVO();
        todo.setId(-targetId);
        todo.setTargetId(targetId);
        todo.setProjectId(10L);
        todo.setProjectName("项目甲");
        todo.setType(type);
        todo.setTitle("质量待办-" + type);
        todo.setBusinessType("QUALITY_ISSUE");
        todo.setPriority("danger");
        return todo;
    }

    private void stubMixedTodoSources() {
        when(projectInfoMapper.selectById(10L)).thenReturn(project(10L, "项目甲"));
        when(projectPermissionService.hasSystemPermission(
                7L, 10L, SystemPermissionCodes.QUALITY_VIEW)).thenReturn(true);
        when(userProjectMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(membership(7L, 10L)));
        when(inspectionService.listTodos(10L, currentUser)).thenReturn(List.of(
                inspectionTodo(1L, 81L, "INSPECTION"),
                inspectionTodo(2L, 82L, "REVIEW"),
                inspectionTodo(3L, 83L, "RECTIFICATION"),
                inspectionTodo(4L, 84L, "RECHECK")));
        when(qualityIssueService.listTodos(10L, currentUser)).thenReturn(List.of(
                qualityTodo(201L, "RECTIFICATION"),
                qualityTodo(202L, "RECHECK")));
        when(approvalTaskMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(task(1L, 100L, 10L, 500L)));
        when(sealApplicationMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(application(100L, 10L, 500L, "PENDING_APPROVAL")));
    }

    private WorkflowApprovalTask task(Long id, Long businessId, Long projectId, Long instanceId) {
        WorkflowApprovalTask task = new WorkflowApprovalTask();
        task.setId(id);
        task.setInstanceId(instanceId);
        task.setBusinessCode("SEAL_APPLICATION");
        task.setBusinessId(businessId);
        task.setProjectId(projectId);
        task.setAssigneeUserId(7L);
        task.setStatus("PENDING");
        task.setCreateTime(LocalDateTime.now());
        return task;
    }

    private SealApplication application(Long id, Long projectId, Long instanceId, String status) {
        SealApplication application = new SealApplication();
        application.setId(id);
        application.setProjectId(projectId);
        application.setApprovalInstanceId(instanceId);
        application.setApplicationNo("YYSQ-" + id);
        application.setSealName("项目章");
        application.setApplicantName("申请人");
        application.setPurpose("资料报审");
        application.setStatus(status);
        application.setDeleted(0);
        return application;
    }

    private UserNotification notification(Long id, Long userId, Long projectId, int read) {
        UserNotification notification = new UserNotification();
        notification.setId(id);
        notification.setUserId(userId);
        notification.setProjectId(projectId);
        notification.setBusinessType("SEAL_APPLICATION");
        notification.setBusinessId(100L);
        notification.setEventCode("SEAL_PENDING_APPROVAL");
        notification.setTitle("用印审批通知");
        notification.setSummary("请及时审批");
        notification.setRouteCode("SEAL_APPLICATION_DETAIL");
        notification.setRouteParamsJson("{\"applicationId\":100}");
        notification.setIsRead(read);
        notification.setCreateTime(LocalDateTime.now());
        if (read == 1) notification.setReadTime(LocalDateTime.now());
        return notification;
    }
}
