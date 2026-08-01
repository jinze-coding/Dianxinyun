package com.example.siteplatform.quality.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.common.PageResult;
import com.example.siteplatform.file.entity.FileResource;
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
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class QualityIssueService {
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RECHECK = "RECHECK";
    public static final String STATUS_CLOSED = "CLOSED";
    public static final String STATUS_VOIDED = "VOIDED";
    private static final String BUSINESS_TYPE_QUALITY_ISSUE = "QUALITY_ISSUE";
    private static final String QUALITY_ORDER_SQL = """
            ORDER BY
              CASE WHEN status IN ('CLOSED', 'VOIDED') THEN 1 ELSE 0 END ASC,
              CASE WHEN status IN ('PENDING', 'RECHECK') AND deadline < CURRENT_DATE THEN 0 ELSE 1 END ASC,
              CASE severity
                WHEN 'DANGER' THEN 0
                WHEN 'WARNING' THEN 1
                WHEN 'NORMAL' THEN 2
                ELSE 3
              END ASC,
              CASE WHEN deadline IS NULL THEN 1 ELSE 0 END ASC,
              deadline ASC,
              create_time DESC,
              id DESC
            """;

    @Autowired
    private QualityIssueMapper issueMapper;

    @Autowired
    private QualityIssueLogMapper logMapper;

    @Autowired
    private ProjectPermissionService projectPermissionService;

    @Autowired
    private QualityAssigneeService qualityAssigneeService;

    @Autowired
    private FileResourceMapper fileMapper;

    @Autowired
    private FileResourceService fileResourceService;

    @Autowired
    private ProjectInfoMapper projectInfoMapper;

    @Autowired
    private WechatNotificationService wechatNotificationService;

    public List<QualityIssueVO> listIssues(Long projectId, String status, String keyword, SysUser currentUser) {
        requireProject(projectId, currentUser);
        return issueMapper.selectList(buildIssueQuery(projectId, status, keyword)).stream()
                .map(issue -> toVO(issue, currentUser, false))
                .toList();
    }

    public PageResult<QualityIssueVO> pageIssues(Long projectId, String status, String keyword,
                                                  Integer pageNo, Integer pageSize, SysUser currentUser) {
        requireProject(projectId, currentUser);
        int page = pageNo == null ? 1 : Math.max(1, pageNo);
        int size = pageSize == null ? 20 : Math.max(1, Math.min(pageSize, 100));
        Page<QualityIssue> result = issueMapper.selectPage(
                new Page<>(page, size), buildIssueQuery(projectId, status, keyword));
        return PageResult.of(page, size, result.getTotal(), result.getRecords().stream()
                .map(issue -> toVO(issue, currentUser, false))
                .toList());
    }

    public QualityIssueSummaryVO getSummary(Long projectId, SysUser currentUser) {
        requireProject(projectId, currentUser);
        LocalDateTime start = LocalDate.now().atStartOfDay();
        int today = count(new LambdaQueryWrapper<QualityIssue>()
                .eq(QualityIssue::getProjectId, projectId)
                .ge(QualityIssue::getCreateTime, start)
                .lt(QualityIssue::getCreateTime, start.plusDays(1)));
        int pending = count(new LambdaQueryWrapper<QualityIssue>()
                .eq(QualityIssue::getProjectId, projectId)
                .eq(QualityIssue::getStatus, STATUS_PENDING));
        int overdue = count(new LambdaQueryWrapper<QualityIssue>()
                .eq(QualityIssue::getProjectId, projectId)
                .in(QualityIssue::getStatus, List.of(STATUS_PENDING, STATUS_RECHECK))
                .lt(QualityIssue::getDeadline, LocalDate.now()));
        int recheck = count(new LambdaQueryWrapper<QualityIssue>()
                .eq(QualityIssue::getProjectId, projectId)
                .eq(QualityIssue::getStatus, STATUS_RECHECK));
        int closed = count(new LambdaQueryWrapper<QualityIssue>()
                .eq(QualityIssue::getProjectId, projectId)
                .eq(QualityIssue::getStatus, STATUS_CLOSED));
        int total = pending + recheck + closed;

        QualityIssueSummaryVO summary = new QualityIssueSummaryVO();
        summary.setTodayCheckCount(today);
        summary.setPendingCount(pending);
        summary.setOverdueCount(overdue);
        summary.setRecheckCount(recheck);
        summary.setClosedCount(closed);
        summary.setClosureRate(total == 0 ? 0 : Math.round(closed * 100F / total));
        summary.setCanManage(projectPermissionService.canManageQuality(currentUser.getId(), projectId));
        return summary;
    }

    public QualityIssueVO getIssue(Long id, SysUser currentUser) {
        QualityIssue issue = requireIssue(id);
        requireProject(issue.getProjectId(), currentUser);
        return toVO(issue, currentUser, true);
    }

    @Transactional
    public QualityIssueVO createIssue(QualityIssueCreateRequest request, SysUser currentUser) {
        validateCreateRequest(request);
        requireManage(currentUser, request.getProjectId());
        String requestKey = normalizeRequestKey(request.getRequestKey());
        QualityIssue existing = findByRequestKey(request.getProjectId(), currentUser.getId(), requestKey);
        if (existing != null) {
            return toVO(existing, currentUser, true);
        }
        SysUser assignee = qualityAssigneeService.requireEligibleAssignee(
                request.getAssigneeId(), request.getProjectId(), currentUser);

        QualityIssue issue = new QualityIssue();
        issue.setProjectId(request.getProjectId());
        issue.setIssueNo(generateIssueNo());
        issue.setRequestKey(requestKey);
        issue.setTitle(request.getTitle().trim());
        issue.setLocation(trimToNull(request.getLocation()));
        issue.setDescription(trimToNull(request.getDescription()));
        issue.setSeverity(normalizeSeverity(request.getSeverity()));
        issue.setStatus(STATUS_PENDING);
        issue.setAssigneeId(assignee.getId());
        issue.setAssigneeName(displayName(assignee));
        issue.setDeadline(request.getDeadline() == null ? LocalDate.now().plusDays(3) : request.getDeadline());
        issue.setCreatedById(currentUser.getId());
        issue.setCreatedByName(displayName(currentUser));
        issue.setVersion(0);
        issue.setCreateTime(LocalDateTime.now());
        issue.setUpdateTime(LocalDateTime.now());
        try {
            issueMapper.insert(issue);
        } catch (DuplicateKeyException duplicate) {
            QualityIssue concurrent = findByRequestKey(request.getProjectId(), currentUser.getId(), requestKey);
            if (concurrent != null) {
                return toVO(concurrent, currentUser, true);
            }
            throw duplicate;
        }
        fileResourceService.validateAndBind(currentUser, issue.getProjectId(), request.getPhotoFileIds(),
                "QUALITY_PENDING", "QUALITY_ISSUE", issue.getId());
        writeLog(issue, "CREATE", null, STATUS_PENDING, currentUser, issue.getDescription(),
                joinIds(request.getPhotoFileIds()));
        safeNotify(issue.getAssigneeId(), "RECTIFICATION_PENDING", issue,
                "质量问题待整改：" + issue.getTitle());
        return toVO(requireIssue(issue.getId()), currentUser, true);
    }

    @Transactional
    public QualityIssueVO submitRectification(Long id, QualityRectificationRequest request, SysUser currentUser) {
        QualityIssue issue = requireIssue(id);
        requireProject(issue.getProjectId(), currentUser);
        projectPermissionService.requireSystemPermission(currentUser.getId(), issue.getProjectId(),
                SystemPermissionCodes.QUALITY_RECTIFY);
        if (!STATUS_PENDING.equals(issue.getStatus())) {
            throw stateConflict("只有待整改问题可以提交整改");
        }
        boolean manager = projectPermissionService.canManageQuality(currentUser.getId(), issue.getProjectId());
        if (!manager && !Objects.equals(issue.getAssigneeId(), currentUser.getId())) {
            throw BusinessException.forbidden("只能处理分配给自己的质量整改");
        }
        if (request == null || !StringUtils.hasText(request.getDescription())) {
            throw new BusinessException("整改说明不能为空");
        }
        if (request.getPhotoFileIds() == null || request.getPhotoFileIds().isEmpty()) {
            throw new BusinessException("请至少上传一张整改照片");
        }
        String description = request.getDescription().trim();
        String photoFileIds = joinIds(request.getPhotoFileIds());
        LocalDateTime now = LocalDateTime.now();
        int updated = issueMapper.updateRectification(
                issue.getId(), STATUS_PENDING, versionOf(issue), STATUS_RECHECK,
                description, photoFileIds, now, now);
        requireWorkflowUpdate(updated);
        issue.setRectificationDescription(description);
        issue.setRectificationPhotoFileIds(photoFileIds);
        issue.setRectifiedTime(now);
        issue.setStatus(STATUS_RECHECK);
        issue.setVersion(versionOf(issue) + 1);
        issue.setUpdateTime(now);
        fileResourceService.validateAndBind(currentUser, issue.getProjectId(), request.getPhotoFileIds(),
                "QUALITY_RECTIFICATION_PENDING", "QUALITY_RECTIFICATION", issue.getId());
        writeLog(issue, "RECTIFY", STATUS_PENDING, STATUS_RECHECK, currentUser,
                issue.getRectificationDescription(), issue.getRectificationPhotoFileIds());
        notifyReviewers(issue);
        return toVO(requireIssue(id), currentUser, true);
    }

    @Transactional
    public QualityIssueVO reviewIssue(Long id, QualityReviewRequest request, SysUser currentUser) {
        QualityIssue issue = requireIssue(id);
        requireProject(issue.getProjectId(), currentUser);
        if (!canReview(currentUser.getId(), issue.getProjectId())) {
            throw BusinessException.forbidden("无质量复查权限");
        }
        if (!STATUS_RECHECK.equals(issue.getStatus())) {
            throw stateConflict("只有待复查问题可以复查");
        }
        if (request == null || request.getPassed() == null) {
            throw new BusinessException("复查结论不能为空");
        }
        if (!request.getPassed() && !StringUtils.hasText(request.getComment())) {
            throw new BusinessException("退回整改时必须填写意见");
        }
        String targetStatus = request.getPassed() ? STATUS_CLOSED : STATUS_PENDING;
        String reviewerName = displayName(currentUser);
        String reviewComment = trimToNull(request.getComment());
        LocalDateTime now = LocalDateTime.now();
        int updated = issueMapper.updateReview(
                issue.getId(), STATUS_RECHECK, versionOf(issue), targetStatus,
                currentUser.getId(), reviewerName, reviewComment, now, now);
        requireWorkflowUpdate(updated);
        issue.setStatus(targetStatus);
        issue.setReviewerId(currentUser.getId());
        issue.setReviewerName(reviewerName);
        issue.setReviewComment(reviewComment);
        issue.setReviewTime(now);
        issue.setVersion(versionOf(issue) + 1);
        issue.setUpdateTime(now);
        fileResourceService.validateAndBind(currentUser, issue.getProjectId(), request.getPhotoFileIds(),
                "QUALITY_REVIEW_PENDING", "QUALITY_REVIEW", issue.getId());
        writeLog(issue, request.getPassed() ? "REVIEW_PASS" : "REVIEW_REJECT", STATUS_RECHECK,
                targetStatus, currentUser, issue.getReviewComment(), joinIds(request.getPhotoFileIds()));
        if (!request.getPassed()) {
            safeNotify(issue.getAssigneeId(), "RECHECK_REJECTED", issue,
                    "质量复查已退回：" + issue.getTitle());
        }
        return toVO(requireIssue(id), currentUser, true);
    }

    @Transactional
    public QualityIssueVO assignIssue(Long id, QualityAssignRequest request, SysUser currentUser) {
        QualityIssue issue = requireIssue(id);
        requireManage(currentUser, issue.getProjectId());
        if (isTerminal(issue.getStatus())) {
            throw stateConflict("已关闭或已作废问题不能改派");
        }
        if (request == null || (request.getAssigneeId() == null && request.getDeadline() == null)) {
            throw new BusinessException("请选择整改人或调整期限");
        }
        if (request.getDeadline() != null && request.getDeadline().isBefore(LocalDate.now())) {
            throw new BusinessException("闭环期限不能早于今天");
        }
        String before = (issue.getAssigneeName() == null ? "-" : issue.getAssigneeName())
                + " / " + (issue.getDeadline() == null ? "-" : issue.getDeadline());
        if (request.getAssigneeId() != null) {
            SysUser assignee = qualityAssigneeService.requireEligibleAssignee(
                    request.getAssigneeId(), issue.getProjectId(), currentUser);
            issue.setAssigneeId(assignee.getId());
            issue.setAssigneeName(displayName(assignee));
        }
        if (request.getDeadline() != null) {
            issue.setDeadline(request.getDeadline());
        }
        LocalDateTime now = LocalDateTime.now();
        int updated = issueMapper.updateAssignment(
                issue.getId(), issue.getStatus(), versionOf(issue),
                issue.getAssigneeId(), issue.getAssigneeName(), issue.getDeadline(), now);
        requireWorkflowUpdate(updated);
        issue.setVersion(versionOf(issue) + 1);
        issue.setUpdateTime(now);
        String after = issue.getAssigneeName() + " / " + issue.getDeadline();
        String comment = StringUtils.hasText(request.getComment())
                ? request.getComment().trim() + "；" : "";
        writeLog(issue, "ASSIGN", issue.getStatus(), issue.getStatus(), currentUser,
                comment + before + " -> " + after, null);
        safeNotify(issue.getAssigneeId(), "RECTIFICATION_PENDING", issue,
                "质量问题负责人或闭环期限已调整：" + issue.getTitle());
        return toVO(requireIssue(id), currentUser, true);
    }

    @Transactional
    public QualityIssueVO voidIssue(Long id, QualityVoidRequest request, SysUser currentUser) {
        QualityIssue issue = requireIssue(id);
        requireManage(currentUser, issue.getProjectId());
        if (!List.of(STATUS_PENDING, STATUS_RECHECK).contains(issue.getStatus())) {
            throw stateConflict("只有待整改或待复查问题可以作废");
        }
        if (request == null || !StringUtils.hasText(request.getComment())) {
            throw new BusinessException("作废原因不能为空");
        }
        String comment = request.getComment().trim();
        if (comment.length() > 1000) {
            throw new BusinessException("作废原因长度不能超过1000个字符");
        }
        String fromStatus = issue.getStatus();
        LocalDateTime now = LocalDateTime.now();
        int updated = issueMapper.updateStatus(
                issue.getId(), fromStatus, versionOf(issue), STATUS_VOIDED, now);
        requireWorkflowUpdate(updated);
        issue.setStatus(STATUS_VOIDED);
        issue.setVersion(versionOf(issue) + 1);
        issue.setUpdateTime(now);
        writeLog(issue, "VOID", fromStatus, STATUS_VOIDED, currentUser, comment, null);
        return toVO(requireIssue(id), currentUser, true);
    }

    public List<QualityTodoVO> listTodos(Long requestedProjectId, SysUser currentUser) {
        if (currentUser == null || currentUser.getId() == null) {
            throw BusinessException.unauthorized("请先登录");
        }
        List<ProjectInfo> projects;
        if (requestedProjectId != null) {
            requireProject(requestedProjectId, currentUser);
            ProjectInfo project = projectInfoMapper.selectById(requestedProjectId);
            projects = project == null ? List.of() : List.of(project);
        } else {
            projects = projectPermissionService.isPlatformAdmin(currentUser.getId())
                    ? projectInfoMapper.selectList(null)
                    : projectPermissionService.getUserProjects(currentUser.getId());
        }

        List<QualityTodoCandidate> todos = new ArrayList<>();
        for (ProjectInfo project : projects) {
            if (project == null || project.getId() == null
                    || !projectPermissionService.hasProjectPermission(currentUser.getId(), project.getId())
                    || !projectPermissionService.hasSystemPermission(
                    currentUser.getId(), project.getId(), SystemPermissionCodes.QUALITY_VIEW)) {
                continue;
            }
            if (projectPermissionService.hasSystemPermission(
                    currentUser.getId(), project.getId(), SystemPermissionCodes.QUALITY_RECTIFY)) {
                appendRectificationTodos(todos, project, currentUser);
            }
            if (canReview(currentUser.getId(), project.getId())) {
                appendRecheckTodos(todos, project);
            }
        }
        return todos.stream()
                .sorted(this::compareTodoCandidates)
                .map(QualityTodoCandidate::todo)
                .toList();
    }

    private void appendRectificationTodos(List<QualityTodoCandidate> todos, ProjectInfo project, SysUser currentUser) {
        issueMapper.selectList(new LambdaQueryWrapper<QualityIssue>()
                        .eq(QualityIssue::getProjectId, project.getId())
                        .eq(QualityIssue::getStatus, STATUS_PENDING)
                        .eq(QualityIssue::getAssigneeId, currentUser.getId()))
                .forEach(issue -> todos.add(new QualityTodoCandidate(
                        toTodo(issue, project, "RECTIFICATION"), issue)));
    }

    private void appendRecheckTodos(List<QualityTodoCandidate> todos, ProjectInfo project) {
        issueMapper.selectList(new LambdaQueryWrapper<QualityIssue>()
                        .eq(QualityIssue::getProjectId, project.getId())
                        .eq(QualityIssue::getStatus, STATUS_RECHECK))
                .forEach(issue -> todos.add(new QualityTodoCandidate(
                        toTodo(issue, project, "RECHECK"), issue)));
    }

    private QualityTodoVO toTodo(QualityIssue issue, ProjectInfo project, String type) {
        boolean overdue = isOverdue(issue);
        QualityTodoVO todo = new QualityTodoVO();
        // 巡检待办使用从 1 开始的临时正数 ID；质量待办使用问题 ID 的负数，
        // 同时由 businessType + targetId 作为客户端稳定业务键。
        todo.setId(issue.getId() == null ? 0L : -Math.abs(issue.getId()));
        todo.setType(type);
        todo.setTitle(issue.getTitle());
        todo.setProjectId(issue.getProjectId());
        todo.setProjectName(projectDisplayName(project));
        todo.setBoxCode(issue.getIssueNo());
        todo.setInstallLocation(issue.getLocation());
        todo.setDueText(buildDueText(issue, overdue));
        todo.setTargetId(issue.getId());
        todo.setBusinessType(BUSINESS_TYPE_QUALITY_ISSUE);
        todo.setPriority(todoPriority(issue, overdue));
        todo.setReviewOverdue(overdue ? 1 : 0);
        return todo;
    }

    private int compareTodoCandidates(QualityTodoCandidate leftCandidate, QualityTodoCandidate rightCandidate) {
        QualityTodoVO left = leftCandidate.todo();
        QualityTodoVO right = rightCandidate.todo();
        int overdue = Integer.compare(
                Objects.equals(right.getReviewOverdue(), 1) ? 1 : 0,
                Objects.equals(left.getReviewOverdue(), 1) ? 1 : 0);
        if (overdue != 0) return overdue;
        int severity = Integer.compare(
                severityRank(leftCandidate.issue().getSeverity()),
                severityRank(rightCandidate.issue().getSeverity()));
        if (severity != 0) return severity;
        int deadline = compareNullableDate(
                leftCandidate.issue().getDeadline(),
                rightCandidate.issue().getDeadline());
        if (deadline != 0) return deadline;
        int created = compareNullableDateTime(
                rightCandidate.issue().getCreateTime(),
                leftCandidate.issue().getCreateTime());
        if (created != 0) return created;
        return Long.compare(
                right.getTargetId() == null ? 0L : right.getTargetId(),
                left.getTargetId() == null ? 0L : left.getTargetId());
    }

    private void validateCreateRequest(QualityIssueCreateRequest request) {
        if (request == null || request.getProjectId() == null) {
            throw new BusinessException("项目ID不能为空");
        }
        if (!StringUtils.hasText(request.getTitle())) {
            throw new BusinessException("质量问题标题不能为空");
        }
        if (request.getDeadline() != null && request.getDeadline().isBefore(LocalDate.now())) {
            throw new BusinessException("闭环期限不能早于今天");
        }
        if (request.getPhotoFileIds() == null || request.getPhotoFileIds().isEmpty()) {
            throw new BusinessException("请至少上传一张问题照片");
        }
    }

    private LambdaQueryWrapper<QualityIssue> buildIssueQuery(Long projectId, String status, String keyword) {
        LambdaQueryWrapper<QualityIssue> wrapper = new LambdaQueryWrapper<QualityIssue>()
                .eq(QualityIssue::getProjectId, projectId);
        if (StringUtils.hasText(status) && !"ALL".equalsIgnoreCase(status)) {
            if ("OVERDUE".equalsIgnoreCase(status)) {
                wrapper.in(QualityIssue::getStatus, List.of(STATUS_PENDING, STATUS_RECHECK))
                        .lt(QualityIssue::getDeadline, LocalDate.now());
            } else {
                wrapper.eq(QualityIssue::getStatus, normalizeStatus(status));
            }
        }
        if (StringUtils.hasText(keyword)) {
            String text = keyword.trim();
            wrapper.and(w -> w.like(QualityIssue::getTitle, text)
                    .or()
                    .like(QualityIssue::getLocation, text)
                    .or()
                    .like(QualityIssue::getAssigneeName, text));
        }
        return wrapper.last(QUALITY_ORDER_SQL);
    }

    private QualityIssue findByRequestKey(Long projectId, Long createdById, String requestKey) {
        if (!StringUtils.hasText(requestKey)) {
            return null;
        }
        return issueMapper.selectOne(new LambdaQueryWrapper<QualityIssue>()
                .eq(QualityIssue::getProjectId, projectId)
                .eq(QualityIssue::getCreatedById, createdById)
                .eq(QualityIssue::getRequestKey, requestKey)
                .last("LIMIT 1"));
    }

    private String normalizeRequestKey(String requestKey) {
        if (!StringUtils.hasText(requestKey)) {
            return null;
        }
        String normalized = requestKey.trim();
        if (normalized.length() > 100) {
            throw new BusinessException("requestKey长度不能超过100个字符");
        }
        return normalized;
    }

    private int versionOf(QualityIssue issue) {
        return issue.getVersion() == null ? 0 : issue.getVersion();
    }

    private BusinessException stateConflict(String message) {
        return BusinessException.of(409, message + "，请刷新后重试");
    }

    private void requireWorkflowUpdate(int updated) {
        if (updated != 1) {
            throw stateConflict("质量问题已被其他人处理");
        }
    }

    private boolean canReview(Long userId, Long projectId) {
        return projectPermissionService.hasSystemPermission(
                userId, projectId, SystemPermissionCodes.QUALITY_REVIEW);
    }

    private void notifyReviewers(QualityIssue issue) {
        try {
            for (Long reviewerId : issueMapper.selectPotentialReviewerIds(issue.getProjectId())) {
                if (reviewerId == null || !canReview(reviewerId, issue.getProjectId())) {
                    continue;
                }
                safeNotify(reviewerId, "RECHECK_PENDING", issue,
                        "质量整改待复查：" + issue.getTitle());
            }
        } catch (Exception ignored) {
            // 微信通知是增强能力；候选人查询或发送异常均不得回滚质量整改。
        }
    }

    private void safeNotify(Long userId, String templateCode, QualityIssue issue, String summary) {
        if (userId == null) {
            return;
        }
        try {
            wechatNotificationService.notifyUser(
                    userId, templateCode, BUSINESS_TYPE_QUALITY_ISSUE, issue.getId(), summary);
        } catch (Exception ignored) {
            // 站内待办由状态实时派生，微信通知失败不得阻断质量闭环。
        }
    }

    private String projectDisplayName(ProjectInfo project) {
        return StringUtils.hasText(project.getShortName()) ? project.getShortName() : project.getProjectName();
    }

    private String todoPriority(QualityIssue issue, boolean overdue) {
        if (overdue || "DANGER".equals(issue.getSeverity())) return "danger";
        if ("WARNING".equals(issue.getSeverity()) || STATUS_RECHECK.equals(issue.getStatus())) return "warning";
        return "normal";
    }

    private int severityRank(String severity) {
        if ("DANGER".equals(severity)) return 0;
        if ("WARNING".equals(severity)) return 1;
        if ("NORMAL".equals(severity)) return 2;
        return 3;
    }

    private int compareNullableDate(LocalDate left, LocalDate right) {
        if (left == null && right == null) return 0;
        if (left == null) return 1;
        if (right == null) return -1;
        return left.compareTo(right);
    }

    private int compareNullableDateTime(LocalDateTime left, LocalDateTime right) {
        if (left == null && right == null) return 0;
        if (left == null) return 1;
        if (right == null) return -1;
        return left.compareTo(right);
    }

    private QualityIssue requireIssue(Long id) {
        QualityIssue issue = issueMapper.selectById(id);
        if (issue == null) {
            throw BusinessException.notFound("质量问题不存在");
        }
        return issue;
    }

    private void requireProject(Long projectId, SysUser currentUser) {
        if (projectId == null) {
            throw new BusinessException("项目ID不能为空");
        }
        projectPermissionService.checkProjectPermission(currentUser.getId(), projectId);
        projectPermissionService.requireSystemPermission(currentUser.getId(), projectId,
                SystemPermissionCodes.QUALITY_VIEW);
    }

    private void requireManage(SysUser currentUser, Long projectId) {
        requireProject(projectId, currentUser);
        if (!projectPermissionService.canManageQuality(currentUser.getId(), projectId)) {
            throw BusinessException.forbidden("无质量管理权限");
        }
    }

    private QualityIssueVO toVO(QualityIssue issue, SysUser currentUser, boolean includeLogs) {
        QualityIssueVO vo = new QualityIssueVO();
        BeanUtils.copyProperties(issue, vo);
        List<Long> issuePhotos = includeLogs ? attachmentIds(issue.getId(), "QUALITY_ISSUE") : Collections.emptyList();
        List<Long> rectificationPhotos = includeLogs ? attachmentIds(issue.getId(), "QUALITY_RECTIFICATION") : Collections.emptyList();
        vo.setIssuePhotoFileIds(issuePhotos);
        vo.setRectificationPhotoFileIds(rectificationPhotos.isEmpty()
                ? splitIds(issue.getRectificationPhotoFileIds()) : rectificationPhotos);
        vo.setReviewPhotoFileIds(includeLogs ? attachmentIds(issue.getId(), "QUALITY_REVIEW") : Collections.emptyList());
        boolean overdue = isOverdue(issue);
        boolean pending = STATUS_PENDING.equals(issue.getStatus());
        boolean canManage = pending && projectPermissionService.canManageQuality(
                currentUser.getId(), issue.getProjectId());
        boolean canRectify = pending && projectPermissionService.hasSystemPermission(
                currentUser.getId(), issue.getProjectId(), SystemPermissionCodes.QUALITY_RECTIFY);
        vo.setOverdue(overdue);
        vo.setDueText(buildDueText(issue, overdue));
        vo.setCanRectify(pending
                && canRectify
                && (canManage || Objects.equals(issue.getAssigneeId(), currentUser.getId())));
        vo.setCanReview(STATUS_RECHECK.equals(issue.getStatus())
                && canReview(currentUser.getId(), issue.getProjectId()));
        vo.setLogs(includeLogs ? logMapper.selectList(new LambdaQueryWrapper<QualityIssueLog>()
                .eq(QualityIssueLog::getIssueId, issue.getId())
                .orderByDesc(QualityIssueLog::getCreateTime)) : Collections.emptyList());
        return vo;
    }

    private String buildDueText(QualityIssue issue, boolean overdue) {
        if (STATUS_CLOSED.equals(issue.getStatus())) return "已关闭";
        if (STATUS_VOIDED.equals(issue.getStatus())) return "已作废";
        String stagePrefix = STATUS_RECHECK.equals(issue.getStatus()) ? "待复查 · " : "";
        if (issue.getDeadline() == null) return stagePrefix + "尽快处理";
        long days = ChronoUnit.DAYS.between(LocalDate.now(), issue.getDeadline());
        if (overdue) return stagePrefix + "已逾期" + Math.abs(days) + "天";
        if (days == 0) return stagePrefix + "今天到期";
        if (days == 1) return stagePrefix + "明天到期";
        return stagePrefix + issue.getDeadline() + " 前";
    }

    private boolean isOverdue(QualityIssue issue) {
        return List.of(STATUS_PENDING, STATUS_RECHECK).contains(issue.getStatus())
                && issue.getDeadline() != null
                && issue.getDeadline().isBefore(LocalDate.now());
    }

    private boolean isTerminal(String status) {
        return STATUS_CLOSED.equals(status) || STATUS_VOIDED.equals(status);
    }

    private void writeLog(QualityIssue issue, String actionType, String fromStatus, String toStatus,
                          SysUser operator, String comment, String photoFileIds) {
        QualityIssueLog log = new QualityIssueLog();
        log.setIssueId(issue.getId());
        log.setProjectId(issue.getProjectId());
        log.setActionType(actionType);
        log.setFromStatus(fromStatus);
        log.setToStatus(toStatus);
        log.setOperatorId(operator.getId());
        log.setOperatorName(displayName(operator));
        log.setComment(comment);
        log.setPhotoFileIds(photoFileIds);
        log.setCreateTime(LocalDateTime.now());
        logMapper.insert(log);
    }

    private String normalizeStatus(String status) {
        String normalized = status.trim().toUpperCase();
        if (!List.of(STATUS_PENDING, STATUS_RECHECK, STATUS_CLOSED, STATUS_VOIDED).contains(normalized)) {
            throw new BusinessException("质量问题状态不支持");
        }
        return normalized;
    }

    private String normalizeSeverity(String severity) {
        if (!StringUtils.hasText(severity)) return "NORMAL";
        String normalized = severity.trim().toUpperCase();
        if (!List.of("NORMAL", "WARNING", "DANGER").contains(normalized)) {
            throw new BusinessException("严重程度只支持 NORMAL、WARNING 或 DANGER");
        }
        return normalized;
    }

    private String generateIssueNo() {
        return "Q-" + UUID.randomUUID().toString().replace("-", "");
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String displayName(SysUser user) {
        return StringUtils.hasText(user.getRealName()) ? user.getRealName() : user.getUsername();
    }

    private int count(LambdaQueryWrapper<QualityIssue> wrapper) {
        Long value = issueMapper.selectCount(wrapper);
        return value == null ? 0 : Math.toIntExact(value);
    }

    private String joinIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return null;
        return ids.stream().filter(Objects::nonNull).map(String::valueOf).distinct().reduce((a, b) -> a + "," + b).orElse(null);
    }

    private List<Long> splitIds(String ids) {
        if (!StringUtils.hasText(ids)) return Collections.emptyList();
        return Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(Long::valueOf)
                .toList();
    }

    private List<Long> attachmentIds(Long issueId, String businessType) {
        return fileMapper.selectList(new LambdaQueryWrapper<FileResource>()
                        .eq(FileResource::getBusinessType, businessType)
                        .eq(FileResource::getBusinessId, issueId)
                        .orderByAsc(FileResource::getCreateTime))
                .stream().map(FileResource::getId).toList();
    }

    private record QualityTodoCandidate(QualityTodoVO todo, QualityIssue issue) {
    }
}
