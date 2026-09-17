package com.example.siteplatform.quality.service;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.file.service.QualityWeeklyFileService;
import com.example.siteplatform.log.entity.OperationLog;
import com.example.siteplatform.log.mapper.OperationLogMapper;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.quality.dto.QualityWeeklyReturnRequest;
import com.example.siteplatform.quality.entity.*;
import com.example.siteplatform.quality.mapper.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class QualityWeeklyReturnTest {
    final QualityWeeklyInspectionService service = new QualityWeeklyInspectionService();
    final QualityWeeklyInspectionMapper weekly = mock(QualityWeeklyInspectionMapper.class);
    final QualityWeeklyInspectionDraftItemMapper drafts = mock(QualityWeeklyInspectionDraftItemMapper.class);
    final QualityIssueMapper issues = mock(QualityIssueMapper.class);
    final QualityIssueLogMapper logs = mock(QualityIssueLogMapper.class);
    final QualityWeeklyFileService files = mock(QualityWeeklyFileService.class);
    final ProjectPermissionService permissions = mock(ProjectPermissionService.class);
    final OperationLogMapper audit = mock(OperationLogMapper.class);
    final List<QualityWeeklyInspectionDraftItem> restored = new ArrayList<>();
    final SysUser admin = new SysUser();
    final QualityWeeklyInspection inspection = new QualityWeeklyInspection();
    final QualityIssue issue = new QualityIssue();
    final QualityWeeklyReturnRequest request = new QualityWeeklyReturnRequest();

    @BeforeEach void setup() {
        ReflectionTestUtils.setField(service, "inspectionMapper", weekly);
        ReflectionTestUtils.setField(service, "draftItemMapper", drafts);
        ReflectionTestUtils.setField(service, "issueMapper", issues);
        ReflectionTestUtils.setField(service, "logMapper", logs);
        ReflectionTestUtils.setField(service, "weeklyFileService", files);
        ReflectionTestUtils.setField(service, "projectPermissionService", permissions);
        ReflectionTestUtils.setField(service, "operationLogMapper", audit);
        ReflectionTestUtils.setField(service, "reminderSettingService", mock(QualityWeeklyReminderSettingService.class));
        admin.setId(1L); admin.setRealName("合成管理员");
        inspection.setId(10L); inspection.setProjectId(20L); inspection.setVersion(150);
        inspection.setWeekStart(LocalDate.now().minusDays(7)); inspection.setInspectionDate(LocalDate.now().minusDays(5));
        inspection.setConclusion("原检查结论"); inspection.setStatus("SUBMITTED"); inspection.setInspectionNo("QA-WEEKLY");
        inspection.setSubmittedIssueCount(1); inspection.setSubmittedById(2L); inspection.setSubmittedTime(LocalDateTime.now());
        issue.setId(30L); issue.setWeeklyInspectionId(10L); issue.setProjectId(20L);
        issue.setTitle("原问题"); issue.setDescription("原说明"); issue.setLocation("原位置"); issue.setSeverity("NORMAL");
        issue.setStatus("PENDING"); issue.setVersion(2); issue.setInspectionItemOrder(1);
        issue.setAssigneeId(4L); issue.setAssigneeName("原负责人"); issue.setDeadline(LocalDate.now().plusDays(1));
        request.setExpectedVersion(150); request.setReason("误点提交");
        when(permissions.isPlatformAdmin(1L)).thenReturn(true);
        when(permissions.canManageQuality(1L,20L)).thenReturn(true);
        when(weekly.selectForUpdate(10L)).thenReturn(inspection);
        when(issues.selectWeeklyIssuesForUpdate(10L)).thenReturn(List.of(issue));
        when(drafts.selectByInspectionId(10L)).thenAnswer(call -> new ArrayList<>(restored));
        when(drafts.insert(any())).thenAnswer(call -> { QualityWeeklyInspectionDraftItem item=call.getArgument(0); item.setId(40L); restored.add(item); return 1; });
        when(issues.withdrawWeeklyIssue(eq(30L),eq(10L),eq(2),any())).thenReturn(1);
        when(weekly.returnToDraft(eq(10L),eq(150),eq(1L),anyString(),any())).thenReturn(1);
        when(files.returnFinalFilesToDraft(20L,"QUALITY_ISSUE",30L,"QUALITY_WEEKLY_DRAFT_ITEM",40L)).thenReturn(List.of(51L));
        when(files.listFileIds("QUALITY_WEEKLY_DRAFT_ITEM",40L)).thenReturn(List.of(51L));
        when(files.listFileIds("QUALITY_WEEKLY_DRAFT",10L)).thenReturn(List.of(52L));
        when(logs.insert(any())).thenReturn(1); when(audit.insert(any())).thenReturn(1);
    }

    @Test void returnsCompleteEditableDraftAndKeepsAuditInsteadOfHardDeletion() {
        var result=service.returnToDraft(10L,request,admin);
        assertEquals("DRAFT",result.getStatus()); assertEquals(151,result.getVersion());
        assertNull(result.getSubmittedTime()); assertNull(result.getInspectionNo()); assertEquals(0,result.getSubmittedIssueCount());
        assertEquals("原检查结论",result.getConclusion()); assertEquals(List.of(52L),result.getOverviewPhotoFileIds());
        var item=result.getDraftItems().get(0);
        assertEquals("原问题",item.getTitle()); assertEquals("原说明",item.getDescription()); assertEquals("原位置",item.getLocation());
        assertEquals(4L,item.getAssigneeId()); assertEquals(issue.getDeadline(),item.getDeadline());
        assertEquals(List.of(51L),item.getBeforePhotoFileIds());
        verify(files).returnFinalFilesToDraft(20L,"QUALITY_WEEKLY_INSPECTION",10L,"QUALITY_WEEKLY_DRAFT",10L);
        verify(issues).withdrawWeeklyIssue(eq(30L),eq(10L),eq(2),any());
        verify(logs).insert(argThat(log -> "WEEKLY_RETURN".equals(log.getActionType()) && log.getComment().contains("误点提交")));
        verify(audit).insert(argThat((OperationLog log) -> "QUALITY_WEEKLY_RETURN".equals(log.getOperationType()) && log.getUserId()==1L));
    }

    @Test void projectQualityManagerCannotUseAdministratorAction() {
        when(permissions.isPlatformAdmin(1L)).thenReturn(false);
        assertEquals(403,assertThrows(BusinessException.class,()->service.returnToDraft(10L,request,admin)).getCode());
        verifyNoInteractions(weekly,files);
    }

    @Test void disabledProjectModuleStillBlocksAdministrator() {
        doThrow(BusinessException.forbidden("模块未启用")).when(permissions).requireSystemPermission(1L,20L,"quality.view");
        assertEquals(403,assertThrows(BusinessException.class,()->service.returnToDraft(10L,request,admin)).getCode());
        verify(files,never()).returnFinalFilesToDraft(any(),any(),any(),any(),any());
    }

    @Test void requiresNonemptyReasonAndCurrentVersion() {
        request.setReason("   ");
        assertEquals(400,assertThrows(BusinessException.class,()->service.returnToDraft(10L,request,admin)).getCode());
        request.setReason("误点"); request.setExpectedVersion(149);
        assertEquals(409,assertThrows(BusinessException.class,()->service.returnToDraft(10L,request,admin)).getCode());
        verify(drafts,never()).insert(any());
    }

    @ParameterizedTest @ValueSource(strings={"RECHECK","CLOSED","VOIDED"})
    void processedOrVoidedIssuePreventsWholeReturn(String status) {
        issue.setStatus(status);
        assertEquals(409,assertThrows(BusinessException.class,()->service.returnToDraft(10L,request,admin)).getCode());
        verify(drafts,never()).insert(any()); verifyNoInteractions(files);
    }

    @Test void rejectedBackToPendingStillCannotBeReturned() {
        issue.setRectifiedTime(LocalDateTime.now()); issue.setReviewTime(LocalDateTime.now());
        assertEquals(409,assertThrows(BusinessException.class,()->service.returnToDraft(10L,request,admin)).getCode());
        verify(drafts,never()).insert(any());
    }

    @Test void historicalRectificationLogAlsoPreventsReturn() {
        when(logs.selectCount(any())).thenReturn(1L);
        assertEquals(409,assertThrows(BusinessException.class,()->service.returnToDraft(10L,request,admin)).getCode());
        verify(drafts,never()).insert(any());
    }

    @Test void deletedIssueIsSkippedAndRecordedWhileSurvivingIssueIsRestored() {
        inspection.setSubmittedIssueCount(2);
        var result=service.returnToDraft(10L,request,admin);
        assertEquals(1,result.getDraftItems().size());
        assertEquals("原问题",result.getDraftItems().get(0).getTitle());
        verify(audit).insert(argThat((OperationLog log) -> log.getOperationDesc().contains("原提交2个问题，恢复1个现存问题，1个已删除问题不恢复")));
    }

    @Test void allDeletedIssuesReopenEmptyDraftWithExistingOverviewPhotos() {
        when(issues.selectWeeklyIssuesForUpdate(10L)).thenReturn(List.of());
        var result=service.returnToDraft(10L,request,admin);
        assertEquals("DRAFT",result.getStatus());
        assertTrue(result.getDraftItems().isEmpty());
        assertEquals("原检查结论",result.getConclusion());
        assertEquals(List.of(52L),result.getOverviewPhotoFileIds());
        verify(drafts,never()).insert(any());
        verify(issues,never()).withdrawWeeklyIssue(any(),any(),any(),any());
        verify(audit).insert(argThat((OperationLog log) -> log.getOperationDesc().contains("原提交1个问题，恢复0个现存问题，1个已删除问题不恢复")));
    }

    @Test void deletedIssueDoesNotPermitReturningProcessedSurvivingIssues() {
        inspection.setSubmittedIssueCount(2);
        issue.setRectifiedTime(LocalDateTime.now());
        assertEquals(409,assertThrows(BusinessException.class,()->service.returnToDraft(10L,request,admin)).getCode());
        verify(drafts,never()).insert(any());
    }

    @Test void unexpectedAdditionalIssueStillBlocksReturn() {
        inspection.setSubmittedIssueCount(0);
        assertEquals(409,assertThrows(BusinessException.class,()->service.returnToDraft(10L,request,admin)).getCode());
        verify(drafts,never()).insert(any());
    }

    @Test void unexpectedExistingDraftStillBlocksReturn() {
        restored.add(new QualityWeeklyInspectionDraftItem());
        assertEquals(409,assertThrows(BusinessException.class,()->service.returnToDraft(10L,request,admin)).getCode());
        verify(drafts,never()).insert(any());
    }

    @Test void noProblemSubmissionCanAlsoReturnItsOverviewPhotos() {
        inspection.setSubmittedIssueCount(0); when(issues.selectWeeklyIssuesForUpdate(10L)).thenReturn(List.of());
        var result=service.returnToDraft(10L,request,admin);
        assertTrue(result.getDraftItems().isEmpty()); assertEquals(List.of(52L),result.getOverviewPhotoFileIds());
        verify(drafts,never()).insert(any());
    }

    @Test void duplicateRequestCannotRestoreItemsTwice() {
        service.returnToDraft(10L,request,admin);
        assertEquals(409,assertThrows(BusinessException.class,()->service.returnToDraft(10L,request,admin)).getCode());
        verify(drafts,times(1)).insert(any());
    }

    @Test void concurrentIssueUpdateFailsBeforeChangingWeeklyStatus() {
        when(issues.withdrawWeeklyIssue(eq(30L),eq(10L),eq(2),any())).thenReturn(0);
        assertEquals(409,assertThrows(BusinessException.class,()->service.returnToDraft(10L,request,admin)).getCode());
        verify(weekly,never()).returnToDraft(any(),any(),any(),any(),any());
    }
}
