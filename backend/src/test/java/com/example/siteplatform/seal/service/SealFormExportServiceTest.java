package com.example.siteplatform.seal.service;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.file.entity.FileResource;
import com.example.siteplatform.file.mapper.FileResourceMapper;
import com.example.siteplatform.file.storage.*;
import com.example.siteplatform.log.mapper.OperationLogMapper;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.seal.dto.SealFormExportRequest;
import com.example.siteplatform.seal.entity.*;
import com.example.siteplatform.seal.mapper.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SealFormExportServiceTest {
    private final SealFormExportJobMapper jobs = mock(SealFormExportJobMapper.class);
    private final SealFormExportItemMapper items = mock(SealFormExportItemMapper.class);
    private final SealApplicationMapper applications = mock(SealApplicationMapper.class);
    private final SealApplicationService applicationService = mock(SealApplicationService.class);
    private final SealPdfService pdf = mock(SealPdfService.class);
    private final SysUserMapper users = mock(SysUserMapper.class);
    private final ProjectPermissionService permissions = mock(ProjectPermissionService.class);
    private final FileResourceMapper files = mock(FileResourceMapper.class);
    private final FileStorageManager storage = mock(FileStorageManager.class);
    private final OperationLogMapper audit = mock(OperationLogMapper.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final TransactionTemplate transactions = new TransactionTemplate(transactionManager);
    private SealFormExportService service;
    private SysUser user;

    @BeforeEach void setup() {
        service = new SealFormExportService(jobs, items, applications, applicationService, pdf, users,
                permissions, files, storage, audit, transactions, new ObjectMapper().findAndRegisterModules());
        user = new SysUser(); user.setId(7L); user.setStatus(1); user.setRealName("测试申请人");
        when(users.selectByIdForUpdate(7L)).thenReturn(user);
        when(users.selectById(7L)).thenReturn(user);
        when(jobs.selectCount(any())).thenReturn(0L);
        when(jobs.insert(any(SealFormExportJob.class))).thenAnswer(call -> {
            call.<SealFormExportJob>getArgument(0).setId(80L); return 1;
        });
        when(jobs.updateById(any(SealFormExportJob.class))).thenReturn(1);
        when(items.insert(any(SealFormExportItem.class))).thenReturn(1);
        when(files.updateById(any(FileResource.class))).thenReturn(1);
        when(files.stageSealFormExportForDelete(90L, 3L, 80L)).thenReturn(1);
        when(transactionManager.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
    }
    @AfterEach void close() { service.shutdown(); }

    @Test void selectedIdsAreDeduplicatedAndFrozenInServerListOrder() {
        SealApplication newest = application(12L, "APPROVED"), older = application(11L, "APPROVED");
        when(applications.selectList(any())).thenReturn(List.of(newest, older));
        var result = service.create(selected(11L, 12L, 11L), user);
        assertEquals(2, result.applicationCount());
        ArgumentCaptor<SealFormExportItem> captured = ArgumentCaptor.forClass(SealFormExportItem.class);
        verify(items, times(2)).insert(captured.capture());
        assertEquals(List.of(12L, 11L), captured.getAllValues().stream().map(SealFormExportItem::getApplicationId).toList());
        assertEquals(List.of(0, 1), captured.getAllValues().stream().map(SealFormExportItem::getItemOrder).toList());
        verify(applicationService).requireReadable(newest, user);
        verify(applicationService).requireFormExportPermission(older, user);
    }

    @Test void mixedApprovalStatesRejectTheWholeSelectionBeforeWriting() {
        when(applications.selectList(any())).thenReturn(List.of(application(12L, "APPROVED"), application(11L, "PENDING_APPROVAL")));
        assertEquals(409, assertThrows(BusinessException.class, () -> service.create(selected(11L, 12L), user)).getCode());
        verify(jobs, never()).insert(any(SealFormExportJob.class));
        verifyNoInteractions(items);
    }

    @Test void missingAndCrossProjectSelectionsNeverSilentlyOmitRecords() {
        when(applications.selectList(any())).thenReturn(List.of(application(12L, "APPROVED")));
        assertEquals(403, assertThrows(BusinessException.class, () -> service.create(selected(11L, 12L), user)).getCode());
        SealApplication otherProject = application(12L, "APPROVED"); otherProject.setProjectId(4L);
        when(applications.selectList(any())).thenReturn(List.of(otherProject));
        assertEquals(403, assertThrows(BusinessException.class, () -> service.create(selected(12L), user)).getCode());
        verify(jobs, never()).insert(any(SealFormExportJob.class));
    }

    @Test void recordAclIsRequiredEvenWithExportPermission() {
        SealApplication application = application(12L, "APPROVED");
        when(applications.selectList(any())).thenReturn(List.of(application));
        doThrow(BusinessException.forbidden("不允许查看此申请")).when(applicationService).requireReadable(application, user);
        assertEquals(403, assertThrows(BusinessException.class, () -> service.create(selected(12L), user)).getCode());
        verify(jobs, never()).insert(any(SealFormExportJob.class));
    }

    @Test void filterUsesAppliedScopeDatesAndFetchesOneExtraToDetectOverflow() {
        SealFormExportRequest request = filter();
        request.setScope("CC_TO_ME"); request.setKeyword("验收"); request.setStatus("APPROVED");
        request.setStartDate(LocalDate.of(2026, 9, 1)); request.setEndDate(LocalDate.of(2026, 9, 11));
        when(applicationService.findFormsByFilter(3L, "CC_TO_ME", "APPROVED", "验收", request.getStartDate(), request.getEndDate(), 501, user))
                .thenReturn(List.of(application(12L, "APPROVED")));
        assertEquals(1, service.create(request, user).applicationCount());
        verifyNoInteractions(applications);
    }

    @Test void emptyAndOverLimitFiltersRejectRatherThanTruncate() {
        when(applicationService.findFormsByFilter(eq(3L), anyString(), isNull(), isNull(), isNull(), isNull(), eq(501), eq(user)))
                .thenReturn(List.of()).thenReturn(Collections.nCopies(501, application(1L, "APPROVED")));
        assertEquals(400, assertThrows(BusinessException.class, () -> service.create(filter(), user)).getCode());
        assertEquals(413, assertThrows(BusinessException.class, () -> service.create(filter(), user)).getCode());
        verify(jobs, never()).insert(any(SealFormExportJob.class));
    }

    @Test void idempotentRequestReturnsOriginalJobAndRejectsChangedPayload() {
        var request = selected(12L, 12L);
        var job = job("PENDING"); job.setRequestHash(service.fingerprint(request));
        when(jobs.selectOne(any())).thenReturn(job); stubItems(job);
        assertEquals(job.getId(), service.create(selected(12L), user).id());
        assertEquals(409, assertThrows(BusinessException.class, () -> service.create(selected(13L), user)).getCode());
        verify(jobs, never()).insert(any(SealFormExportJob.class));
    }

    @Test void twoUnfinishedJobsBlockNewWorkAcrossProjects() {
        when(jobs.selectCount(any())).thenReturn(2L);
        assertEquals(409, assertThrows(BusinessException.class, () -> service.create(selected(12L), user)).getCode());
        verify(users).selectByIdForUpdate(7L);
        verifyNoInteractions(applications);
    }

    @Test void losingExportPermissionRejectsCreationAndDownload() {
        doThrow(BusinessException.forbidden("缺少导出权限")).when(permissions).requireSystemPermission(7L, 3L, "seal.application.export");
        when(jobs.selectById(80L)).thenReturn(job("SUCCEEDED"));
        assertEquals(403, assertThrows(BusinessException.class, () -> service.create(selected(12L), user)).getCode());
        assertEquals(403, assertThrows(BusinessException.class, () -> service.download(80L, user)).getCode());
        verifyNoInteractions(storage);
    }

    @Test void downloadRechecksRecordAccessAndNeverAllowsAnotherOwner() {
        var job = job("SUCCEEDED"); when(jobs.selectById(80L)).thenReturn(job); stubItems(job);
        SealApplication application = applicationService.requireApplication(12L);
        doThrow(BusinessException.forbidden("记录权限已撤销")).when(applicationService).requireReadable(application, user);
        assertEquals(403, assertThrows(BusinessException.class, () -> service.download(80L, user)).getCode());
        job.setRequestedById(8L);
        assertEquals(403, assertThrows(BusinessException.class, () -> service.get(80L, user)).getCode());
        verifyNoInteractions(storage);
    }

    @Test void downloadsOnlyUnexpiredOwnedResultThroughStorageStream() {
        var job = job("SUCCEEDED"); when(jobs.selectById(80L)).thenReturn(job); stubItems(job);
        FileResource file = file("UPLOADED"); when(files.selectById(90L)).thenReturn(file);
        var resource = new ByteArrayResource(new byte[]{1, 2}); when(storage.load(file)).thenReturn(resource);
        assertSame(resource, service.download(80L, user).resource());
        job.setExpiresTime(LocalDateTime.now().minusDays(1));
        assertEquals(409, assertThrows(BusinessException.class, () -> service.download(80L, user)).getCode());
    }

    @Test void interruptedJobIsClaimedWithNewLeaseAndOldFileQueuedForCleanup() {
        var job = job("RUNNING"); when(jobs.nextForUpdate()).thenReturn(job);
        FileResource file = file("GENERATING"); when(files.selectById(90L)).thenReturn(file);
        String oldLease = job.getLeaseOwner();
        var claimed = service.claim();
        assertEquals(2, claimed.getAttempts()); assertNotEquals(oldLease, claimed.getLeaseOwner());
        assertNull(claimed.getFileResourceId()); assertEquals(0, claimed.getProcessedCount());
        verify(files).stageSealFormExportForDelete(90L, 3L, 80L);
        verifyNoInteractions(storage);
    }

    @Test void repeatedlyInterruptedJobStopsAndCanBeExplicitlyRetried() {
        var job = job("RUNNING"); job.setAttempts(3); when(jobs.nextForUpdate()).thenReturn(job);
        assertNull(service.claim()); assertEquals("FAILED", job.getStatus()); assertNull(job.getLeaseOwner());
        when(jobs.selectById(80L)).thenReturn(job); stubItems(job);
        when(applications.selectList(any())).thenReturn(List.of(application(12L, "APPROVED")));
        assertEquals("PENDING", service.retry(80L, "retry-key", user).status());
    }

    @Test void staleWorkerCannotPublishOrFailAnotherWorkersJob() {
        var claimed = job("RUNNING"); var live = job("RUNNING"); live.setLeaseOwner("new-worker");
        when(jobs.lock(80L)).thenReturn(live);
        assertEquals(409, assertThrows(BusinessException.class, () -> service.complete(claimed, 90L, stored(), 2)).getCode());
        service.fail(claimed, new RuntimeException("old worker"));
        assertEquals("RUNNING", live.getStatus()); verifyNoInteractions(files, audit);
    }

    @Test void expiredResultsUseDeferredCleanupWithoutDeletingPhysicalFilesInTransaction() {
        var job = job("SUCCEEDED"); job.setExpiresTime(LocalDateTime.now().minusDays(1));
        when(jobs.migrationApplied()).thenReturn(1); when(jobs.selectList(any())).thenReturn(List.of(job));
        when(jobs.lock(80L)).thenReturn(job);
        FileResource file = file("UPLOADED"); when(files.selectById(90L)).thenReturn(file);
        service.expire();
        assertEquals("EXPIRED", job.getStatus());
        verify(files).stageSealFormExportForDelete(90L, 3L, 80L);
        verify(transactionManager).commit(any()); verifyNoInteractions(storage);
    }

    @Test void failedCleanupClaimRollsBackExpiryAndRetainsThePhysicalFile() {
        var job = job("SUCCEEDED"); job.setExpiresTime(LocalDateTime.now().minusDays(1));
        when(jobs.migrationApplied()).thenReturn(1); when(jobs.selectList(any())).thenReturn(List.of(job));
        when(jobs.lock(80L)).thenReturn(job); when(files.selectById(90L)).thenReturn(file("UPLOADED"));
        when(files.stageSealFormExportForDelete(90L, 3L, 80L)).thenReturn(0);
        assertEquals(409, assertThrows(BusinessException.class, service::expire).getCode());
        assertEquals("SUCCEEDED", job.getStatus());
        verify(transactionManager).rollback(any()); verify(transactionManager, never()).commit(any());
        verifyNoInteractions(storage);
    }

    @Test void completionAuditFailureRollsBackThePublicationTransaction() {
        var job = job("RUNNING"); when(jobs.lock(80L)).thenReturn(job); stubItems(job);
        when(files.selectById(90L)).thenReturn(file("GENERATING")); when(audit.insert(any())).thenReturn(0);
        assertThrows(BusinessException.class, () -> transactions.executeWithoutResult(status -> service.complete(job, 90L, stored(), 2)));
        verify(transactionManager).rollback(any()); verify(transactionManager, never()).commit(any());
    }

    @Test void successfulCompletionWritesApplicationAndBatchAuditAndSevenDayExpiry() {
        var job = job("RUNNING"); when(jobs.lock(80L)).thenReturn(job); stubItems(job);
        when(files.selectById(90L)).thenReturn(file("GENERATING")); when(audit.insert(any())).thenReturn(1);
        service.complete(job, 90L, stored(), 2);
        assertEquals("SUCCEEDED", job.getStatus()); assertEquals(2, job.getPageCount());
        assertTrue(job.getExpiresTime().isAfter(LocalDateTime.now().plusDays(6)));
        verify(applicationService).recordExternalAction(any(), eq("EXPORT_MERGED_PDF"), eq(user), isNull(), contains("80"), isNull());
        verify(audit).insert(any());
    }

    @Test void failedWorkQueuesTheReservedFileAndDoesNotExposeInternalExceptionDetails() {
        var job = job("RUNNING"); when(jobs.lock(80L)).thenReturn(job);
        FileResource file = file("GENERATING"); when(files.selectById(90L)).thenReturn(file);
        service.fail(job, new RuntimeException("secret storage credential"));
        assertEquals("FAILED", job.getStatus()); assertNull(job.getFileResourceId());
        assertFalse(job.getErrorMessage().contains("secret"));
        verify(files).stageSealFormExportForDelete(90L, 3L, 80L);
        verifyNoInteractions(storage);
    }

    private SealApplication application(Long id, String status) {
        SealApplication row = new SealApplication(); row.setId(id); row.setProjectId(3L); row.setStatus(status); return row;
    }
    private SealFormExportRequest selected(Long... ids) {
        SealFormExportRequest request = filter(); request.setSelectionMode("SELECTED"); request.setApplicationIds(Arrays.asList(ids)); return request;
    }
    private SealFormExportRequest filter() {
        SealFormExportRequest request = new SealFormExportRequest(); request.setProjectId(3L);
        request.setSelectionMode("FILTER"); request.setScope("INITIATED"); request.setRequestKey("test-idempotency"); return request;
    }
    private SealFormExportJob job(String status) {
        SealFormExportJob row = new SealFormExportJob(); row.setId(80L); row.setProjectId(3L); row.setRequestedById(7L);
        row.setStatus(status); row.setLeaseOwner("old-worker"); row.setAttempts(1); row.setProcessedCount(1);
        row.setFileResourceId(90L); row.setApplicationCount(1); row.setExpiresTime(LocalDateTime.now().plusDays(7)); return row;
    }
    private FileResource file(String status) {
        FileResource row = new FileResource(); row.setId(90L); row.setBusinessType("SEAL_FORM_EXPORT");
        row.setBusinessId(80L); row.setProjectId(3L); row.setStatus(status); row.setDeleted(0); return row;
    }
    private void stubItems(SealFormExportJob job) {
        SealFormExportItem item = new SealFormExportItem(); item.setJobId(job.getId()); item.setApplicationId(12L);
        when(items.selectList(any())).thenReturn(List.of(item));
        when(applicationService.requireApplication(12L)).thenReturn(application(12L, "APPROVED"));
    }
    private StoredFile stored() { return new StoredFile("local", "key.pdf", "合并.pdf", "application/pdf", "pdf", 100, "sha"); }
}
