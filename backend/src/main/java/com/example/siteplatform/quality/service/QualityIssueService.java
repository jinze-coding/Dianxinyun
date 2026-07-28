package com.example.siteplatform.quality.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.file.entity.FileResource;
import com.example.siteplatform.file.mapper.FileResourceMapper;
import com.example.siteplatform.file.service.FileResourceService;
import com.example.siteplatform.quality.dto.QualityAssignRequest;
import com.example.siteplatform.quality.dto.QualityIssueCreateRequest;
import com.example.siteplatform.quality.dto.QualityRectificationRequest;
import com.example.siteplatform.quality.dto.QualityReviewRequest;
import com.example.siteplatform.quality.entity.QualityIssue;
import com.example.siteplatform.quality.entity.QualityIssueLog;
import com.example.siteplatform.quality.mapper.QualityIssueLogMapper;
import com.example.siteplatform.quality.mapper.QualityIssueMapper;
import com.example.siteplatform.quality.vo.QualityIssueSummaryVO;
import com.example.siteplatform.quality.vo.QualityIssueVO;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.system.constant.SystemPermissionCodes;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Service
public class QualityIssueService {
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RECHECK = "RECHECK";
    public static final String STATUS_CLOSED = "CLOSED";

    @Autowired
    private QualityIssueMapper issueMapper;

    @Autowired
    private QualityIssueLogMapper logMapper;

    @Autowired
    private ProjectPermissionService projectPermissionService;

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private FileResourceMapper fileMapper;

    @Autowired
    private FileResourceService fileResourceService;

    public List<QualityIssueVO> listIssues(Long projectId, String status, String keyword, SysUser currentUser) {
        requireProject(projectId, currentUser);
        LambdaQueryWrapper<QualityIssue> wrapper = new LambdaQueryWrapper<QualityIssue>()
                .eq(QualityIssue::getProjectId, projectId);
        if (StringUtils.hasText(status) && !"ALL".equalsIgnoreCase(status)) {
            if ("OVERDUE".equalsIgnoreCase(status)) {
                wrapper.eq(QualityIssue::getStatus, STATUS_PENDING)
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
        wrapper.orderByAsc(QualityIssue::getStatus)
                .orderByAsc(QualityIssue::getDeadline)
                .orderByDesc(QualityIssue::getCreateTime);
        return issueMapper.selectList(wrapper).stream()
                .map(issue -> toVO(issue, currentUser, false))
                .toList();
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
                .eq(QualityIssue::getStatus, STATUS_PENDING)
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
        summary.setClosureRate(total == 0 ? 100 : Math.round(closed * 100F / total));
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
        SysUser assignee = resolveAssignee(request.getAssigneeId(), request.getProjectId(), currentUser);

        QualityIssue issue = new QualityIssue();
        issue.setProjectId(request.getProjectId());
        issue.setIssueNo("Q-" + LocalDate.now().toString().replace("-", "") + "-" + System.currentTimeMillis() % 1000000);
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
        issue.setCreateTime(LocalDateTime.now());
        issue.setUpdateTime(LocalDateTime.now());
        issueMapper.insert(issue);
        fileResourceService.validateAndBind(currentUser, issue.getProjectId(), request.getPhotoFileIds(),
                "QUALITY_PENDING", "QUALITY_ISSUE", issue.getId());
        writeLog(issue, "CREATE", null, STATUS_PENDING, currentUser, issue.getDescription(),
                joinIds(request.getPhotoFileIds()));
        return toVO(issueMapper.selectById(issue.getId()), currentUser, true);
    }

    @Transactional
    public QualityIssueVO submitRectification(Long id, QualityRectificationRequest request, SysUser currentUser) {
        QualityIssue issue = requireIssue(id);
        requireProject(issue.getProjectId(), currentUser);
        projectPermissionService.requireSystemPermission(currentUser.getId(), issue.getProjectId(),
                SystemPermissionCodes.QUALITY_RECTIFY);
        if (!STATUS_PENDING.equals(issue.getStatus())) {
            throw new BusinessException("只有待整改问题可以提交整改");
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
        String fromStatus = issue.getStatus();
        issue.setRectificationDescription(request.getDescription().trim());
        issue.setRectificationPhotoFileIds(joinIds(request.getPhotoFileIds()));
        issue.setRectifiedTime(LocalDateTime.now());
        issue.setStatus(STATUS_RECHECK);
        issue.setUpdateTime(LocalDateTime.now());
        issueMapper.updateById(issue);
        fileResourceService.validateAndBind(currentUser, issue.getProjectId(), request.getPhotoFileIds(),
                "QUALITY_RECTIFICATION_PENDING", "QUALITY_RECTIFICATION", issue.getId());
        writeLog(issue, "RECTIFY", fromStatus, STATUS_RECHECK, currentUser,
                issue.getRectificationDescription(), issue.getRectificationPhotoFileIds());
        return toVO(issueMapper.selectById(id), currentUser, true);
    }

    @Transactional
    public QualityIssueVO reviewIssue(Long id, QualityReviewRequest request, SysUser currentUser) {
        QualityIssue issue = requireIssue(id);
        requireProject(issue.getProjectId(), currentUser);
        projectPermissionService.requireSystemPermission(currentUser.getId(), issue.getProjectId(),
                SystemPermissionCodes.QUALITY_REVIEW);
        if (!projectPermissionService.canManageQuality(currentUser.getId(), issue.getProjectId())) {
            throw BusinessException.forbidden("无质量复查权限");
        }
        if (!STATUS_RECHECK.equals(issue.getStatus())) {
            throw new BusinessException("只有待复查问题可以复查");
        }
        if (request == null || request.getPassed() == null) {
            throw new BusinessException("复查结论不能为空");
        }
        if (!request.getPassed() && !StringUtils.hasText(request.getComment())) {
            throw new BusinessException("退回整改时必须填写意见");
        }
        String targetStatus = request.getPassed() ? STATUS_CLOSED : STATUS_PENDING;
        issue.setStatus(targetStatus);
        issue.setReviewerId(currentUser.getId());
        issue.setReviewerName(displayName(currentUser));
        issue.setReviewComment(trimToNull(request.getComment()));
        issue.setReviewTime(LocalDateTime.now());
        issue.setUpdateTime(LocalDateTime.now());
        issueMapper.updateById(issue);
        fileResourceService.validateAndBind(currentUser, issue.getProjectId(), request.getPhotoFileIds(),
                "QUALITY_REVIEW_PENDING", "QUALITY_REVIEW", issue.getId());
        writeLog(issue, request.getPassed() ? "REVIEW_PASS" : "REVIEW_REJECT", STATUS_RECHECK,
                targetStatus, currentUser, issue.getReviewComment(), joinIds(request.getPhotoFileIds()));
        return toVO(issueMapper.selectById(id), currentUser, true);
    }

    @Transactional
    public QualityIssueVO assignIssue(Long id, QualityAssignRequest request, SysUser currentUser) {
        QualityIssue issue = requireIssue(id);
        requireManage(currentUser, issue.getProjectId());
        if (STATUS_CLOSED.equals(issue.getStatus())) {
            throw new BusinessException("已关闭问题不能改派");
        }
        if (request == null || (request.getAssigneeId() == null && request.getDeadline() == null)) {
            throw new BusinessException("请选择整改人或调整期限");
        }
        if (request.getDeadline() != null && request.getDeadline().isBefore(LocalDate.now())) {
            throw new BusinessException("整改期限不能早于今天");
        }
        String before = (issue.getAssigneeName() == null ? "-" : issue.getAssigneeName())
                + " / " + (issue.getDeadline() == null ? "-" : issue.getDeadline());
        if (request.getAssigneeId() != null) {
            SysUser assignee = resolveAssignee(request.getAssigneeId(), issue.getProjectId(), currentUser);
            issue.setAssigneeId(assignee.getId());
            issue.setAssigneeName(displayName(assignee));
        }
        if (request.getDeadline() != null) {
            issue.setDeadline(request.getDeadline());
        }
        issue.setUpdateTime(LocalDateTime.now());
        issueMapper.updateById(issue);
        String after = issue.getAssigneeName() + " / " + issue.getDeadline();
        String comment = StringUtils.hasText(request.getComment())
                ? request.getComment().trim() + "；" : "";
        writeLog(issue, "ASSIGN", issue.getStatus(), issue.getStatus(), currentUser,
                comment + before + " -> " + after, null);
        return toVO(issueMapper.selectById(id), currentUser, true);
    }

    private void validateCreateRequest(QualityIssueCreateRequest request) {
        if (request == null || request.getProjectId() == null) {
            throw new BusinessException("项目ID不能为空");
        }
        if (!StringUtils.hasText(request.getTitle())) {
            throw new BusinessException("质量问题标题不能为空");
        }
        if (request.getDeadline() != null && request.getDeadline().isBefore(LocalDate.now())) {
            throw new BusinessException("整改期限不能早于今天");
        }
        if (request.getPhotoFileIds() == null || request.getPhotoFileIds().isEmpty()) {
            throw new BusinessException("请至少上传一张问题照片");
        }
    }

    private SysUser resolveAssignee(Long assigneeId, Long projectId, SysUser currentUser) {
        Long targetId = assigneeId == null ? currentUser.getId() : assigneeId;
        SysUser assignee = userMapper.selectById(targetId);
        if (assignee == null) {
            throw BusinessException.notFound("整改负责人不存在");
        }
        if (!projectPermissionService.isPlatformAdmin(targetId)
                && !projectPermissionService.hasProjectPermission(targetId, projectId)) {
            throw new BusinessException("整改负责人不属于当前项目");
        }
        return assignee;
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
        projectPermissionService.requireSystemPermission(currentUser.getId(), projectId,
                SystemPermissionCodes.QUALITY_MANAGE);
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
        boolean overdue = STATUS_PENDING.equals(issue.getStatus())
                && issue.getDeadline() != null
                && issue.getDeadline().isBefore(LocalDate.now());
        boolean manager = projectPermissionService.canManageQuality(currentUser.getId(), issue.getProjectId());
        vo.setOverdue(overdue);
        vo.setDueText(buildDueText(issue, overdue));
        vo.setCanRectify(STATUS_PENDING.equals(issue.getStatus())
                && (manager || Objects.equals(issue.getAssigneeId(), currentUser.getId())));
        vo.setCanReview(STATUS_RECHECK.equals(issue.getStatus()) && manager);
        vo.setLogs(includeLogs ? logMapper.selectList(new LambdaQueryWrapper<QualityIssueLog>()
                .eq(QualityIssueLog::getIssueId, issue.getId())
                .orderByDesc(QualityIssueLog::getCreateTime)) : Collections.emptyList());
        return vo;
    }

    private String buildDueText(QualityIssue issue, boolean overdue) {
        if (STATUS_CLOSED.equals(issue.getStatus())) return "已关闭";
        if (STATUS_RECHECK.equals(issue.getStatus())) return "等待复查";
        if (issue.getDeadline() == null) return "尽快处理";
        long days = ChronoUnit.DAYS.between(LocalDate.now(), issue.getDeadline());
        if (overdue) return "已逾期" + Math.abs(days) + "天";
        if (days == 0) return "今天到期";
        if (days == 1) return "明天到期";
        return issue.getDeadline() + " 前";
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
        if (!List.of(STATUS_PENDING, STATUS_RECHECK, STATUS_CLOSED).contains(normalized)) {
            throw new BusinessException("质量问题状态不支持");
        }
        return normalized;
    }

    private String normalizeSeverity(String severity) {
        if (!StringUtils.hasText(severity)) return "NORMAL";
        String normalized = severity.trim().toUpperCase();
        return List.of("NORMAL", "WARNING", "DANGER").contains(normalized) ? normalized : "NORMAL";
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
}
