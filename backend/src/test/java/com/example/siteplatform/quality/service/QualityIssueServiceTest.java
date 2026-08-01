package com.example.siteplatform.quality.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.common.PageResult;
import com.example.siteplatform.file.mapper.FileResourceMapper;
import com.example.siteplatform.file.service.FileResourceService;
import com.example.siteplatform.notification.service.WechatNotificationService;
import com.example.siteplatform.quality.dto.QualityAssignRequest;
import com.example.siteplatform.quality.dto.QualityIssueCreateRequest;
import com.example.siteplatform.quality.dto.QualityRectificationRequest;
import com.example.siteplatform.quality.dto.QualityReviewRequest;
import com.example.siteplatform.quality.dto.QualityVoidRequest;
import com.example.siteplatform.quality.entity.QualityIssue;
import com.example.siteplatform.quality.entity.QualityIssueLog;
import com.example.siteplatform.quality.mapper.QualityIssueLogMapper;
import com.example.siteplatform.quality.mapper.QualityIssueMapper;
import com.example.siteplatform.quality.vo.QualityIssueSummaryVO;
import com.example.siteplatform.quality.vo.QualityIssueVO;
import com.example.siteplatform.quality.vo.QualityTodoVO;
import com.example.siteplatform.project.entity.ProjectInfo;
import com.example.siteplatform.project.mapper.ProjectInfoMapper;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.system.constant.SystemPermissionCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QualityIssueServiceTest {

    @Mock private QualityIssueMapper issueMapper;
    @Mock private QualityIssueLogMapper logMapper;
    @Mock private ProjectPermissionService projectPermissionService;
    @Mock private QualityAssigneeService qualityAssigneeService;
    @Mock private FileResourceMapper fileMapper;
    @Mock private FileResourceService fileResourceService;
    @Mock private ProjectInfoMapper projectInfoMapper;
    @Mock private WechatNotificationService wechatNotificationService;

    private QualityIssueService service;
    private SysUser operator;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), QualityIssueMapper.class.getName()),
                QualityIssue.class);
        service = new QualityIssueService();
        ReflectionTestUtils.setField(service, "issueMapper", issueMapper);
        ReflectionTestUtils.setField(service, "logMapper", logMapper);
        ReflectionTestUtils.setField(service, "projectPermissionService", projectPermissionService);
        ReflectionTestUtils.setField(service, "qualityAssigneeService", qualityAssigneeService);
        ReflectionTestUtils.setField(service, "fileMapper", fileMapper);
        ReflectionTestUtils.setField(service, "fileResourceService", fileResourceService);
        ReflectionTestUtils.setField(service, "projectInfoMapper", projectInfoMapper);
        ReflectionTestUtils.setField(service, "wechatNotificationService", wechatNotificationService);
        operator = new SysUser();
        operator.setId(1L);
        operator.setUsername("manager");
    }

    @Test
    void createRequiresAtLeastOneProblemPhoto() {
        QualityIssueCreateRequest request = new QualityIssueCreateRequest();
        request.setProjectId(1L);
        request.setTitle("防水层收口不完整");
        request.setDeadline(LocalDate.now().plusDays(3));
        request.setPhotoFileIds(Collections.emptyList());

        assertThrows(BusinessException.class, () -> service.createIssue(request, new SysUser()));
    }

    @Test
    void createUsesSharedAssigneeEligibilityRuleBeforeWritingIssue() {
        QualityIssueCreateRequest request = validCreateRequest();
        when(projectPermissionService.canManageQuality(1L, 9L)).thenReturn(true);
        when(qualityAssigneeService.requireEligibleAssignee(2L, 9L, operator))
                .thenThrow(new BusinessException("整改负责人必须具备质量整改权限"));

        assertThrows(BusinessException.class, () -> service.createIssue(request, operator));

        verify(qualityAssigneeService).requireEligibleAssignee(2L, 9L, operator);
        verify(issueMapper, never()).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void assignUsesSameSharedAssigneeEligibilityRuleBeforeUpdatingIssue() {
        QualityIssue issue = new QualityIssue();
        issue.setId(100L);
        issue.setProjectId(9L);
        issue.setStatus(QualityIssueService.STATUS_PENDING);
        when(issueMapper.selectById(100L)).thenReturn(issue);
        when(projectPermissionService.canManageQuality(1L, 9L)).thenReturn(true);
        when(qualityAssigneeService.requireEligibleAssignee(2L, 9L, operator))
                .thenThrow(new BusinessException("整改负责人没有当前项目的有效访问权限"));
        QualityAssignRequest request = new QualityAssignRequest();
        request.setAssigneeId(2L);

        assertThrows(BusinessException.class, () -> service.assignIssue(100L, request, operator));

        verify(qualityAssigneeService).requireEligibleAssignee(2L, 9L, operator);
        verify(issueMapper, never()).updateAssignment(
                anyLong(), any(), anyInt(), anyLong(), any(), any(), any());
    }

    @Test
    void createWithSameRequestKeyReturnsExistingIssueWithoutSecondWrite() {
        QualityIssueCreateRequest request = validCreateRequest();
        request.setRequestKey("web-9b2ce64f");
        QualityIssue existing = issue(100L, QualityIssueService.STATUS_PENDING, 0);
        existing.setRequestKey(request.getRequestKey());
        existing.setCreatedById(operator.getId());
        when(projectPermissionService.canManageQuality(1L, 9L)).thenReturn(true);
        when(issueMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);
        stubEmptyDetails();

        QualityIssueVO result = service.createIssue(request, operator);

        assertEquals(100L, result.getId());
        verify(issueMapper, never()).insert(any());
        verify(qualityAssigneeService, never()).requireEligibleAssignee(any(), anyLong(), any());
        verify(fileResourceService, never()).validateAndBind(any(), anyLong(), any(), any(), any(), anyLong());
        verify(wechatNotificationService, never()).notifyUser(anyLong(), any(), any(), anyLong(), any());
    }

    @Test
    void concurrentDuplicateRequestKeyReturnsCommittedWinner() {
        QualityIssueCreateRequest request = validCreateRequest();
        request.setRequestKey("mini-duplicate-key");
        QualityIssue assignee = userAsIssueAssignee(2L, "整改人");
        QualityIssue existing = issue(101L, QualityIssueService.STATUS_PENDING, 0);
        existing.setRequestKey(request.getRequestKey());
        existing.setCreatedById(operator.getId());
        when(projectPermissionService.canManageQuality(1L, 9L)).thenReturn(true);
        when(issueMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null, existing);
        when(qualityAssigneeService.requireEligibleAssignee(2L, 9L, operator))
                .thenReturn(asUser(assignee.getAssigneeId(), assignee.getAssigneeName()));
        when(issueMapper.insert(any())).thenThrow(new DuplicateKeyException("duplicate request key"));
        stubEmptyDetails();

        QualityIssueVO result = service.createIssue(request, operator);

        assertEquals(101L, result.getId());
        verify(issueMapper).insert(any());
        verify(fileResourceService, never()).validateAndBind(any(), anyLong(), any(), any(), any(), anyLong());
        verify(logMapper, never()).insert(any());
    }

    @Test
    void createNotifiesAssignedRectifierAfterSuccessfulWrite() {
        QualityIssueCreateRequest request = validCreateRequest();
        request.setRequestKey("web-create-success");
        SysUser assignee = asUser(2L, "整改人");
        when(projectPermissionService.canManageQuality(1L, 9L)).thenReturn(true);
        when(issueMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(qualityAssigneeService.requireEligibleAssignee(2L, 9L, operator)).thenReturn(assignee);
        when(issueMapper.insert(any())).thenAnswer(invocation -> {
            QualityIssue inserted = invocation.getArgument(0);
            inserted.setId(103L);
            return 1;
        });
        QualityIssue persisted = issue(103L, QualityIssueService.STATUS_PENDING, 0);
        persisted.setAssigneeId(2L);
        persisted.setAssigneeName("整改人");
        when(issueMapper.selectById(103L)).thenReturn(persisted);
        when(logMapper.insert(any())).thenReturn(1);
        stubEmptyDetails();

        QualityIssueVO result = service.createIssue(request, operator);

        assertEquals(103L, result.getId());
        ArgumentCaptor<QualityIssue> issueCaptor = ArgumentCaptor.forClass(QualityIssue.class);
        verify(issueMapper).insert(issueCaptor.capture());
        String issueNo = issueCaptor.getValue().getIssueNo();
        assertEquals(34, issueNo.length());
        assertTrue(issueNo.matches("^Q-[0-9a-f]{32}$"));
        verify(fileResourceService).validateAndBind(
                operator, 9L, List.of(88L), "QUALITY_PENDING", "QUALITY_ISSUE", 103L);
        verify(wechatNotificationService).notifyUser(
                eq(2L), eq("RECTIFICATION_PENDING"), eq("QUALITY_ISSUE"), eq(103L), any());
    }

    @Test
    void rectificationUsesStatusAndVersionAndReturns409WhenAnotherActionWins() {
        QualityIssue issue = issue(100L, QualityIssueService.STATUS_PENDING, 7);
        issue.setAssigneeId(operator.getId());
        when(issueMapper.selectById(100L)).thenReturn(issue);
        when(issueMapper.updateRectification(
                eq(100L), eq(QualityIssueService.STATUS_PENDING), eq(7),
                eq(QualityIssueService.STATUS_RECHECK), eq("已完成整改"), eq("88"),
                any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(0);
        QualityRectificationRequest request = new QualityRectificationRequest();
        request.setDescription("已完成整改");
        request.setPhotoFileIds(List.of(88L));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.submitRectification(100L, request, operator));

        assertEquals(409, error.getCode());
        verify(fileResourceService, never()).validateAndBind(any(), anyLong(), any(), any(), any(), anyLong());
        verify(logMapper, never()).insert(any());
    }

    @Test
    void reviewUsesStatusAndVersionAndReturns409WhenAnotherActionWins() {
        QualityIssue issue = issue(100L, QualityIssueService.STATUS_RECHECK, 4);
        when(issueMapper.selectById(100L)).thenReturn(issue);
        when(projectPermissionService.hasSystemPermission(
                1L, 9L, SystemPermissionCodes.QUALITY_REVIEW)).thenReturn(true);
        when(issueMapper.updateReview(
                eq(100L), eq(QualityIssueService.STATUS_RECHECK), eq(4),
                eq(QualityIssueService.STATUS_CLOSED), eq(1L), eq("manager"),
                eq(null), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(0);
        QualityReviewRequest request = new QualityReviewRequest();
        request.setPassed(true);
        request.setPhotoFileIds(List.of());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.reviewIssue(100L, request, operator));

        assertEquals(409, error.getCode());
        verify(fileResourceService, never()).validateAndBind(any(), anyLong(), any(), any(), any(), anyLong());
        verify(logMapper, never()).insert(any());
    }

    @Test
    void rejectedReviewReturnsToPendingAndNotifiesRectifier() {
        QualityIssue before = issue(104L, QualityIssueService.STATUS_RECHECK, 5);
        before.setAssigneeId(2L);
        QualityIssue persisted = issue(104L, QualityIssueService.STATUS_PENDING, 6);
        persisted.setAssigneeId(2L);
        persisted.setReviewerId(1L);
        persisted.setReviewerName("manager");
        persisted.setReviewComment("仍有渗漏");
        when(issueMapper.selectById(104L)).thenReturn(before, persisted);
        when(projectPermissionService.hasSystemPermission(
                1L, 9L, SystemPermissionCodes.QUALITY_REVIEW)).thenReturn(true);
        when(projectPermissionService.canManageQuality(1L, 9L)).thenReturn(true);
        when(issueMapper.updateReview(
                eq(104L), eq(QualityIssueService.STATUS_RECHECK), eq(5),
                eq(QualityIssueService.STATUS_PENDING), eq(1L), eq("manager"),
                eq("仍有渗漏"), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(1);
        when(logMapper.insert(any())).thenReturn(1);
        stubEmptyDetails();
        QualityReviewRequest request = new QualityReviewRequest();
        request.setPassed(false);
        request.setComment("仍有渗漏");
        request.setPhotoFileIds(List.of());

        QualityIssueVO result = service.reviewIssue(104L, request, operator);

        assertEquals(QualityIssueService.STATUS_PENDING, result.getStatus());
        verify(wechatNotificationService).notifyUser(
                eq(2L), eq("RECHECK_REJECTED"), eq("QUALITY_ISSUE"), eq(104L), any());
    }

    @Test
    void assignmentUsesStatusAndVersionAndReturns409WhenAnotherActionWins() {
        QualityIssue issue = issue(100L, QualityIssueService.STATUS_RECHECK, 2);
        issue.setAssigneeId(2L);
        issue.setAssigneeName("整改人");
        when(issueMapper.selectById(100L)).thenReturn(issue);
        when(projectPermissionService.canManageQuality(1L, 9L)).thenReturn(true);
        LocalDate newDeadline = LocalDate.now().plusDays(5);
        when(issueMapper.updateAssignment(
                eq(100L), eq(QualityIssueService.STATUS_RECHECK), eq(2),
                eq(2L), eq("整改人"), eq(newDeadline), any(LocalDateTime.class))).thenReturn(0);
        QualityAssignRequest request = new QualityAssignRequest();
        request.setDeadline(newDeadline);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.assignIssue(100L, request, operator));

        assertEquals(409, error.getCode());
        verify(logMapper, never()).insert(any());
        verify(wechatNotificationService, never()).notifyUser(anyLong(), any(), any(), anyLong(), any());
    }

    @Test
    void voidPendingIssueUsesStatusAndVersionAndWritesAuditLog() {
        QualityIssue before = issue(105L, QualityIssueService.STATUS_PENDING, 8);
        before.setDeadline(LocalDate.now().minusDays(2));
        QualityIssue persisted = issue(105L, QualityIssueService.STATUS_VOIDED, 9);
        persisted.setDeadline(before.getDeadline());
        when(issueMapper.selectById(105L)).thenReturn(before, persisted);
        when(projectPermissionService.canManageQuality(1L, 9L)).thenReturn(true);
        when(issueMapper.updateStatus(
                eq(105L), eq(QualityIssueService.STATUS_PENDING), eq(8),
                eq(QualityIssueService.STATUS_VOIDED), any(LocalDateTime.class))).thenReturn(1);
        when(logMapper.insert(any())).thenReturn(1);
        stubEmptyDetails();
        QualityVoidRequest request = new QualityVoidRequest();
        request.setComment("重复录入");

        QualityIssueVO result = service.voidIssue(105L, request, operator);

        assertEquals(QualityIssueService.STATUS_VOIDED, result.getStatus());
        assertEquals("已作废", result.getDueText());
        assertFalse(result.getOverdue());
        assertFalse(result.getCanRectify());
        assertFalse(result.getCanReview());
        ArgumentCaptor<QualityIssueLog> logCaptor = ArgumentCaptor.forClass(QualityIssueLog.class);
        verify(logMapper).insert(logCaptor.capture());
        assertEquals("VOID", logCaptor.getValue().getActionType());
        assertEquals(QualityIssueService.STATUS_PENDING, logCaptor.getValue().getFromStatus());
        assertEquals(QualityIssueService.STATUS_VOIDED, logCaptor.getValue().getToStatus());
        assertEquals("重复录入", logCaptor.getValue().getComment());
    }

    @Test
    void voidIssueReturns409WhenAnotherActionWins() {
        QualityIssue issue = issue(106L, QualityIssueService.STATUS_RECHECK, 3);
        when(issueMapper.selectById(106L)).thenReturn(issue);
        when(projectPermissionService.canManageQuality(1L, 9L)).thenReturn(true);
        when(issueMapper.updateStatus(
                eq(106L), eq(QualityIssueService.STATUS_RECHECK), eq(3),
                eq(QualityIssueService.STATUS_VOIDED), any(LocalDateTime.class))).thenReturn(0);
        QualityVoidRequest request = new QualityVoidRequest();
        request.setComment("误建");

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.voidIssue(106L, request, operator));

        assertEquals(409, error.getCode());
        verify(logMapper, never()).insert(any());
    }

    @Test
    void voidedIssueCannotBeAssignedAgain() {
        QualityIssue issue = issue(107L, QualityIssueService.STATUS_VOIDED, 1);
        when(issueMapper.selectById(107L)).thenReturn(issue);
        when(projectPermissionService.canManageQuality(1L, 9L)).thenReturn(true);
        QualityAssignRequest request = new QualityAssignRequest();
        request.setDeadline(LocalDate.now().plusDays(1));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.assignIssue(107L, request, operator));

        assertEquals(409, error.getCode());
        verify(issueMapper, never()).updateAssignment(
                anyLong(), any(), anyInt(), any(), any(), any(), any());
    }

    @Test
    void invalidSeverityIsRejectedInsteadOfSilentlyDowngraded() {
        QualityIssueCreateRequest request = validCreateRequest();
        request.setSeverity("CRITICAL");
        when(projectPermissionService.canManageQuality(1L, 9L)).thenReturn(true);
        when(qualityAssigneeService.requireEligibleAssignee(2L, 9L, operator))
                .thenReturn(asUser(2L, "整改人"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.createIssue(request, operator));

        assertEquals(400, error.getCode());
        assertTrue(error.getMessage().contains("严重程度"));
        verify(issueMapper, never()).insert(any());
    }

    @Test
    void notificationFailureDoesNotRollbackSuccessfulAssignment() {
        QualityIssue before = issue(100L, QualityIssueService.STATUS_PENDING, 3);
        before.setAssigneeId(2L);
        before.setAssigneeName("整改人");
        before.setDeadline(LocalDate.now().plusDays(2));
        QualityIssue persisted = issue(100L, QualityIssueService.STATUS_PENDING, 4);
        persisted.setAssigneeId(2L);
        persisted.setAssigneeName("整改人");
        persisted.setDeadline(LocalDate.now().plusDays(4));
        when(issueMapper.selectById(100L)).thenReturn(before, persisted);
        when(projectPermissionService.canManageQuality(1L, 9L)).thenReturn(true);
        when(issueMapper.updateAssignment(
                eq(100L), eq(QualityIssueService.STATUS_PENDING), eq(3),
                eq(2L), eq("整改人"), eq(persisted.getDeadline()), any(LocalDateTime.class))).thenReturn(1);
        when(logMapper.insert(any())).thenReturn(1);
        stubEmptyDetails();
        doThrow(new RuntimeException("微信服务不可用")).when(wechatNotificationService)
                .notifyUser(eq(2L), eq("RECTIFICATION_PENDING"), eq("QUALITY_ISSUE"), eq(100L), any());
        QualityAssignRequest request = new QualityAssignRequest();
        request.setDeadline(persisted.getDeadline());

        QualityIssueVO result = service.assignIssue(100L, request, operator);

        assertEquals(100L, result.getId());
        assertEquals(persisted.getDeadline(), result.getDeadline());
        verify(logMapper).insert(any());
        verify(wechatNotificationService).notifyUser(
                eq(2L), eq("RECTIFICATION_PENDING"), eq("QUALITY_ISSUE"), eq(100L), any());
    }

    @Test
    void rectificationNotifiesOnlyEligibleReviewers() {
        QualityIssue before = issue(100L, QualityIssueService.STATUS_PENDING, 1);
        before.setAssigneeId(operator.getId());
        QualityIssue persisted = issue(100L, QualityIssueService.STATUS_RECHECK, 2);
        persisted.setAssigneeId(operator.getId());
        persisted.setRectificationDescription("整改完成");
        persisted.setRectificationPhotoFileIds("88");
        when(issueMapper.selectById(100L)).thenReturn(before, persisted);
        when(issueMapper.updateRectification(
                eq(100L), eq(QualityIssueService.STATUS_PENDING), eq(1),
                eq(QualityIssueService.STATUS_RECHECK), eq("整改完成"), eq("88"),
                any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(1);
        when(issueMapper.selectPotentialReviewerIds(9L)).thenReturn(List.of(5L, 6L));
        when(projectPermissionService.canManageQuality(1L, 9L)).thenReturn(false);
        when(projectPermissionService.hasSystemPermission(5L, 9L, SystemPermissionCodes.QUALITY_REVIEW))
                .thenReturn(true);
        when(projectPermissionService.hasSystemPermission(6L, 9L, SystemPermissionCodes.QUALITY_REVIEW))
                .thenReturn(false);
        when(logMapper.insert(any())).thenReturn(1);
        stubEmptyDetails();
        QualityRectificationRequest request = new QualityRectificationRequest();
        request.setDescription("整改完成");
        request.setPhotoFileIds(List.of(88L));

        QualityIssueVO result = service.submitRectification(100L, request, operator);

        assertEquals(QualityIssueService.STATUS_RECHECK, result.getStatus());
        verify(wechatNotificationService).notifyUser(
                eq(5L), eq("RECHECK_PENDING"), eq("QUALITY_ISSUE"), eq(100L), any());
        verify(wechatNotificationService, never()).notifyUser(
                eq(6L), any(), any(), anyLong(), any());
    }

    @Test
    void todosReturnOnlyAssignedRectificationAndAuthorizedSharedRecheck() {
        ProjectInfo project = new ProjectInfo();
        project.setId(9L);
        project.setProjectName("智慧工地项目");
        QualityIssue pending = issue(101L, QualityIssueService.STATUS_PENDING, 0);
        pending.setIssueNo("Q-101");
        pending.setAssigneeId(operator.getId());
        pending.setDeadline(LocalDate.now().plusDays(1));
        QualityIssue recheck = issue(102L, QualityIssueService.STATUS_RECHECK, 0);
        recheck.setIssueNo("Q-102");
        recheck.setDeadline(LocalDate.now().minusDays(1));
        recheck.setSeverity("DANGER");
        when(projectInfoMapper.selectById(9L)).thenReturn(project);
        when(projectPermissionService.hasProjectPermission(1L, 9L)).thenReturn(true);
        when(projectPermissionService.hasSystemPermission(1L, 9L, SystemPermissionCodes.QUALITY_VIEW))
                .thenReturn(true);
        when(projectPermissionService.hasSystemPermission(1L, 9L, SystemPermissionCodes.QUALITY_RECTIFY))
                .thenReturn(true);
        when(projectPermissionService.hasSystemPermission(1L, 9L, SystemPermissionCodes.QUALITY_REVIEW))
                .thenReturn(true);
        when(issueMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(pending), List.of(recheck));

        List<QualityTodoVO> result = service.listTodos(9L, operator);

        assertEquals(2, result.size());
        assertEquals("RECHECK", result.get(0).getType());
        assertEquals("QUALITY_ISSUE", result.get(0).getBusinessType());
        assertEquals(-102L, result.get(0).getId());
        assertEquals(1, result.get(0).getReviewOverdue());
        assertTrue(result.get(0).getDueText().contains("已逾期"));
        assertEquals("RECTIFICATION", result.get(1).getType());

        ArgumentCaptor<LambdaQueryWrapper<QualityIssue>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(issueMapper, times(2)).selectList(wrapperCaptor.capture());
        assertTrue(wrapperCaptor.getAllValues().get(0).getSqlSegment().contains("assignee_id"));
        assertTrue(wrapperCaptor.getAllValues().get(1).getSqlSegment().contains("status"));
    }

    @Test
    void todosDoNotExposeRecheckWithoutReviewPermission() {
        ProjectInfo project = new ProjectInfo();
        project.setId(9L);
        when(projectInfoMapper.selectById(9L)).thenReturn(project);
        when(projectPermissionService.hasProjectPermission(1L, 9L)).thenReturn(true);
        when(projectPermissionService.hasSystemPermission(1L, 9L, SystemPermissionCodes.QUALITY_VIEW))
                .thenReturn(true);
        when(projectPermissionService.hasSystemPermission(1L, 9L, SystemPermissionCodes.QUALITY_RECTIFY))
                .thenReturn(true);
        when(projectPermissionService.hasSystemPermission(1L, 9L, SystemPermissionCodes.QUALITY_REVIEW))
                .thenReturn(false);
        when(issueMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        List<QualityTodoVO> result = service.listTodos(9L, operator);

        assertTrue(result.isEmpty());
        verify(issueMapper, times(1)).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    void reviewerPermissionCanCloseIssueWithoutManagePermission() {
        QualityIssue before = issue(108L, QualityIssueService.STATUS_RECHECK, 2);
        QualityIssue persisted = issue(108L, QualityIssueService.STATUS_CLOSED, 3);
        persisted.setReviewerId(operator.getId());
        persisted.setReviewerName("manager");
        when(issueMapper.selectById(108L)).thenReturn(before, persisted);
        when(projectPermissionService.hasSystemPermission(
                1L, 9L, SystemPermissionCodes.QUALITY_REVIEW)).thenReturn(true);
        when(issueMapper.updateReview(
                eq(108L), eq(QualityIssueService.STATUS_RECHECK), eq(2),
                eq(QualityIssueService.STATUS_CLOSED), eq(1L), eq("manager"),
                eq(null), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(1);
        when(logMapper.insert(any())).thenReturn(1);
        stubEmptyDetails();
        QualityReviewRequest request = new QualityReviewRequest();
        request.setPassed(true);
        request.setPhotoFileIds(List.of());

        QualityIssueVO result = service.reviewIssue(108L, request, operator);

        assertEquals(QualityIssueService.STATUS_CLOSED, result.getStatus());
        verify(issueMapper).updateReview(
                eq(108L), eq(QualityIssueService.STATUS_RECHECK), eq(2),
                eq(QualityIssueService.STATUS_CLOSED), eq(1L), eq("manager"),
                eq(null), any(LocalDateTime.class), any(LocalDateTime.class));
        verify(projectPermissionService, never()).canManageQuality(1L, 9L);
    }

    @Test
    void managePermissionDoesNotReplaceReviewPermission() {
        when(issueMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(projectPermissionService.canManageQuality(1L, 9L)).thenReturn(true);
        QualityIssueSummaryVO summary = service.getSummary(9L, operator);
        assertTrue(summary.getCanManage());

        QualityIssue issue = issue(109L, QualityIssueService.STATUS_RECHECK, 1);
        when(issueMapper.selectById(109L)).thenReturn(issue);
        when(projectPermissionService.hasSystemPermission(
                1L, 9L, SystemPermissionCodes.QUALITY_REVIEW)).thenReturn(false);
        stubEmptyDetails();
        QualityIssueVO capability = service.getIssue(109L, operator);
        assertFalse(capability.getCanReview());

        QualityReviewRequest request = new QualityReviewRequest();
        request.setPassed(true);
        request.setPhotoFileIds(List.of());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.reviewIssue(109L, request, operator));

        assertEquals(403, error.getCode());
        verify(issueMapper, never()).updateReview(
                anyLong(), any(), anyInt(), any(), anyLong(), any(), any(), any(), any());
    }

    @Test
    void detailCapabilityFieldsUseIndependentReviewAndRectificationPermissions() {
        QualityIssue recheck = issue(110L, QualityIssueService.STATUS_RECHECK, 0);
        when(issueMapper.selectById(110L)).thenReturn(recheck);
        when(projectPermissionService.hasSystemPermission(
                1L, 9L, SystemPermissionCodes.QUALITY_REVIEW)).thenReturn(true);
        stubEmptyDetails();

        QualityIssueVO reviewable = service.getIssue(110L, operator);

        assertTrue(reviewable.getCanReview());
        assertFalse(reviewable.getCanRectify());

        QualityIssue pending = issue(111L, QualityIssueService.STATUS_PENDING, 0);
        pending.setAssigneeId(2L);
        when(issueMapper.selectById(111L)).thenReturn(pending);
        when(projectPermissionService.hasSystemPermission(
                1L, 9L, SystemPermissionCodes.QUALITY_RECTIFY)).thenReturn(true);
        when(projectPermissionService.canManageQuality(1L, 9L)).thenReturn(true);

        QualityIssueVO manageableRectification = service.getIssue(111L, operator);

        assertTrue(manageableRectification.getCanRectify());
        assertFalse(manageableRectification.getCanReview());
    }

    @Test
    void managePermissionAloneDoesNotExposeRectificationCapability() {
        QualityIssue pending = issue(112L, QualityIssueService.STATUS_PENDING, 0);
        pending.setAssigneeId(operator.getId());
        when(issueMapper.selectById(112L)).thenReturn(pending);
        when(projectPermissionService.canManageQuality(1L, 9L)).thenReturn(true);
        when(projectPermissionService.hasSystemPermission(
                1L, 9L, SystemPermissionCodes.QUALITY_RECTIFY)).thenReturn(false);
        stubEmptyDetails();

        QualityIssueVO result = service.getIssue(112L, operator);

        assertFalse(result.getCanRectify());
    }

    @Test
    void summaryTreatsRecheckAsOverdueAndEmptyClosureRateAsZero() {
        when(issueMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        QualityIssueSummaryVO result = service.getSummary(9L, operator);

        assertEquals(0, result.getClosureRate());
        ArgumentCaptor<LambdaQueryWrapper<QualityIssue>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(issueMapper, times(5)).selectCount(wrapperCaptor.capture());
        LambdaQueryWrapper<QualityIssue> overdueWrapper = wrapperCaptor.getAllValues().get(2);
        assertTrue(overdueWrapper.getSqlSegment().contains(" IN "));
        assertTrue(overdueWrapper.getParamNameValuePairs().containsValue(QualityIssueService.STATUS_PENDING));
        assertTrue(overdueWrapper.getParamNameValuePairs().containsValue(QualityIssueService.STATUS_RECHECK));
    }

    @Test
    void listUsesUnclosedOverdueSeverityDeadlineOrderingAndOverdueIncludesRecheck() {
        when(issueMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        List<QualityIssueVO> result = service.listIssues(9L, "OVERDUE", "", operator);

        assertTrue(result.isEmpty());
        ArgumentCaptor<LambdaQueryWrapper<QualityIssue>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(issueMapper).selectList(wrapperCaptor.capture());
        String sql = wrapperCaptor.getValue().getSqlSegment();
        assertTrue(sql.contains("status IN ('CLOSED', 'VOIDED')"));
        assertTrue(sql.contains("deadline < CURRENT_DATE"));
        assertTrue(sql.contains("WHEN 'DANGER'"));
        assertTrue(wrapperCaptor.getValue().getParamNameValuePairs()
                .containsValue(QualityIssueService.STATUS_RECHECK));
    }

    @Test
    void pageEndpointNormalizesBoundsAndUsesSameQuery() {
        when(issueMapper.selectPage(any(Page.class), any(Wrapper.class))).thenAnswer(invocation -> {
            Page<QualityIssue> page = invocation.getArgument(0);
            page.setTotal(0);
            page.setRecords(List.of());
            return page;
        });

        PageResult<QualityIssueVO> result =
                service.pageIssues(9L, "ALL", "", 0, 500, operator);

        assertEquals(1, result.getPageNo());
        assertEquals(100, result.getPageSize());
        assertEquals(0L, result.getTotal());
        assertTrue(result.getRecords().isEmpty());
    }

    private QualityIssueCreateRequest validCreateRequest() {
        QualityIssueCreateRequest request = new QualityIssueCreateRequest();
        request.setProjectId(9L);
        request.setTitle("防水层收口不完整");
        request.setAssigneeId(2L);
        request.setDeadline(LocalDate.now().plusDays(3));
        request.setPhotoFileIds(List.of(88L));
        return request;
    }

    private QualityIssue issue(Long id, String status, int version) {
        QualityIssue issue = new QualityIssue();
        issue.setId(id);
        issue.setProjectId(9L);
        issue.setIssueNo("Q-" + id);
        issue.setTitle("质量问题" + id);
        issue.setSeverity("NORMAL");
        issue.setStatus(status);
        issue.setVersion(version);
        issue.setCreateTime(LocalDateTime.now());
        return issue;
    }

    private QualityIssue userAsIssueAssignee(Long userId, String name) {
        QualityIssue issue = new QualityIssue();
        issue.setAssigneeId(userId);
        issue.setAssigneeName(name);
        return issue;
    }

    private SysUser asUser(Long id, String name) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(name);
        user.setRealName(name);
        return user;
    }

    private void stubEmptyDetails() {
        when(fileMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(logMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
    }
}
