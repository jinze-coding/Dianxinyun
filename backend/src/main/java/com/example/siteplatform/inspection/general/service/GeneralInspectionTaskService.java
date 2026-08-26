package com.example.siteplatform.inspection.general.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.file.service.FileResourceService;
import com.example.siteplatform.inspection.general.dto.*;
import com.example.siteplatform.inspection.general.entity.*;
import com.example.siteplatform.inspection.general.mapper.*;
import com.example.siteplatform.inspection.general.vo.*;
import com.example.siteplatform.notification.service.UserNotificationService;
import com.example.siteplatform.project.constant.InspectionPermissionCodes;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GeneralInspectionTaskService {

    private static final Set<String> RESULTS = Set.of("NORMAL", "ABNORMAL");
    private static final Set<String> SUBMITTED_TASK_STATES = Set.of("COMPLETED", "RECTIFICATION_PENDING", "CLOSED");
    private static final Set<String> OPEN_RECTIFICATION_STATES = Set.of("UNASSIGNED", "PENDING", "COMPLETED", "REJECTED");

    private final GeneralInspectionPermissionService permissionService;
    private final GeneralInspectionTaskMapper taskMapper;
    private final GeneralInspectionTaskItemMapper taskItemMapper;
    private final GeneralInspectionPointMapper pointMapper;
    private final GeneralInspectionTemplateVersionMapper templateVersionMapper;
    private final GeneralInspectionRectificationMapper rectificationMapper;
    private final GeneralInspectionActionLogMapper actionLogMapper;
    private final GeneralInspectionProjectSettingMapper settingMapper;
    private final SysUserMapper userMapper;
    private final FileResourceService fileResourceService;
    private final UserNotificationService notificationService;
    private final GeneralInspectionEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public List<GeneralInspectionTaskVO> listTasks(Long projectId, String status, LocalDate startDate,
                                                   LocalDate endDate, boolean mine, SysUser currentUser) {
        requireUser(currentUser);
        LambdaQueryWrapper<GeneralInspectionTask> query = new LambdaQueryWrapper<>();
        if (projectId != null) {
            if (mine) permissionService.requireEnabled(projectId, currentUser);
            else permissionService.requireRecordView(projectId, currentUser);
            query.eq(GeneralInspectionTask::getProjectId, projectId);
        } else {
            if (!mine) throw new BusinessException("跨项目记录查询必须指定项目");
            List<Long> enabledProjectIds = settingMapper.selectList(
                            new LambdaQueryWrapper<GeneralInspectionProjectSetting>()
                                    .eq(GeneralInspectionProjectSetting::getEnabled, 1))
                    .stream().map(GeneralInspectionProjectSetting::getProjectId).toList();
            if (enabledProjectIds.isEmpty()) return List.of();
            query.in(GeneralInspectionTask::getProjectId, enabledProjectIds);
        }
        if (mine) query.eq(GeneralInspectionTask::getAssigneeId, currentUser.getId());
        query.isNotNull(GeneralInspectionTask::getPointTypeCode);
        if (StringUtils.hasText(status)) query.eq(GeneralInspectionTask::getStatus, status.trim().toUpperCase());
        if (startDate != null) query.ge(GeneralInspectionTask::getOccurrenceDate, startDate);
        if (endDate != null) query.le(GeneralInspectionTask::getOccurrenceDate, endDate);
        return taskMapper.selectList(query.orderByAsc(GeneralInspectionTask::getDueTime)
                        .orderByAsc(GeneralInspectionTask::getId)).stream()
                .filter(task -> permissionService.hasActiveProjectAccess(task.getProjectId(), currentUser.getId()))
                .filter(task -> canReadTask(task, currentUser, mine))
                .map(task -> toTaskVO(task, false, currentUser)).toList();
    }

    public List<GeneralInspectionTaskVO> listTasksNeedingAssignment(Long projectId, SysUser currentUser) {
        permissionService.requireManage(projectId, currentUser);
        return taskMapper.selectList(new LambdaQueryWrapper<GeneralInspectionTask>()
                        .eq(GeneralInspectionTask::getProjectId, projectId)
                        .eq(GeneralInspectionTask::getStatus, "PENDING")
                        .isNull(GeneralInspectionTask::getAssigneeId)
                        .isNotNull(GeneralInspectionTask::getPointTypeCode)
                        .orderByAsc(GeneralInspectionTask::getDueTime))
                .stream().map(task -> toTaskVO(task, false, currentUser)).toList();
    }

    public List<GeneralInspectionRectificationVO> listRectificationsNeedingReviewer(
            Long projectId, SysUser currentUser) {
        permissionService.requireManage(projectId, currentUser);
        return rectificationMapper.selectList(new LambdaQueryWrapper<GeneralInspectionRectification>()
                        .eq(GeneralInspectionRectification::getProjectId, projectId)
                        .eq(GeneralInspectionRectification::getStatus, "COMPLETED")
                        .isNull(GeneralInspectionRectification::getReviewerId)
                        .orderByAsc(GeneralInspectionRectification::getDeadline))
                .stream()
                .filter(rectification -> isEdgeTask(taskMapper.selectById(rectification.getTaskId())))
                .map(rectification -> toRectificationVO(rectification, currentUser)).toList();
    }

    public GeneralInspectionTaskVO getTask(Long id, SysUser currentUser) {
        GeneralInspectionTask task = requireTask(id);
        if (!isEdgeTask(task)) throw BusinessException.notFound("临边巡检任务不存在");
        permissionService.requireEnabled(task.getProjectId(), currentUser);
        if (!canReadTask(task, currentUser, false)) throw BusinessException.forbidden("无该巡检任务访问权限");
        return toTaskVO(task, true, currentUser);
    }

    public GeneralInspectionScanVO resolveScan(String rawSceneCode, SysUser currentUser) {
        requireUser(currentUser);
        String code = normalizeScene(rawSceneCode);
        GeneralInspectionPoint point = pointMapper.selectOne(new LambdaQueryWrapper<GeneralInspectionPoint>()
                .eq(GeneralInspectionPoint::getPublicCode, code)
                .eq(GeneralInspectionPoint::getDeleted, 0).last("LIMIT 1"));
        if (point == null) throw BusinessException.notFound("巡检点位码不存在或已换码");
        permissionService.requireEnabled(point.getProjectId(), currentUser);
        GeneralInspectionScanVO vo = baseScan(point);
        vo.setMode("INTERNAL");
        List<GeneralInspectionTask> eligible = taskMapper.selectList(new LambdaQueryWrapper<GeneralInspectionTask>()
                .eq(GeneralInspectionTask::getPointId, point.getId())
                .eq(GeneralInspectionTask::getAssigneeId, currentUser.getId())
                .eq(GeneralInspectionTask::getStatus, "PENDING")
                .orderByAsc(GeneralInspectionTask::getDueTime));
        vo.setEligibleTasks(eligible.stream().map(task -> toTaskVO(task, false, currentUser)).toList());
        if (eligible.isEmpty()) vo.setReason("当前没有分配给你的待执行任务");
        return vo;
    }

    @Transactional
    public GeneralInspectionTaskVO verifyScan(Long taskId, GeneralInspectionScanRequest request,
                                               SysUser currentUser) {
        GeneralInspectionTask task = taskMapper.selectByIdForUpdate(taskId);
        if (task == null) throw BusinessException.notFound("巡检任务不存在");
        permissionService.requireSubmit(task.getProjectId(), currentUser);
        requirePrimaryAssignee(task, currentUser);
        if (!"PENDING".equals(task.getStatus())) throw conflict("任务状态已变化");
        GeneralInspectionPoint point = pointMapper.selectById(task.getPointId());
        if (point == null || !Objects.equals(normalizeScene(request.getSceneCode()), point.getPublicCode())
                || !Integer.valueOf(1).equals(point.getQrEnabled())
                || !Objects.equals(task.getQrVersion(), point.getQrVersion())) {
            throw BusinessException.forbidden("点位码已失效、已换码或与当前任务不匹配");
        }
        task.setScanVerifiedBy(currentUser.getId());
        task.setScanVerifiedTime(LocalDateTime.now());
        task.setVersion(value(task.getVersion(), 0) + 1);
        requireOne(taskMapper.updateById(task), "扫码凭证保存");
        record(task.getProjectId(), "TASK", task.getId(), "SCAN_VERIFY", currentUser,
                task.getStatus(), task.getStatus(), null, null, null);
        return toTaskVO(task, true, currentUser);
    }

    @Transactional
    public GeneralInspectionTaskVO submitTask(Long id, GeneralInspectionTaskSubmitRequest request,
                                               SysUser currentUser) {
        GeneralInspectionTask task = taskMapper.selectByIdForUpdate(id);
        if (!isEdgeTask(task)) throw BusinessException.notFound("临边巡检任务不存在");
        permissionService.requireSubmit(task.getProjectId(), currentUser);
        requirePrimaryAssignee(task, currentUser);
        requireExpected(task.getVersion(), request.getExpectedVersion(), "任务版本已变化，请刷新后重试");
        if (!"PENDING".equals(task.getStatus())) throw conflict("任务已提交、取消或状态已变化");
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(task.getAvailableTime())) throw BusinessException.forbidden("任务尚未进入提前执行窗口");
        validateQr(task, currentUser);
        List<GeneralInspectionTaskItem> items = listTaskItems(id);
        GeneralInspectionTemplateVersion templateVersion = templateVersionMapper.selectById(task.getTemplateVersionId());
        if (templateVersion == null) throw conflict("任务模板快照不存在");
        Submission submission = validateSubmission(task, items, templateVersion, request.getOverallPhotoFileIds(),
                request.getRemark(), request.getItems());

        applySubmission(task, items, submission, request.getOverallPhotoFileIds(), request.getRemark(),
                request.getPublicRemark(), currentUser, now, false);
        bindTaskFiles(task, submission.allPhotoIds(), currentUser);
        createRectifications(task, items, request.getItems(), currentUser);
        notifyAfterSubmit(task);
        eventPublisher.publish(new GeneralInspectionDomainEvent("TASK_SUBMITTED", task.getProjectId(), "TASK",
                task.getId(), now, Map.of("late", now.isAfter(task.getDueTime()), "abnormalCount", submission.abnormalCount())));
        return toTaskVO(task, true, currentUser);
    }

    @Transactional
    public List<Long> cancelTasks(GeneralInspectionBatchCancelRequest request, SysUser currentUser) {
        String reason = trimRequired(request.getReason(), "取消原因", 500);
        List<Long> ids = request.getTaskIds().stream().filter(Objects::nonNull).distinct().toList();
        if (ids.size() != request.getTaskIds().size()) throw new BusinessException("取消任务列表包含重复或无效标识");
        for (Long id : ids) {
            GeneralInspectionTask task = taskMapper.selectByIdForUpdate(id);
            if (!isEdgeTask(task)) throw BusinessException.notFound("临边巡检任务不存在：" + id);
            permissionService.requireManage(task.getProjectId(), currentUser);
            if (!"PENDING".equals(task.getStatus())) throw conflict("只能取消尚未提交的任务：" + id);
            task.setStatus("CANCELLED");
            task.setCancelReason(reason);
            task.setCancelledById(currentUser.getId());
            task.setCancelledByName(userName(currentUser));
            task.setCancelledTime(LocalDateTime.now());
            task.setVersion(value(task.getVersion(), 0) + 1);
            requireOne(taskMapper.updateById(task), "任务取消");
            record(task.getProjectId(), "TASK", id, "CANCEL", currentUser, "PENDING", "CANCELLED", reason, null, null);
        }
        return ids;
    }

    @Transactional
    public GeneralInspectionTaskVO reassignTask(Long id, GeneralInspectionTaskActionRequest request,
                                                 SysUser currentUser) {
        GeneralInspectionTask task = taskMapper.selectByIdForUpdate(id);
        if (!isEdgeTask(task)) throw BusinessException.notFound("临边巡检任务不存在");
        permissionService.requireManage(task.getProjectId(), currentUser);
        requireExpected(task.getVersion(), request.getExpectedVersion(), "任务版本已变化，请刷新后重试");
        requireTaskReassignable(task.getStatus());
        if (request.getAssigneeId() == null && request.getReviewerId() == null) throw new BusinessException("至少指定新的主巡检人或主复查人");
        String before = snapshot(task);
        if (request.getAssigneeId() != null) {
            SysUser assignee = requireActivePermissionUser(task.getProjectId(), request.getAssigneeId(),
                    InspectionPermissionCodes.EDGE_INSPECTION_SUBMIT, "主巡检人");
            task.setAssigneeId(assignee.getId());
            task.setAssigneeName(userName(assignee));
        }
        if (request.getReviewerId() != null) {
            SysUser reviewer = requireActivePermissionUser(task.getProjectId(), request.getReviewerId(),
                    InspectionPermissionCodes.EDGE_INSPECTION_REVIEW, "主复查人");
            task.setReviewerId(reviewer.getId());
            task.setReviewerName(userName(reviewer));
            List<GeneralInspectionRectification> rectifications = listRectificationsByTask(task.getId());
            for (GeneralInspectionRectification rectification : rectifications) {
                if (OPEN_RECTIFICATION_STATES.contains(rectification.getStatus())) {
                    rectification.setReviewerId(reviewer.getId());
                    rectification.setReviewerName(userName(reviewer));
                    rectification.setVersion(value(rectification.getVersion(), 0) + 1);
                    requireOne(rectificationMapper.updateById(rectification), "整改复查人改派");
                }
            }
        }
        task.setVersion(value(task.getVersion(), 0) + 1);
        requireOne(taskMapper.updateById(task), "任务改派");
        record(task.getProjectId(), "TASK", id, "REASSIGN", currentUser, task.getStatus(), task.getStatus(),
                trimRequired(request.getReason(), "改派原因", 500), before, snapshot(task));
        return toTaskVO(task, true, currentUser);
    }

    @Transactional
    public GeneralInspectionTaskVO correctTask(Long id, GeneralInspectionCorrectionRequest request,
                                                SysUser currentUser) {
        GeneralInspectionTask task = taskMapper.selectByIdForUpdate(id);
        if (task == null) throw BusinessException.notFound("巡检任务不存在");
        permissionService.requireManage(task.getProjectId(), currentUser);
        requireExpected(task.getVersion(), request.getExpectedVersion(), "任务版本已变化，请刷新后重试");
        if (!SUBMITTED_TASK_STATES.contains(task.getStatus())) throw conflict("仅已提交巡检记录允许纠错");
        List<GeneralInspectionRectification> rectifications = listRectificationsByTask(id);
        if (rectifications.stream().anyMatch(rect -> Set.of("COMPLETED", "REJECTED", "CLOSED").contains(rect.getStatus()))) {
            throw conflict("已有整改反馈，不能修改会影响整改流程的检查结果；请追加说明或作废后重检");
        }
        List<GeneralInspectionTaskItem> items = listTaskItems(id);
        GeneralInspectionTemplateVersion templateVersion = templateVersionMapper.selectById(task.getTemplateVersionId());
        Submission submission = validateSubmission(task, items, templateVersion, request.getOverallPhotoFileIds(),
                request.getRemark(), request.getItems());
        String before = snapshot(Map.of("task", task, "items", items, "rectifications", rectifications));
        for (GeneralInspectionRectification rectification : rectifications) {
            rectification.setStatus("VOIDED");
            rectification.setReviewComment("管理纠错作废：" + trimRequired(request.getReason(), "纠错原因", 1000));
            rectification.setReviewTime(LocalDateTime.now());
            rectification.setVersion(value(rectification.getVersion(), 0) + 1);
            requireOne(rectificationMapper.updateById(rectification), "原整改作废");
        }
        Set<Long> existingPhotos = collectExistingPhotoIds(task, items);
        applySubmission(task, items, submission, request.getOverallPhotoFileIds(), request.getRemark(),
                request.getPublicRemark(), currentUser, LocalDateTime.now(), true);
        List<Long> newPhotos = submission.allPhotoIds().stream().filter(idValue -> !existingPhotos.contains(idValue)).toList();
        bindTaskFiles(task, newPhotos, currentUser);
        createRectifications(task, items, request.getItems(), currentUser);
        task.setCorrectionNote(appendCorrectionNote(task.getCorrectionNote(), request.getReason()));
        requireOne(taskMapper.updateById(task), "纠错说明保存");
        record(task.getProjectId(), "TASK", id, "CORRECT", currentUser, null, task.getStatus(),
                trimRequired(request.getReason(), "纠错原因", 1000), before,
                snapshot(Map.of("task", task, "items", items)));
        return toTaskVO(task, true, currentUser);
    }

    @Transactional
    public GeneralInspectionTaskVO appendCorrectionNote(Long id, GeneralInspectionCorrectionNoteRequest request,
                                                         SysUser currentUser) {
        GeneralInspectionTask task = taskMapper.selectByIdForUpdate(id);
        if (task == null) throw BusinessException.notFound("巡检任务不存在");
        permissionService.requireManage(task.getProjectId(), currentUser);
        requireExpected(task.getVersion(), request.getExpectedVersion(), "任务版本已变化，请刷新后重试");
        if (!SUBMITTED_TASK_STATES.contains(task.getStatus())) throw conflict("仅已提交巡检记录允许追加更正说明");
        String reason = trimRequired(request.getReason(), "更正说明", 1000);
        String before = snapshot(task);
        if (request.getRemark() != null) task.setRemark(trim(request.getRemark(), 1000));
        if (request.getPublicRemark() != null) task.setPublicRemark(trim(request.getPublicRemark(), 500));
        task.setCorrectionNote(appendCorrectionNote(task.getCorrectionNote(), reason));
        task.setVersion(value(task.getVersion(), 0) + 1);
        requireOne(taskMapper.updateById(task), "更正说明保存");
        record(task.getProjectId(), "TASK", id, "CORRECTION_NOTE", currentUser,
                task.getStatus(), task.getStatus(), reason, before, snapshot(task));
        return toTaskVO(task, true, currentUser);
    }

    @Transactional
    public GeneralInspectionTaskVO voidAndReinspect(Long id, GeneralInspectionTaskActionRequest request,
                                                     SysUser currentUser) {
        GeneralInspectionTask source = taskMapper.selectByIdForUpdate(id);
        if (source == null) throw BusinessException.notFound("巡检任务不存在");
        permissionService.requireManage(source.getProjectId(), currentUser);
        requireExpected(source.getVersion(), request.getExpectedVersion(), "任务版本已变化，请刷新后重试");
        if (!SUBMITTED_TASK_STATES.contains(source.getStatus())) throw conflict("仅已提交巡检记录允许作废后重检");
        String reason = trimRequired(request.getReason(), "作废重检原因", 1000);
        String sourceStatus = source.getStatus();
        String before = snapshot(Map.of("task", source, "items", listTaskItems(id),
                "rectifications", listRectificationsByTask(id)));
        LocalDateTime now = LocalDateTime.now();
        for (GeneralInspectionRectification rectification : listRectificationsByTask(id)) {
            if ("VOIDED".equals(rectification.getStatus())) continue;
            String from = rectification.getStatus();
            rectification.setStatus("VOIDED");
            rectification.setReviewComment(appendCorrectionNote(rectification.getReviewComment(), "原巡检作废：" + reason));
            rectification.setReviewTime(now);
            rectification.setVersion(value(rectification.getVersion(), 0) + 1);
            requireOne(rectificationMapper.updateById(rectification), "关联整改作废");
            record(source.getProjectId(), "RECTIFICATION", rectification.getId(), "VOID_BY_REINSPECTION",
                    currentUser, from, "VOIDED", reason, null, null);
        }
        source.setStatus("CANCELLED");
        source.setCancelReason("原巡检作废重检：" + reason);
        source.setCancelledById(currentUser.getId());
        source.setCancelledByName(userName(currentUser));
        source.setCancelledTime(now);
        source.setCorrectionNote(appendCorrectionNote(source.getCorrectionNote(), reason));
        source.setVersion(value(source.getVersion(), 0) + 1);
        requireOne(taskMapper.updateById(source), "原巡检记录作废");

        GeneralInspectionTask replacement = replacementTask(source);
        GeneralInspectionPoint currentPoint = pointMapper.selectById(source.getPointId());
        if (currentPoint == null || !"ACTIVE".equals(currentPoint.getStatus())) {
            throw conflict("点位已失效，不能生成重检任务");
        }
        replacement.setQrRequired(Integer.valueOf(1).equals(currentPoint.getQrEnabled()) ? 1 : 0);
        replacement.setQrVersion(value(currentPoint.getQrVersion(), 0));
        replacement.setRevisionNo(value(source.getRevisionNo(), 0) + 1);
        replacement.setReplacesTaskId(source.getId());
        replacement.setStatus("PENDING");
        replacement.setVersion(0);
        try {
            requireOne(taskMapper.insert(replacement), "重检任务生成");
        } catch (DuplicateKeyException ex) {
            throw conflict("重检任务已生成，请刷新后重试");
        }
        for (GeneralInspectionTaskItem oldItem : listTaskItems(id)) {
            GeneralInspectionTaskItem newItem = new GeneralInspectionTaskItem();
            newItem.setTaskId(replacement.getId());
            newItem.setTemplateItemId(oldItem.getTemplateItemId());
            newItem.setItemKey(oldItem.getItemKey());
            newItem.setItemName(oldItem.getItemName());
            newItem.setGuidance(oldItem.getGuidance());
            newItem.setStandardReference(oldItem.getStandardReference());
            newItem.setAllowNa(oldItem.getAllowNa());
            newItem.setNormalPhotoMin(oldItem.getNormalPhotoMin());
            newItem.setAbnormalPhotoMin(oldItem.getAbnormalPhotoMin());
            newItem.setPhotoMax(oldItem.getPhotoMax());
            newItem.setNormalDescriptionRequired(oldItem.getNormalDescriptionRequired());
            newItem.setAbnormalDescriptionRequired(oldItem.getAbnormalDescriptionRequired());
            newItem.setSortOrder(oldItem.getSortOrder());
            requireOne(taskItemMapper.insert(newItem), "重检检查项快照复制");
        }
        record(source.getProjectId(), "TASK", source.getId(), "VOID_FOR_REINSPECTION", currentUser,
                sourceStatus, "CANCELLED", reason, before, snapshot(source));
        record(replacement.getProjectId(), "TASK", replacement.getId(), "REINSPECTION_CREATE", currentUser,
                null, "PENDING", "替代任务 " + source.getId(), null, snapshot(replacement));
        return toTaskVO(replacement, true, currentUser);
    }

    public List<GeneralInspectionRectificationVO> listRectifications(Long projectId, String status, String scope,
                                                                     SysUser currentUser) {
        requireUser(currentUser);
        LambdaQueryWrapper<GeneralInspectionRectification> query = new LambdaQueryWrapper<>();
        if (projectId != null) {
            permissionService.requireEnabled(projectId, currentUser);
            query.eq(GeneralInspectionRectification::getProjectId, projectId);
        } else {
            List<Long> enabledProjectIds = settingMapper.selectList(
                            new LambdaQueryWrapper<GeneralInspectionProjectSetting>()
                                    .eq(GeneralInspectionProjectSetting::getEnabled, 1))
                    .stream().map(GeneralInspectionProjectSetting::getProjectId).toList();
            if (enabledProjectIds.isEmpty()) return List.of();
            query.in(GeneralInspectionRectification::getProjectId, enabledProjectIds);
        }
        String normalizedScope = StringUtils.hasText(scope) ? scope.trim().toUpperCase() : "MINE";
        if ("RECTIFY".equals(normalizedScope)) query.eq(GeneralInspectionRectification::getAssigneeId, currentUser.getId());
        else if ("REVIEW".equals(normalizedScope)) query.eq(GeneralInspectionRectification::getReviewerId, currentUser.getId());
        else if ("ALL".equals(normalizedScope)) {
            if (projectId == null) throw new BusinessException("管理查询必须指定项目");
            permissionService.requireRecordView(projectId, currentUser);
        } else if (!"MINE".equals(normalizedScope)) {
            throw new BusinessException("整改范围仅支持 MINE、RECTIFY、REVIEW 或 ALL");
        }
        if (StringUtils.hasText(status)) query.eq(GeneralInspectionRectification::getStatus, status.trim().toUpperCase());
        return rectificationMapper.selectList(query.orderByAsc(GeneralInspectionRectification::getDeadline)
                        .orderByAsc(GeneralInspectionRectification::getId)).stream()
                .filter(rect -> projectId != null || permissionService.hasActiveProjectAccess(rect.getProjectId(), currentUser.getId()))
                .filter(rect -> matchesRectificationScope(rect, normalizedScope, currentUser))
                .map(rect -> toRectificationVO(rect, currentUser)).toList();
    }

    public GeneralInspectionRectificationVO getRectification(Long id, SysUser currentUser) {
        GeneralInspectionRectification rectification = requireRectification(id);
        permissionService.requireEnabled(rectification.getProjectId(), currentUser);
        if (!canReadRectification(rectification, currentUser)) throw BusinessException.forbidden("无该整改任务访问权限");
        return toRectificationVO(rectification, currentUser);
    }

    @Transactional
    public GeneralInspectionRectificationVO assignRectification(Long id, GeneralInspectionRectificationRequest request,
                                                                 SysUser currentUser) {
        GeneralInspectionRectification rectification = rectificationMapper.selectByIdForUpdate(id);
        if (rectification == null) throw BusinessException.notFound("整改任务不存在");
        permissionService.requireManage(rectification.getProjectId(), currentUser);
        requireExpected(rectification.getVersion(), request.getExpectedVersion(), "整改版本已变化，请刷新后重试");
        if (Set.of("CLOSED", "VOIDED").contains(rectification.getStatus())) throw conflict("已关闭或已作废整改不能改派");
        SysUser assignee = requireActivePermissionUser(rectification.getProjectId(), request.getAssigneeId(),
                InspectionPermissionCodes.EDGE_INSPECTION_RECTIFY, "整改人");
        if (request.getDeadline() == null || request.getDeadline().isBefore(LocalDate.now())) throw new BusinessException("整改期限不能早于今天");
        String from = rectification.getStatus();
        rectification.setAssigneeId(assignee.getId());
        rectification.setAssigneeName(userName(assignee));
        rectification.setDeadline(request.getDeadline());
        if (StringUtils.hasText(request.getRequirement())) rectification.setRequirement(trim(request.getRequirement(), 1000));
        rectification.setStatus("PENDING");
        rectification.setVersion(value(rectification.getVersion(), 0) + 1);
        requireOne(rectificationMapper.updateById(rectification), "整改任务指派");
        record(rectification.getProjectId(), "RECTIFICATION", id, "ASSIGN", currentUser, from, "PENDING",
                trim(request.getComment(), 1000), null, null);
        notifyRectifier(rectification);
        return toRectificationVO(rectification, currentUser);
    }

    @Transactional
    public GeneralInspectionRectificationVO completeRectification(Long id,
                                                                   GeneralInspectionRectificationRequest request,
                                                                   SysUser currentUser) {
        GeneralInspectionRectification rectification = rectificationMapper.selectByIdForUpdate(id);
        if (rectification == null) throw BusinessException.notFound("整改任务不存在");
        permissionService.requireRectify(rectification.getProjectId(), currentUser);
        requireExpected(rectification.getVersion(), request.getExpectedVersion(), "整改版本已变化，请刷新后重试");
        if (!Objects.equals(rectification.getAssigneeId(), currentUser.getId())) throw BusinessException.forbidden("仅当前整改人可提交反馈");
        if (!Set.of("PENDING", "REJECTED").contains(rectification.getStatus())) throw conflict("当前整改状态不能提交反馈");
        String feedback = trimRequired(request.getComment(), "整改说明", 1000);
        if (request.getPhotoFileIds() == null || request.getPhotoFileIds().isEmpty()) throw new BusinessException("整改至少上传1张照片");
        List<Long> requestedPhotos = distinctIds(request.getPhotoFileIds(), "整改照片");
        Set<Long> existingPhotos = new HashSet<>(parseIds(rectification.getRectificationPhotoFileIds()));
        List<Long> newPhotos = requestedPhotos.stream().filter(fileId -> !existingPhotos.contains(fileId)).toList();
        String from = rectification.getStatus();
        rectification.setFeedback(feedback);
        rectification.setRectificationPhotoFileIds(joinIds(requestedPhotos));
        rectification.setCompletedTime(LocalDateTime.now());
        rectification.setStatus("COMPLETED");
        rectification.setVersion(value(rectification.getVersion(), 0) + 1);
        requireOne(rectificationMapper.updateById(rectification), "整改反馈提交");
        fileResourceService.validateAndBind(currentUser, rectification.getProjectId(), newPhotos,
                "EDGE_INSPECTION_RECTIFICATION_PENDING", "EDGE_INSPECTION_RECTIFICATION", rectification.getId());
        record(rectification.getProjectId(), "RECTIFICATION", id, "COMPLETE", currentUser, from, "COMPLETED", feedback, null, null);
        notifyReviewer(rectification);
        eventPublisher.publish(new GeneralInspectionDomainEvent("RECTIFICATION_REVIEW_PENDING",
                rectification.getProjectId(), "RECTIFICATION", id, LocalDateTime.now(),
                Map.of("version", value(rectification.getVersion(), 0))));
        return toRectificationVO(rectification, currentUser);
    }

    @Transactional
    public GeneralInspectionRectificationVO reviewRectification(Long id, GeneralInspectionRectificationRequest request,
                                                                 boolean approve, SysUser currentUser) {
        GeneralInspectionRectification rectification = rectificationMapper.selectByIdForUpdate(id);
        if (rectification == null) throw BusinessException.notFound("整改任务不存在");
        permissionService.requireReview(rectification.getProjectId(), currentUser);
        requireExpected(rectification.getVersion(), request.getExpectedVersion(), "整改版本已变化，请刷新后重试");
        if (!"COMPLETED".equals(rectification.getStatus())) throw conflict("仅待复查整改可以关闭或退回");
        GeneralInspectionTask task = taskMapper.selectById(rectification.getTaskId());
        if (!isReviewer(task, rectification, currentUser.getId())) {
            throw BusinessException.forbidden("仅计划主复查人或备选复查人可执行复查；平台管理员也必须被明确指派");
        }
        String comment = trimRequired(request.getComment(), approve ? "复查意见" : "退回原因", 1000);
        rectification.setReviewComment(comment);
        rectification.setReviewTime(LocalDateTime.now());
        rectification.setStatus(approve ? "CLOSED" : "REJECTED");
        if (approve) {
            rectification.setCloseTime(LocalDateTime.now());
        } else {
            rectification.setRejectCount(value(rectification.getRejectCount(), 0) + 1);
        }
        rectification.setVersion(value(rectification.getVersion(), 0) + 1);
        requireOne(rectificationMapper.updateById(rectification), approve ? "整改复查关闭" : "整改复查退回");
        record(rectification.getProjectId(), "RECTIFICATION", id, approve ? "CLOSE" : "REJECT", currentUser,
                "COMPLETED", rectification.getStatus(), comment, null, null);
        if (approve) closeTaskWhenDone(rectification.getTaskId());
        else notifyRectifier(rectification);
        return toRectificationVO(rectification, currentUser);
    }

    public List<EdgeInspectionRectificationSheetVO> listEdgeRectificationSheets(
            Long projectId, String status, String scope, SysUser currentUser) {
        requireUser(currentUser);
        String normalizedScope = StringUtils.hasText(scope) ? scope.trim().toUpperCase() : "MINE";
        if (!Set.of("MINE", "RECTIFY", "REVIEW", "ALL").contains(normalizedScope)) {
            throw new BusinessException("整改范围仅支持 MINE、RECTIFY、REVIEW 或 ALL");
        }
        LambdaQueryWrapper<GeneralInspectionTask> taskQuery = new LambdaQueryWrapper<GeneralInspectionTask>()
                .isNotNull(GeneralInspectionTask::getPointTypeCode)
                .gt(GeneralInspectionTask::getAbnormalCount, 0);
        if (projectId != null) {
            permissionService.requireEnabled(projectId, currentUser);
            switch (normalizedScope) {
                case "ALL" -> permissionService.requireRecordView(projectId, currentUser);
                case "RECTIFY" -> permissionService.requireRectify(projectId, currentUser);
                case "REVIEW" -> permissionService.requireReview(projectId, currentUser);
                default -> {
                    // MINE is authorized per assigned sheet below.
                }
            }
            taskQuery.eq(GeneralInspectionTask::getProjectId, projectId);
        } else {
            if ("ALL".equals(normalizedScope)) throw new BusinessException("管理查询必须指定项目");
            List<Long> enabledProjects = settingMapper.selectList(
                            new LambdaQueryWrapper<GeneralInspectionProjectSetting>()
                                    .eq(GeneralInspectionProjectSetting::getEnabled, 1))
                    .stream().map(GeneralInspectionProjectSetting::getProjectId).toList();
            if (enabledProjects.isEmpty()) return List.of();
            taskQuery.in(GeneralInspectionTask::getProjectId, enabledProjects);
        }
        List<GeneralInspectionTask> tasks = taskMapper.selectList(taskQuery
                .orderByAsc(GeneralInspectionTask::getDueTime)
                .orderByAsc(GeneralInspectionTask::getId));
        List<EdgeInspectionRectificationSheetVO> result = new ArrayList<>();
        for (GeneralInspectionTask task : tasks) {
            if (!permissionService.hasActiveProjectAccess(task.getProjectId(), currentUser.getId())) continue;
            List<GeneralInspectionRectification> rectifications = listRectificationsByTask(task.getId()).stream()
                    .filter(rectification -> !"VOIDED".equals(rectification.getStatus())).toList();
            if (rectifications.isEmpty() || !matchesSheetScope(rectifications, normalizedScope, currentUser)) continue;
            EdgeInspectionRectificationSheetVO sheet = toEdgeSheet(task, rectifications, currentUser);
            if (StringUtils.hasText(status)
                    && !sheet.getStatus().equals(status.trim().toUpperCase())) continue;
            result.add(sheet);
        }
        return result;
    }

    public EdgeInspectionRectificationSheetVO getEdgeRectificationSheet(Long taskId, SysUser currentUser) {
        GeneralInspectionTask task = requireTask(taskId);
        if (!isEdgeTask(task)) throw BusinessException.notFound("临边巡检整改单不存在");
        permissionService.requireEnabled(task.getProjectId(), currentUser);
        List<GeneralInspectionRectification> rectifications = listRectificationsByTask(taskId).stream()
                .filter(rectification -> !"VOIDED".equals(rectification.getStatus())).toList();
        if (rectifications.isEmpty()) throw BusinessException.notFound("临边巡检整改单不存在");
        boolean directlyAssigned = rectifications.stream().anyMatch(rectification ->
                Objects.equals(rectification.getAssigneeId(), currentUser.getId())
                        && permissionService.hasInspectionPermission(task.getProjectId(), currentUser.getId(),
                        InspectionPermissionCodes.EDGE_INSPECTION_RECTIFY)
                        || Objects.equals(rectification.getReviewerId(), currentUser.getId())
                        && permissionService.hasInspectionPermission(task.getProjectId(), currentUser.getId(),
                        InspectionPermissionCodes.EDGE_INSPECTION_REVIEW));
        if (!directlyAssigned && !canReadTask(task, currentUser, false)) {
            throw BusinessException.forbidden("无该临边巡检整改单访问权限");
        }
        return toEdgeSheet(task, rectifications, currentUser);
    }

    @Transactional
    public EdgeInspectionRectificationSheetVO completeEdgeRectificationSheet(
            Long taskId, EdgeInspectionRectificationCompleteRequest request, SysUser currentUser) {
        GeneralInspectionTask task = taskMapper.selectByIdForUpdate(taskId);
        if (task == null || !isEdgeTask(task)) throw BusinessException.notFound("临边巡检整改单不存在");
        permissionService.requireRectify(task.getProjectId(), currentUser);
        requireExpected(task.getVersion(), request.getExpectedVersion(), "整改单版本已变化，请刷新后重试");
        List<GeneralInspectionRectification> rectifications = rectificationMapper.selectByTaskIdForUpdate(taskId)
                .stream().filter(rectification -> !"VOIDED".equals(rectification.getStatus())).toList();
        if (rectifications.isEmpty()) throw BusinessException.notFound("临边巡检整改单不存在");
        if (rectifications.stream().anyMatch(rectification ->
                !Objects.equals(rectification.getAssigneeId(), currentUser.getId()))) {
            throw BusinessException.forbidden("仅整改单当前整改人可整单提交反馈");
        }
        if (rectifications.stream().anyMatch(rectification ->
                !Set.of("PENDING", "REJECTED").contains(rectification.getStatus()))) {
            throw conflict("整改单包含不可提交反馈的异常项，请刷新后重试");
        }
        String fromStatus = sheetStatus(rectifications);
        Map<Long, EdgeInspectionRectificationCompleteRequest.ItemFeedback> feedbacks = request.getItems().stream()
                .collect(Collectors.toMap(EdgeInspectionRectificationCompleteRequest.ItemFeedback::getRectificationId,
                        Function.identity(), (a, b) -> { throw new BusinessException("整改反馈项不能重复"); }));
        Set<Long> expectedIds = rectifications.stream().map(GeneralInspectionRectification::getId)
                .collect(Collectors.toSet());
        if (!feedbacks.keySet().equals(expectedIds)) throw new BusinessException("必须一次提交整单全部异常项反馈");
        Set<Long> allPhotos = new HashSet<>();
        LocalDateTime now = LocalDateTime.now();
        for (GeneralInspectionRectification rectification : rectifications) {
            EdgeInspectionRectificationCompleteRequest.ItemFeedback feedback = feedbacks.get(rectification.getId());
            requireExpected(rectification.getVersion(), feedback.getExpectedVersion(),
                    rectification.getItemName() + "整改版本已变化，请刷新后重试");
            String feedbackText = trimRequired(feedback.getFeedback(), "整改说明", 1000);
            List<Long> photos = distinctIds(feedback.getPhotoFileIds(), rectification.getItemName() + "整改照片");
            if (photos.isEmpty()) throw new BusinessException(rectification.getItemName() + "至少上传1张整改照片");
            List<Long> existingPhotos = parseIds(rectification.getRectificationPhotoFileIds());
            if (!photos.containsAll(existingPhotos)) {
                throw new BusinessException(rectification.getItemName() + "已提交的整改照片不能移除");
            }
            if (photos.stream().anyMatch(allPhotos::contains)) {
                throw new BusinessException("同一整改照片不能重复用于多个异常项");
            }
            allPhotos.addAll(photos);
            List<Long> newPhotos = photos.stream().filter(photoId -> !existingPhotos.contains(photoId)).toList();
            rectification.setFeedback(feedbackText);
            rectification.setRectificationPhotoFileIds(joinIds(photos));
            rectification.setCompletedTime(now);
            rectification.setReviewComment(null);
            rectification.setReviewTime(null);
            rectification.setStatus("COMPLETED");
            rectification.setVersion(value(rectification.getVersion(), 0) + 1);
            requireOne(rectificationMapper.updateById(rectification), "整单整改反馈保存");
            if (!newPhotos.isEmpty()) {
                fileResourceService.validateAndBind(currentUser, task.getProjectId(), newPhotos,
                        "EDGE_INSPECTION_RECTIFICATION_PENDING", "EDGE_INSPECTION_RECTIFICATION", rectification.getId());
            }
        }
        task.setVersion(value(task.getVersion(), 0) + 1);
        requireOne(taskMapper.updateById(task), "整改单版本更新");
        record(task.getProjectId(), "RECTIFICATION_SHEET", taskId, "COMPLETE", currentUser,
                fromStatus, "COMPLETED", "整单整改反馈已提交", null, null);
        GeneralInspectionRectification first = rectifications.get(0);
        notifyReviewer(first);
        eventPublisher.publish(new GeneralInspectionDomainEvent("RECTIFICATION_REVIEW_PENDING",
                task.getProjectId(), "RECTIFICATION_SHEET", taskId, now,
                Map.of("version", value(task.getVersion(), 0))));
        return toEdgeSheet(task, rectifications, currentUser);
    }

    @Transactional
    public EdgeInspectionRectificationSheetVO reviewEdgeRectificationSheet(
            Long taskId, EdgeInspectionRectificationReviewRequest request, boolean approve, SysUser currentUser) {
        GeneralInspectionTask task = taskMapper.selectByIdForUpdate(taskId);
        if (task == null || !isEdgeTask(task)) throw BusinessException.notFound("临边巡检整改单不存在");
        permissionService.requireReview(task.getProjectId(), currentUser);
        requireExpected(task.getVersion(), request.getExpectedVersion(), "整改单版本已变化，请刷新后重试");
        if (!Objects.equals(task.getReviewerId(), currentUser.getId())) {
            throw BusinessException.forbidden("仅明确指派的整单复查人可以复查；平台管理员也不能代审");
        }
        List<GeneralInspectionRectification> rectifications = rectificationMapper.selectByTaskIdForUpdate(taskId)
                .stream().filter(rectification -> !"VOIDED".equals(rectification.getStatus())).toList();
        if (rectifications.isEmpty()) throw BusinessException.notFound("临边巡检整改单不存在");
        if (rectifications.stream().anyMatch(rectification -> !"COMPLETED".equals(rectification.getStatus()))) {
            throw conflict("只有全部异常项均已整改反馈后才能整单复查");
        }
        String comment = approve ? trim(request.getComment(), 1000)
                : trimRequired(request.getComment(), "退回原因", 1000);
        LocalDateTime now = LocalDateTime.now();
        String sheetFromStatus = sheetStatus(rectifications);
        boolean becameUnassigned = false;
        for (GeneralInspectionRectification rectification : rectifications) {
            if (!Objects.equals(rectification.getReviewerId(), currentUser.getId())) {
                throw BusinessException.forbidden("整改单复查人已变化，请刷新后重试");
            }
            rectification.setReviewComment(comment);
            rectification.setReviewTime(now);
            if (approve) {
                rectification.setStatus("CLOSED");
                rectification.setCloseTime(now);
            } else {
                SysUser activeRectifier = validRectifier(task.getProjectId(), rectification.getAssigneeId());
                if (activeRectifier == null) {
                    rectification.setAssigneeId(null);
                    rectification.setAssigneeName(null);
                    rectification.setStatus("UNASSIGNED");
                    becameUnassigned = true;
                } else {
                    rectification.setAssigneeName(userName(activeRectifier));
                    rectification.setStatus("REJECTED");
                }
                rectification.setRejectCount(value(rectification.getRejectCount(), 0) + 1);
            }
            rectification.setVersion(value(rectification.getVersion(), 0) + 1);
            requireOne(rectificationMapper.updateById(rectification), approve ? "整单复查关闭" : "整单复查退回");
        }
        task.setStatus(approve ? "CLOSED" : "RECTIFICATION_PENDING");
        if (becameUnassigned) {
            task.setDefaultRectifierId(null);
            task.setDefaultRectifierName(null);
        }
        task.setVersion(value(task.getVersion(), 0) + 1);
        requireOne(taskMapper.updateById(task), "巡检任务整单复查状态更新");
        record(task.getProjectId(), "RECTIFICATION_SHEET", taskId, approve ? "CLOSE" : "REJECT", currentUser,
                sheetFromStatus, sheetStatus(rectifications), comment, null, null);
        if (!approve && becameUnassigned) {
            eventPublisher.publish(new GeneralInspectionDomainEvent("RECTIFICATION_UNASSIGNED",
                    task.getProjectId(), "RECTIFICATION_SHEET", taskId, now,
                    Map.of("version", value(task.getVersion(), 0), "reason", "RECTIFIER_PERMISSION_INVALID")));
        } else if (!approve) {
            notifyRectifier(rectifications.get(0));
        }
        return toEdgeSheet(task, rectifications, currentUser);
    }

    @Transactional
    public EdgeInspectionRectificationSheetVO reassignEdgeRectificationSheet(
            Long taskId, EdgeInspectionRectificationReassignRequest request, SysUser currentUser) {
        GeneralInspectionTask task = taskMapper.selectByIdForUpdate(taskId);
        if (task == null || !isEdgeTask(task)) throw BusinessException.notFound("临边巡检整改单不存在");
        permissionService.requireManage(task.getProjectId(), currentUser);
        requireExpected(task.getVersion(), request.getExpectedVersion(), "整改单版本已变化，请刷新后重试");
        if (request.getAssigneeId() == null && request.getReviewerId() == null) {
            throw new BusinessException("至少指定新的整改人或复查人");
        }
        SysUser assignee = request.getAssigneeId() == null ? null
                : requireActivePermissionUser(task.getProjectId(), request.getAssigneeId(),
                InspectionPermissionCodes.EDGE_INSPECTION_RECTIFY, "整改人");
        SysUser reviewer = request.getReviewerId() == null ? null
                : requireActivePermissionUser(task.getProjectId(), request.getReviewerId(),
                InspectionPermissionCodes.EDGE_INSPECTION_REVIEW, "复查人");
        if (request.getDeadline() != null && request.getDeadline().isBefore(LocalDate.now())) {
            throw new BusinessException("整改期限不能早于今天");
        }
        List<GeneralInspectionRectification> rectifications = rectificationMapper.selectByTaskIdForUpdate(taskId)
                .stream().filter(rectification -> !Set.of("CLOSED", "VOIDED").contains(rectification.getStatus())).toList();
        if (rectifications.isEmpty()) throw conflict("已关闭整改单不能改派");
        String fromStatus = sheetStatus(rectifications);
        String beforeSnapshot = snapshot(edgeReassignmentSnapshot(task, rectifications));
        for (GeneralInspectionRectification rectification : rectifications) {
            if (assignee != null) {
                rectification.setAssigneeId(assignee.getId());
                rectification.setAssigneeName(userName(assignee));
                if ("UNASSIGNED".equals(rectification.getStatus())) rectification.setStatus("PENDING");
            }
            if (reviewer != null) {
                rectification.setReviewerId(reviewer.getId());
                rectification.setReviewerName(userName(reviewer));
            }
            if (request.getDeadline() != null) rectification.setDeadline(request.getDeadline());
            rectification.setVersion(value(rectification.getVersion(), 0) + 1);
            requireOne(rectificationMapper.updateById(rectification), "整改单改派");
        }
        if (assignee != null) {
            task.setDefaultRectifierId(assignee.getId());
            task.setDefaultRectifierName(userName(assignee));
        }
        if (reviewer != null) {
            task.setReviewerId(reviewer.getId());
            task.setReviewerName(userName(reviewer));
        }
        task.setVersion(value(task.getVersion(), 0) + 1);
        requireOne(taskMapper.updateById(task), "整改单改派任务快照更新");
        String toStatus = sheetStatus(rectifications);
        record(task.getProjectId(), "RECTIFICATION_SHEET", taskId, "REASSIGN", currentUser,
                fromStatus, toStatus, trimRequired(request.getReason(), "改派原因", 500),
                beforeSnapshot, snapshot(edgeReassignmentSnapshot(task, rectifications)));
        if (assignee != null && rectifications.stream().anyMatch(rectification ->
                Set.of("PENDING", "REJECTED").contains(rectification.getStatus()))) {
            notifyRectifier(rectifications.get(0));
        }
        return toEdgeSheet(task, rectifications, currentUser);
    }

    private boolean matchesSheetScope(List<GeneralInspectionRectification> rectifications, String scope,
                                      SysUser currentUser) {
        if ("ALL".equals(scope)) return true;
        if ("RECTIFY".equals(scope)) {
            return permissionService.hasInspectionPermission(rectifications.get(0).getProjectId(), currentUser.getId(),
                    InspectionPermissionCodes.EDGE_INSPECTION_RECTIFY)
                    && rectifications.stream().anyMatch(rectification ->
                    Objects.equals(rectification.getAssigneeId(), currentUser.getId()));
        }
        if ("REVIEW".equals(scope)) {
            return permissionService.hasInspectionPermission(rectifications.get(0).getProjectId(), currentUser.getId(),
                    InspectionPermissionCodes.EDGE_INSPECTION_REVIEW)
                    && rectifications.stream().anyMatch(rectification ->
                    Objects.equals(rectification.getReviewerId(), currentUser.getId()));
        }
        return rectifications.stream().anyMatch(rectification ->
                Objects.equals(rectification.getAssigneeId(), currentUser.getId())
                        && permissionService.hasInspectionPermission(rectification.getProjectId(), currentUser.getId(),
                        InspectionPermissionCodes.EDGE_INSPECTION_RECTIFY)
                        || Objects.equals(rectification.getReviewerId(), currentUser.getId())
                        && permissionService.hasInspectionPermission(rectification.getProjectId(), currentUser.getId(),
                        InspectionPermissionCodes.EDGE_INSPECTION_REVIEW));
    }

    private EdgeInspectionRectificationSheetVO toEdgeSheet(GeneralInspectionTask task,
                                                             List<GeneralInspectionRectification> rectifications,
                                                             SysUser user) {
        EdgeInspectionRectificationSheetVO vo = new EdgeInspectionRectificationSheetVO();
        vo.setTaskId(task.getId());
        vo.setProjectId(task.getProjectId());
        vo.setPointId(task.getPointId());
        vo.setPointCode(task.getPointCode());
        vo.setPointName(task.getPointName());
        vo.setPointTypeCode(task.getPointTypeCode());
        vo.setPointTypeName(task.getPointTypeName());
        vo.setBuildingName(task.getBuildingName());
        vo.setFloorName(task.getFloorName());
        vo.setLocationDesc(task.getLocationDesc());
        vo.setOccurrenceDate(task.getOccurrenceDate());
        vo.setStatus(sheetStatus(rectifications));
        GeneralInspectionRectification representative = rectifications.get(0);
        vo.setReviewComment(rectifications.stream()
                .map(GeneralInspectionRectification::getReviewComment)
                .filter(StringUtils::hasText)
                .findFirst().orElse(null));
        vo.setAssigneeId(representative.getAssigneeId());
        vo.setAssigneeName(representative.getAssigneeName());
        vo.setReviewerId(representative.getReviewerId());
        vo.setReviewerName(representative.getReviewerName());
        vo.setDeadline(rectifications.stream().map(GeneralInspectionRectification::getDeadline)
                .filter(Objects::nonNull).min(LocalDate::compareTo).orElse(null));
        vo.setVersion(task.getVersion());
        vo.setOverdue(vo.getDeadline() != null && vo.getDeadline().isBefore(LocalDate.now())
                && !"CLOSED".equals(vo.getStatus()));
        vo.setCanRectify(rectifications.stream().allMatch(rectification ->
                Objects.equals(rectification.getAssigneeId(), user.getId()))
                && rectifications.stream().allMatch(rectification ->
                Set.of("PENDING", "REJECTED").contains(rectification.getStatus()))
                && permissionService.hasInspectionPermission(task.getProjectId(), user.getId(),
                InspectionPermissionCodes.EDGE_INSPECTION_RECTIFY));
        vo.setCanReview(Objects.equals(task.getReviewerId(), user.getId())
                && rectifications.stream().allMatch(rectification -> "COMPLETED".equals(rectification.getStatus()))
                && permissionService.hasInspectionPermission(task.getProjectId(), user.getId(),
                InspectionPermissionCodes.EDGE_INSPECTION_REVIEW));
        vo.setCanAssign(permissionService.canManage(task.getProjectId(), user));
        Set<Long> taskItemIds = rectifications.stream()
                .map(GeneralInspectionRectification::getTaskItemId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, GeneralInspectionTaskItem> taskItems = taskItemIds.isEmpty() ? Map.of()
                : taskItemMapper.selectBatchIds(taskItemIds).stream()
                .collect(Collectors.toMap(GeneralInspectionTaskItem::getId, Function.identity()));
        vo.setItems(rectifications.stream().map(rectification -> {
            EdgeInspectionRectificationSheetVO.Item item = new EdgeInspectionRectificationSheetVO.Item();
            item.setRectificationId(rectification.getId());
            item.setTaskItemId(rectification.getTaskItemId());
            item.setItemName(rectification.getItemName());
            item.setProblemDesc(rectification.getProblemDesc());
            item.setRequirement(rectification.getRequirement());
            GeneralInspectionTaskItem taskItem = taskItems.get(rectification.getTaskItemId());
            item.setEvidencePhotoFileIds(taskItem == null ? List.of() : parseIds(taskItem.getPhotoFileIds()));
            item.setStatus(rectification.getStatus());
            item.setFeedback(rectification.getFeedback());
            item.setPhotoFileIds(parseIds(rectification.getRectificationPhotoFileIds()));
            item.setCompletedTime(rectification.getCompletedTime());
            item.setReviewComment(rectification.getReviewComment());
            item.setReviewTime(rectification.getReviewTime());
            item.setRejectCount(rectification.getRejectCount());
            item.setVersion(rectification.getVersion());
            return item;
        }).toList());
        return vo;
    }

    private String sheetStatus(List<GeneralInspectionRectification> rectifications) {
        Set<String> states = rectifications.stream().map(GeneralInspectionRectification::getStatus)
                .collect(Collectors.toSet());
        if (!states.isEmpty() && states.stream().allMatch("CLOSED"::equals)) return "CLOSED";
        if (states.contains("UNASSIGNED")) return "UNASSIGNED";
        if (states.contains("REJECTED")) return "REJECTED";
        if (!states.isEmpty() && states.stream().allMatch("COMPLETED"::equals)) return "COMPLETED";
        return "PENDING";
    }

    private boolean isEdgeTask(GeneralInspectionTask task) {
        return task != null && StringUtils.hasText(task.getPointTypeCode());
    }

    private Submission validateSubmission(GeneralInspectionTask task, List<GeneralInspectionTaskItem> items,
                                            GeneralInspectionTemplateVersion templateVersion,
                                            List<Long> overallPhotos, String remark,
                                            List<GeneralInspectionTaskSubmitRequest.ItemResult> results) {
        if (templateVersion == null) throw conflict("任务模板快照不存在");
        List<Long> normalizedOverall = distinctIds(overallPhotos, "整体照片");
        if (normalizedOverall.size() < value(task.getOverallPhotoMin(), 0)
                || normalizedOverall.size() > value(task.getOverallPhotoMax(), 9)) {
            throw new BusinessException("整体照片数量不符合任务模板规则");
        }
        if (Integer.valueOf(1).equals(task.getOverallRemarkRequired()) && !StringUtils.hasText(remark)) {
            throw new BusinessException("当前模板要求填写总备注");
        }
        if (results == null || results.size() != items.size()) throw new BusinessException("必须提交全部检查项结果");
        Map<Long, GeneralInspectionTaskSubmitRequest.ItemResult> resultMap = results.stream().collect(
                Collectors.toMap(GeneralInspectionTaskSubmitRequest.ItemResult::getTaskItemId, Function.identity(),
                        (a, b) -> { throw new BusinessException("检查项结果不能重复"); }));
        Set<Long> expectedIds = items.stream().map(GeneralInspectionTaskItem::getId).collect(Collectors.toSet());
        if (!expectedIds.equals(resultMap.keySet())) throw new BusinessException("提交的检查项与任务快照不一致");
        List<Long> allPhotos = new ArrayList<>(normalizedOverall);
        int abnormalCount = 0;
        for (GeneralInspectionTaskItem item : items) {
            GeneralInspectionTaskSubmitRequest.ItemResult result = resultMap.get(item.getId());
            String normalized = result.getResult() == null ? "" : result.getResult().trim().toUpperCase();
            if (!RESULTS.contains(normalized)) throw new BusinessException("临边巡检结果仅支持正常或异常");
            if ("ABNORMAL".equals(normalized) && !StringUtils.hasText(result.getDescription())) {
                throw new BusinessException(item.getItemName() + "异常时必须填写说明");
            }
            List<Long> photos = distinctIds(result.getPhotoFileIds(), item.getItemName() + "照片");
            int min = "ABNORMAL".equals(normalized) ? 1 : 0;
            if (photos.size() < min || photos.size() > value(item.getPhotoMax(), 9)) throw new BusinessException(item.getItemName() + "照片数量不符合模板规则");
            allPhotos.addAll(photos);
            if ("ABNORMAL".equals(normalized)) abnormalCount++;
        }
        if (allPhotos.stream().distinct().count() != allPhotos.size()) throw new BusinessException("同一照片不能重复用于多个检查项");
        return new Submission(resultMap, allPhotos, abnormalCount);
    }

    private void applySubmission(GeneralInspectionTask task, List<GeneralInspectionTaskItem> items,
                                 Submission submission, List<Long> overallPhotos, String remark,
                                 String publicRemark, SysUser currentUser, LocalDateTime now, boolean correction) {
        for (GeneralInspectionTaskItem item : items) {
            GeneralInspectionTaskSubmitRequest.ItemResult result = submission.results().get(item.getId());
            item.setResult(result.getResult().trim().toUpperCase());
            item.setDescription(trim(result.getDescription(), 1000));
            item.setPhotoFileIds(joinIds(result.getPhotoFileIds()));
            requireOne(taskItemMapper.updateById(item), "任务检查项结果保存");
        }
        task.setOverallPhotoFileIds(joinIds(overallPhotos));
        task.setRemark(trim(remark, 1000));
        task.setPublicRemark(trim(publicRemark, 500));
        task.setAbnormalCount(submission.abnormalCount());
        task.setStatus(submission.abnormalCount() == 0 ? "COMPLETED" : "RECTIFICATION_PENDING");
        if (!correction) {
            task.setSubmittedById(currentUser.getId());
            task.setSubmittedByName(userName(currentUser));
            task.setSubmittedTime(now);
            task.setOnTime(now.isAfter(task.getDueTime()) ? 0 : 1);
        }
        task.setVersion(value(task.getVersion(), 0) + 1);
        requireOne(taskMapper.updateById(task), correction ? "任务纠错结果保存" : "巡检任务提交");
        record(task.getProjectId(), "TASK", task.getId(), correction ? "CORRECTION_RESULT" : "SUBMIT",
                currentUser, "PENDING", task.getStatus(), correction ? null : task.getRemark(), null, null);
    }

    private void createRectifications(GeneralInspectionTask task, List<GeneralInspectionTaskItem> items,
                                      List<GeneralInspectionTaskSubmitRequest.ItemResult> results,
                                      SysUser currentUser) {
        Map<Long, GeneralInspectionTaskSubmitRequest.ItemResult> resultMap = results.stream().collect(
                Collectors.toMap(GeneralInspectionTaskSubmitRequest.ItemResult::getTaskItemId, Function.identity()));
        for (GeneralInspectionTaskItem item : items) {
            if (!"ABNORMAL".equals(item.getResult())) continue;
            GeneralInspectionTaskSubmitRequest.ItemResult result = resultMap.get(item.getId());
            Long candidateId = task.getDefaultRectifierId();
            SysUser assignee = validRectifier(task.getProjectId(), candidateId);
            LocalDate deadline = LocalDate.now().plusDays(value(task.getDefaultRectificationDays(), 3));
            if (deadline.isBefore(LocalDate.now())) throw new BusinessException("整改期限不能早于今天");
            GeneralInspectionRectification rectification = new GeneralInspectionRectification();
            rectification.setProjectId(task.getProjectId());
            rectification.setTaskId(task.getId());
            rectification.setTaskItemId(item.getId());
            rectification.setPointId(task.getPointId());
            rectification.setPointName(task.getPointName());
            rectification.setItemName(item.getItemName());
            rectification.setProblemDesc(item.getDescription());
            rectification.setRequirement(null);
            rectification.setAssigneeId(assignee == null ? null : assignee.getId());
            rectification.setAssigneeName(assignee == null ? null : userName(assignee));
            rectification.setDeadline(deadline);
            rectification.setReviewerId(validReviewer(task.getProjectId(), task.getReviewerId()) ? task.getReviewerId() : null);
            rectification.setReviewerName(validReviewer(task.getProjectId(), task.getReviewerId()) ? task.getReviewerName() : null);
            rectification.setStatus(assignee == null ? "UNASSIGNED" : "PENDING");
            rectification.setRejectCount(0);
            rectification.setVersion(0);
            requireOne(rectificationMapper.insert(rectification), "逐项整改生成");
            record(task.getProjectId(), "RECTIFICATION", rectification.getId(), "CREATE", currentUser,
                    null, rectification.getStatus(), null, null, null);
            if (assignee == null) {
                eventPublisher.publish(new GeneralInspectionDomainEvent("RECTIFICATION_UNASSIGNED",
                        task.getProjectId(), "RECTIFICATION", rectification.getId(), LocalDateTime.now(),
                        Map.of("version", value(rectification.getVersion(), 0))));
            } else notifyRectifier(rectification);
        }
    }

    private void closeTaskWhenDone(Long taskId) {
        List<GeneralInspectionRectification> all = listRectificationsByTask(taskId);
        if (all.isEmpty() || all.stream().anyMatch(rect -> !Set.of("CLOSED", "VOIDED").contains(rect.getStatus()))) return;
        GeneralInspectionTask task = taskMapper.selectByIdForUpdate(taskId);
        if (task == null || "CLOSED".equals(task.getStatus())) return;
        task.setStatus("CLOSED");
        task.setVersion(value(task.getVersion(), 0) + 1);
        requireOne(taskMapper.updateById(task), "巡检任务闭环");
    }

    private void validateQr(GeneralInspectionTask task, SysUser currentUser) {
        if (!Integer.valueOf(1).equals(task.getQrRequired())) return;
        GeneralInspectionPoint point = pointMapper.selectById(task.getPointId());
        if (point == null || !Integer.valueOf(1).equals(point.getQrEnabled())
                || !Objects.equals(point.getQrVersion(), task.getQrVersion())
                || !Objects.equals(task.getScanVerifiedBy(), currentUser.getId())
                || task.getScanVerifiedTime() == null) {
            throw BusinessException.forbidden("该点位任务必须使用当前有效二维码扫码后提交");
        }
    }

    private void bindTaskFiles(GeneralInspectionTask task, List<Long> fileIds, SysUser user) {
        fileResourceService.validateAndBind(user, task.getProjectId(), fileIds,
                "EDGE_INSPECTION_TASK_PENDING", "EDGE_INSPECTION_TASK", task.getId());
    }

    private GeneralInspectionTaskVO toTaskVO(GeneralInspectionTask task, boolean includeItems, SysUser user) {
        LocalDateTime now = LocalDateTime.now();
        GeneralInspectionTaskVO vo = new GeneralInspectionTaskVO();
        vo.setId(task.getId());
        vo.setProjectId(task.getProjectId());
        vo.setPointId(task.getPointId());
        vo.setRevisionNo(value(task.getRevisionNo(), 0));
        vo.setReplacesTaskId(task.getReplacesTaskId());
        vo.setPlanName(task.getPlanName());
        vo.setTemplateName(task.getTemplateName());
        vo.setPointCode(task.getPointCode());
        vo.setPointName(task.getPointName());
        vo.setPointTypeCode(task.getPointTypeCode());
        vo.setPointTypeName(task.getPointTypeName());
        vo.setBuildingName(task.getBuildingName());
        vo.setFloorName(task.getFloorName());
        vo.setLocationDesc(task.getLocationDesc());
        vo.setSlotCode(task.getSlotCode());
        vo.setSlotName(task.getSlotName());
        vo.setOccurrenceDate(task.getOccurrenceDate());
        vo.setAvailableTime(task.getAvailableTime());
        vo.setStartTime(task.getStartTime());
        vo.setDueTime(task.getDueTime());
        vo.setAssigneeId(task.getAssigneeId());
        vo.setAssigneeName(task.getAssigneeName());
        vo.setReviewerId(task.getReviewerId());
        vo.setReviewerName(task.getReviewerName());
        vo.setQrRequired(Integer.valueOf(1).equals(task.getQrRequired()));
        vo.setScanVerified(task.getScanVerifiedTime() != null && Objects.equals(task.getScanVerifiedBy(), user.getId()));
        vo.setStatus(task.getStatus());
        boolean overdue = "PENDING".equals(task.getStatus()) && now.isAfter(task.getDueTime());
        vo.setOverdue(overdue);
        vo.setLateSubmission(task.getSubmittedTime() != null && Integer.valueOf(0).equals(task.getOnTime()));
        vo.setDisplayStatus(displayStatus(task, overdue));
        vo.setCanExecute(canExecuteTask(task, user, now));
        boolean canManage = permissionService.canManage(task.getProjectId(), user);
        vo.setCanManage(canManage);
        vo.setCanReassign(canReassignTask(task.getStatus(), canManage));
        vo.setSubmittedTime(task.getSubmittedTime());
        vo.setOverallPhotoMin(value(task.getOverallPhotoMin(), 0));
        vo.setOverallPhotoMax(value(task.getOverallPhotoMax(), 9));
        vo.setOverallRemarkRequired(Integer.valueOf(1).equals(task.getOverallRemarkRequired()));
        vo.setOverallPhotoFileIds(parseIds(task.getOverallPhotoFileIds()));
        vo.setRemark(task.getRemark());
        vo.setPublicRemark(task.getPublicRemark());
        vo.setAbnormalCount(task.getAbnormalCount());
        vo.setVersion(task.getVersion());
        if (includeItems) vo.setItems(listTaskItems(task.getId()).stream().map(this::toItemVO).toList());
        return vo;
    }

    private GeneralInspectionTaskItemVO toItemVO(GeneralInspectionTaskItem item) {
        GeneralInspectionTaskItemVO vo = new GeneralInspectionTaskItemVO();
        vo.setId(item.getId());
        vo.setItemKey(item.getItemKey());
        vo.setItemName(item.getItemName());
        vo.setGuidance(item.getGuidance());
        vo.setStandardReference(item.getStandardReference());
        vo.setAllowNa(Integer.valueOf(1).equals(item.getAllowNa()));
        vo.setNormalPhotoMin(item.getNormalPhotoMin());
        vo.setAbnormalPhotoMin(item.getAbnormalPhotoMin());
        vo.setPhotoMax(item.getPhotoMax());
        vo.setNormalDescriptionRequired(Integer.valueOf(1).equals(item.getNormalDescriptionRequired()));
        vo.setAbnormalDescriptionRequired(Integer.valueOf(1).equals(item.getAbnormalDescriptionRequired()));
        vo.setSortOrder(item.getSortOrder());
        vo.setResult(item.getResult());
        vo.setDescription(item.getDescription());
        vo.setPhotoFileIds(parseIds(item.getPhotoFileIds()));
        return vo;
    }

    private GeneralInspectionTask replacementTask(GeneralInspectionTask source) {
        GeneralInspectionTask task = new GeneralInspectionTask();
        task.setProjectId(source.getProjectId());
        task.setPlanId(source.getPlanId());
        task.setPlanVersionId(source.getPlanVersionId());
        task.setTemplateId(source.getTemplateId());
        task.setTemplateVersionId(source.getTemplateVersionId());
        task.setPointId(source.getPointId());
        task.setPlanName(source.getPlanName());
        task.setTemplateName(source.getTemplateName());
        task.setPointCode(source.getPointCode());
        task.setPointName(source.getPointName());
        task.setPointTypeCode(source.getPointTypeCode());
        task.setPointTypeName(source.getPointTypeName());
        task.setBuildingName(source.getBuildingName());
        task.setFloorName(source.getFloorName());
        task.setLocationDesc(source.getLocationDesc());
        task.setSlotCode(source.getSlotCode());
        task.setSlotName(source.getSlotName());
        task.setOccurrenceDate(source.getOccurrenceDate());
        task.setAvailableTime(source.getAvailableTime());
        task.setStartTime(source.getStartTime());
        task.setDueTime(source.getDueTime());
        task.setAssigneeId(source.getAssigneeId());
        task.setAssigneeName(source.getAssigneeName());
        task.setBackupAssigneeIds(source.getBackupAssigneeIds());
        task.setDefaultRectifierId(source.getDefaultRectifierId());
        task.setDefaultRectifierName(source.getDefaultRectifierName());
        task.setDefaultRectificationDays(source.getDefaultRectificationDays());
        task.setReviewerId(source.getReviewerId());
        task.setReviewerName(source.getReviewerName());
        task.setBackupReviewerIds(source.getBackupReviewerIds());
        task.setQrRequired(source.getQrRequired());
        task.setQrVersion(source.getQrVersion());
        task.setOverallPhotoMin(source.getOverallPhotoMin());
        task.setOverallPhotoMax(source.getOverallPhotoMax());
        task.setOverallRemarkRequired(source.getOverallRemarkRequired());
        task.setAbnormalCount(0);
        return task;
    }

    private GeneralInspectionRectificationVO toRectificationVO(GeneralInspectionRectification rectification,
                                                                 SysUser user) {
        GeneralInspectionRectificationVO vo = new GeneralInspectionRectificationVO();
        vo.setId(rectification.getId());
        vo.setProjectId(rectification.getProjectId());
        vo.setTaskId(rectification.getTaskId());
        vo.setTaskItemId(rectification.getTaskItemId());
        vo.setPointId(rectification.getPointId());
        vo.setPointName(rectification.getPointName());
        vo.setItemName(rectification.getItemName());
        vo.setProblemDesc(rectification.getProblemDesc());
        vo.setRequirement(rectification.getRequirement());
        vo.setAssigneeId(rectification.getAssigneeId());
        vo.setAssigneeName(rectification.getAssigneeName());
        vo.setDeadline(rectification.getDeadline());
        vo.setReviewerId(rectification.getReviewerId());
        vo.setReviewerName(rectification.getReviewerName());
        vo.setStatus(rectification.getStatus());
        vo.setFeedback(rectification.getFeedback());
        vo.setRectificationPhotoFileIds(parseIds(rectification.getRectificationPhotoFileIds()));
        vo.setCompletedTime(rectification.getCompletedTime());
        vo.setReviewComment(rectification.getReviewComment());
        vo.setReviewTime(rectification.getReviewTime());
        vo.setRejectCount(rectification.getRejectCount());
        vo.setCloseTime(rectification.getCloseTime());
        vo.setVersion(rectification.getVersion());
        vo.setOverdue(rectification.getDeadline() != null && rectification.getDeadline().isBefore(LocalDate.now())
                && !Set.of("CLOSED", "VOIDED").contains(rectification.getStatus()));
        vo.setCanRectify(Objects.equals(rectification.getAssigneeId(), user.getId())
                && Set.of("PENDING", "REJECTED").contains(rectification.getStatus()));
        GeneralInspectionTask task = taskMapper.selectById(rectification.getTaskId());
        vo.setCanReview("COMPLETED".equals(rectification.getStatus()) && isReviewer(task, rectification, user.getId()));
        vo.setCanAssign(permissionService.canManage(rectification.getProjectId(), user));
        return vo;
    }

    private boolean canReadTask(GeneralInspectionTask task, SysUser user, boolean alreadyMine) {
        if ((alreadyMine || Objects.equals(task.getAssigneeId(), user.getId()))
                && permissionService.hasInspectionPermission(task.getProjectId(), user.getId(),
                InspectionPermissionCodes.EDGE_INSPECTION_SUBMIT)) return true;
        if (Objects.equals(task.getReviewerId(), user.getId())
                && permissionService.hasInspectionPermission(task.getProjectId(), user.getId(),
                InspectionPermissionCodes.EDGE_INSPECTION_REVIEW)) return true;
        try {
            permissionService.requireRecordView(task.getProjectId(), user);
            return true;
        } catch (BusinessException ex) {
            return false;
        }
    }

    private boolean canReadRectification(GeneralInspectionRectification rectification, SysUser user) {
        if (Objects.equals(rectification.getAssigneeId(), user.getId()) || Objects.equals(rectification.getReviewerId(), user.getId())) return true;
        GeneralInspectionTask task = taskMapper.selectById(rectification.getTaskId());
        if (task != null && csvContains(task.getBackupReviewerIds(), user.getId())) return true;
        try {
            permissionService.requireRecordView(rectification.getProjectId(), user);
            return true;
        } catch (BusinessException ex) {
            return false;
        }
    }

    private boolean matchesRectificationScope(GeneralInspectionRectification rectification, String scope, SysUser user) {
        if ("ALL".equals(scope)) return true;
        if ("RECTIFY".equals(scope)) return Objects.equals(rectification.getAssigneeId(), user.getId());
        GeneralInspectionTask task = taskMapper.selectById(rectification.getTaskId());
        if ("REVIEW".equals(scope)) return isReviewer(task, rectification, user.getId());
        return Objects.equals(rectification.getAssigneeId(), user.getId())
                || isReviewer(task, rectification, user.getId());
    }

    private boolean isReviewer(GeneralInspectionTask task, GeneralInspectionRectification rectification, Long userId) {
        return Objects.equals(rectification.getReviewerId(), userId)
                || task != null && csvContains(task.getBackupReviewerIds(), userId);
    }

    private SysUser validRectifier(Long projectId, Long userId) {
        if (userId == null || !permissionService.hasActiveProjectAccess(projectId, userId)
                || !permissionService.hasInspectionPermission(projectId, userId,
                InspectionPermissionCodes.EDGE_INSPECTION_RECTIFY)) return null;
        SysUser user = userMapper.selectById(userId);
        return active(user) ? user : null;
    }

    private boolean validReviewer(Long projectId, Long userId) {
        if (userId == null || !permissionService.hasActiveProjectAccess(projectId, userId)
                || !permissionService.hasInspectionPermission(projectId, userId,
                InspectionPermissionCodes.EDGE_INSPECTION_REVIEW)) return false;
        return active(userMapper.selectById(userId));
    }

    private SysUser requireActivePermissionUser(Long projectId, Long userId, String permissionCode, String label) {
        if (userId == null || !permissionService.hasActiveProjectAccess(projectId, userId)) throw new BusinessException(label + "必须是项目有效成员");
        SysUser user = userMapper.selectById(userId);
        if (!active(user) || !permissionService.hasInspectionPermission(projectId, userId, permissionCode)) {
            throw new BusinessException(label + "账号或权限无效");
        }
        return user;
    }

    private boolean active(SysUser user) {
        return user != null && Integer.valueOf(1).equals(user.getStatus())
                && !Integer.valueOf(1).equals(user.getDeleted());
    }

    private void requirePrimaryAssignee(GeneralInspectionTask task, SysUser user) {
        if (!Objects.equals(task.getAssigneeId(), user.getId())) throw BusinessException.forbidden("仅任务当前主巡检人可执行，备选人须由管理者明确改派");
    }

    private GeneralInspectionTask requireTask(Long id) {
        GeneralInspectionTask task = taskMapper.selectById(id);
        if (task == null) throw BusinessException.notFound("巡检任务不存在");
        return task;
    }

    private GeneralInspectionRectification requireRectification(Long id) {
        GeneralInspectionRectification rectification = rectificationMapper.selectById(id);
        if (rectification == null) throw BusinessException.notFound("整改任务不存在");
        return rectification;
    }

    private List<GeneralInspectionTaskItem> listTaskItems(Long taskId) {
        return taskItemMapper.selectList(new LambdaQueryWrapper<GeneralInspectionTaskItem>()
                .eq(GeneralInspectionTaskItem::getTaskId, taskId)
                .orderByAsc(GeneralInspectionTaskItem::getSortOrder));
    }

    private List<GeneralInspectionRectification> listRectificationsByTask(Long taskId) {
        return rectificationMapper.selectList(new LambdaQueryWrapper<GeneralInspectionRectification>()
                .eq(GeneralInspectionRectification::getTaskId, taskId).orderByAsc(GeneralInspectionRectification::getId));
    }

    private GeneralInspectionScanVO baseScan(GeneralInspectionPoint point) {
        GeneralInspectionScanVO vo = new GeneralInspectionScanVO();
        vo.setPublicCode(point.getPublicCode());
        vo.setProjectId(point.getProjectId());
        vo.setPointId(point.getId());
        vo.setPointCode(point.getPointCode());
        vo.setPointName(point.getPointName());
        vo.setLocationDesc(point.getLocationDesc());
        vo.setPublicAccessEnabled(Integer.valueOf(1).equals(point.getPublicAccessEnabled()));
        return vo;
    }

    private void notifyAfterSubmit(GeneralInspectionTask task) {
        if (task.getAbnormalCount() == null || task.getAbnormalCount() == 0) return;
        if (task.getReviewerId() != null) {
            notificationService.notify(task.getReviewerId(), task.getProjectId(), "EDGE_INSPECTION_TASK",
                    task.getId(), "ABNORMAL_SUBMITTED", "临边巡检发现异常",
                    task.getPointName() + "有" + task.getAbnormalCount() + "项异常待整改闭环",
                    "edge-task-abnormal:" + task.getId(), "EDGE_INSPECTION_TASK_DETAIL",
                    "{\"taskId\":" + task.getId() + "}");
        }
    }

    private void notifyRectifier(GeneralInspectionRectification rectification) {
        notificationService.notify(rectification.getAssigneeId(), rectification.getProjectId(),
                "EDGE_INSPECTION_RECTIFICATION", rectification.getTaskId(), "RECTIFY_PENDING",
                "临边巡检整改待处理", rectification.getPointName() + "存在异常项待整单整改",
                "edge-rectify:" + rectification.getTaskId(),
                "EDGE_INSPECTION_RECTIFICATION_DETAIL", "{\"taskId\":" + rectification.getTaskId() + "}");
    }

    private void notifyReviewer(GeneralInspectionRectification rectification) {
        notificationService.notify(rectification.getReviewerId(), rectification.getProjectId(),
                "EDGE_INSPECTION_REVIEW", rectification.getTaskId(), "REVIEW_PENDING",
                "临边巡检整改待复查", rectification.getPointName() + "整改单待复查",
                "edge-review:" + rectification.getTaskId() + ":" + rectification.getVersion(),
                "EDGE_INSPECTION_RECTIFICATION_DETAIL", "{\"taskId\":" + rectification.getTaskId() + "}");
    }

    private void record(Long projectId, String businessType, Long businessId, String action, SysUser user,
                        String from, String to, String comment, String before, String after) {
        GeneralInspectionActionLog log = new GeneralInspectionActionLog();
        log.setProjectId(projectId);
        log.setBusinessType(businessType);
        log.setBusinessId(businessId);
        log.setActionType(action);
        log.setOperatorId(user.getId());
        log.setOperatorName(userName(user));
        log.setFromStatus(from);
        log.setToStatus(to);
        log.setComment(comment);
        log.setBeforeJson(before);
        log.setAfterJson(after);
        requireOne(actionLogMapper.insert(log), "临边巡检操作日志写入");
    }

    private Set<Long> collectExistingPhotoIds(GeneralInspectionTask task, List<GeneralInspectionTaskItem> items) {
        Set<Long> ids = new HashSet<>(parseIds(task.getOverallPhotoFileIds()));
        items.forEach(item -> ids.addAll(parseIds(item.getPhotoFileIds())));
        return ids;
    }

    private Map<String, Object> edgeReassignmentSnapshot(GeneralInspectionTask task,
                                                         List<GeneralInspectionRectification> rectifications) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", task.getId());
        result.put("defaultRectifierId", task.getDefaultRectifierId());
        result.put("defaultRectifierName", task.getDefaultRectifierName());
        result.put("reviewerId", task.getReviewerId());
        result.put("reviewerName", task.getReviewerName());
        result.put("items", rectifications.stream().map(rectification -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("rectificationId", rectification.getId());
            item.put("assigneeId", rectification.getAssigneeId());
            item.put("assigneeName", rectification.getAssigneeName());
            item.put("reviewerId", rectification.getReviewerId());
            item.put("reviewerName", rectification.getReviewerName());
            item.put("deadline", rectification.getDeadline());
            item.put("status", rectification.getStatus());
            return item;
        }).toList());
        return result;
    }

    private List<Long> distinctIds(List<Long> ids, String label) {
        if (ids == null) return List.of();
        List<Long> result = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (result.size() != ids.size()) throw new BusinessException(label + "包含重复或无效文件");
        return result;
    }

    private List<Long> parseIds(String csv) {
        if (!StringUtils.hasText(csv)) return List.of();
        try {
            return Arrays.stream(csv.split(",")).map(String::trim).filter(StringUtils::hasText)
                    .map(Long::valueOf).distinct().toList();
        } catch (NumberFormatException ex) {
            throw conflict("历史附件标识格式异常");
        }
    }

    private boolean csvContains(String csv, Long id) {
        return id != null && parseIds(csv).contains(id);
    }

    private String joinIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return null;
        return ids.stream().filter(Objects::nonNull).distinct().map(String::valueOf).collect(Collectors.joining(","));
    }

    static String displayStatus(GeneralInspectionTask task, boolean overdue) {
        if (overdue) return "OVERDUE_MISSED";
        if ("COMPLETED".equals(task.getStatus())
                && task.getSubmittedTime() != null
                && Integer.valueOf(0).equals(task.getOnTime())) {
            return "LATE_COMPLETED";
        }
        return task.getStatus();
    }

    static void requireTaskReassignable(String status) {
        if (!"PENDING".equals(status)) {
            throw BusinessException.of(409, "仅待巡检任务可改派；整改人或复查人请在整改单中改派");
        }
    }

    static boolean canReassignTask(String status, boolean canManage) {
        return canManage && "PENDING".equals(status);
    }

    boolean canExecuteTask(GeneralInspectionTask task, SysUser user, LocalDateTime now) {
        return task != null && user != null && user.getId() != null
                && "PENDING".equals(task.getStatus())
                && task.getAvailableTime() != null
                && !now.isBefore(task.getAvailableTime())
                && Objects.equals(task.getAssigneeId(), user.getId())
                && permissionService.hasInspectionPermission(task.getProjectId(), user.getId(),
                InspectionPermissionCodes.EDGE_INSPECTION_SUBMIT);
    }

    private String appendCorrectionNote(String old, String reason) {
        String line = LocalDateTime.now() + " " + trimRequired(reason, "纠错原因", 1000);
        String combined = StringUtils.hasText(old) ? old + "\n" + line : line;
        return combined.length() <= 1000 ? combined : combined.substring(combined.length() - 1000);
    }

    private String snapshot(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw conflict("业务快照序列化失败");
        }
    }

    private String normalizeScene(String raw) {
        if (!StringUtils.hasText(raw)) throw new BusinessException("点位码不能为空");
        String result = raw.trim();
        if (result.startsWith("P:")) result = result.substring(2);
        if (!result.matches("[A-Za-z0-9_-]{20,80}")) throw new BusinessException("点位码格式无效");
        return result;
    }

    private String trimRequired(String value, String label, int max) {
        if (!StringUtils.hasText(value)) throw new BusinessException(label + "不能为空");
        return trim(value, max);
    }

    private String trim(String value, int max) {
        if (!StringUtils.hasText(value)) return null;
        String result = value.trim();
        if (result.length() > max) throw new BusinessException("字段长度不能超过" + max + "个字符");
        return result;
    }

    private String userName(SysUser user) {
        return StringUtils.hasText(user.getRealName()) ? user.getRealName().trim() : user.getUsername();
    }

    private void requireExpected(Integer actual, Integer expected, String message) {
        if (!Objects.equals(actual, expected)) throw conflict(message);
    }

    private void requireUser(SysUser user) {
        if (user == null || user.getId() == null) throw BusinessException.unauthorized("请先登录");
    }

    private int value(Integer actual, int fallback) {
        return actual == null ? fallback : actual;
    }

    private void requireOne(int affected, String action) {
        if (affected != 1) throw conflict(action + "未生效");
    }

    private BusinessException conflict(String message) {
        return BusinessException.of(409, message);
    }

    private record Submission(Map<Long, GeneralInspectionTaskSubmitRequest.ItemResult> results,
                              List<Long> allPhotoIds, int abnormalCount) {
    }
}
