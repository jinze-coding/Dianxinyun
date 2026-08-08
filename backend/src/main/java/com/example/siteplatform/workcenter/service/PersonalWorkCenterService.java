package com.example.siteplatform.workcenter.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PersonalWorkCenterService {

    public static final String SCOPE_PENDING = "PENDING";
    public static final String SCOPE_CC = "CC";
    public static final String BUSINESS_SEAL = "SEAL_APPLICATION";
    public static final String BUSINESS_INSPECTION = "INSPECTION_RECORD";
    public static final String BUSINESS_QUALITY = "QUALITY_ISSUE";

    private static final String SEAL_PENDING = "PENDING_APPROVAL";
    private static final String TASK_PENDING = "PENDING";
    private static final String TASK_SEAL_APPROVAL = "SEAL_APPROVAL";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Set<String> ROUTE_CODES = Set.of(
            "SEAL_APPLICATION_DETAIL",
            "QUALITY_ISSUE_DETAIL",
            "INSPECTION_FORM",
            "INSPECTION_RECORD_DETAIL",
            "INSPECTION_RECTIFICATION_DETAIL"
    );
    private static final Comparator<PersonalTodoVO> TODO_ORDER =
            Comparator.comparingInt((PersonalTodoVO todo) -> priorityRank(todo.getPriority()))
                    .thenComparing(PersonalTodoVO::getCreatedAt,
                            Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(PersonalTodoVO::getTodoKey,
                            Comparator.nullsLast(Comparator.naturalOrder()));

    private final InspectionService inspectionService;
    private final QualityIssueService qualityIssueService;
    private final WorkflowApprovalTaskMapper approvalTaskMapper;
    private final WorkflowCcRecipientMapper ccRecipientMapper;
    private final SealApplicationMapper sealApplicationMapper;
    private final UserNotificationMapper notificationMapper;
    private final ProjectPermissionService projectPermissionService;
    private final ProjectInfoMapper projectInfoMapper;
    private final SysUserProjectMapper userProjectMapper;
    private final ObjectMapper objectMapper;

    public PersonalWorkCenterService(InspectionService inspectionService,
                                     QualityIssueService qualityIssueService,
                                     WorkflowApprovalTaskMapper approvalTaskMapper,
                                     WorkflowCcRecipientMapper ccRecipientMapper,
                                     SealApplicationMapper sealApplicationMapper,
                                     UserNotificationMapper notificationMapper,
                                     ProjectPermissionService projectPermissionService,
                                     ProjectInfoMapper projectInfoMapper,
                                     SysUserProjectMapper userProjectMapper,
                                     ObjectMapper objectMapper) {
        this.inspectionService = inspectionService;
        this.qualityIssueService = qualityIssueService;
        this.approvalTaskMapper = approvalTaskMapper;
        this.ccRecipientMapper = ccRecipientMapper;
        this.sealApplicationMapper = sealApplicationMapper;
        this.notificationMapper = notificationMapper;
        this.projectPermissionService = projectPermissionService;
        this.projectInfoMapper = projectInfoMapper;
        this.userProjectMapper = userProjectMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public PageResult<PersonalTodoVO> listTodos(String scope, Long projectId, String type,
                                                Integer pageNo, Integer pageSize, SysUser currentUser) {
        SysUser user = requireEnabledUser(currentUser);
        ProjectScope projectScope = resolveProjectScope(user, projectId);
        String normalizedScope = normalizeScope(scope);
        List<PersonalTodoVO> todos = SCOPE_CC.equals(normalizedScope)
                ? sealCcTodos(projectScope, user)
                : pendingTodos(projectScope, user);
        List<PersonalTodoVO> filtered = filterTodoType(todos, type);
        filtered.sort(TODO_ORDER);
        return page(filtered, pageNo, pageSize);
    }

    @Transactional(readOnly = true)
    public WorkSummaryVO workSummary(Long projectId, SysUser currentUser) {
        SysUser user = requireEnabledUser(currentUser);
        ProjectScope projectScope = resolveProjectScope(user, projectId);
        List<PersonalTodoVO> pending = pendingTodos(projectScope, user);
        List<PersonalTodoVO> cc = sealCcTodos(projectScope, user);
        long unread = countUnreadNotifications(projectScope, user.getId());

        Map<String, Long> byBusinessType = countBy(pending, PersonalTodoVO::getBusinessType);
        Map<String, Long> byTaskType = countBy(pending, PersonalTodoVO::getTaskType);
        long pendingCount = pending.size();
        long ccCount = cc.size();

        WorkSummaryVO summary = new WorkSummaryVO();
        summary.setPendingCount(pendingCount);
        summary.setCcCount(ccCount);
        summary.setUnreadNotificationCount(unread);
        summary.setBadgeCount(pendingCount + unread);
        summary.setTotal(pendingCount);
        summary.setTodoCount(pendingCount);
        summary.setByBusinessType(byBusinessType);
        summary.setByTaskType(byTaskType);
        return summary;
    }

    @Transactional(readOnly = true)
    public PageResult<InboxNotificationVO> inbox(String readStatus, String businessType, Long projectId,
                                                 Integer pageNo, Integer pageSize, SysUser currentUser) {
        SysUser user = requireEnabledUser(currentUser);
        ProjectScope scope = resolveProjectScope(user, projectId);
        int pageNoValue = normalizePageNo(pageNo);
        int pageSizeValue = normalizePageSize(pageSize);
        LambdaQueryWrapper<UserNotification> query = notificationScopeQuery(scope, user.getId());
        applyReadStatus(query, readStatus);
        if (StringUtils.hasText(businessType)) {
            String normalizedBusinessType = businessType.trim().toUpperCase(Locale.ROOT);
            if (normalizedBusinessType.length() > 50) {
                throw new BusinessException("业务类型长度不能超过50个字符");
            }
            query.eq(UserNotification::getBusinessType, normalizedBusinessType);
        }
        query.orderByDesc(UserNotification::getCreateTime).orderByDesc(UserNotification::getId);
        Page<UserNotification> result = notificationMapper.selectPage(
                new Page<>(pageNoValue, pageSizeValue), query);
        if (result == null) {
            return PageResult.of(pageNoValue, pageSizeValue, 0L, List.of());
        }
        List<InboxNotificationVO> records = safeList(result.getRecords()).stream()
                .map(notification -> toNotificationVO(notification, scope.projectNames()))
                .toList();
        return PageResult.of(pageNoValue, pageSizeValue, result.getTotal(), records);
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long projectId, SysUser currentUser) {
        SysUser user = requireEnabledUser(currentUser);
        return countUnreadNotifications(resolveProjectScope(user, projectId), user.getId());
    }

    @Transactional
    public InboxNotificationVO markRead(Long notificationId, SysUser currentUser) {
        SysUser user = requireEnabledUser(currentUser);
        if (notificationId == null || notificationId <= 0) {
            throw new BusinessException("通知ID不正确");
        }
        UserNotification notification = findUserNotification(notificationId, user.getId());
        if (notification == null) {
            throw BusinessException.notFound("通知不存在");
        }
        Map<Long, String> projectNames = Map.of();
        if (notification.getProjectId() != null) {
            projectNames = resolveProjectScope(user, notification.getProjectId()).projectNames();
        }
        if (Integer.valueOf(1).equals(notification.getIsRead())) {
            return toNotificationVO(notification, projectNames);
        }

        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        int affected = notificationMapper.markRead(notificationId, user.getId(), now);
        if (affected == 1) {
            notification.setIsRead(1);
            notification.setReadTime(now);
            notification.setUpdateTime(now);
            return toNotificationVO(notification, projectNames);
        }
        if (affected == 0) {
            UserNotification current = findUserNotification(notificationId, user.getId());
            if (current != null && Integer.valueOf(1).equals(current.getIsRead())) {
                return toNotificationVO(current, projectNames);
            }
        }
        throw BusinessException.of(409, "通知状态已变化，请刷新后重试");
    }

    @Transactional
    public void markAllRead(Long projectId, SysUser currentUser) {
        SysUser user = requireEnabledUser(currentUser);
        ProjectScope scope = resolveProjectScope(user, projectId);
        long expected = countUnreadNotifications(scope, user.getId());
        if (expected == 0) {
            return;
        }
        List<Long> projectIds = new ArrayList<>(scope.projectIds());
        boolean includeGlobal = scope.requestedProjectId() == null;
        int affected = notificationMapper.markAllReadInScope(
                user.getId(), projectIds, includeGlobal, LocalDateTime.now(BUSINESS_ZONE));
        if (affected < 0 || affected > expected) {
            throw BusinessException.of(409, "通知批量已读结果异常，请刷新后重试");
        }
        if (affected != expected && countUnreadNotifications(scope, user.getId()) != 0) {
            throw BusinessException.of(409, "部分通知状态已变化，请刷新后重试");
        }
    }

    private List<PersonalTodoVO> pendingTodos(ProjectScope scope, SysUser user) {
        List<PersonalTodoVO> todos = new ArrayList<>();
        for (InspectionTodoVO source : safeList(
                inspectionService.listTodos(scope.requestedProjectId(), user))) {
            PersonalTodoVO todo = toInspectionTodo(source);
            if (todo != null && scope.projectIds().contains(todo.getProjectId())) {
                todos.add(todo);
            }
        }

        boolean loadQuality = scope.requestedProjectId() == null
                || projectPermissionService.hasSystemPermission(
                user.getId(), scope.requestedProjectId(), SystemPermissionCodes.QUALITY_VIEW);
        if (loadQuality) {
            for (QualityTodoVO source : safeList(
                    qualityIssueService.listTodos(scope.requestedProjectId(), user))) {
                PersonalTodoVO todo = toQualityTodo(source);
                if (todo != null && scope.projectIds().contains(todo.getProjectId())) {
                    todos.add(todo);
                }
            }
        }
        todos.addAll(sealApprovalTodos(scope, user));
        return todos;
    }

    private List<PersonalTodoVO> sealApprovalTodos(ProjectScope scope, SysUser user) {
        Set<Long> memberProjectIds = activeMemberProjectIds(scope, user.getId());
        if (memberProjectIds.isEmpty()) {
            return List.of();
        }
        List<WorkflowApprovalTask> tasks = safeList(approvalTaskMapper.selectList(
                new LambdaQueryWrapper<WorkflowApprovalTask>()
                        .eq(WorkflowApprovalTask::getBusinessCode, BUSINESS_SEAL)
                        .eq(WorkflowApprovalTask::getAssigneeUserId, user.getId())
                        .eq(WorkflowApprovalTask::getStatus, TASK_PENDING)
                        .in(WorkflowApprovalTask::getProjectId, memberProjectIds)
                        .orderByDesc(WorkflowApprovalTask::getCreateTime)
                        .orderByDesc(WorkflowApprovalTask::getId)));
        Map<Long, SealApplication> applications = loadApplications(tasks.stream()
                .map(WorkflowApprovalTask::getBusinessId).filter(Objects::nonNull).toList());
        List<PersonalTodoVO> result = new ArrayList<>();
        for (WorkflowApprovalTask task : tasks) {
            SealApplication application = applications.get(task.getBusinessId());
            if (!BUSINESS_SEAL.equals(task.getBusinessCode())
                    || !TASK_PENDING.equals(task.getStatus())
                    || !Objects.equals(user.getId(), task.getAssigneeUserId())
                    || application == null
                    || !SEAL_PENDING.equals(application.getStatus())
                    || !Objects.equals(application.getProjectId(), task.getProjectId())
                    || !Objects.equals(application.getApprovalInstanceId(), task.getInstanceId())
                    || !memberProjectIds.contains(application.getProjectId())) {
                continue;
            }
            PersonalTodoVO todo = sealTodo(application, scope, task.getCreateTime());
            todo.setId(task.getId());
            todo.setTaskId(task.getId());
            todo.setTodoKey("SEAL_APPROVAL:TASK:" + task.getId());
            todo.setScope(SCOPE_PENDING);
            todo.setReadOnly(false);
            todo.setTitle(sealTitle(application, "待审批"));
            todo.setDueText("请及时审批");
            todo.setPriority("warning");
            result.add(todo);
        }
        return result;
    }

    private List<PersonalTodoVO> sealCcTodos(ProjectScope scope, SysUser user) {
        Set<Long> memberProjectIds = activeMemberProjectIds(scope, user.getId());
        if (memberProjectIds.isEmpty()) {
            return List.of();
        }
        List<WorkflowCcRecipient> recipients = safeList(ccRecipientMapper.selectList(
                new LambdaQueryWrapper<WorkflowCcRecipient>()
                        .eq(WorkflowCcRecipient::getBusinessCode, BUSINESS_SEAL)
                        .eq(WorkflowCcRecipient::getUserId, user.getId())
                        .in(WorkflowCcRecipient::getProjectId, memberProjectIds)
                        .orderByDesc(WorkflowCcRecipient::getCreateTime)
                        .orderByDesc(WorkflowCcRecipient::getId)));
        Map<Long, SealApplication> applications = loadApplications(recipients.stream()
                .map(WorkflowCcRecipient::getBusinessId).filter(Objects::nonNull).toList());
        List<PersonalTodoVO> result = new ArrayList<>();
        for (WorkflowCcRecipient recipient : recipients) {
            SealApplication application = applications.get(recipient.getBusinessId());
            if (!BUSINESS_SEAL.equals(recipient.getBusinessCode())
                    || !Objects.equals(user.getId(), recipient.getUserId())
                    || application == null
                    || "DRAFT".equals(application.getStatus())
                    || !Objects.equals(application.getProjectId(), recipient.getProjectId())
                    || !memberProjectIds.contains(application.getProjectId())) {
                continue;
            }
            PersonalTodoVO todo = sealTodo(application, scope, recipient.getCreateTime());
            todo.setId(recipient.getId());
            todo.setTodoKey("SEAL_APPLICATION:CC:" + recipient.getId());
            todo.setScope(SCOPE_CC);
            todo.setReadOnly(true);
            todo.setTitle(sealTitle(application, "抄送"));
            todo.setDueText(sealStatusText(application.getStatus()));
            todo.setPriority("normal");
            result.add(todo);
        }
        return result;
    }

    private PersonalTodoVO sealTodo(SealApplication application, ProjectScope scope,
                                    LocalDateTime createdAt) {
        PersonalTodoVO todo = new PersonalTodoVO();
        todo.setBusinessType(BUSINESS_SEAL);
        todo.setTaskType(TASK_SEAL_APPROVAL);
        todo.setType(TASK_SEAL_APPROVAL);
        todo.setTargetId(application.getId());
        todo.setProjectId(application.getProjectId());
        todo.setProjectName(scope.projectNames().get(application.getProjectId()));
        todo.setSummary(application.getPurpose());
        todo.setApplicantName(application.getApplicantName());
        todo.setCreatedAt(createdAt);
        todo.setRouteCode("SEAL_APPLICATION_DETAIL");
        todo.setRouteParams(Map.of("applicationId", application.getId()));
        return todo;
    }

    private PersonalTodoVO toInspectionTodo(InspectionTodoVO source) {
        if (source == null || source.getProjectId() == null || source.getTargetId() == null) {
            return null;
        }
        String taskType = normalizeText(source.getType());
        String routeCode;
        Map<String, Object> routeParams;
        switch (taskType) {
            case "INSPECTION" -> {
                routeCode = "INSPECTION_FORM";
                routeParams = Map.of("boxId", source.getTargetId());
            }
            case "REVIEW" -> {
                routeCode = "INSPECTION_RECORD_DETAIL";
                routeParams = Map.of("recordId", source.getTargetId());
            }
            case "RECTIFICATION", "RECHECK" -> {
                routeCode = "INSPECTION_RECTIFICATION_DETAIL";
                routeParams = Map.of("rectificationId", source.getTargetId());
            }
            default -> {
                return null;
            }
        }
        PersonalTodoVO todo = new PersonalTodoVO();
        todo.setId(source.getTargetId());
        todo.setTodoKey("INSPECTION:" + source.getProjectId() + ":" + taskType + ":" + source.getTargetId());
        todo.setBusinessType(BUSINESS_INSPECTION);
        todo.setTaskType(taskType);
        todo.setType(taskType);
        todo.setTargetId(source.getTargetId());
        todo.setProjectId(source.getProjectId());
        todo.setProjectName(source.getProjectName());
        todo.setTitle(source.getTitle());
        todo.setSummary(joinSummary(source.getBoxCode(), source.getInstallLocation()));
        todo.setDueAt(source.getReviewDueTime());
        todo.setDueText(source.getDueText());
        todo.setPriority(normalizePriority(source.getPriority()));
        todo.setRouteCode(routeCode);
        todo.setRouteParams(routeParams);
        todo.setScope(SCOPE_PENDING);
        todo.setReadOnly(false);
        todo.setBoxCode(source.getBoxCode());
        todo.setInstallLocation(source.getInstallLocation());
        todo.setReviewDueTime(source.getReviewDueTime());
        todo.setAssignedReviewerId(source.getAssignedReviewerId());
        todo.setAssignedReviewerName(source.getAssignedReviewerName());
        todo.setReviewOverdue(source.getReviewOverdue());
        return todo;
    }

    private PersonalTodoVO toQualityTodo(QualityTodoVO source) {
        if (source == null || source.getProjectId() == null || source.getTargetId() == null) {
            return null;
        }
        String taskType = normalizeText(source.getType());
        if (!Set.of("RECTIFICATION", "RECHECK").contains(taskType)) {
            return null;
        }
        PersonalTodoVO todo = new PersonalTodoVO();
        todo.setId(source.getTargetId());
        todo.setTodoKey("QUALITY_ISSUE:" + source.getProjectId() + ":" + taskType + ":" + source.getTargetId());
        todo.setBusinessType(BUSINESS_QUALITY);
        todo.setTaskType(taskType);
        todo.setType(taskType);
        todo.setTargetId(source.getTargetId());
        todo.setProjectId(source.getProjectId());
        todo.setProjectName(source.getProjectName());
        todo.setTitle(source.getTitle());
        todo.setSummary(joinSummary(source.getBoxCode(), source.getInstallLocation()));
        todo.setDueText(source.getDueText());
        todo.setPriority(normalizePriority(source.getPriority()));
        todo.setRouteCode("QUALITY_ISSUE_DETAIL");
        todo.setRouteParams(Map.of("issueId", source.getTargetId()));
        todo.setScope(SCOPE_PENDING);
        todo.setReadOnly(false);
        todo.setBoxCode(source.getBoxCode());
        todo.setInstallLocation(source.getInstallLocation());
        todo.setReviewOverdue(source.getReviewOverdue());
        return todo;
    }

    private List<PersonalTodoVO> filterTodoType(List<PersonalTodoVO> todos, String type) {
        if (!StringUtils.hasText(type) || "ALL".equalsIgnoreCase(type.trim())) {
            return new ArrayList<>(todos);
        }
        String normalized = type.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "SEAL", "SEAL_APPLICATION" -> todos.stream()
                    .filter(todo -> BUSINESS_SEAL.equals(todo.getBusinessType()))
                    .collect(Collectors.toCollection(ArrayList::new));
            case "QUALITY", "QUALITY_ISSUE" -> todos.stream()
                    .filter(todo -> BUSINESS_QUALITY.equals(todo.getBusinessType()))
                    .collect(Collectors.toCollection(ArrayList::new));
            case "INSPECTION_RECORD" -> todos.stream()
                    .filter(todo -> BUSINESS_INSPECTION.equals(todo.getBusinessType()))
                    .collect(Collectors.toCollection(ArrayList::new));
            case "INSPECTION", "REVIEW", "RECTIFICATION", "RECHECK", TASK_SEAL_APPROVAL -> todos.stream()
                    .filter(todo -> normalized.equals(todo.getTaskType()))
                    .collect(Collectors.toCollection(ArrayList::new));
            default -> throw new BusinessException("不支持的待办类型: " + normalized);
        };
    }

    private Map<Long, SealApplication> loadApplications(Collection<Long> applicationIds) {
        Set<Long> ids = applicationIds == null ? Set.of() : applicationIds.stream()
                .filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        if (ids.isEmpty()) {
            return Map.of();
        }
        return safeList(sealApplicationMapper.selectList(
                new LambdaQueryWrapper<SealApplication>()
                        .in(SealApplication::getId, ids)
                        .eq(SealApplication::getDeleted, 0))).stream()
                .collect(Collectors.toMap(SealApplication::getId, Function.identity(),
                        (left, right) -> left, LinkedHashMap::new));
    }

    private Set<Long> activeMemberProjectIds(ProjectScope scope, Long userId) {
        return queryActiveMembershipProjectIds(scope.projectIds(), userId);
    }

    private Set<Long> queryActiveMembershipProjectIds(Collection<Long> candidateProjectIds, Long userId) {
        if (candidateProjectIds == null || candidateProjectIds.isEmpty()) {
            return Set.of();
        }
        return safeList(userProjectMapper.selectList(new LambdaQueryWrapper<SysUserProject>()
                .eq(SysUserProject::getUserId, userId)
                .eq(SysUserProject::getStatus, "ACTIVE")
                .in(SysUserProject::getProjectId, candidateProjectIds))).stream()
                .map(SysUserProject::getProjectId)
                .filter(candidateProjectIds::contains)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private ProjectScope resolveProjectScope(SysUser user, Long requestedProjectId) {
        if (requestedProjectId != null) {
            if (requestedProjectId <= 0) {
                throw new BusinessException("项目ID不正确");
            }
            projectPermissionService.checkProjectPermission(user.getId(), requestedProjectId);
            if (!projectPermissionService.isPlatformAdmin(user.getId())
                    && !queryActiveMembershipProjectIds(Set.of(requestedProjectId), user.getId())
                    .contains(requestedProjectId)) {
                throw BusinessException.forbidden("当前项目成员关系已失效");
            }
            ProjectInfo project = projectInfoMapper.selectById(requestedProjectId);
            if (project == null || Integer.valueOf(1).equals(project.getDeleted())) {
                throw BusinessException.notFound("项目不存在");
            }
            return projectScope(List.of(project), requestedProjectId);
        }
        List<ProjectInfo> projects = safeList(projectPermissionService.getUserProjects(user.getId()));
        if (!projectPermissionService.isPlatformAdmin(user.getId())) {
            Set<Long> candidateProjectIds = projects.stream()
                    .filter(Objects::nonNull)
                    .map(ProjectInfo::getId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            Set<Long> activeProjectIds = queryActiveMembershipProjectIds(candidateProjectIds, user.getId());
            projects = projects.stream()
                    .filter(project -> project != null && activeProjectIds.contains(project.getId()))
                    .toList();
        }
        return projectScope(projects, null);
    }

    private ProjectScope projectScope(List<ProjectInfo> projects, Long requestedProjectId) {
        Set<Long> projectIds = new LinkedHashSet<>();
        Map<Long, String> projectNames = new LinkedHashMap<>();
        for (ProjectInfo project : safeList(projects)) {
            if (project == null || project.getId() == null || Integer.valueOf(1).equals(project.getDeleted())) {
                continue;
            }
            projectIds.add(project.getId());
            projectNames.put(project.getId(), projectDisplayName(project));
        }
        return new ProjectScope(projectIds, projectNames, requestedProjectId);
    }

    private LambdaQueryWrapper<UserNotification> notificationScopeQuery(ProjectScope scope, Long userId) {
        LambdaQueryWrapper<UserNotification> query = new LambdaQueryWrapper<UserNotification>()
                .eq(UserNotification::getUserId, userId);
        if (scope.requestedProjectId() != null) {
            query.eq(UserNotification::getProjectId, scope.requestedProjectId());
        } else if (scope.projectIds().isEmpty()) {
            query.isNull(UserNotification::getProjectId);
        } else {
            query.and(row -> row.isNull(UserNotification::getProjectId)
                    .or().in(UserNotification::getProjectId, scope.projectIds()));
        }
        return query;
    }

    private long countUnreadNotifications(ProjectScope scope, Long userId) {
        Long count = notificationMapper.selectCount(notificationScopeQuery(scope, userId)
                .eq(UserNotification::getIsRead, 0));
        return count == null ? 0L : count;
    }

    private void applyReadStatus(LambdaQueryWrapper<UserNotification> query, String readStatus) {
        String normalized = StringUtils.hasText(readStatus)
                ? readStatus.trim().toUpperCase(Locale.ROOT) : "ALL";
        switch (normalized) {
            case "ALL" -> { }
            case "UNREAD" -> query.eq(UserNotification::getIsRead, 0);
            case "READ" -> query.eq(UserNotification::getIsRead, 1);
            default -> throw new BusinessException("不支持的通知读取状态: " + normalized);
        }
    }

    private UserNotification findUserNotification(Long notificationId, Long userId) {
        return notificationMapper.selectOne(new LambdaQueryWrapper<UserNotification>()
                .eq(UserNotification::getId, notificationId)
                .eq(UserNotification::getUserId, userId)
                .last("LIMIT 1"));
    }

    private InboxNotificationVO toNotificationVO(UserNotification notification,
                                                  Map<Long, String> projectNames) {
        InboxNotificationVO vo = new InboxNotificationVO();
        vo.setId(notification.getId());
        vo.setNotificationId(notification.getId());
        vo.setProjectId(notification.getProjectId());
        vo.setProjectName(notification.getProjectId() == null
                ? null : projectNames.get(notification.getProjectId()));
        vo.setBusinessType(notification.getBusinessType());
        vo.setBusinessId(notification.getBusinessId());
        vo.setTargetId(notification.getBusinessId());
        vo.setEventCode(notification.getEventCode());
        vo.setTitle(notification.getTitle());
        vo.setSummary(notification.getSummary());
        boolean read = Integer.valueOf(1).equals(notification.getIsRead());
        vo.setIsRead(read);
        vo.setRead(read);
        vo.setReadStatus(read ? "READ" : "UNREAD");
        vo.setReadTime(notification.getReadTime());
        vo.setCreateTime(notification.getCreateTime());
        vo.setCreatedAt(notification.getCreateTime());
        String routeCode = normalizeRouteCode(notification.getRouteCode());
        vo.setRouteCode(routeCode);
        vo.setRouteParams(routeCode == null ? Map.of() : parseRouteParams(notification.getRouteParamsJson()));
        return vo;
    }

    private Map<String, Object> parseRouteParams(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || !root.isObject()) {
                return Map.of();
            }
            Map<String, Object> values = new LinkedHashMap<>();
            root.fields().forEachRemaining(entry -> {
                JsonNode value = entry.getValue();
                if (value.isTextual()) values.put(entry.getKey(), value.textValue());
                else if (value.isIntegralNumber()) values.put(entry.getKey(), value.longValue());
                else if (value.isFloatingPointNumber()) values.put(entry.getKey(), value.doubleValue());
                else if (value.isBoolean()) values.put(entry.getKey(), value.booleanValue());
            });
            return values;
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private String normalizeRouteCode(String routeCode) {
        if (!StringUtils.hasText(routeCode)) {
            return null;
        }
        String normalized = routeCode.trim().toUpperCase(Locale.ROOT);
        return ROUTE_CODES.contains(normalized) ? normalized : null;
    }

    private SysUser requireEnabledUser(SysUser currentUser) {
        if (currentUser == null || currentUser.getId() == null) {
            throw BusinessException.unauthorized("请先登录");
        }
        if (!Integer.valueOf(1).equals(currentUser.getStatus())
                || Integer.valueOf(1).equals(currentUser.getDeleted())) {
            throw BusinessException.forbidden("当前账号已停用");
        }
        return currentUser;
    }

    private String normalizeScope(String scope) {
        String normalized = StringUtils.hasText(scope)
                ? scope.trim().toUpperCase(Locale.ROOT) : SCOPE_PENDING;
        if (!Set.of(SCOPE_PENDING, SCOPE_CC).contains(normalized)) {
            throw new BusinessException("待办范围只支持 PENDING 或 CC");
        }
        return normalized;
    }

    private PageResult<PersonalTodoVO> page(List<PersonalTodoVO> all, Integer pageNo, Integer pageSize) {
        int pageNoValue = normalizePageNo(pageNo);
        int pageSizeValue = normalizePageSize(pageSize);
        long fromValue = (long) (pageNoValue - 1) * pageSizeValue;
        if (fromValue >= all.size()) {
            return PageResult.of(pageNoValue, pageSizeValue, (long) all.size(), List.of());
        }
        int from = (int) fromValue;
        int to = Math.min(all.size(), from + pageSizeValue);
        return PageResult.of(pageNoValue, pageSizeValue, (long) all.size(),
                new ArrayList<>(all.subList(from, to)));
    }

    private int normalizePageNo(Integer pageNo) {
        return pageNo == null ? 1 : Math.max(1, pageNo);
    }

    private int normalizePageSize(Integer pageSize) {
        return pageSize == null ? 20 : Math.max(1, Math.min(100, pageSize));
    }

    private Map<String, Long> countBy(List<PersonalTodoVO> todos,
                                      Function<PersonalTodoVO, String> classifier) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (PersonalTodoVO todo : todos) {
            String key = classifier.apply(todo);
            if (StringUtils.hasText(key)) {
                counts.merge(key, 1L, Long::sum);
            }
        }
        return counts;
    }

    private String sealTitle(SealApplication application, String suffix) {
        String marker = StringUtils.hasText(application.getApplicationNo())
                ? application.getApplicationNo() : application.getSealName();
        return (StringUtils.hasText(marker) ? marker : "用印申请") + " " + suffix;
    }

    private String sealStatusText(String status) {
        return switch (Objects.toString(status, "")) {
            case "PENDING_APPROVAL" -> "审批中";
            case "APPROVED" -> "已批准";
            case "REJECTED" -> "已驳回";
            case "WITHDRAWN" -> "已撤回";
            default -> "查看详情";
        };
    }

    private String projectDisplayName(ProjectInfo project) {
        return StringUtils.hasText(project.getShortName())
                ? project.getShortName().trim()
                : Objects.toString(project.getProjectName(), "");
    }

    private String joinSummary(String marker, String location) {
        List<String> values = new ArrayList<>();
        if (StringUtils.hasText(marker)) values.add(marker.trim());
        if (StringUtils.hasText(location)) values.add(location.trim());
        return String.join(" · ", values);
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
    }

    private String normalizePriority(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "normal";
        return Set.of("normal", "warning", "danger").contains(normalized) ? normalized : "normal";
    }

    private static int priorityRank(String priority) {
        return switch (Objects.toString(priority, "normal")) {
            case "danger" -> 0;
            case "warning" -> 1;
            default -> 2;
        };
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private record ProjectScope(Set<Long> projectIds,
                                Map<Long, String> projectNames,
                                Long requestedProjectId) {
    }
}
