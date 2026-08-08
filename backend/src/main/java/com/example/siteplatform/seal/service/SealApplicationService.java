package com.example.siteplatform.seal.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.common.PageResult;
import com.example.siteplatform.file.entity.FileResource;
import com.example.siteplatform.file.mapper.FileResourceMapper;
import com.example.siteplatform.notification.service.UserNotificationService;
import com.example.siteplatform.project.entity.ProjectInfo;
import com.example.siteplatform.project.entity.SysUserProject;
import com.example.siteplatform.project.mapper.ProjectInfoMapper;
import com.example.siteplatform.project.mapper.SysUserProjectMapper;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.seal.dto.SealApplicationCopyRequest;
import com.example.siteplatform.seal.dto.SealApplicationItemRequest;
import com.example.siteplatform.seal.dto.SealApplicationSaveRequest;
import com.example.siteplatform.seal.dto.SealTransferRequest;
import com.example.siteplatform.seal.entity.SealApplication;
import com.example.siteplatform.seal.entity.SealApplicationFile;
import com.example.siteplatform.seal.entity.SealApplicationItem;
import com.example.siteplatform.seal.entity.SealApplicationLog;
import com.example.siteplatform.seal.entity.SealDefinition;
import com.example.siteplatform.seal.mapper.SealApplicationFileMapper;
import com.example.siteplatform.seal.mapper.SealApplicationItemMapper;
import com.example.siteplatform.seal.mapper.SealApplicationLogMapper;
import com.example.siteplatform.seal.mapper.SealApplicationMapper;
import com.example.siteplatform.seal.vo.SealApplicationFileVO;
import com.example.siteplatform.seal.vo.SealApplicationItemVO;
import com.example.siteplatform.seal.vo.SealApplicationLogVO;
import com.example.siteplatform.seal.vo.SealApplicationVO;
import com.example.siteplatform.seal.vo.SealCcRecipientVO;
import com.example.siteplatform.seal.vo.SealUserOptionVO;
import com.example.siteplatform.system.constant.SystemPermissionCodes;
import com.example.siteplatform.workflow.entity.WorkflowApprovalConfig;
import com.example.siteplatform.workflow.entity.WorkflowApprovalConfigUser;
import com.example.siteplatform.workflow.entity.WorkflowApprovalInstance;
import com.example.siteplatform.workflow.entity.WorkflowApprovalTask;
import com.example.siteplatform.workflow.entity.WorkflowCcRecipient;
import com.example.siteplatform.workflow.mapper.WorkflowApprovalInstanceMapper;
import com.example.siteplatform.workflow.mapper.WorkflowApprovalTaskMapper;
import com.example.siteplatform.workflow.mapper.WorkflowCcRecipientMapper;
import com.example.siteplatform.workflow.service.WorkflowApprovalConfigService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
public class SealApplicationService {
    public static final String BUSINESS_CODE = "SEAL_APPLICATION";
    public static final String DRAFT = "DRAFT";
    public static final String PENDING_APPROVAL = "PENDING_APPROVAL";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String WITHDRAWN = "WITHDRAWN";
    private static final Set<String> STATUSES = Set.of(DRAFT, PENDING_APPROVAL, APPROVED, REJECTED, WITHDRAWN);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final SealApplicationMapper applicationMapper;
    private final SealApplicationItemMapper itemMapper;
    private final SealApplicationFileMapper applicationFileMapper;
    private final SealApplicationLogMapper logMapper;
    private final WorkflowApprovalInstanceMapper instanceMapper;
    private final WorkflowApprovalTaskMapper taskMapper;
    private final WorkflowCcRecipientMapper ccMapper;
    private final FileResourceMapper fileMapper;
    private final SysUserMapper userMapper;
    private final SysUserProjectMapper userProjectMapper;
    private final ProjectInfoMapper projectMapper;
    private final SealDefinitionService sealService;
    private final WorkflowApprovalConfigService configService;
    private final ProjectPermissionService permissionService;
    private final UserNotificationService notificationService;

    public SealApplicationService(SealApplicationMapper applicationMapper,
                                  SealApplicationItemMapper itemMapper,
                                  SealApplicationFileMapper applicationFileMapper,
                                  SealApplicationLogMapper logMapper,
                                  WorkflowApprovalInstanceMapper instanceMapper,
                                  WorkflowApprovalTaskMapper taskMapper,
                                  WorkflowCcRecipientMapper ccMapper,
                                  FileResourceMapper fileMapper,
                                  SysUserMapper userMapper,
                                  SysUserProjectMapper userProjectMapper,
                                  ProjectInfoMapper projectMapper,
                                  SealDefinitionService sealService,
                                  WorkflowApprovalConfigService configService,
                                  ProjectPermissionService permissionService,
                                  UserNotificationService notificationService) {
        this.applicationMapper = applicationMapper;
        this.itemMapper = itemMapper;
        this.applicationFileMapper = applicationFileMapper;
        this.logMapper = logMapper;
        this.instanceMapper = instanceMapper;
        this.taskMapper = taskMapper;
        this.ccMapper = ccMapper;
        this.fileMapper = fileMapper;
        this.userMapper = userMapper;
        this.userProjectMapper = userProjectMapper;
        this.projectMapper = projectMapper;
        this.sealService = sealService;
        this.configService = configService;
        this.permissionService = permissionService;
        this.notificationService = notificationService;
    }

    public PageResult<SealApplicationVO> list(Long projectId, String scope, String status, String keyword,
                                               LocalDate startDate, LocalDate endDate,
                                               Integer pageNo, Integer pageSize, SysUser currentUser) {
        return list(projectId, scope, status, keyword, startDate, endDate, null, pageNo, pageSize, currentUser);
    }

    public PageResult<SealApplicationVO> list(Long projectId, String scope, String status, String keyword,
                                               LocalDate startDate, LocalDate endDate, String dateBasis,
                                               Integer pageNo, Integer pageSize, SysUser currentUser) {
        String normalizedScope = normalizeScope(scope);
        int page = pageNo == null ? 1 : Math.max(1, pageNo);
        int size = pageSize == null ? 20 : Math.max(1, Math.min(pageSize, 100));
        List<Long> activeProjectIds = activeProjectIds(currentUser);
        if (projectId != null && !activeProjectIds.contains(projectId)) {
            throw BusinessException.forbidden("仅当前项目有效成员可查看用印申请");
        }
        if ("ALL".equals(normalizedScope)) {
            if (projectId == null) throw new BusinessException("全部申请查询必须指定项目");
            permissionService.requireSystemPermission(currentUser.getId(), projectId, SystemPermissionCodes.SEAL_VIEW);
        }
        if (activeProjectIds.isEmpty()) return PageResult.of(page, size, 0L, List.of());

        LambdaQueryWrapper<SealApplication> query = new LambdaQueryWrapper<SealApplication>()
                .in(SealApplication::getProjectId, projectId == null ? activeProjectIds : List.of(projectId));
        switch (normalizedScope) {
            case "INITIATED" -> query.eq(SealApplication::getApplicantId, currentUser.getId());
            case "PENDING_FOR_ME" -> query.inSql(SealApplication::getId,
                    "SELECT business_id FROM workflow_approval_task WHERE business_code = 'SEAL_APPLICATION'"
                            + " AND status = 'PENDING' AND assignee_user_id = " + currentUser.getId());
            case "CC_TO_ME" -> query.inSql(SealApplication::getId,
                    "SELECT business_id FROM workflow_cc_recipient WHERE business_code = 'SEAL_APPLICATION'"
                            + " AND user_id = " + currentUser.getId())
                    .ne(SealApplication::getStatus, DRAFT);
            case "ALL" -> { }
            default -> throw new BusinessException("不支持的用印申请视图");
        }
        if (StringUtils.hasText(status)) query.eq(SealApplication::getStatus, normalizeStatus(status));
        if (StringUtils.hasText(keyword)) {
            String value = keyword.trim();
            query.and(row -> row.like(SealApplication::getApplicationNo, value)
                    .or().like(SealApplication::getPurpose, value)
                    .or().like(SealApplication::getApplicantName, value)
                    .or().like(SealApplication::getSealName, value));
        }
        boolean approvalDate = "APPROVAL_TIME".equalsIgnoreCase(Objects.toString(dateBasis, ""));
        if (approvalDate) {
            if (startDate != null) query.ge(SealApplication::getApprovalTime, startDate.atStartOfDay());
            if (endDate != null) query.lt(SealApplication::getApprovalTime, endDate.plusDays(1).atStartOfDay());
        } else {
            if (startDate != null) query.ge(SealApplication::getApplicationDate, startDate);
            if (endDate != null) query.le(SealApplication::getApplicationDate, endDate);
        }
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new BusinessException("开始日期不能晚于结束日期");
        }
        query.orderByDesc(SealApplication::getCreateTime).orderByDesc(SealApplication::getId);
        Page<SealApplication> result = applicationMapper.selectPage(new Page<>(page, size), query);
        return PageResult.of(page, size, result.getTotal(), result.getRecords().stream()
                .map(application -> toVO(application, currentUser, false)).toList());
    }

    public SealApplicationVO detail(Long id, SysUser currentUser) {
        SealApplication application = requireApplication(id);
        requireReadable(application, currentUser);
        return toVO(application, currentUser, true);
    }

    @Transactional
    public SealApplicationVO create(SealApplicationSaveRequest request, SysUser currentUser,
                                    HttpServletRequest servletRequest) {
        if (request == null) throw new BusinessException("申请内容不能为空");
        String requestKey = required(request.getRequestKey(), 64, "requestKey");
        SealApplication existing = applicationMapper.selectOne(new LambdaQueryWrapper<SealApplication>()
                .eq(SealApplication::getApplicantId, currentUser.getId())
                .eq(SealApplication::getRequestKey, requestKey).last("LIMIT 1"));
        if (existing != null) {
            requireReadable(existing, currentUser);
            return toVO(existing, currentUser, true);
        }

        SealDefinition seal;
        if (StringUtils.hasText(request.getScene())) {
            sealService.requireActiveWechatBinding(currentUser);
            seal = sealService.requireSceneSeal(request.getScene());
            if (request.getProjectId() != null && !request.getProjectId().equals(seal.getProjectId())) {
                throw new BusinessException("二维码项目与请求项目不一致");
            }
            if (request.getSealId() != null && !request.getSealId().equals(seal.getId())) {
                throw new BusinessException("二维码印章与请求印章不一致");
            }
        } else {
            if (request.getProjectId() == null || request.getSealId() == null) {
                throw new BusinessException("项目和印章不能为空");
            }
            seal = sealService.requireActiveSeal(request.getSealId(), request.getProjectId());
        }
        requireActiveMember(currentUser, seal.getProjectId());
        ProjectInfo project = requireProject(seal.getProjectId());
        List<SealApplicationItemRequest> items = validateItems(request.getItems());
        List<Long> requestedCcUserIds = request.getCcUserIds() == null
                ? configService.defaultCcUserIds(seal.getProjectId(), seal.getId())
                : request.getCcUserIds();
        List<Long> ccUserIds = validateCcUsers(
                seal.getProjectId(), requestedCcUserIds, currentUser.getId());

        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        SealApplication application = new SealApplication();
        application.setApplicationNo(null);
        application.setRequestKey(requestKey);
        application.setProjectId(seal.getProjectId());
        application.setSealId(seal.getId());
        application.setSealName(seal.getSealName());
        application.setCompanyName(seal.getCompanyName());
        // Project/department is a trusted server-side snapshot; client text is deliberately ignored.
        application.setDepartmentName(project.getProjectName());
        application.setPurpose(required(request.getPurpose(), 1000, "用印事由"));
        application.setApplicantId(currentUser.getId());
        application.setApplicantName(displayName(currentUser));
        application.setApplicantPhone(currentUser.getPhone());
        application.setApplicationDate(null);
        application.setStatus(DRAFT);
        application.setVersion(0);
        application.setDeleted(0);
        application.setCreateTime(now);
        application.setUpdateTime(now);
        try {
            requireSingleWrite(applicationMapper.insert(application), "用印申请新增");
        } catch (DuplicateKeyException duplicate) {
            // The unique applicant/requestKey constraint is the concurrency backstop. Use
            // a current locking read so MySQL REPEATABLE READ does not keep the stale
            // snapshot created by the optimistic lookup above.
            SealApplication concurrent = applicationMapper.selectByRequestKeyForUpdate(
                    currentUser.getId(), requestKey);
            if (concurrent == null) {
                throw BusinessException.of(409, "重复请求正在处理中，请稍后查询");
            }
            requireReadable(concurrent, currentUser);
            return toVO(concurrent, currentUser, true);
        }
        replaceItems(application, items, now);
        replaceCc(application, ccUserIds, now);
        record(application, "CREATE", null, DRAFT, currentUser, null, "创建用印申请草稿", servletRequest);
        return toVO(application, currentUser, true);
    }

    @Transactional
    public SealApplicationVO update(Long id, SealApplicationSaveRequest request, SysUser currentUser,
                                    HttpServletRequest servletRequest) {
        if (request == null) throw new BusinessException("申请内容不能为空");
        SealApplication current = applicationMapper.selectForUpdate(id);
        if (current == null) throw BusinessException.notFound("用印申请不存在");
        requireActiveMember(currentUser, current.getProjectId());
        if (!currentUser.getId().equals(current.getApplicantId()) || !DRAFT.equals(current.getStatus())) {
            throw BusinessException.forbidden("只有申请人可以编辑草稿");
        }
        if (request.getProjectId() != null && !request.getProjectId().equals(current.getProjectId())) {
            throw new BusinessException("申请项目不可修改");
        }
        if (request.getSealId() != null && !request.getSealId().equals(current.getSealId())) {
            throw new BusinessException("申请印章不可修改");
        }
        ProjectInfo project = requireProject(current.getProjectId());
        List<SealApplicationItemRequest> items = validateItems(request.getItems());
        List<Long> ccUserIds = request.getCcUserIds() == null
                ? currentCcIds(current.getId())
                : validateCcUsers(current.getProjectId(), request.getCcUserIds(), currentUser.getId());
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        requireSingleWrite(applicationMapper.updateDraft(current.getId(), currentUser.getId(), current.getVersion(),
                project.getProjectName(), required(request.getPurpose(), 1000, "用印事由"), now), "用印草稿更新");
        replaceItems(current, items, now);
        replaceCc(current, ccUserIds, now);
        record(current, "UPDATE", DRAFT, DRAFT, currentUser, null, "修改用印申请草稿", servletRequest);
        return toVO(requireApplication(id), currentUser, true);
    }

    @Transactional
    public SealApplicationVO copy(Long sourceId, SealApplicationCopyRequest request, SysUser currentUser,
                                  HttpServletRequest servletRequest) {
        SealApplication source = requireApplication(sourceId);
        requireActiveMember(currentUser, source.getProjectId());
        if (!Objects.equals(source.getApplicantId(), currentUser.getId())) {
            throw BusinessException.forbidden("只能复制本人申请");
        }
        if (!REJECTED.equals(source.getStatus()) && !WITHDRAWN.equals(source.getStatus())) {
            throw stateConflict("只有已驳回或已撤回申请可以复制");
        }
        SealApplicationSaveRequest create = new SealApplicationSaveRequest();
        create.setRequestKey(request == null ? null : request.getRequestKey());
        create.setProjectId(source.getProjectId());
        create.setSealId(source.getSealId());
        create.setPurpose(source.getPurpose());
        create.setCcUserIds(request == null || request.getCcUserIds() == null
                ? currentCcIds(sourceId) : request.getCcUserIds());
        create.setItems(itemMapper.selectList(new LambdaQueryWrapper<SealApplicationItem>()
                        .eq(SealApplicationItem::getApplicationId, sourceId)
                        .orderByAsc(SealApplicationItem::getSortOrder)).stream()
                .map(item -> {
                    SealApplicationItemRequest row = new SealApplicationItemRequest();
                    row.setDocumentName(item.getDocumentName());
                    row.setCopies(item.getCopies());
                    return row;
                }).toList());
        SealApplicationVO result = create(create, currentUser, servletRequest);
        SealApplication copied = applicationMapper.selectForUpdate(result.getId());
        if (copied != null && copied.getSourceApplicationId() == null) {
            copied.setSourceApplicationId(sourceId);
            copied.setUpdateTime(LocalDateTime.now(BUSINESS_ZONE));
            requireSingleWrite(applicationMapper.updateById(copied), "复制来源写入");
            record(copied, "COPY", null, DRAFT, currentUser, null,
                    "从用印申请 " + source.getApplicationNo() + " 复制；附件未复制", servletRequest);
        }
        return toVO(requireApplication(result.getId()), currentUser, true);
    }

    @Transactional
    public SealApplicationVO submit(Long id, SysUser currentUser, HttpServletRequest servletRequest) {
        SealApplication application = applicationMapper.selectForUpdate(id);
        if (application == null) throw BusinessException.notFound("用印申请不存在");
        requireActiveMember(currentUser, application.getProjectId());
        if (!currentUser.getId().equals(application.getApplicantId())) {
            throw BusinessException.forbidden("只有申请人可以提交申请");
        }
        if (!DRAFT.equals(application.getStatus())) {
            if (application.getApplicationNo() != null && application.getApprovalInstanceId() != null
                    && Set.of(PENDING_APPROVAL, APPROVED, REJECTED, WITHDRAWN).contains(application.getStatus())) {
                // A retried submission must not create a second instance or task set.
                return toVO(application, currentUser, true);
            }
            throw stateConflict("只有草稿可以提交");
        }
        SealDefinition currentSeal = sealService.requireActiveSeal(application.getSealId(), application.getProjectId());
        ProjectInfo currentProject = requireProject(application.getProjectId());
        if (itemMapper.selectCount(new LambdaQueryWrapper<SealApplicationItem>()
                .eq(SealApplicationItem::getApplicationId, id)) == 0) throw new BusinessException("至少填写一项用印文件");
        if (applicationFileMapper.selectCount(new LambdaQueryWrapper<SealApplicationFile>()
                .eq(SealApplicationFile::getApplicationId, id)
                .eq(SealApplicationFile::getFileRole, "SOURCE")) == 0) {
            throw new BusinessException("请先上传至少一份待盖章资料");
        }
        WorkflowApprovalConfigService.ApprovalConfigSnapshot configSnapshot =
                configService.requireEnabledSnapshot(application.getProjectId(), application.getSealId());
        WorkflowApprovalConfig config = configSnapshot.config();
        List<WorkflowApprovalConfigUser> approvers = configSnapshot.approvers();
        Set<Long> approverIds = approvers.stream().map(WorkflowApprovalConfigUser::getUserId)
                .filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        for (WorkflowApprovalConfigUser approver : approvers) {
            requireEligibleUser(application.getProjectId(), approver.getUserId());
        }
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        WorkflowApprovalInstance instance = new WorkflowApprovalInstance();
        instance.setBusinessCode(BUSINESS_CODE);
        instance.setBusinessId(id);
        instance.setProjectId(application.getProjectId());
        instance.setConfigId(config.getId());
        instance.setConfigVersion(config.getConfigVersion());
        instance.setApprovalMode("ANY_ONE");
        instance.setStatus("PENDING");
        instance.setInitiatorId(currentUser.getId());
        instance.setInitiatorName(displayName(currentUser));
        instance.setVersion(0);
        instance.setCreateTime(now);
        instance.setUpdateTime(now);
        requireSingleWrite(instanceMapper.insert(instance), "审批实例新增");
        for (WorkflowApprovalConfigUser relation : approvers) {
            SysUser assignee = requireUser(relation.getUserId());
            WorkflowApprovalTask task = new WorkflowApprovalTask();
            task.setInstanceId(instance.getId());
            task.setBusinessCode(BUSINESS_CODE);
            task.setBusinessId(id);
            task.setProjectId(application.getProjectId());
            task.setAssigneeUserId(assignee.getId());
            task.setAssigneeName(displayName(assignee));
            task.setStatus("PENDING");
            task.setAssignmentSource("CONFIG");
            task.setVersion(0);
            task.setCreateTime(now);
            task.setUpdateTime(now);
            requireSingleWrite(taskMapper.insert(task), "审批任务新增");
            notificationService.notify(assignee.getId(), application.getProjectId(), BUSINESS_CODE, id,
                    "SEAL_PENDING_APPROVAL", "待审批：" + application.getSealName(),
                    application.getApplicantName() + "提交了用印申请", "seal:task:" + task.getId());
        }
        LocalDate applicationDate = now.toLocalDate();
        String applicationNo = generateApplicationNo(application.getId(), applicationDate);
        requireSingleWrite(applicationMapper.submit(id, application.getVersion(), applicationNo,
                applicationDate, currentProject.getProjectName(), currentSeal.getSealName(), currentSeal.getCompanyName(),
                displayName(currentUser), currentUser.getPhone(), instance.getId(), now), "用印申请提交");
        for (WorkflowCcRecipient recipient : ccRecipients(id)) {
            if (Objects.equals(recipient.getUserId(), application.getApplicantId())
                    || approverIds.contains(recipient.getUserId())) continue;
            notificationService.notify(recipient.getUserId(), application.getProjectId(), BUSINESS_CODE, id,
                    "SEAL_CC_SUBMITTED", "用印申请抄送", application.getApplicantName() + "提交了用印申请",
                    "seal:cc:submit:" + id + ":" + recipient.getUserId());
        }
        record(application, "SUBMIT", DRAFT, PENDING_APPROVAL, currentUser, null, "提交项目经理审批", servletRequest);
        return toVO(requireApplication(id), currentUser, true);
    }

    @Transactional
    public SealApplicationVO approve(Long id, String opinion, SysUser currentUser,
                                     HttpServletRequest servletRequest) {
        return decide(id, APPROVED, required(opinion, 1000, "项目经理审批意见"), currentUser, servletRequest);
    }

    @Transactional
    public SealApplicationVO reject(Long id, String opinion, SysUser currentUser,
                                    HttpServletRequest servletRequest) {
        return decide(id, REJECTED, required(opinion, 1000, "项目经理审批意见"), currentUser, servletRequest);
    }

    private SealApplicationVO decide(Long id, String targetStatus, String opinion, SysUser currentUser,
                                     HttpServletRequest servletRequest) {
        SealApplication application = applicationMapper.selectForUpdate(id);
        if (application == null) throw BusinessException.notFound("用印申请不存在");
        requireActiveMember(currentUser, application.getProjectId());
        if (!PENDING_APPROVAL.equals(application.getStatus())) throw stateConflict("申请当前不可审批");
        WorkflowApprovalTask task = pendingTask(application, currentUser.getId());
        if (task == null) throw BusinessException.forbidden("当前用户不是该申请的待办审批人");
        WorkflowApprovalInstance instance = instanceMapper.selectById(application.getApprovalInstanceId());
        if (instance == null || !"PENDING".equals(instance.getStatus())) throw stateConflict("审批任务已处理");
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        String taskStatus = APPROVED.equals(targetStatus) ? "APPROVED" : "REJECTED";
        requireSingleWrite(taskMapper.decide(task.getId(), task.getVersion(), taskStatus, currentUser.getId(),
                displayName(currentUser), opinion, now), "审批任务处理");
        requireSingleWrite(instanceMapper.decide(instance.getId(), instance.getVersion(), taskStatus,
                currentUser.getId(), displayName(currentUser), opinion, now), "审批实例处理");
        taskMapper.cancelPendingByInstance(instance.getId(), now);
        requireSingleWrite(applicationMapper.decide(id, application.getVersion(), targetStatus, currentUser.getId(),
                displayName(currentUser), opinion, now), "用印申请审批");
        String event = APPROVED.equals(targetStatus) ? "SEAL_APPROVED" : "SEAL_REJECTED";
        String label = APPROVED.equals(targetStatus) ? "审批通过" : "审批驳回";
        notificationService.notify(application.getApplicantId(), application.getProjectId(), BUSINESS_CODE, id,
                event, "用印申请" + label, displayName(currentUser) + "已处理您的申请",
                "seal:decision:" + targetStatus + ":" + id + ":applicant");
        for (WorkflowCcRecipient recipient : ccRecipients(id)) {
            if (Objects.equals(recipient.getUserId(), application.getApplicantId())
                    || Objects.equals(recipient.getUserId(), currentUser.getId())) continue;
            notificationService.notify(recipient.getUserId(), application.getProjectId(), BUSINESS_CODE, id,
                    event, "抄送的用印申请" + label, application.getApplicationNo(),
                    "seal:decision:" + targetStatus + ":" + id + ":cc:" + recipient.getUserId());
        }
        record(application, APPROVED.equals(targetStatus) ? "APPROVE" : "REJECT", PENDING_APPROVAL,
                targetStatus, currentUser, opinion, label, servletRequest);
        return toVO(requireApplication(id), currentUser, true);
    }

    @Transactional
    public SealApplicationVO withdraw(Long id, SysUser currentUser, HttpServletRequest servletRequest) {
        SealApplication application = applicationMapper.selectForUpdate(id);
        if (application == null) throw BusinessException.notFound("用印申请不存在");
        requireActiveMember(currentUser, application.getProjectId());
        if (!currentUser.getId().equals(application.getApplicantId())) {
            throw BusinessException.forbidden("只有申请人可以撤回申请");
        }
        if (!PENDING_APPROVAL.equals(application.getStatus())) {
            throw stateConflict("当前状态不能撤回");
        }
        String from = application.getStatus();
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        WorkflowApprovalInstance instance = instanceMapper.selectById(application.getApprovalInstanceId());
        if (instance == null || !"PENDING".equals(instance.getStatus())) throw stateConflict("审批已经处理");
        Set<Long> recipients = taskMapper.selectList(new LambdaQueryWrapper<WorkflowApprovalTask>()
                        .eq(WorkflowApprovalTask::getInstanceId, instance.getId())
                        .eq(WorkflowApprovalTask::getStatus, "PENDING"))
                .stream().map(WorkflowApprovalTask::getAssigneeUserId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        ccRecipients(id).stream().map(WorkflowCcRecipient::getUserId)
                .filter(Objects::nonNull).forEach(recipients::add);
        recipients.remove(currentUser.getId());
        requireSingleWrite(instanceMapper.withdraw(instance.getId(), instance.getVersion(), now), "审批实例撤回");
        taskMapper.cancelPendingByInstance(instance.getId(), now);
        requireSingleWrite(applicationMapper.withdraw(id, application.getVersion(), now), "用印申请撤回");
        for (Long recipientId : recipients) {
            notificationService.notify(recipientId, application.getProjectId(), BUSINESS_CODE, id,
                    "SEAL_WITHDRAWN", "用印申请已撤回", application.getApplicationNo(),
                    "seal:withdraw:" + id + ":" + recipientId);
        }
        record(application, "WITHDRAW", from, WITHDRAWN, currentUser, null, "申请人撤回用印申请", servletRequest);
        return toVO(requireApplication(id), currentUser, true);
    }

    @Transactional
    public SealApplicationVO transfer(Long id, SealTransferRequest request, SysUser currentUser,
                                      HttpServletRequest servletRequest) {
        if (request == null) throw new BusinessException("转办信息不能为空");
        SealApplication application = applicationMapper.selectForUpdate(id);
        if (application == null) throw BusinessException.notFound("用印申请不存在");
        requireApprovalManage(currentUser, application.getProjectId());
        if (!PENDING_APPROVAL.equals(application.getStatus())) throw stateConflict("申请当前不可转办");
        Long targetId = request.getAssigneeUserId();
        SysUser target = requireEligibleUser(application.getProjectId(), targetId);
        WorkflowApprovalInstance instance = instanceMapper.selectById(application.getApprovalInstanceId());
        if (instance == null || !"PENDING".equals(instance.getStatus())) throw stateConflict("审批已经处理");
        String reason = required(request.getReason(), 500, "转办原因");
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        List<WorkflowApprovalTask> previousTasks = taskMapper.selectList(
                new LambdaQueryWrapper<WorkflowApprovalTask>()
                        .eq(WorkflowApprovalTask::getInstanceId, instance.getId())
                        .orderByDesc(WorkflowApprovalTask::getId));
        int cancelled = taskMapper.cancelPendingByInstance(instance.getId(), now);
        long expectedPending = previousTasks.stream().filter(item -> "PENDING".equals(item.getStatus())).count();
        if (cancelled != expectedPending) throw stateConflict("待办任务状态已变化，请刷新后重试");
        WorkflowApprovalTask task = new WorkflowApprovalTask();
        task.setInstanceId(instance.getId());
        task.setBusinessCode(BUSINESS_CODE);
        task.setBusinessId(id);
        task.setProjectId(application.getProjectId());
        task.setAssigneeUserId(targetId);
        task.setAssigneeName(displayName(target));
        task.setStatus("PENDING");
        task.setAssignmentSource("ADMIN_REASSIGN");
        if (!previousTasks.isEmpty()) task.setTransferredFromTaskId(previousTasks.get(0).getId());
        task.setVersion(0);
        task.setCreateTime(now);
        task.setUpdateTime(now);
        requireSingleWrite(taskMapper.insert(task), "转办任务新增");
        notificationService.notify(targetId, application.getProjectId(), BUSINESS_CODE, id,
                "SEAL_TASK_TRANSFERRED", "转办待审批：" + application.getSealName(), reason,
                "seal:transfer:task:" + task.getId());
        record(application, "TRANSFER", PENDING_APPROVAL, PENDING_APPROVAL, currentUser, reason,
                "转办给" + displayName(target), servletRequest);
        return toVO(requireApplication(id), currentUser, true);
    }

    public List<SealUserOptionVO> transferCandidates(Long id, String keyword, SysUser currentUser) {
        SealApplication application = requireApplication(id);
        requireApprovalManage(currentUser, application.getProjectId());
        if (!PENDING_APPROVAL.equals(application.getStatus())) throw stateConflict("申请当前不可改派");
        return configService.candidates(application.getProjectId(), application.getSealId(), keyword,
                currentUser, true);
    }

    public List<SealUserOptionVO> ccCandidates(Long projectId, Long sealId, String keyword, SysUser currentUser) {
        return configService.candidates(projectId, sealId, keyword, currentUser, false);
    }

    public SealApplication requireApplication(Long id) {
        SealApplication application = id == null ? null : applicationMapper.selectById(id);
        if (application == null) throw BusinessException.notFound("用印申请不存在");
        return application;
    }

    /**
     * Serializes every mutation whose validity depends on the application status.
     * Source-file changes use the same row lock as submit(), so a submitted
     * application cannot gain or lose source files after submit validation.
     */
    public SealApplication requireApplicationForUpdate(Long id) {
        SealApplication application = id == null ? null : applicationMapper.selectForUpdate(id);
        if (application == null) throw BusinessException.notFound("用印申请不存在");
        return application;
    }

    public void requireReadable(SealApplication application, SysUser currentUser) {
        requireActiveMember(currentUser, application.getProjectId());
        if (isParticipant(application, currentUser.getId())) return;
        if (!permissionService.hasSystemPermission(currentUser.getId(), application.getProjectId(),
                SystemPermissionCodes.SEAL_VIEW)
                && !permissionService.hasSystemPermission(currentUser.getId(), application.getProjectId(),
                SystemPermissionCodes.APPROVAL_MANAGE)) {
            throw BusinessException.forbidden("无权查看该用印申请");
        }
    }

    public boolean isParticipant(SealApplication application, Long userId) {
        if (Objects.equals(application.getApplicantId(), userId)) return true;
        // Draft CC rows are editable selections owned by the applicant. They only become
        // read-only participants once the application has actually been submitted.
        if (DRAFT.equals(application.getStatus())) return false;
        if (taskMapper.selectCount(new LambdaQueryWrapper<WorkflowApprovalTask>()
                .eq(WorkflowApprovalTask::getBusinessCode, BUSINESS_CODE)
                .eq(WorkflowApprovalTask::getBusinessId, application.getId())
                .eq(WorkflowApprovalTask::getAssigneeUserId, userId)) > 0) return true;
        return ccMapper.selectCount(new LambdaQueryWrapper<WorkflowCcRecipient>()
                .eq(WorkflowCcRecipient::getBusinessCode, BUSINESS_CODE)
                .eq(WorkflowCcRecipient::getBusinessId, application.getId())
                .eq(WorkflowCcRecipient::getUserId, userId)) > 0;
    }

    public boolean canManage(SysUser user, Long projectId) {
        return permissionService.hasSystemPermission(user.getId(), projectId, SystemPermissionCodes.SEAL_MANAGE);
    }

    public SealApplicationFileVO fileVO(SealApplicationFile relation, SealApplication application, SysUser user) {
        return toFileVO(relation, application, user);
    }

    public void recordExternalAction(SealApplication application, String action, SysUser user,
                                     String opinion, String description, HttpServletRequest request) {
        record(application, action, application.getStatus(), application.getStatus(), user, opinion, description, request);
    }

    public void notifyStampedResult(SealApplication application, Long fileRelationId, String fileName, Long actorId) {
        Set<Long> recipients = new LinkedHashSet<>();
        recipients.add(application.getApplicantId());
        ccRecipients(application.getId()).stream().map(WorkflowCcRecipient::getUserId)
                .filter(Objects::nonNull).forEach(recipients::add);
        recipients.remove(actorId);
        for (Long recipientId : recipients) {
            notificationService.notify(recipientId, application.getProjectId(), BUSINESS_CODE,
                    application.getId(), "SEAL_STAMPED_RESULT_UPLOADED", "用印盖章件已补传",
                    fileName, "seal:stamped:" + application.getId() + ":" + fileRelationId + ":" + recipientId);
        }
    }

    public void notifyArchived(SealApplication application, Long fileRelationId, Long documentId,
                               Long versionId, Long actorId) {
        Set<Long> recipients = new LinkedHashSet<>();
        recipients.add(application.getApplicantId());
        ccRecipients(application.getId()).stream().map(WorkflowCcRecipient::getUserId)
                .filter(Objects::nonNull).forEach(recipients::add);
        recipients.remove(actorId);
        String summary = "已归档到资料 " + documentId + " / 版本 " + versionId;
        for (Long recipientId : recipients) {
            notificationService.notify(recipientId, application.getProjectId(), BUSINESS_CODE,
                    application.getId(), "SEAL_ARCHIVED", "用印盖章件已归档", summary,
                    "seal:archive:" + application.getId() + ":" + fileRelationId + ":" + recipientId);
        }
    }

    private SealApplicationVO toVO(SealApplication application, SysUser user, boolean includeDetails) {
        SealApplicationVO vo = new SealApplicationVO();
        vo.setId(application.getId());
        vo.setApplicationNo(application.getApplicationNo());
        vo.setRequestKey(application.getRequestKey());
        vo.setSourceApplicationId(application.getSourceApplicationId());
        vo.setProjectId(application.getProjectId());
        vo.setProjectName(application.getDepartmentName());
        vo.setCompanyName(application.getCompanyName());
        vo.setDepartmentName(application.getDepartmentName());
        vo.setSealId(application.getSealId());
        vo.setSealName(application.getSealName());
        vo.setPurpose(application.getPurpose());
        vo.setStatus(application.getStatus());
        vo.setStatusLabel(statusLabel(application.getStatus()));
        vo.setApplicantId(application.getApplicantId());
        vo.setApplicantName(application.getApplicantName());
        vo.setApplicantDepartmentName(application.getDepartmentName());
        vo.setApplicantPhone(application.getApplicantPhone());
        vo.setApplicationDate(application.getApplicationDate());
        vo.setSubmitTime(application.getSubmitTime());
        vo.setApproverId(application.getApproverId());
        vo.setApproverName(application.getApproverName());
        vo.setApprovalOpinion(application.getApprovalOpinion());
        vo.setApprovalTime(application.getApprovalTime());
        vo.setCreateTime(application.getCreateTime());
        vo.setUpdateTime(application.getUpdateTime());
        List<SealApplicationItem> applicationItems = itemMapper.selectList(
                new LambdaQueryWrapper<SealApplicationItem>()
                        .eq(SealApplicationItem::getApplicationId, application.getId())
                        .orderByAsc(SealApplicationItem::getSortOrder));
        vo.setItemCount(applicationItems.size());
        vo.setTotalCopies(applicationItems.stream().map(SealApplicationItem::getCopies)
                .filter(Objects::nonNull).mapToInt(Integer::intValue).sum());
        boolean owner = Objects.equals(application.getApplicantId(), user.getId());
        boolean pendingAssignee = pendingTask(application, user.getId()) != null;
        boolean manager = canManage(user, application.getProjectId());
        vo.setCanEdit(owner && DRAFT.equals(application.getStatus()));
        vo.setCanSubmit(owner && DRAFT.equals(application.getStatus()));
        vo.setCanApprove(pendingAssignee && PENDING_APPROVAL.equals(application.getStatus()));
        vo.setCanReject(pendingAssignee && PENDING_APPROVAL.equals(application.getStatus()));
        vo.setCanTransfer(PENDING_APPROVAL.equals(application.getStatus())
                && permissionService.hasSystemPermission(user.getId(), application.getProjectId(),
                SystemPermissionCodes.APPROVAL_MANAGE));
        vo.setCanCancel(owner && PENDING_APPROVAL.equals(application.getStatus()));
        vo.setCanUploadStampedResult(APPROVED.equals(application.getStatus()) && (owner || manager));
        boolean documentUpload = permissionService.hasSystemPermission(user.getId(), application.getProjectId(),
                SystemPermissionCodes.DOCUMENT_UPLOAD);
        boolean documentManager = permissionService.hasSystemPermission(user.getId(), application.getProjectId(),
                SystemPermissionCodes.SEAL_VIEW) || manager;
        vo.setCanArchive(APPROVED.equals(application.getStatus()) && documentUpload
                && (owner || (!isCcOnly(application, user.getId()) && documentManager)));
        if (includeDetails) {
            vo.setItems(applicationItems.stream().map(this::toItemVO).toList());
            vo.setFiles(applicationFileMapper.selectList(new LambdaQueryWrapper<SealApplicationFile>()
                            .eq(SealApplicationFile::getApplicationId, application.getId())
                            .orderByAsc(SealApplicationFile::getCreateTime))
                    .stream().map(file -> toFileVO(file, application, user)).toList());
            vo.setCcRecipients(ccRecipients(application.getId()).stream().map(this::toCcVO).toList());
            vo.setLogs(logMapper.selectList(new LambdaQueryWrapper<SealApplicationLog>()
                            .eq(SealApplicationLog::getApplicationId, application.getId())
                            .orderByAsc(SealApplicationLog::getCreateTime).orderByAsc(SealApplicationLog::getId))
                    .stream().map(this::toLogVO).toList());
        }
        return vo;
    }

    private SealApplicationItemVO toItemVO(SealApplicationItem item) {
        SealApplicationItemVO vo = new SealApplicationItemVO();
        vo.setId(item.getId());
        vo.setDocumentName(item.getDocumentName());
        vo.setCopies(item.getCopies());
        vo.setSortOrder(item.getSortOrder());
        return vo;
    }

    private SealApplicationFileVO toFileVO(SealApplicationFile relation, SealApplication application, SysUser user) {
        FileResource file = fileMapper.selectById(relation.getFileResourceId());
        SealApplicationFileVO vo = new SealApplicationFileVO();
        vo.setId(relation.getId());
        vo.setFileRole(relation.getFileRole());
        vo.setItemId(relation.getItemId());
        if (file != null) {
            vo.setFileName(StringUtils.hasText(file.getOriginalFileName()) ? file.getOriginalFileName() : file.getFileName());
            vo.setOriginalFileName(file.getOriginalFileName());
            vo.setFileSize(file.getFileSize());
            vo.setMimeType(file.getMimeType());
            vo.setFileExtension(file.getFileExtension());
        }
        vo.setUploaderId(relation.getUploaderId());
        vo.setUploaderName(relation.getUploaderName());
        vo.setArchivedDocumentId(relation.getArchivedDocumentId());
        vo.setArchivedVersionId(relation.getArchivedVersionId());
        vo.setCreateTime(relation.getCreateTime());
        vo.setCanPreview(true);
        boolean unarchived = relation.getArchivedDocumentId() == null;
        boolean canDeleteSource = DRAFT.equals(application.getStatus())
                && application.getApplicantId().equals(user.getId()) && "SOURCE".equals(relation.getFileRole());
        boolean canDeleteStamped = APPROVED.equals(application.getStatus()) && unarchived
                && "STAMPED_RESULT".equals(relation.getFileRole())
                && (relation.getUploaderId().equals(user.getId()) || canManage(user, application.getProjectId()));
        vo.setCanDelete(unarchived && (canDeleteSource || canDeleteStamped));
        return vo;
    }

    private SealCcRecipientVO toCcVO(WorkflowCcRecipient recipient) {
        SealCcRecipientVO vo = new SealCcRecipientVO();
        vo.setUserId(recipient.getUserId());
        vo.setDisplayName(recipient.getUserName());
        vo.setReadTime(recipient.getReadTime());
        return vo;
    }

    private SealApplicationLogVO toLogVO(SealApplicationLog log) {
        SealApplicationLogVO vo = new SealApplicationLogVO();
        vo.setId(log.getId());
        vo.setAction(log.getActionCode());
        vo.setActionLabel(actionLabel(log.getActionCode()));
        vo.setOperatorId(log.getOperatorId());
        vo.setOperatorName(log.getOperatorName());
        vo.setOpinion(log.getOpinion());
        vo.setDescription(log.getDescription());
        vo.setCreateTime(log.getCreateTime());
        return vo;
    }

    private void replaceItems(SealApplication application, List<SealApplicationItemRequest> rows, LocalDateTime now) {
        List<SealApplicationItem> previous = itemMapper.selectList(new LambdaQueryWrapper<SealApplicationItem>()
                .eq(SealApplicationItem::getApplicationId, application.getId()));
        int deleted = itemMapper.delete(new LambdaQueryWrapper<SealApplicationItem>()
                .eq(SealApplicationItem::getApplicationId, application.getId()));
        if (deleted != previous.size()) throw BusinessException.of(409, "用印文件明细更新冲突，请重试");
        for (int i = 0; i < rows.size(); i++) {
            SealApplicationItemRequest source = rows.get(i);
            SealApplicationItem item = new SealApplicationItem();
            item.setApplicationId(application.getId());
            item.setProjectId(application.getProjectId());
            item.setDocumentName(required(source.getDocumentName(), 200, "用印文件名称"));
            item.setCopies(source.getCopies() == null ? 1 : source.getCopies());
            item.setSortOrder(i + 1);
            item.setCreateTime(now);
            item.setUpdateTime(now);
            requireSingleWrite(itemMapper.insert(item), "用印文件明细新增");
        }
    }

    private void replaceCc(SealApplication application, List<Long> userIds, LocalDateTime now) {
        List<WorkflowCcRecipient> previous = ccRecipients(application.getId());
        int deleted = ccMapper.delete(new LambdaQueryWrapper<WorkflowCcRecipient>()
                .eq(WorkflowCcRecipient::getBusinessCode, BUSINESS_CODE)
                .eq(WorkflowCcRecipient::getBusinessId, application.getId()));
        if (deleted != previous.size()) throw BusinessException.of(409, "抄送人更新冲突，请重试");
        for (Long userId : userIds) {
            SysUser user = requireEligibleUser(application.getProjectId(), userId);
            WorkflowCcRecipient recipient = new WorkflowCcRecipient();
            recipient.setBusinessCode(BUSINESS_CODE);
            recipient.setBusinessId(application.getId());
            recipient.setProjectId(application.getProjectId());
            recipient.setUserId(userId);
            recipient.setUserName(displayName(user));
            recipient.setSource("MANUAL");
            recipient.setCreateTime(now);
            requireSingleWrite(ccMapper.insert(recipient), "抄送人新增");
        }
    }

    private List<SealApplicationItemRequest> validateItems(List<SealApplicationItemRequest> values) {
        if (values == null || values.isEmpty()) throw new BusinessException("至少填写一项用印文件");
        if (values.size() > 20) throw new BusinessException("用印文件不能超过20项");
        for (SealApplicationItemRequest item : values) {
            required(item == null ? null : item.getDocumentName(), 200, "用印文件名称");
            int copies = item == null || item.getCopies() == null ? 1 : item.getCopies();
            if (copies < 1 || copies > 999) throw new BusinessException("每项文件份数应为1-999");
        }
        return values;
    }

    private List<Long> validateCcUsers(Long projectId, List<Long> values, Long applicantId) {
        if (values == null) return List.of();
        if (values.size() > 100) throw new BusinessException("抄送人不能超过100人");
        List<Long> result = values.stream().filter(Objects::nonNull).filter(id -> !id.equals(applicantId)).distinct().toList();
        result.forEach(id -> requireEligibleUser(projectId, id));
        return result;
    }

    private List<Long> currentCcIds(Long applicationId) {
        return ccRecipients(applicationId).stream().map(WorkflowCcRecipient::getUserId).toList();
    }

    private List<WorkflowCcRecipient> ccRecipients(Long applicationId) {
        return ccMapper.selectList(new LambdaQueryWrapper<WorkflowCcRecipient>()
                .eq(WorkflowCcRecipient::getBusinessCode, BUSINESS_CODE)
                .eq(WorkflowCcRecipient::getBusinessId, applicationId)
                .orderByAsc(WorkflowCcRecipient::getId));
    }

    private WorkflowApprovalTask pendingTask(SealApplication application, Long userId) {
        if (application.getApprovalInstanceId() == null || !PENDING_APPROVAL.equals(application.getStatus())) return null;
        return taskMapper.selectOne(new LambdaQueryWrapper<WorkflowApprovalTask>()
                .eq(WorkflowApprovalTask::getInstanceId, application.getApprovalInstanceId())
                .eq(WorkflowApprovalTask::getAssigneeUserId, userId)
                .eq(WorkflowApprovalTask::getStatus, "PENDING")
                .orderByDesc(WorkflowApprovalTask::getId).last("LIMIT 1"));
    }

    private List<Long> activeProjectIds(SysUser user) {
        if (user == null) throw BusinessException.unauthorized("请先登录");
        return permissionService.getUserProjects(user.getId()).stream().map(ProjectInfo::getId).distinct().toList();
    }

    private void requireActiveMember(SysUser user, Long projectId) {
        if (user == null) throw BusinessException.unauthorized("请先登录");
        permissionService.checkProjectPermission(user.getId(), projectId);
        if (!"ACTIVE".equals(permissionService.getProjectAccessStatus(user.getId(), projectId))) {
            throw BusinessException.forbidden("仅当前项目有效成员可操作用印申请");
        }
    }

    private SysUser requireEligibleUser(Long projectId, Long userId) {
        SysUser user = requireUser(userId);
        if (!Integer.valueOf(1).equals(user.getStatus())
                || userProjectMapper.selectCount(new LambdaQueryWrapper<SysUserProject>()
                .eq(SysUserProject::getUserId, userId)
                .eq(SysUserProject::getProjectId, projectId)
                .eq(SysUserProject::getStatus, "ACTIVE")) == 0) {
            throw new BusinessException("所选用户不是当前项目有效成员: " + userId);
        }
        return user;
    }

    private SysUser requireUser(Long id) {
        SysUser user = id == null ? null : userMapper.selectById(id);
        if (user == null) throw new BusinessException("用户不存在: " + id);
        return user;
    }

    private ProjectInfo requireProject(Long id) {
        ProjectInfo project = projectMapper.selectById(id);
        if (project == null) throw BusinessException.notFound("项目不存在");
        return project;
    }

    private void record(SealApplication application, String action, String fromStatus, String toStatus,
                        SysUser user, String opinion, String description, HttpServletRequest request) {
        SealApplicationLog log = new SealApplicationLog();
        log.setApplicationId(application.getId());
        log.setProjectId(application.getProjectId());
        log.setActionCode(action);
        log.setFromStatus(fromStatus);
        log.setToStatus(toStatus);
        log.setOperatorId(user.getId());
        log.setOperatorName(displayName(user));
        log.setOpinion(opinion);
        log.setDescription(description);
        log.setIpAddress(request == null ? null : request.getRemoteAddr());
        log.setCreateTime(LocalDateTime.now(BUSINESS_ZONE));
        requireSingleWrite(logMapper.insert(log), "用印审计日志写入");
    }

    private String generateApplicationNo(Long id, LocalDate applicationDate) {
        LocalDate date = applicationDate == null ? LocalDate.now(BUSINESS_ZONE) : applicationDate;
        return "YYSQ-" + date.format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + String.format("%08d", id);
    }

    private void requireApprovalManage(SysUser user, Long projectId) {
        requireActiveMember(user, projectId);
        permissionService.requireSystemPermission(user.getId(), projectId, SystemPermissionCodes.APPROVAL_MANAGE);
    }

    private boolean isCcOnly(SealApplication application, Long userId) {
        if (Objects.equals(application.getApplicantId(), userId)) return false;
        if (DRAFT.equals(application.getStatus())) return false;
        boolean cc = ccMapper.selectCount(new LambdaQueryWrapper<WorkflowCcRecipient>()
                .eq(WorkflowCcRecipient::getBusinessCode, BUSINESS_CODE)
                .eq(WorkflowCcRecipient::getBusinessId, application.getId())
                .eq(WorkflowCcRecipient::getUserId, userId)) > 0;
        boolean taskParticipant = taskMapper.selectCount(new LambdaQueryWrapper<WorkflowApprovalTask>()
                .eq(WorkflowApprovalTask::getBusinessCode, BUSINESS_CODE)
                .eq(WorkflowApprovalTask::getBusinessId, application.getId())
                .eq(WorkflowApprovalTask::getAssigneeUserId, userId)) > 0;
        return cc && !taskParticipant;
    }

    private String normalizeScope(String value) {
        String scope = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "INITIATED";
        return switch (scope) {
            case "MINE" -> "INITIATED";
            case "APPROVAL" -> "PENDING_FOR_ME";
            case "CC" -> "CC_TO_ME";
            default -> scope;
        };
    }

    private String normalizeStatus(String value) {
        String status = value.trim().toUpperCase(Locale.ROOT);
        if ("CANCELLED".equals(status)) status = WITHDRAWN;
        if (!STATUSES.contains(status)) throw new BusinessException("用印申请状态不正确");
        return status;
    }

    private String statusLabel(String status) {
        return switch (Objects.toString(status, "")) {
            case DRAFT -> "草稿";
            case PENDING_APPROVAL -> "审批中";
            case APPROVED -> "已通过";
            case REJECTED -> "已驳回";
            case WITHDRAWN -> "已撤回";
            default -> status;
        };
    }

    private String actionLabel(String action) {
        return switch (Objects.toString(action, "")) {
            case "CREATE" -> "创建";
            case "COPY" -> "复制";
            case "UPDATE" -> "修改";
            case "UPLOAD" -> "上传附件";
            case "DELETE_FILE" -> "删除附件";
            case "SUBMIT" -> "提交";
            case "APPROVE" -> "审批通过";
            case "REJECT" -> "审批驳回";
            case "TRANSFER" -> "转办";
            case "WITHDRAW" -> "撤回";
            case "ARCHIVE" -> "归档";
            default -> action;
        };
    }

    private String displayName(SysUser user) {
        return StringUtils.hasText(user.getRealName()) ? user.getRealName() : user.getUsername();
    }

    private String required(String value, int maxLength, String label) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) throw new BusinessException(label + "不能为空");
        if (normalized.length() > maxLength) throw new BusinessException(label + "不能超过" + maxLength + "个字符");
        return normalized;
    }

    private BusinessException stateConflict(String message) {
        return BusinessException.of(409, message + "，请刷新后重试");
    }

    private void requireSingleWrite(int affectedRows, String operation) {
        if (affectedRows != 1) throw BusinessException.of(409, operation + "未生效，请刷新后重试");
    }
}
