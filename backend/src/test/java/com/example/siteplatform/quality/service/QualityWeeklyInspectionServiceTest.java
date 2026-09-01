package com.example.siteplatform.quality.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.file.service.QualityWeeklyFileService;
import com.example.siteplatform.log.mapper.OperationLogMapper;
import com.example.siteplatform.notification.service.WechatNotificationService;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.quality.dto.QualityWeeklyActionRequest;
import com.example.siteplatform.quality.dto.QualityWeeklyDraftCreateRequest;
import com.example.siteplatform.quality.dto.QualityWeeklyDraftItemRequest;
import com.example.siteplatform.quality.dto.QualityWeeklyDraftSaveRequest;
import com.example.siteplatform.quality.entity.QualityIssue;
import com.example.siteplatform.quality.entity.QualityWeeklyInspection;
import com.example.siteplatform.quality.entity.QualityWeeklyInspectionDraftItem;
import com.example.siteplatform.quality.mapper.QualityIssueLogMapper;
import com.example.siteplatform.quality.mapper.QualityIssueMapper;
import com.example.siteplatform.quality.mapper.QualityWeeklyInspectionDraftItemMapper;
import com.example.siteplatform.quality.mapper.QualityWeeklyInspectionMapper;
import com.example.siteplatform.quality.vo.QualityWeeklyInspectionVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QualityWeeklyInspectionServiceTest {

    @Mock private QualityWeeklyInspectionMapper inspectionMapper;
    @Mock private QualityWeeklyInspectionDraftItemMapper draftItemMapper;
    @Mock private QualityIssueMapper issueMapper;
    @Mock private QualityIssueLogMapper logMapper;
    @Mock private QualityIssueService qualityIssueService;
    @Mock private QualityAssigneeService qualityAssigneeService;
    @Mock private QualityWeeklyReminderSettingService reminderSettingService;
    @Mock private ProjectPermissionService projectPermissionService;
    @Mock private QualityWeeklyFileService weeklyFileService;
    @Mock private WechatNotificationService wechatNotificationService;
    @Mock private OperationLogMapper operationLogMapper;

    private QualityWeeklyInspectionService service;
    private SysUser manager;

    @BeforeEach
    void setUp() {
        service = new QualityWeeklyInspectionService();
        ReflectionTestUtils.setField(service, "inspectionMapper", inspectionMapper);
        ReflectionTestUtils.setField(service, "draftItemMapper", draftItemMapper);
        ReflectionTestUtils.setField(service, "issueMapper", issueMapper);
        ReflectionTestUtils.setField(service, "logMapper", logMapper);
        ReflectionTestUtils.setField(service, "qualityIssueService", qualityIssueService);
        ReflectionTestUtils.setField(service, "qualityAssigneeService", qualityAssigneeService);
        ReflectionTestUtils.setField(service, "reminderSettingService", reminderSettingService);
        ReflectionTestUtils.setField(service, "projectPermissionService", projectPermissionService);
        ReflectionTestUtils.setField(service, "weeklyFileService", weeklyFileService);
        ReflectionTestUtils.setField(service, "wechatNotificationService", wechatNotificationService);
        ReflectionTestUtils.setField(service, "operationLogMapper", operationLogMapper);
        manager = user(1L, "质量经理");
        when(projectPermissionService.canManageQuality(1L, 9L)).thenReturn(true);
    }

    @Test
    void createRejectsFutureWeekBeforeWriting() {
        QualityWeeklyDraftCreateRequest request = new QualityWeeklyDraftCreateRequest();
        request.setProjectId(9L);
        request.setWeekStart(LocalDate.now().plusWeeks(2));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.createOrRestore(request, manager));

        assertEquals(400, error.getCode());
        assertTrue(error.getMessage().contains("未来周"));
        verify(inspectionMapper, never()).insert(any());
    }

    @Test
    void createNormalizesMondayAndWritesImportantOperationAudit() {
        QualityWeeklyDraftCreateRequest request = new QualityWeeklyDraftCreateRequest();
        request.setProjectId(9L);
        request.setWeekStart(LocalDate.now());
        when(inspectionMapper.insert(any())).thenAnswer(invocation -> {
            QualityWeeklyInspection inserted = invocation.getArgument(0);
            inserted.setId(100L);
            return 1;
        });
        when(operationLogMapper.insert(any())).thenReturn(1);
        when(draftItemMapper.selectByInspectionId(100L)).thenReturn(List.of());

        QualityWeeklyInspectionVO result = service.createOrRestore(request, manager);

        assertEquals(100L, result.getId());
        assertEquals(DayOfWeek.MONDAY, result.getWeekStart().getDayOfWeek());
        verify(operationLogMapper).insert(any());
    }

    @Test
    void saveReturns409WhenExpectedVersionIsStale() {
        QualityWeeklyInspection draft = draft(100L, 3);
        when(inspectionMapper.selectForUpdate(100L)).thenReturn(draft);
        QualityWeeklyDraftSaveRequest request = new QualityWeeklyDraftSaveRequest();
        request.setExpectedVersion(2);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.saveDraft(100L, request, manager));

        assertEquals(409, error.getCode());
        verify(inspectionMapper, never()).updateDraft(
                anyLong(), anyInt(), any(), any(), anyLong(), any(), any());
    }

    @Test
    void saveRejectsDuplicateProblemOrder() {
        QualityWeeklyInspection draft = draft(100L, 0);
        when(inspectionMapper.selectForUpdate(100L)).thenReturn(draft);
        QualityWeeklyDraftSaveRequest request = new QualityWeeklyDraftSaveRequest();
        request.setExpectedVersion(0);
        request.setInspectionDate(LocalDate.now());
        request.setItems(List.of(itemRequest("a", 1), itemRequest("b", 1)));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.saveDraft(100L, request, manager));

        assertEquals(400, error.getCode());
        assertTrue(error.getMessage().contains("顺序不能重复"));
    }

    @Test
    void submitNoIssueRequiresConclusionAndOverviewEvidence() {
        QualityWeeklyInspection draft = draft(100L, 0);
        draft.setConclusion(null);
        when(inspectionMapper.selectForUpdate(100L)).thenReturn(draft);
        when(draftItemMapper.selectByInspectionId(100L)).thenReturn(List.of());
        when(weeklyFileService.listFileIds(QualityWeeklyFileService.WEEKLY_DRAFT, 100L))
                .thenReturn(List.of());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.submit(100L, action(0), manager));

        assertEquals(400, error.getCode());
        assertTrue(error.getMessage().contains("无问题周检"));
        verify(inspectionMapper, never()).submit(
                anyLong(), anyInt(), any(), anyInt(), anyLong(), any(), any());
    }

    @Test
    void submitRejectsProblemWhoseSeverityWasLeftIncompleteInDraft() {
        QualityWeeklyInspection draft = draft(100L, 0);
        QualityWeeklyInspectionDraftItem item = draftItem(
                201L, 1, "临边防护缺失", 2L, LocalDate.now().plusDays(2));
        item.setSeverity(null);
        when(inspectionMapper.selectForUpdate(100L)).thenReturn(draft);
        when(draftItemMapper.selectByInspectionId(100L)).thenReturn(List.of(item));
        when(weeklyFileService.listFileIds(QualityWeeklyFileService.WEEKLY_DRAFT, 100L))
                .thenReturn(List.of());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.submit(100L, action(0), manager));

        assertEquals(400, error.getCode());
        assertTrue(error.getMessage().contains("严重程度"));
        verify(inspectionMapper, never()).submit(
                anyLong(), anyInt(), any(), anyInt(), anyLong(), any(), any());
    }

    @Test
    void zeroIssueSubmissionIsAtomicAndKeepsSubmittedSnapshot() {
        QualityWeeklyInspection draft = draft(100L, 0);
        draft.setConclusion("本周检查未发现质量问题");
        when(inspectionMapper.selectForUpdate(100L)).thenReturn(draft);
        when(draftItemMapper.selectByInspectionId(100L)).thenReturn(List.of());
        when(weeklyFileService.listFileIds(QualityWeeklyFileService.WEEKLY_DRAFT, 100L))
                .thenReturn(List.of(10L));
        when(weeklyFileService.listFileIds(QualityWeeklyFileService.WEEKLY_FINAL, 100L))
                .thenReturn(List.of(10L));
        when(inspectionMapper.submit(eq(100L), eq(0), any(), eq(0), eq(1L), any(), any()))
                .thenReturn(1);
        when(draftItemMapper.deleteByInspectionId(100L)).thenReturn(0);
        when(operationLogMapper.insert(any())).thenReturn(1);
        when(issueMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        QualityWeeklyInspectionVO result = service.submit(100L, action(0), manager);

        assertEquals(QualityWeeklyInspectionService.STATUS_SUBMITTED, result.getStatus());
        assertEquals(0, result.getSubmittedIssueCount());
        verify(reminderSettingService).lockByInspectionId(100L);
        verify(weeklyFileService).transferDraftFiles(9L,
                QualityWeeklyFileService.WEEKLY_DRAFT, 100L,
                QualityWeeklyFileService.WEEKLY_FINAL, 100L);
        verify(operationLogMapper).insert(any());
    }

    @Test
    void batchSubmitCreatesIndependentIssuesWithOwnAssigneeDeadlineAndPhotos() {
        QualityWeeklyInspection draft = draft(100L, 4);
        QualityWeeklyInspectionDraftItem first = draftItem(201L, 1, "裂缝", 2L,
                LocalDate.now().plusDays(2));
        QualityWeeklyInspectionDraftItem second = draftItem(202L, 2, "渗漏", 3L,
                LocalDate.now().plusDays(5));
        when(inspectionMapper.selectForUpdate(100L)).thenReturn(draft);
        when(draftItemMapper.selectByInspectionId(100L)).thenReturn(List.of(first, second));
        when(weeklyFileService.listFileIds(QualityWeeklyFileService.WEEKLY_DRAFT, 100L))
                .thenReturn(List.of());
        when(weeklyFileService.listFileIds(QualityWeeklyFileService.WEEKLY_DRAFT_ITEM, 201L))
                .thenReturn(List.of(31L));
        when(weeklyFileService.listFileIds(QualityWeeklyFileService.WEEKLY_DRAFT_ITEM, 202L))
                .thenReturn(List.of(41L, 42L));
        when(weeklyFileService.listFileIds(QualityWeeklyFileService.WEEKLY_FINAL, 100L))
                .thenReturn(List.of());
        when(qualityAssigneeService.requireEligibleAssignee(2L, 9L, manager))
                .thenReturn(user(2L, "整改甲"));
        when(qualityAssigneeService.requireEligibleAssignee(3L, 9L, manager))
                .thenReturn(user(3L, "整改乙"));
        when(inspectionMapper.submit(eq(100L), eq(4), any(), eq(2), eq(1L), any(), any()))
                .thenReturn(1);
        AtomicLong ids = new AtomicLong(300L);
        when(issueMapper.insert(any())).thenAnswer(invocation -> {
            QualityIssue issue = invocation.getArgument(0);
            issue.setId(ids.getAndIncrement());
            return 1;
        });
        when(weeklyFileService.transferDraftFiles(9L,
                QualityWeeklyFileService.WEEKLY_DRAFT_ITEM, 201L,
                QualityWeeklyFileService.ISSUE_FINAL, 300L)).thenReturn(List.of(31L));
        when(weeklyFileService.transferDraftFiles(9L,
                QualityWeeklyFileService.WEEKLY_DRAFT_ITEM, 202L,
                QualityWeeklyFileService.ISSUE_FINAL, 301L)).thenReturn(List.of(41L, 42L));
        when(logMapper.insert(any())).thenReturn(1);
        when(draftItemMapper.deleteByInspectionId(100L)).thenReturn(2);
        when(operationLogMapper.insert(any())).thenReturn(1);
        when(issueMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(issueMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        QualityWeeklyInspectionVO result = service.submit(100L, action(4), manager);

        assertEquals(2, result.getSubmittedIssueCount());
        ArgumentCaptor<QualityIssue> issueCaptor = ArgumentCaptor.forClass(QualityIssue.class);
        verify(issueMapper, times(2)).insert(issueCaptor.capture());
        List<QualityIssue> created = issueCaptor.getAllValues();
        assertEquals(100L, created.get(0).getWeeklyInspectionId());
        assertEquals(draft.getInspectionDate(), created.get(0).getRecordDate());
        assertEquals(draft.getInspectionDate(), created.get(1).getRecordDate());
        assertEquals(1, created.get(0).getInspectionItemOrder());
        assertEquals(2L, created.get(0).getAssigneeId());
        assertEquals(first.getDeadline(), created.get(0).getDeadline());
        assertEquals(3L, created.get(1).getAssigneeId());
        assertEquals(second.getDeadline(), created.get(1).getDeadline());
        verify(logMapper, times(2)).insert(any());
        verify(draftItemMapper).deleteByInspectionId(100L);
    }

    @Test
    void repeatedSubmitReturnsExistingRecordWithoutCreatingSecondIssues() {
        QualityWeeklyInspection submitted = draft(100L, 5);
        submitted.setStatus(QualityWeeklyInspectionService.STATUS_SUBMITTED);
        submitted.setInspectionNo("QW-EXISTING");
        submitted.setSubmittedIssueCount(2);
        submitted.setSubmittedTime(LocalDateTime.now());
        when(inspectionMapper.selectForUpdate(100L)).thenReturn(submitted);
        when(issueMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(issueMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        QualityWeeklyInspectionVO result = service.submit(100L, action(4), manager);

        assertEquals("QW-EXISTING", result.getInspectionNo());
        verify(inspectionMapper, never()).submit(
                anyLong(), anyInt(), any(), anyInt(), anyLong(), any(), any());
        verify(issueMapper, never()).insert(any());
    }

    @Test
    void secondProblemFailureStopsBatchAndLeavesNoSuccessAuditOrNotification() {
        QualityWeeklyInspection draft = draft(100L, 2);
        QualityWeeklyInspectionDraftItem first = draftItem(201L, 1, "裂缝", 2L,
                LocalDate.now().plusDays(2));
        QualityWeeklyInspectionDraftItem second = draftItem(202L, 2, "渗漏", 3L,
                LocalDate.now().plusDays(5));
        when(inspectionMapper.selectForUpdate(100L)).thenReturn(draft);
        when(draftItemMapper.selectByInspectionId(100L)).thenReturn(List.of(first, second));
        when(weeklyFileService.listFileIds(QualityWeeklyFileService.WEEKLY_DRAFT, 100L))
                .thenReturn(List.of());
        when(weeklyFileService.listFileIds(QualityWeeklyFileService.WEEKLY_DRAFT_ITEM, 201L))
                .thenReturn(List.of(31L));
        when(weeklyFileService.listFileIds(QualityWeeklyFileService.WEEKLY_DRAFT_ITEM, 202L))
                .thenReturn(List.of(41L));
        when(qualityAssigneeService.requireEligibleAssignee(2L, 9L, manager))
                .thenReturn(user(2L, "整改甲"));
        when(qualityAssigneeService.requireEligibleAssignee(3L, 9L, manager))
                .thenReturn(user(3L, "整改乙"));
        when(inspectionMapper.submit(eq(100L), eq(2), any(), eq(2), eq(1L), any(), any()))
                .thenReturn(1);
        AtomicLong attempts = new AtomicLong();
        when(issueMapper.insert(any())).thenAnswer(invocation -> {
            if (attempts.getAndIncrement() == 0) {
                QualityIssue issue = invocation.getArgument(0);
                issue.setId(300L);
                return 1;
            }
            throw new RuntimeException("第二条问题写入失败");
        });
        when(weeklyFileService.transferDraftFiles(9L,
                QualityWeeklyFileService.WEEKLY_DRAFT_ITEM, 201L,
                QualityWeeklyFileService.ISSUE_FINAL, 300L)).thenReturn(List.of(31L));
        when(logMapper.insert(any())).thenReturn(1);

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> service.submit(100L, action(2), manager));

        assertEquals("第二条问题写入失败", error.getMessage());
        verify(issueMapper, times(2)).insert(any());
        verify(draftItemMapper, never()).deleteByInspectionId(anyLong());
        verify(operationLogMapper, never()).insert(any());
        verify(wechatNotificationService, never()).notifyUser(
                anyLong(), any(), any(), anyLong(), any());
    }

    private QualityWeeklyInspection draft(Long id, int version) {
        LocalDate weekStart = LocalDate.now().with(
                TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        QualityWeeklyInspection draft = new QualityWeeklyInspection();
        draft.setId(id);
        draft.setProjectId(9L);
        draft.setWeekStart(weekStart);
        draft.setInspectionDate(LocalDate.now());
        draft.setStatus(QualityWeeklyInspectionService.STATUS_DRAFT);
        draft.setVersion(version);
        draft.setSubmittedIssueCount(0);
        draft.setCreateTime(LocalDateTime.now());
        draft.setUpdateTime(LocalDateTime.now());
        return draft;
    }

    private QualityWeeklyInspectionDraftItem draftItem(Long id, int order, String title,
                                                        Long assigneeId, LocalDate deadline) {
        QualityWeeklyInspectionDraftItem item = new QualityWeeklyInspectionDraftItem();
        item.setId(id);
        item.setProjectId(9L);
        item.setInspectionId(100L);
        item.setItemKey("item-" + id);
        item.setItemOrder(order);
        item.setTitle(title);
        item.setSeverity("NORMAL");
        item.setAssigneeId(assigneeId);
        item.setDeadline(deadline);
        return item;
    }

    private QualityWeeklyDraftItemRequest itemRequest(String key, int order) {
        QualityWeeklyDraftItemRequest item = new QualityWeeklyDraftItemRequest();
        item.setItemKey(key);
        item.setItemOrder(order);
        return item;
    }

    private QualityWeeklyActionRequest action(int version) {
        QualityWeeklyActionRequest request = new QualityWeeklyActionRequest();
        request.setExpectedVersion(version);
        return request;
    }

    private SysUser user(Long id, String name) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(name);
        user.setRealName(name);
        return user;
    }
}
