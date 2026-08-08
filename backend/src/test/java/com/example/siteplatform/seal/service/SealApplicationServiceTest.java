package com.example.siteplatform.seal.service;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.file.mapper.FileResourceMapper;
import com.example.siteplatform.notification.service.UserNotificationService;
import com.example.siteplatform.project.entity.ProjectInfo;
import com.example.siteplatform.project.mapper.ProjectInfoMapper;
import com.example.siteplatform.project.mapper.SysUserProjectMapper;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.seal.dto.SealApplicationItemRequest;
import com.example.siteplatform.seal.dto.SealApplicationSaveRequest;
import com.example.siteplatform.seal.dto.SealTransferRequest;
import com.example.siteplatform.seal.entity.SealApplication;
import com.example.siteplatform.seal.entity.SealApplicationItem;
import com.example.siteplatform.seal.entity.SealApplicationLog;
import com.example.siteplatform.seal.entity.SealDefinition;
import com.example.siteplatform.seal.mapper.SealApplicationFileMapper;
import com.example.siteplatform.seal.mapper.SealApplicationItemMapper;
import com.example.siteplatform.seal.mapper.SealApplicationLogMapper;
import com.example.siteplatform.seal.mapper.SealApplicationMapper;
import com.example.siteplatform.workflow.entity.WorkflowApprovalConfig;
import com.example.siteplatform.workflow.entity.WorkflowApprovalConfigUser;
import com.example.siteplatform.workflow.entity.WorkflowApprovalInstance;
import com.example.siteplatform.workflow.entity.WorkflowApprovalTask;
import com.example.siteplatform.workflow.entity.WorkflowCcRecipient;
import com.example.siteplatform.workflow.mapper.WorkflowApprovalInstanceMapper;
import com.example.siteplatform.workflow.mapper.WorkflowApprovalTaskMapper;
import com.example.siteplatform.workflow.mapper.WorkflowCcRecipientMapper;
import com.example.siteplatform.workflow.service.WorkflowApprovalConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SealApplicationServiceTest {

    @Mock private SealApplicationMapper applicationMapper;
    @Mock private SealApplicationItemMapper itemMapper;
    @Mock private SealApplicationFileMapper applicationFileMapper;
    @Mock private SealApplicationLogMapper logMapper;
    @Mock private WorkflowApprovalInstanceMapper instanceMapper;
    @Mock private WorkflowApprovalTaskMapper taskMapper;
    @Mock private WorkflowCcRecipientMapper ccMapper;
    @Mock private FileResourceMapper fileMapper;
    @Mock private SysUserMapper userMapper;
    @Mock private SysUserProjectMapper userProjectMapper;
    @Mock private ProjectInfoMapper projectMapper;
    @Mock private SealDefinitionService sealService;
    @Mock private WorkflowApprovalConfigService configService;
    @Mock private ProjectPermissionService permissionService;
    @Mock private UserNotificationService notificationService;

    private SealApplicationService service;
    private SysUser applicant;

    @BeforeEach
    void setUp() {
        service = new SealApplicationService(applicationMapper, itemMapper, applicationFileMapper, logMapper,
                instanceMapper, taskMapper, ccMapper, fileMapper, userMapper, userProjectMapper, projectMapper,
                sealService, configService, permissionService, notificationService);
        applicant = user(7L, "申请人张三", "19900000007");

        lenient().when(permissionService.getProjectAccessStatus(anyLong(), anyLong())).thenReturn("ACTIVE");
        lenient().when(permissionService.hasSystemPermission(anyLong(), anyLong(), anyString())).thenReturn(false);
        lenient().when(itemMapper.selectList(any())).thenReturn(List.of());
        lenient().when(itemMapper.delete(any())).thenReturn(0);
        lenient().when(itemMapper.insert(any())).thenReturn(1);
        lenient().when(applicationFileMapper.selectList(any())).thenReturn(List.of());
        lenient().when(ccMapper.selectList(any())).thenReturn(List.of());
        lenient().when(ccMapper.delete(any())).thenReturn(0);
        lenient().when(ccMapper.insert(any())).thenReturn(1);
        lenient().when(ccMapper.selectCount(any())).thenReturn(0L);
        lenient().when(taskMapper.selectCount(any())).thenReturn(0L);
        lenient().when(logMapper.selectList(any())).thenReturn(List.of());
        lenient().when(logMapper.insert(any())).thenReturn(1);
    }

    @Test
    void explicitEmptyCcListMeansNoRecipientsAndDoesNotRestoreConfiguredDefaults() {
        SealApplicationSaveRequest request = saveRequest(List.of());
        stubCreateContext();

        service.create(request, applicant, null);

        verify(configService, never()).defaultCcUserIds(anyLong(), anyLong());
        verify(ccMapper, never()).insert(any());
    }

    @Test
    void omittedCcListUsesConfiguredDefaults() {
        SealApplicationSaveRequest request = saveRequest(null);
        stubCreateContext();
        when(configService.defaultCcUserIds(9L, 3L)).thenReturn(List.of(7L, 11L, 11L));
        when(userMapper.selectById(11L)).thenReturn(user(11L, "抄送人王五", "19900000011"));
        when(userProjectMapper.selectCount(any())).thenReturn(1L);

        service.create(request, applicant, null);

        ArgumentCaptor<WorkflowCcRecipient> recipient = ArgumentCaptor.forClass(WorkflowCcRecipient.class);
        verify(ccMapper).insert(recipient.capture());
        assertEquals(11L, recipient.getValue().getUserId());
        assertEquals("抄送人王五", recipient.getValue().getUserName());
        verify(userMapper, never()).selectById(7L);
    }

    @Test
    void draftCcSelectionDoesNotGrantRecordOrAttachmentReadAccessBeforeSubmit() {
        SealApplication draft = draft(42L, 7L);
        SysUser selectedCc = user(11L, "抄送人王五", "19900000011");

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.requireReadable(draft, selectedCc));

        assertEquals(403, error.getCode());
    }

    @Test
    void submittedCcRecipientHasReadOnlyParticipantAccess() {
        SealApplication submitted = pending(42L, 7L, 501L);
        SysUser selectedCc = user(11L, "抄送人王五", "19900000011");
        when(ccMapper.selectCount(any())).thenReturn(1L);

        service.requireReadable(submitted, selectedCc);

        verify(ccMapper).selectCount(any());
    }

    @Test
    void concurrentCreateWithSameRequestKeyReturnsExistingDraft() {
        SealApplication existing = draft(42L, applicant.getId());
        when(applicationMapper.selectOne(any())).thenReturn(null);
        when(sealService.requireActiveSeal(3L, 9L))
                .thenReturn(seal(3L, 9L, "项目章", "上海建工智慧营造有限公司"));
        when(projectMapper.selectById(9L)).thenReturn(project(9L, "智慧营造项目"));
        when(applicationMapper.insert(any())).thenThrow(new DuplicateKeyException("duplicate request key"));
        when(applicationMapper.selectByRequestKeyForUpdate(applicant.getId(), "request-42"))
                .thenReturn(existing);

        var result = service.create(saveRequest(List.of()), applicant, null);

        assertEquals(42L, result.getId());
        verify(itemMapper, never()).insert(any());
        verify(logMapper, never()).insert(any());
    }

    @Test
    void submitGeneratesNumberAndSnapshotsCurrentProjectSealApplicantConfigAndCandidates() {
        SealApplication draft = draft(42L, applicant.getId());
        SealDefinition currentSeal = seal(3L, 9L, "合同专用章", "最新公司名称");
        ProjectInfo currentProject = project(9L, "最新项目名称");
        WorkflowApprovalConfig config = config(81L, 6);
        WorkflowApprovalConfigUser selfApprover = configUser(81L, 7L);
        WorkflowApprovalConfigUser secondApprover = configUser(81L, 8L);
        SysUser second = user(8L, "项目经理李四", "19900000008");

        when(applicationMapper.selectForUpdate(42L)).thenReturn(draft);
        when(sealService.requireActiveSeal(3L, 9L)).thenReturn(currentSeal);
        when(projectMapper.selectById(9L)).thenReturn(currentProject);
        when(itemMapper.selectCount(any())).thenReturn(1L);
        when(applicationFileMapper.selectCount(any())).thenReturn(1L);
        when(configService.requireEnabledSnapshot(9L, 3L)).thenReturn(
                new WorkflowApprovalConfigService.ApprovalConfigSnapshot(
                        config, List.of(selfApprover, secondApprover)));
        when(userMapper.selectById(7L)).thenReturn(applicant);
        when(userMapper.selectById(8L)).thenReturn(second);
        when(userProjectMapper.selectCount(any())).thenReturn(1L);
        doAnswer(invocation -> {
            WorkflowApprovalInstance instance = invocation.getArgument(0);
            instance.setId(501L);
            return 1;
        }).when(instanceMapper).insert(any());
        AtomicLong taskIds = new AtomicLong(700L);
        doAnswer(invocation -> {
            WorkflowApprovalTask task = invocation.getArgument(0);
            task.setId(taskIds.incrementAndGet());
            return 1;
        }).when(taskMapper).insert(any());
        doAnswer(invocation -> {
            draft.setApplicationNo(invocation.getArgument(2));
            draft.setApplicationDate(invocation.getArgument(3));
            draft.setDepartmentName(invocation.getArgument(4));
            draft.setSealName(invocation.getArgument(5));
            draft.setCompanyName(invocation.getArgument(6));
            draft.setApplicantName(invocation.getArgument(7));
            draft.setApplicantPhone(invocation.getArgument(8));
            draft.setApprovalInstanceId(invocation.getArgument(9));
            draft.setSubmitTime(invocation.getArgument(10));
            draft.setStatus(SealApplicationService.PENDING_APPROVAL);
            draft.setVersion(1);
            return 1;
        }).when(applicationMapper).submit(eq(42L), eq(0), anyString(), any(), anyString(), anyString(),
                anyString(), anyString(), anyString(), eq(501L), any());
        when(applicationMapper.selectById(42L)).thenReturn(draft);
        SealApplicationItem item = item(42L, "施工方案", 2);
        when(itemMapper.selectList(any())).thenReturn(List.of(item));
        WorkflowCcRecipient approverAlsoCc = cc(42L, 8L, "项目经理李四");
        WorkflowCcRecipient ordinaryCc = cc(42L, 11L, "抄送人王五");
        when(ccMapper.selectList(any())).thenReturn(List.of(approverAlsoCc, ordinaryCc));
        WorkflowApprovalTask selfTask = task(701L, 501L, 7L, 0);
        when(taskMapper.selectOne(any())).thenReturn(selfTask);

        var result = service.submit(42L, applicant, null);

        ArgumentCaptor<String> number = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<LocalDate> applicationDate = ArgumentCaptor.forClass(LocalDate.class);
        verify(applicationMapper).submit(eq(42L), eq(0), number.capture(), applicationDate.capture(),
                eq("最新项目名称"), eq("合同专用章"), eq("最新公司名称"),
                eq("申请人张三"), eq("19900000007"), eq(501L), any());
        assertEquals("YYSQ-" + applicationDate.getValue().format(DateTimeFormatter.BASIC_ISO_DATE)
                + "-00000042", number.getValue());
        assertEquals(number.getValue(), result.getApplicationNo());
        assertEquals("最新项目名称", result.getDepartmentName());
        assertEquals("合同专用章", result.getSealName());
        assertTrue(result.getCanApprove(), "申请人被直接配置为审批人时应能审批本人申请");

        ArgumentCaptor<WorkflowApprovalInstance> instance = ArgumentCaptor.forClass(WorkflowApprovalInstance.class);
        verify(instanceMapper).insert(instance.capture());
        assertEquals(6, instance.getValue().getConfigVersion());
        assertEquals("ANY_ONE", instance.getValue().getApprovalMode());
        assertEquals(7L, instance.getValue().getInitiatorId());

        ArgumentCaptor<WorkflowApprovalTask> tasks = ArgumentCaptor.forClass(WorkflowApprovalTask.class);
        verify(taskMapper, times(2)).insert(tasks.capture());
        assertEquals(List.of(7L, 8L), tasks.getAllValues().stream()
                .map(WorkflowApprovalTask::getAssigneeUserId).toList());
        assertEquals(List.of("申请人张三", "项目经理李四"), tasks.getAllValues().stream()
                .map(WorkflowApprovalTask::getAssigneeName).toList());
        assertTrue(tasks.getAllValues().stream().allMatch(task -> "CONFIG".equals(task.getAssignmentSource())));
        verify(notificationService).notify(eq(11L), eq(9L), eq(SealApplicationService.BUSINESS_CODE), eq(42L),
                eq("SEAL_CC_SUBMITTED"), anyString(), anyString(), anyString());
        verify(notificationService, never()).notify(eq(8L), eq(9L), eq(SealApplicationService.BUSINESS_CODE),
                eq(42L), eq("SEAL_CC_SUBMITTED"), anyString(), anyString(), anyString());
    }

    @Test
    void repeatedSubmitReturnsExistingApplicationWithoutCreatingDuplicateWorkflow() {
        SealApplication alreadySubmitted = pending(42L, applicant.getId(), 501L);
        when(applicationMapper.selectForUpdate(42L)).thenReturn(alreadySubmitted);

        var result = service.submit(42L, applicant, null);

        assertEquals(SealApplicationService.PENDING_APPROVAL, result.getStatus());
        assertEquals("YYSQ-20260808-00000042", result.getApplicationNo());
        verify(instanceMapper, never()).insert(any());
        verify(taskMapper, never()).insert(any());
        verify(applicationMapper, never()).submit(anyLong(), any(), anyString(), any(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyLong(), any());
    }

    @Test
    void approveAndRejectBothRequireNonBlankManagerOpinionBeforeAnyWrite() {
        BusinessException approve = assertThrows(BusinessException.class,
                () -> service.approve(42L, "   ", applicant, null));
        BusinessException reject = assertThrows(BusinessException.class,
                () -> service.reject(42L, null, applicant, null));

        assertTrue(approve.getMessage().contains("项目经理审批意见不能为空"));
        assertTrue(reject.getMessage().contains("项目经理审批意见不能为空"));
        verify(applicationMapper, never()).selectForUpdate(anyLong());
        verify(taskMapper, never()).decide(anyLong(), any(), anyString(), anyLong(), anyString(), anyString(), any());
    }

    @Test
    void explicitlyAssignedApplicantCanApproveOwnApplicationAndClosesOtherAnyOneTasks() {
        SealApplication pending = pending(42L, 7L, 501L);
        WorkflowApprovalTask ownTask = task(701L, 501L, 7L, 0);
        WorkflowApprovalInstance instance = instance(501L, 0);
        stubDecisionContext(pending, ownTask, instance);
        when(taskMapper.decide(eq(701L), eq(0), eq("APPROVED"), eq(7L), eq("申请人张三"),
                eq("同意用印"), any())).thenReturn(1);
        when(instanceMapper.decide(eq(501L), eq(0), eq("APPROVED"), eq(7L), eq("申请人张三"),
                eq("同意用印"), any())).thenReturn(1);
        when(taskMapper.cancelPendingByInstance(eq(501L), any())).thenReturn(1);
        doAnswer(invocation -> {
            pending.setStatus(SealApplicationService.APPROVED);
            pending.setApproverId(7L);
            pending.setApproverName("申请人张三");
            pending.setApprovalOpinion("同意用印");
            pending.setApprovalTime(invocation.getArgument(6));
            pending.setVersion(2);
            return 1;
        }).when(applicationMapper).decide(eq(42L), eq(1), eq(SealApplicationService.APPROVED), eq(7L),
                eq("申请人张三"), eq("同意用印"), any());

        var result = service.approve(42L, "同意用印", applicant, null);

        assertEquals(SealApplicationService.APPROVED, result.getStatus());
        assertEquals(7L, result.getApproverId());
        assertEquals("同意用印", result.getApprovalOpinion());
        verify(taskMapper).cancelPendingByInstance(eq(501L), any());
        ArgumentCaptor<SealApplicationLog> log = ArgumentCaptor.forClass(SealApplicationLog.class);
        verify(logMapper).insert(log.capture());
        assertEquals("APPROVE", log.getValue().getActionCode());
        assertEquals("同意用印", log.getValue().getOpinion());
    }

    @Test
    void administratorWithoutAssignedPendingTaskCannotApprove() {
        SysUser administrator = user(99L, "平台管理员", "19900000099");
        SealApplication pending = pending(42L, 7L, 501L);
        when(applicationMapper.selectForUpdate(42L)).thenReturn(pending);
        when(taskMapper.selectOne(any())).thenReturn(null);
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.approve(42L, "管理员代批", administrator, null));

        assertEquals(403, error.getCode());
        assertTrue(error.getMessage().contains("不是该申请的待办审批人"));
        verify(taskMapper, never()).decide(anyLong(), any(), anyString(), anyLong(), anyString(), anyString(), any());
        verify(instanceMapper, never()).decide(anyLong(), any(), anyString(), anyLong(), anyString(), anyString(), any());
        verify(applicationMapper, never()).decide(anyLong(), any(), anyString(), anyLong(), anyString(), anyString(), any());
    }

    @Test
    void userRemovedFromProjectCannotUsePreviouslyAssignedPendingTask() {
        SysUser removedApprover = user(8L, "已移出审批人", "19900000008");
        SealApplication pending = pending(42L, 7L, 501L);
        when(applicationMapper.selectForUpdate(42L)).thenReturn(pending);
        when(permissionService.getProjectAccessStatus(8L, 9L)).thenReturn("DISABLED");

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.approve(42L, "试图继续审批", removedApprover, null));

        assertEquals(403, error.getCode());
        verify(taskMapper, never()).selectOne(any());
        verify(taskMapper, never()).decide(anyLong(), any(), anyString(), anyLong(), anyString(), anyString(), any());
        verify(applicationMapper, never()).decide(anyLong(), any(), anyString(), anyLong(), anyString(), anyString(), any());
    }

    @Test
    void staleTaskCasReturnsConflictBeforeInstanceOrApplicationCanBeDecided() {
        SealApplication pending = pending(42L, 7L, 501L);
        WorkflowApprovalTask task = task(701L, 501L, 8L, 3);
        WorkflowApprovalInstance instance = instance(501L, 4);
        SysUser approver = user(8L, "项目经理李四", "19900000008");
        stubDecisionContext(pending, task, instance);
        when(taskMapper.decide(eq(701L), eq(3), eq("REJECTED"), eq(8L), eq("项目经理李四"),
                eq("资料不完整"), any())).thenReturn(0);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.reject(42L, "资料不完整", approver, null));

        assertEquals(409, error.getCode());
        verify(instanceMapper, never()).decide(anyLong(), any(), anyString(), anyLong(), anyString(), anyString(), any());
        verify(applicationMapper, never()).decide(anyLong(), any(), anyString(), anyLong(), anyString(), anyString(), any());
        verify(taskMapper, never()).cancelPendingByInstance(anyLong(), any());
        verify(logMapper, never()).insert(any());
    }

    @Test
    void transferRequiresReasonAndValidTransferPreservesAuditChain() {
        SysUser administrator = user(99L, "平台管理员", "19900000099");
        SysUser target = user(12L, "新项目经理", "19900000012");
        SealApplication pending = pending(42L, 7L, 501L);
        WorkflowApprovalInstance instance = instance(501L, 0);
        WorkflowApprovalTask oldA = task(701L, 501L, 8L, 0);
        WorkflowApprovalTask oldB = task(702L, 501L, 9L, 0);
        when(applicationMapper.selectForUpdate(42L)).thenReturn(pending);
        when(applicationMapper.selectById(42L)).thenReturn(pending);
        when(instanceMapper.selectById(501L)).thenReturn(instance);
        when(userMapper.selectById(12L)).thenReturn(target);
        when(userProjectMapper.selectCount(any())).thenReturn(1L);

        SealTransferRequest invalid = new SealTransferRequest();
        invalid.setAssigneeUserId(12L);
        invalid.setReason("   ");
        BusinessException reasonError = assertThrows(BusinessException.class,
                () -> service.transfer(42L, invalid, administrator, null));
        assertTrue(reasonError.getMessage().contains("转办原因不能为空"));
        verify(taskMapper, never()).cancelPendingByInstance(anyLong(), any());

        when(taskMapper.selectList(any())).thenReturn(List.of(oldB, oldA));
        when(taskMapper.cancelPendingByInstance(eq(501L), any())).thenReturn(2);
        doAnswer(invocation -> {
            WorkflowApprovalTask task = invocation.getArgument(0);
            task.setId(801L);
            return 1;
        }).when(taskMapper).insert(any());
        SealTransferRequest valid = new SealTransferRequest();
        valid.setAssigneeUserId(12L);
        valid.setReason("原审批人休假，改派处理");

        service.transfer(42L, valid, administrator, null);

        ArgumentCaptor<WorkflowApprovalTask> created = ArgumentCaptor.forClass(WorkflowApprovalTask.class);
        verify(taskMapper).insert(created.capture());
        assertEquals("ADMIN_REASSIGN", created.getValue().getAssignmentSource());
        assertEquals(702L, created.getValue().getTransferredFromTaskId());
        assertEquals(12L, created.getValue().getAssigneeUserId());
        ArgumentCaptor<SealApplicationLog> log = ArgumentCaptor.forClass(SealApplicationLog.class);
        verify(logMapper).insert(log.capture());
        assertEquals("TRANSFER", log.getValue().getActionCode());
        assertEquals("原审批人休假，改派处理", log.getValue().getOpinion());
        assertFalse(SealApplicationService.APPROVED.equals(pending.getStatus()));
        assertEquals(SealApplicationService.PENDING_APPROVAL, pending.getStatus());
    }

    private void stubCreateContext() {
        SealDefinition seal = seal(3L, 9L, "项目章", "上海建工智慧营造有限公司");
        when(applicationMapper.selectOne(any())).thenReturn(null);
        when(sealService.requireActiveSeal(3L, 9L)).thenReturn(seal);
        when(projectMapper.selectById(9L)).thenReturn(project(9L, "智慧营造项目"));
        doAnswer(invocation -> {
            SealApplication application = invocation.getArgument(0);
            application.setId(42L);
            return 1;
        }).when(applicationMapper).insert(any());
    }

    private void stubDecisionContext(SealApplication application, WorkflowApprovalTask task,
                                     WorkflowApprovalInstance instance) {
        when(applicationMapper.selectForUpdate(42L)).thenReturn(application);
        lenient().when(applicationMapper.selectById(42L)).thenReturn(application);
        when(taskMapper.selectOne(any())).thenReturn(task);
        when(instanceMapper.selectById(501L)).thenReturn(instance);
    }

    private SealApplicationSaveRequest saveRequest(List<Long> ccUserIds) {
        SealApplicationSaveRequest request = new SealApplicationSaveRequest();
        request.setRequestKey("request-42");
        request.setProjectId(9L);
        request.setSealId(3L);
        request.setDepartmentName("客户端伪造项目名");
        request.setPurpose("申请加盖项目章");
        request.setItems(List.of(itemRequest("施工方案", 2)));
        request.setCcUserIds(ccUserIds);
        return request;
    }

    private SealApplicationItemRequest itemRequest(String name, int copies) {
        SealApplicationItemRequest item = new SealApplicationItemRequest();
        item.setDocumentName(name);
        item.setCopies(copies);
        return item;
    }

    private SealApplication draft(Long id, Long applicantId) {
        SealApplication application = new SealApplication();
        application.setId(id);
        application.setProjectId(9L);
        application.setSealId(3L);
        application.setSealName("旧印章快照");
        application.setCompanyName("旧公司快照");
        application.setDepartmentName("旧项目快照");
        application.setPurpose("申请加盖项目章");
        application.setApplicantId(applicantId);
        application.setApplicantName("旧申请人快照");
        application.setApplicantPhone("10000000000");
        application.setStatus(SealApplicationService.DRAFT);
        application.setVersion(0);
        application.setDeleted(0);
        application.setCreateTime(LocalDateTime.now());
        return application;
    }

    private SealApplication pending(Long id, Long applicantId, Long instanceId) {
        SealApplication application = draft(id, applicantId);
        application.setApplicationNo("YYSQ-20260808-00000042");
        application.setStatus(SealApplicationService.PENDING_APPROVAL);
        application.setApprovalInstanceId(instanceId);
        application.setApplicationDate(LocalDate.of(2026, 8, 8));
        application.setVersion(1);
        return application;
    }

    private SealApplicationItem item(Long applicationId, String name, int copies) {
        SealApplicationItem item = new SealApplicationItem();
        item.setId(91L);
        item.setApplicationId(applicationId);
        item.setDocumentName(name);
        item.setCopies(copies);
        item.setSortOrder(1);
        return item;
    }

    private SealDefinition seal(Long id, Long projectId, String name, String company) {
        SealDefinition seal = new SealDefinition();
        seal.setId(id);
        seal.setProjectId(projectId);
        seal.setSealName(name);
        seal.setCompanyName(company);
        seal.setStatus("ENABLED");
        return seal;
    }

    private ProjectInfo project(Long id, String name) {
        ProjectInfo project = new ProjectInfo();
        project.setId(id);
        project.setProjectName(name);
        return project;
    }

    private WorkflowApprovalConfig config(Long id, int version) {
        WorkflowApprovalConfig config = new WorkflowApprovalConfig();
        config.setId(id);
        config.setProjectId(9L);
        config.setSealId(3L);
        config.setBusinessCode(SealApplicationService.BUSINESS_CODE);
        config.setApprovalMode("ANY_ONE");
        config.setEnabled(1);
        config.setConfigVersion(version);
        return config;
    }

    private WorkflowApprovalConfigUser configUser(Long configId, Long userId) {
        WorkflowApprovalConfigUser relation = new WorkflowApprovalConfigUser();
        relation.setConfigId(configId);
        relation.setProjectId(9L);
        relation.setUserId(userId);
        relation.setAssignmentType("APPROVER");
        return relation;
    }

    private WorkflowApprovalTask task(Long id, Long instanceId, Long assigneeId, int version) {
        WorkflowApprovalTask task = new WorkflowApprovalTask();
        task.setId(id);
        task.setInstanceId(instanceId);
        task.setBusinessCode(SealApplicationService.BUSINESS_CODE);
        task.setBusinessId(42L);
        task.setProjectId(9L);
        task.setAssigneeUserId(assigneeId);
        task.setAssigneeName("用户" + assigneeId);
        task.setStatus("PENDING");
        task.setVersion(version);
        return task;
    }

    private WorkflowApprovalInstance instance(Long id, int version) {
        WorkflowApprovalInstance instance = new WorkflowApprovalInstance();
        instance.setId(id);
        instance.setStatus("PENDING");
        instance.setVersion(version);
        return instance;
    }

    private WorkflowCcRecipient cc(Long applicationId, Long userId, String name) {
        WorkflowCcRecipient recipient = new WorkflowCcRecipient();
        recipient.setBusinessCode(SealApplicationService.BUSINESS_CODE);
        recipient.setBusinessId(applicationId);
        recipient.setProjectId(9L);
        recipient.setUserId(userId);
        recipient.setUserName(name);
        return recipient;
    }

    private SysUser user(Long id, String name, String phone) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(phone);
        user.setRealName(name);
        user.setPhone(phone);
        user.setStatus(1);
        user.setDeleted(0);
        return user;
    }
}
