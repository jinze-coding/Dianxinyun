package com.example.siteplatform.quality.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.common.PageResult;
import com.example.siteplatform.file.service.QualityWeeklyFileService;
import com.example.siteplatform.log.entity.OperationLog;
import com.example.siteplatform.log.mapper.OperationLogMapper;
import com.example.siteplatform.notification.service.WechatNotificationService;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.quality.dto.QualityWeeklyActionRequest;
import com.example.siteplatform.quality.dto.QualityWeeklyDraftCreateRequest;
import com.example.siteplatform.quality.dto.QualityWeeklyDraftItemRequest;
import com.example.siteplatform.quality.dto.QualityWeeklyDraftSaveRequest;
import com.example.siteplatform.quality.entity.QualityIssue;
import com.example.siteplatform.quality.entity.QualityIssueLog;
import com.example.siteplatform.quality.entity.QualityWeeklyInspection;
import com.example.siteplatform.quality.entity.QualityWeeklyInspectionDraftItem;
import com.example.siteplatform.quality.mapper.QualityIssueLogMapper;
import com.example.siteplatform.quality.mapper.QualityIssueMapper;
import com.example.siteplatform.quality.mapper.QualityWeeklyInspectionDraftItemMapper;
import com.example.siteplatform.quality.mapper.QualityWeeklyInspectionMapper;
import com.example.siteplatform.quality.vo.QualityIssueVO;
import com.example.siteplatform.quality.vo.QualityWeeklyDraftItemVO;
import com.example.siteplatform.quality.vo.QualityWeeklyInspectionVO;
import com.example.siteplatform.quality.vo.QualityWeeklySummaryVO;
import com.example.siteplatform.system.constant.SystemPermissionCodes;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class QualityWeeklyInspectionService {
    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_SUBMITTED = "SUBMITTED";
    private static final int MAX_ISSUES = 50;
    private static final int MAX_PHOTOS = 20;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    @Autowired
    private QualityWeeklyInspectionMapper inspectionMapper;

    @Autowired
    private QualityWeeklyInspectionDraftItemMapper draftItemMapper;

    @Autowired
    private QualityIssueMapper issueMapper;

    @Autowired
    private QualityIssueLogMapper logMapper;

    @Autowired
    private QualityIssueService qualityIssueService;

    @Autowired
    private QualityAssigneeService qualityAssigneeService;

    @Autowired
    private QualityWeeklyReminderSettingService reminderSettingService;

    @Autowired
    private ProjectPermissionService projectPermissionService;

    @Autowired
    private QualityWeeklyFileService weeklyFileService;

    @Autowired
    private WechatNotificationService wechatNotificationService;

    @Autowired
    private OperationLogMapper operationLogMapper;

    public PageResult<QualityWeeklyInspectionVO> page(Long projectId,
                                                       String status,
                                                       String keyword,
                                                       Integer pageNo,
                                                       Integer pageSize,
                                                       SysUser currentUser) {
        requireView(currentUser, projectId);
        boolean canManage = canManage(currentUser, projectId);
        int page = pageNo == null ? 1 : Math.max(1, pageNo);
        int size = pageSize == null ? 20 : Math.max(1, Math.min(pageSize, 100));
        LambdaQueryWrapper<QualityWeeklyInspection> query =
                new LambdaQueryWrapper<QualityWeeklyInspection>()
                        .eq(QualityWeeklyInspection::getProjectId, projectId);
        if (StringUtils.hasText(status) && !"ALL".equalsIgnoreCase(status)) {
            String normalized = normalizeStatus(status);
            if (STATUS_DRAFT.equals(normalized) && !canManage) {
                throw BusinessException.forbidden("无权查看质量周检草稿");
            }
            query.eq(QualityWeeklyInspection::getStatus, normalized);
        } else if (!canManage) {
            query.eq(QualityWeeklyInspection::getStatus, STATUS_SUBMITTED);
        }
        if (StringUtils.hasText(keyword)) {
            String text = keyword.trim();
            query.and(row -> row.like(QualityWeeklyInspection::getInspectionNo, text)
                    .or().like(QualityWeeklyInspection::getConclusion, text)
                    .or().like(QualityWeeklyInspection::getCreatedByName, text)
                    .or().like(QualityWeeklyInspection::getLastEditedByName, text));
        }
        query.orderByDesc(QualityWeeklyInspection::getWeekStart)
                .orderByDesc(QualityWeeklyInspection::getId);
        Page<QualityWeeklyInspection> result = inspectionMapper.selectPage(new Page<>(page, size), query);
        List<QualityWeeklyInspectionVO> records = safeList(result.getRecords()).stream()
                .map(inspection -> toVO(inspection, currentUser, false))
                .toList();
        return PageResult.of(page, size, result.getTotal(), records);
    }

    public QualityWeeklySummaryVO summary(Long projectId, SysUser currentUser) {
        requireView(currentUser, projectId);
        LocalDate weekStart = currentWeekStart();
        boolean canManage = canManage(currentUser, projectId);
        QualityWeeklyInspection inspection = inspectionMapper.selectByProjectWeek(projectId, weekStart);
        if (inspection != null && STATUS_DRAFT.equals(inspection.getStatus()) && !canManage) {
            inspection = null;
        }
        QualityWeeklySummaryVO summary = new QualityWeeklySummaryVO();
        summary.setProjectId(projectId);
        summary.setWeekStart(weekStart);
        summary.setWeekEnd(weekStart.plusDays(6));
        summary.setHasInspection(inspection != null);
        summary.setCanManage(canManage);
        summary.setDraftItemCount(0);
        summary.setSubmittedIssueCount(0);
        summary.setPendingCount(0);
        summary.setRecheckCount(0);
        summary.setClosedCount(0);
        summary.setVoidedCount(0);
        summary.setLateSubmission(false);
        if (inspection == null) {
            return summary;
        }
        summary.setInspectionId(inspection.getId());
        summary.setInspectionNo(inspection.getInspectionNo());
        summary.setStatus(inspection.getStatus());
        summary.setVersion(versionOf(inspection));
        summary.setSubmittedIssueCount(numberOrZero(inspection.getSubmittedIssueCount()));
        summary.setLateSubmission(isLateSubmission(inspection));
        if (STATUS_DRAFT.equals(inspection.getStatus())) {
            summary.setDraftItemCount(safeList(
                    draftItemMapper.selectByInspectionId(inspection.getId())).size());
        } else {
            Map<String, Integer> counts = issueCounts(inspection.getId());
            summary.setPendingCount(counts.get(QualityIssueService.STATUS_PENDING));
            summary.setRecheckCount(counts.get(QualityIssueService.STATUS_RECHECK));
            summary.setClosedCount(counts.get(QualityIssueService.STATUS_CLOSED));
            summary.setVoidedCount(counts.get(QualityIssueService.STATUS_VOIDED));
        }
        return summary;
    }

    public QualityWeeklyInspectionVO detail(Long id, SysUser currentUser) {
        QualityWeeklyInspection inspection = requireInspection(id);
        requireReadable(currentUser, inspection);
        return toVO(inspection, currentUser, true);
    }

    @Transactional
    public QualityWeeklyInspectionVO createOrRestore(QualityWeeklyDraftCreateRequest request,
                                                      SysUser currentUser) {
        if (request == null || request.getProjectId() == null || request.getWeekStart() == null) {
            throw new BusinessException("项目和周起始日不能为空");
        }
        requireManage(currentUser, request.getProjectId());
        LocalDate weekStart = normalizeWeekStart(request.getWeekStart());
        rejectFutureWeek(weekStart);
        QualityWeeklyInspection existing = inspectionMapper.selectByProjectWeek(
                request.getProjectId(), weekStart);
        if (existing != null) {
            return toVO(existing, currentUser, true);
        }

        LocalDateTime now = now();
        QualityWeeklyInspection inspection = new QualityWeeklyInspection();
        inspection.setProjectId(request.getProjectId());
        inspection.setWeekStart(weekStart);
        inspection.setInspectionDate(weekStart.equals(currentWeekStart())
                ? today() : weekStart.plusDays(6));
        inspection.setStatus(STATUS_DRAFT);
        inspection.setSubmittedIssueCount(0);
        inspection.setCreatedById(currentUser.getId());
        inspection.setCreatedByName(displayName(currentUser));
        inspection.setLastEditedById(currentUser.getId());
        inspection.setLastEditedByName(displayName(currentUser));
        inspection.setVersion(0);
        inspection.setCreateTime(now);
        inspection.setUpdateTime(now);
        try {
            requireSingleWrite(inspectionMapper.insert(inspection), "质量周检草稿创建");
        } catch (DuplicateKeyException duplicate) {
            QualityWeeklyInspection concurrent = inspectionMapper.selectByProjectWeekForUpdate(
                    request.getProjectId(), weekStart);
            if (concurrent == null) {
                throw BusinessException.of(409, "同周周检正在创建，请稍后重试");
            }
            return toVO(concurrent, currentUser, true);
        }
        recordOperation(currentUser, "QUALITY_WEEKLY_DRAFT_CREATE", inspection.getId(),
                "创建" + weekStart + "质量周检共享草稿");
        return toVO(inspection, currentUser, true);
    }

    @Transactional
    public QualityWeeklyInspectionVO saveDraft(Long id,
                                                QualityWeeklyDraftSaveRequest request,
                                                SysUser currentUser) {
        if (request == null || request.getExpectedVersion() == null) {
            throw new BusinessException("expectedVersion不能为空");
        }
        QualityWeeklyInspection inspection = requireInspectionForUpdate(id);
        requireManage(currentUser, inspection.getProjectId());
        requireDraftAndVersion(inspection, request.getExpectedVersion());
        validateInspectionDate(request.getInspectionDate(), inspection.getWeekStart(), true);
        List<QualityWeeklyDraftItemRequest> requestedItems = safeList(request.getItems());
        if (requestedItems.size() > MAX_ISSUES) {
            throw new BusinessException("每份周检最多录入50个问题");
        }
        prevalidateDraftItems(requestedItems);
        validatePhotoCount(request.getOverviewPhotoFileIds(), "周检现场照片", true);

        LocalDateTime now = now();
        String conclusion = trimToNull(request.getConclusion());
        requireSingleWrite(inspectionMapper.updateDraft(
                inspection.getId(), request.getExpectedVersion(), request.getInspectionDate(), conclusion,
                currentUser.getId(), displayName(currentUser), now), "质量周检草稿保存");

        List<QualityWeeklyInspectionDraftItem> existingItems = safeList(
                draftItemMapper.selectByInspectionId(inspection.getId()));
        Map<String, QualityWeeklyInspectionDraftItem> existingByKey = new LinkedHashMap<>();
        Map<Integer, QualityWeeklyInspectionDraftItem> existingByOrder = new HashMap<>();
        for (QualityWeeklyInspectionDraftItem item : existingItems) {
            existingByKey.put(item.getItemKey(), item);
            existingByOrder.putIfAbsent(item.getItemOrder(), item);
        }
        if (!existingItems.isEmpty()
                && draftItemMapper.moveOrdersOutOfRange(inspection.getId()) != existingItems.size()) {
            throw stateConflict("质量周检问题草稿排序已被其他人修改");
        }
        Set<String> requestedKeys = new HashSet<>();
        Set<Integer> requestedOrders = new HashSet<>();
        Set<Long> retainedIds = new HashSet<>();
        int sequence = 0;
        for (QualityWeeklyDraftItemRequest itemRequest : requestedItems) {
            sequence++;
            if (itemRequest == null) {
                throw new BusinessException("问题草稿项不能为空");
            }
            Integer itemOrder = itemRequest.getItemOrder() == null ? sequence : itemRequest.getItemOrder();
            if (itemOrder < 1 || itemOrder > MAX_ISSUES) {
                throw new BusinessException("问题顺序必须在1到50之间");
            }
            if (!requestedOrders.add(itemOrder)) {
                throw new BusinessException("同一周检内问题顺序不能重复");
            }
            String itemKey = normalizeItemKey(itemRequest.getItemKey());
            if (!StringUtils.hasText(itemKey)) {
                QualityWeeklyInspectionDraftItem sameOrder = existingByOrder.get(itemOrder);
                itemKey = sameOrder != null && !retainedIds.contains(sameOrder.getId())
                        ? sameOrder.getItemKey() : UUID.randomUUID().toString();
            }
            if (!requestedKeys.add(itemKey)) {
                throw new BusinessException("同一周检内问题草稿键不能重复");
            }
            validatePhotoCount(itemRequest.getBeforePhotoFileIds(), "整改前照片", true);
            String severity = normalizeDraftSeverity(itemRequest.getSeverity());
            String assigneeName = null;
            if (itemRequest.getAssigneeId() != null) {
                SysUser assignee = qualityAssigneeService.requireEligibleAssignee(
                        itemRequest.getAssigneeId(), inspection.getProjectId(), currentUser);
                assigneeName = displayName(assignee);
            }

            QualityWeeklyInspectionDraftItem item = existingByKey.get(itemKey);
            boolean creating = item == null;
            if (creating) {
                item = new QualityWeeklyInspectionDraftItem();
                item.setProjectId(inspection.getProjectId());
                item.setInspectionId(inspection.getId());
                item.setItemKey(itemKey);
                item.setCreateTime(now);
            }
            item.setItemOrder(itemOrder);
            item.setTitle(trimToNull(itemRequest.getTitle()));
            item.setLocation(trimToNull(itemRequest.getLocation()));
            item.setDescription(trimToNull(itemRequest.getDescription()));
            item.setSeverity(severity);
            item.setAssigneeId(itemRequest.getAssigneeId());
            item.setAssigneeName(assigneeName);
            item.setDeadline(itemRequest.getDeadline());
            item.setUpdateTime(now);
            if (creating) {
                requireSingleWrite(draftItemMapper.insert(item), "质量周检问题草稿新增");
            } else {
                requireSingleWrite(draftItemMapper.updateById(item), "质量周检问题草稿更新");
            }
            if (item.getId() == null) {
                throw BusinessException.of(409, "质量周检问题草稿主键生成失败");
            }
            retainedIds.add(item.getId());
            weeklyFileService.syncDraftFiles(currentUser, inspection.getProjectId(),
                    nullToEmpty(itemRequest.getBeforePhotoFileIds()),
                    QualityWeeklyFileService.WEEKLY_ITEM_PENDING,
                    QualityWeeklyFileService.WEEKLY_DRAFT_ITEM, item.getId());
        }

        List<Long> removedIds = existingItems.stream()
                .map(QualityWeeklyInspectionDraftItem::getId)
                .filter(Objects::nonNull)
                .filter(itemId -> !retainedIds.contains(itemId))
                .toList();
        if (!removedIds.isEmpty()) {
            weeklyFileService.discardDraftFiles(inspection.getProjectId(),
                    QualityWeeklyFileService.WEEKLY_DRAFT_ITEM, removedIds);
            int deleted = draftItemMapper.deleteBatchIds(removedIds);
            if (deleted != removedIds.size()) {
                throw stateConflict("质量周检问题草稿已被其他人修改");
            }
        }
        weeklyFileService.syncDraftFiles(currentUser, inspection.getProjectId(),
                nullToEmpty(request.getOverviewPhotoFileIds()),
                QualityWeeklyFileService.WEEKLY_OVERVIEW_PENDING,
                QualityWeeklyFileService.WEEKLY_DRAFT, inspection.getId());

        inspection.setInspectionDate(request.getInspectionDate());
        inspection.setConclusion(conclusion);
        inspection.setLastEditedById(currentUser.getId());
        inspection.setLastEditedByName(displayName(currentUser));
        inspection.setVersion(request.getExpectedVersion() + 1);
        inspection.setUpdateTime(now);
        recordOperation(currentUser, "QUALITY_WEEKLY_DRAFT_SAVE", inspection.getId(),
                "保存质量周检共享草稿，问题草稿" + requestedItems.size() + "项");
        return toVO(inspection, currentUser, true);
    }

    @Transactional
    public QualityWeeklyInspectionVO submit(Long id,
                                             QualityWeeklyActionRequest request,
                                             SysUser currentUser) {
        if (request == null || request.getExpectedVersion() == null) {
            throw new BusinessException("expectedVersion不能为空");
        }
        // 与未提交提醒调度统一先锁项目提醒规则，再锁周检主记录，避免截止瞬间误提醒。
        reminderSettingService.lockByInspectionId(id);
        QualityWeeklyInspection inspection = requireInspectionForUpdate(id);
        requireManage(currentUser, inspection.getProjectId());
        if (STATUS_SUBMITTED.equals(inspection.getStatus())) {
            return toVO(inspection, currentUser, true);
        }
        requireDraftAndVersion(inspection, request.getExpectedVersion());
        validateInspectionDate(inspection.getInspectionDate(), inspection.getWeekStart(), false);
        if (inspection.getInspectionDate() == null) {
            throw new BusinessException("检查日期不能为空");
        }

        List<QualityWeeklyInspectionDraftItem> items = safeList(
                draftItemMapper.selectByInspectionId(inspection.getId()));
        if (items.size() > MAX_ISSUES) {
            throw new BusinessException("每份周检最多录入50个问题");
        }
        List<Long> overviewPhotoIds = weeklyFileService.listFileIds(
                QualityWeeklyFileService.WEEKLY_DRAFT, inspection.getId());
        validatePhotoCount(overviewPhotoIds, "周检现场照片", true);
        if (items.isEmpty()) {
            if (!StringUtils.hasText(inspection.getConclusion())) {
                throw new BusinessException("无问题周检必须填写检查结论");
            }
            if (overviewPhotoIds.isEmpty()) {
                throw new BusinessException("无问题周检必须上传至少一张现场照片");
            }
        }

        Map<Long, SysUser> assignees = new HashMap<>();
        Map<Long, List<Long>> beforePhotos = new LinkedHashMap<>();
        for (QualityWeeklyInspectionDraftItem item : items) {
            validateSubmitItem(item);
            SysUser assignee = qualityAssigneeService.requireEligibleAssignee(
                    item.getAssigneeId(), inspection.getProjectId(), currentUser);
            assignees.put(item.getId(), assignee);
            List<Long> photos = weeklyFileService.listFileIds(
                    QualityWeeklyFileService.WEEKLY_DRAFT_ITEM, item.getId());
            validatePhotoCount(photos, "整改前照片", false);
            beforePhotos.put(item.getId(), photos);
        }

        LocalDateTime submittedTime = now();
        String inspectionNo = generateInspectionNo(inspection.getWeekStart());
        requireSingleWrite(inspectionMapper.submit(
                inspection.getId(), request.getExpectedVersion(), inspectionNo, items.size(),
                currentUser.getId(), displayName(currentUser), submittedTime), "质量周检提交");

        List<QualityIssue> createdIssues = new ArrayList<>();
        for (QualityWeeklyInspectionDraftItem item : items) {
            SysUser assignee = assignees.get(item.getId());
            QualityIssue issue = new QualityIssue();
            issue.setProjectId(inspection.getProjectId());
            issue.setWeeklyInspectionId(inspection.getId());
            issue.setInspectionItemOrder(item.getItemOrder());
            issue.setRecordDate(inspection.getInspectionDate());
            issue.setIssueNo(generateIssueNo());
            issue.setRequestKey("W:" + inspection.getId() + ":" + item.getId());
            issue.setTitle(item.getTitle().trim());
            issue.setLocation(trimToNull(item.getLocation()));
            issue.setDescription(trimToNull(item.getDescription()));
            issue.setSeverity(normalizeSeverity(item.getSeverity()));
            issue.setStatus(QualityIssueService.STATUS_PENDING);
            issue.setAssigneeId(assignee.getId());
            issue.setAssigneeName(displayName(assignee));
            issue.setDeadline(item.getDeadline());
            issue.setCreatedById(currentUser.getId());
            issue.setCreatedByName(displayName(currentUser));
            issue.setVersion(0);
            issue.setDeleted(0);
            issue.setCreateTime(submittedTime);
            issue.setUpdateTime(submittedTime);
            requireSingleWrite(issueMapper.insert(issue), "质量周检问题生成");
            List<Long> finalPhotoIds = weeklyFileService.transferDraftFiles(
                    inspection.getProjectId(), QualityWeeklyFileService.WEEKLY_DRAFT_ITEM,
                    item.getId(), QualityWeeklyFileService.ISSUE_FINAL, issue.getId());
            if (!new HashSet<>(finalPhotoIds).equals(new HashSet<>(beforePhotos.get(item.getId())))) {
                throw stateConflict("质量周检问题照片已变化");
            }
            writeCreateLog(issue, currentUser, finalPhotoIds);
            createdIssues.add(issue);
        }
        weeklyFileService.transferDraftFiles(
                inspection.getProjectId(), QualityWeeklyFileService.WEEKLY_DRAFT,
                inspection.getId(), QualityWeeklyFileService.WEEKLY_FINAL, inspection.getId());
        int deletedItems = draftItemMapper.deleteByInspectionId(inspection.getId());
        if (deletedItems != items.size()) {
            throw stateConflict("质量周检问题草稿已被其他人修改");
        }

        inspection.setInspectionNo(inspectionNo);
        inspection.setStatus(STATUS_SUBMITTED);
        inspection.setSubmittedIssueCount(items.size());
        inspection.setSubmittedById(currentUser.getId());
        inspection.setSubmittedByName(displayName(currentUser));
        inspection.setLastEditedById(currentUser.getId());
        inspection.setLastEditedByName(displayName(currentUser));
        inspection.setSubmittedTime(submittedTime);
        inspection.setVersion(request.getExpectedVersion() + 1);
        inspection.setUpdateTime(submittedTime);
        recordOperation(currentUser, "QUALITY_WEEKLY_SUBMIT", inspection.getId(),
                "提交质量周检" + inspectionNo + "，生成质量问题" + items.size() + "项");
        notifyAssigneesAfterCommit(createdIssues);
        return toVO(inspection, currentUser, true);
    }

    @Transactional
    public void discard(Long id, QualityWeeklyActionRequest request, SysUser currentUser) {
        if (request == null || request.getExpectedVersion() == null) {
            throw new BusinessException("expectedVersion不能为空");
        }
        QualityWeeklyInspection inspection = requireInspectionForUpdate(id);
        requireManage(currentUser, inspection.getProjectId());
        requireDraftAndVersion(inspection, request.getExpectedVersion());
        List<Long> itemIds = safeList(draftItemMapper.selectByInspectionId(id)).stream()
                .map(QualityWeeklyInspectionDraftItem::getId)
                .filter(Objects::nonNull)
                .toList();
        weeklyFileService.discardDraftFiles(inspection.getProjectId(),
                QualityWeeklyFileService.WEEKLY_DRAFT, List.of(id));
        weeklyFileService.discardDraftFiles(inspection.getProjectId(),
                QualityWeeklyFileService.WEEKLY_DRAFT_ITEM, itemIds);
        int deletedItems = draftItemMapper.deleteByInspectionId(id);
        if (deletedItems != itemIds.size()) {
            throw stateConflict("质量周检问题草稿已被其他人修改");
        }
        requireSingleWrite(inspectionMapper.deleteDraft(id, request.getExpectedVersion()), "质量周检草稿放弃");
        recordOperation(currentUser, "QUALITY_WEEKLY_DRAFT_DISCARD", id,
                "放弃" + inspection.getWeekStart() + "质量周检共享草稿");
    }

    private QualityWeeklyInspectionVO toVO(QualityWeeklyInspection inspection,
                                            SysUser currentUser,
                                            boolean includeDetails) {
        QualityWeeklyInspectionVO vo = new QualityWeeklyInspectionVO();
        BeanUtils.copyProperties(inspection, vo);
        vo.setWeekEnd(inspection.getWeekStart() == null ? null : inspection.getWeekStart().plusDays(6));
        vo.setSubmittedIssueCount(numberOrZero(inspection.getSubmittedIssueCount()));
        vo.setLateSubmission(isLateSubmission(inspection));
        vo.setPendingCount(0);
        vo.setRecheckCount(0);
        vo.setClosedCount(0);
        vo.setVoidedCount(0);
        String overviewType = STATUS_SUBMITTED.equals(inspection.getStatus())
                ? QualityWeeklyFileService.WEEKLY_FINAL : QualityWeeklyFileService.WEEKLY_DRAFT;
        vo.setOverviewPhotoFileIds(weeklyFileService.listFileIds(overviewType, inspection.getId()));
        if (includeDetails && STATUS_DRAFT.equals(inspection.getStatus())) {
            vo.setDraftItems(safeList(draftItemMapper.selectByInspectionId(inspection.getId())).stream()
                    .map(this::toDraftItemVO)
                    .toList());
        } else {
            vo.setDraftItems(Collections.emptyList());
        }
        if (STATUS_SUBMITTED.equals(inspection.getStatus())) {
            Map<String, Integer> counts = issueCounts(inspection.getId());
            vo.setPendingCount(counts.get(QualityIssueService.STATUS_PENDING));
            vo.setRecheckCount(counts.get(QualityIssueService.STATUS_RECHECK));
            vo.setClosedCount(counts.get(QualityIssueService.STATUS_CLOSED));
            vo.setVoidedCount(counts.get(QualityIssueService.STATUS_VOIDED));
            if (includeDetails) {
                List<QualityIssueVO> issues = safeList(issueMapper.selectList(
                                new LambdaQueryWrapper<QualityIssue>()
                                        .eq(QualityIssue::getWeeklyInspectionId, inspection.getId())
                                        .orderByAsc(QualityIssue::getInspectionItemOrder)
                                        .orderByAsc(QualityIssue::getId)))
                        .stream()
                        .map(issue -> qualityIssueService.toWeeklyIssueVO(issue, currentUser, true))
                        .toList();
                vo.setIssues(issues);
            } else {
                vo.setIssues(Collections.emptyList());
            }
        } else {
            vo.setIssues(Collections.emptyList());
        }
        return vo;
    }

    private QualityWeeklyDraftItemVO toDraftItemVO(QualityWeeklyInspectionDraftItem item) {
        QualityWeeklyDraftItemVO vo = new QualityWeeklyDraftItemVO();
        BeanUtils.copyProperties(item, vo);
        vo.setBeforePhotoFileIds(weeklyFileService.listFileIds(
                QualityWeeklyFileService.WEEKLY_DRAFT_ITEM, item.getId()));
        return vo;
    }

    private Map<String, Integer> issueCounts(Long inspectionId) {
        Map<String, Integer> counts = new HashMap<>();
        for (String status : List.of(
                QualityIssueService.STATUS_PENDING,
                QualityIssueService.STATUS_RECHECK,
                QualityIssueService.STATUS_CLOSED,
                QualityIssueService.STATUS_VOIDED)) {
            Long count = issueMapper.selectCount(new LambdaQueryWrapper<QualityIssue>()
                    .eq(QualityIssue::getWeeklyInspectionId, inspectionId)
                    .eq(QualityIssue::getStatus, status));
            counts.put(status, count == null ? 0 : Math.toIntExact(count));
        }
        return counts;
    }

    private void validateSubmitItem(QualityWeeklyInspectionDraftItem item) {
        if (!StringUtils.hasText(item.getTitle())) {
            throw new BusinessException("每个周检问题都必须填写标题");
        }
        if (item.getTitle().trim().length() > 200) {
            throw new BusinessException("质量问题标题长度不能超过200个字符");
        }
        if (!StringUtils.hasText(item.getSeverity())) {
            throw new BusinessException("每个周检问题都必须选择严重程度");
        }
        normalizeSeverity(item.getSeverity());
        if (item.getAssigneeId() == null) {
            throw new BusinessException("每个周检问题都必须选择整改负责人");
        }
        if (item.getDeadline() == null) {
            throw new BusinessException("每个周检问题都必须填写闭环期限");
        }
        if (item.getDeadline().isBefore(today())) {
            throw new BusinessException("闭环期限不能早于提交日");
        }
    }

    private void prevalidateDraftItems(List<QualityWeeklyDraftItemRequest> items) {
        Set<String> itemKeys = new HashSet<>();
        Set<Integer> itemOrders = new HashSet<>();
        int sequence = 0;
        for (QualityWeeklyDraftItemRequest item : items) {
            sequence++;
            if (item == null) throw new BusinessException("问题草稿项不能为空");
            Integer itemOrder = item.getItemOrder() == null ? sequence : item.getItemOrder();
            if (itemOrder < 1 || itemOrder > MAX_ISSUES) {
                throw new BusinessException("问题顺序必须在1到50之间");
            }
            if (!itemOrders.add(itemOrder)) {
                throw new BusinessException("同一周检内问题顺序不能重复");
            }
            String itemKey = normalizeItemKey(item.getItemKey());
            if (itemKey != null && !itemKeys.add(itemKey)) {
                throw new BusinessException("同一周检内问题草稿键不能重复");
            }
            validatePhotoCount(item.getBeforePhotoFileIds(), "整改前照片", true);
            normalizeDraftSeverity(item.getSeverity());
        }
    }

    private void validateInspectionDate(LocalDate inspectionDate, LocalDate weekStart, boolean allowNull) {
        if (inspectionDate == null) {
            if (!allowNull) throw new BusinessException("检查日期不能为空");
            return;
        }
        LocalDate weekEnd = weekStart.plusDays(6);
        if (inspectionDate.isBefore(weekStart) || inspectionDate.isAfter(weekEnd)) {
            throw new BusinessException("检查日期必须位于所选自然周内");
        }
        if (inspectionDate.isAfter(today())) {
            throw new BusinessException("检查日期不能晚于今天");
        }
    }

    private void validatePhotoCount(List<Long> ids, String label, boolean allowEmpty) {
        List<Long> values = nullToEmpty(ids);
        if (!allowEmpty && values.isEmpty()) {
            throw new BusinessException("请至少上传一张" + label);
        }
        if (values.size() > MAX_PHOTOS) {
            throw new BusinessException(label + "不能超过20张");
        }
        if (values.stream().anyMatch(id -> id == null || id <= 0)
                || new HashSet<>(values).size() != values.size()) {
            throw new BusinessException(label + "包含重复或无效文件");
        }
    }

    private void writeCreateLog(QualityIssue issue, SysUser currentUser, List<Long> photoIds) {
        QualityIssueLog log = new QualityIssueLog();
        log.setIssueId(issue.getId());
        log.setProjectId(issue.getProjectId());
        log.setActionType("CREATE");
        log.setToStatus(QualityIssueService.STATUS_PENDING);
        log.setOperatorId(currentUser.getId());
        log.setOperatorName(displayName(currentUser));
        log.setComment(issue.getDescription());
        log.setPhotoFileIds(joinIds(photoIds));
        log.setCreateTime(now());
        requireSingleWrite(logMapper.insert(log), "质量周检问题日志写入");
    }

    private void recordOperation(SysUser user, String operationType, Long businessId, String description) {
        OperationLog log = new OperationLog();
        log.setUserId(user.getId());
        log.setUsername(displayName(user));
        log.setOperationType(operationType);
        log.setOperationDesc(description);
        log.setBusinessType("QUALITY_WEEKLY_INSPECTION");
        log.setBusinessId(businessId);
        log.setCreateTime(now());
        requireSingleWrite(operationLogMapper.insert(log), "质量周检操作日志写入");
    }

    private void notifyAssigneesAfterCommit(List<QualityIssue> issues) {
        Runnable notification = () -> issues.forEach(issue -> {
            try {
                wechatNotificationService.notifyUser(issue.getAssigneeId(), "RECTIFICATION_PENDING",
                        "QUALITY_ISSUE", issue.getId(), "质量问题待整改：" + issue.getTitle());
            } catch (RuntimeException ignored) {
                // 站内待办由问题状态派生；微信通知失败不影响已提交周检。
            }
        });
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    notification.run();
                }
            });
        } else {
            notification.run();
        }
    }

    private void requireReadable(SysUser currentUser, QualityWeeklyInspection inspection) {
        requireView(currentUser, inspection.getProjectId());
        if (STATUS_DRAFT.equals(inspection.getStatus()) && !canManage(currentUser, inspection.getProjectId())) {
            throw BusinessException.forbidden("无权查看质量周检草稿");
        }
    }

    private void requireView(SysUser currentUser, Long projectId) {
        if (currentUser == null || currentUser.getId() == null) {
            throw BusinessException.unauthorized("请先登录");
        }
        if (projectId == null) {
            throw new BusinessException("项目ID不能为空");
        }
        projectPermissionService.checkProjectPermission(currentUser.getId(), projectId);
        projectPermissionService.requireSystemPermission(
                currentUser.getId(), projectId, SystemPermissionCodes.QUALITY_VIEW);
    }

    private void requireManage(SysUser currentUser, Long projectId) {
        requireView(currentUser, projectId);
        if (!canManage(currentUser, projectId)) {
            throw BusinessException.forbidden("无质量管理权限");
        }
    }

    private boolean canManage(SysUser currentUser, Long projectId) {
        return currentUser != null && currentUser.getId() != null
                && projectPermissionService.canManageQuality(currentUser.getId(), projectId);
    }

    private QualityWeeklyInspection requireInspection(Long id) {
        QualityWeeklyInspection inspection = id == null ? null : inspectionMapper.selectById(id);
        if (inspection == null) throw BusinessException.notFound("质量周检不存在");
        return inspection;
    }

    private QualityWeeklyInspection requireInspectionForUpdate(Long id) {
        QualityWeeklyInspection inspection = id == null ? null : inspectionMapper.selectForUpdate(id);
        if (inspection == null) throw BusinessException.notFound("质量周检不存在");
        return inspection;
    }

    private void requireDraftAndVersion(QualityWeeklyInspection inspection, Integer expectedVersion) {
        if (!STATUS_DRAFT.equals(inspection.getStatus())) {
            throw stateConflict("已提交周检不可修改");
        }
        if (!Objects.equals(versionOf(inspection), expectedVersion)) {
            throw stateConflict("周检草稿已被其他人修改");
        }
    }

    private void rejectFutureWeek(LocalDate weekStart) {
        if (weekStart.isAfter(currentWeekStart())) {
            throw new BusinessException("不能创建未来周的质量周检");
        }
    }

    private LocalDate normalizeWeekStart(LocalDate value) {
        return value.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private LocalDate currentWeekStart() {
        return normalizeWeekStart(today());
    }

    private LocalDate today() {
        return LocalDate.now(BUSINESS_ZONE);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(BUSINESS_ZONE);
    }

    private boolean isLateSubmission(QualityWeeklyInspection inspection) {
        return STATUS_SUBMITTED.equals(inspection.getStatus())
                && inspection.getSubmittedTime() != null
                && inspection.getWeekStart() != null
                && inspection.getSubmittedTime().toLocalDate().isAfter(inspection.getWeekStart().plusDays(6));
    }

    private String normalizeStatus(String status) {
        String normalized = status.trim().toUpperCase();
        if (!List.of(STATUS_DRAFT, STATUS_SUBMITTED).contains(normalized)) {
            throw new BusinessException("质量周检状态不支持");
        }
        return normalized;
    }

    private String normalizeDraftSeverity(String severity) {
        return StringUtils.hasText(severity) ? normalizeSeverity(severity) : null;
    }

    private String normalizeSeverity(String severity) {
        if (!StringUtils.hasText(severity)) return "NORMAL";
        String normalized = severity.trim().toUpperCase();
        if (!List.of("NORMAL", "WARNING", "DANGER").contains(normalized)) {
            throw new BusinessException("严重程度只支持 NORMAL、WARNING 或 DANGER");
        }
        return normalized;
    }

    private String normalizeItemKey(String itemKey) {
        if (!StringUtils.hasText(itemKey)) return null;
        String normalized = itemKey.trim();
        if (normalized.length() > 100) {
            throw new BusinessException("问题草稿键长度不能超过100个字符");
        }
        return normalized;
    }

    private String generateInspectionNo(LocalDate weekStart) {
        return "QW-" + weekStart.toString().replace("-", "") + "-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private String generateIssueNo() {
        return "Q-" + UUID.randomUUID().toString().replace("-", "");
    }

    private String displayName(SysUser user) {
        return StringUtils.hasText(user.getRealName()) ? user.getRealName() : user.getUsername();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String joinIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return null;
        return ids.stream().map(String::valueOf).reduce((left, right) -> left + "," + right).orElse(null);
    }

    private int versionOf(QualityWeeklyInspection inspection) {
        return numberOrZero(inspection.getVersion());
    }

    private int numberOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private void requireSingleWrite(int affected, String action) {
        if (affected != 1) throw stateConflict(action + "未完整生效");
    }

    private BusinessException stateConflict(String message) {
        return BusinessException.of(409, message + "，请刷新后重试");
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? Collections.emptyList() : values;
    }

    private List<Long> nullToEmpty(List<Long> values) {
        return values == null ? Collections.emptyList() : values;
    }
}
