package com.example.siteplatform.file.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.file.entity.FileResource;
import com.example.siteplatform.file.mapper.FileResourceMapper;
import com.example.siteplatform.file.storage.FileStorageManager;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.system.constant.SystemPermissionCodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Owns attachment transitions for the collaborative quality-weekly draft.
 *
 * <p>Draft files are bound business attachments and therefore survive the
 * ordinary 24-hour staging cleanup. Only this service may replace, transfer or
 * discard them; the generic file endpoints keep rejecting writes to bound
 * workflow files.</p>
 */
@Service
public class QualityWeeklyFileService {
    public static final String WEEKLY_OVERVIEW_PENDING = "QUALITY_WEEKLY_PENDING";
    public static final String WEEKLY_ITEM_PENDING = "QUALITY_WEEKLY_ITEM_PENDING";
    public static final String WEEKLY_DRAFT = "QUALITY_WEEKLY_DRAFT";
    public static final String WEEKLY_DRAFT_ITEM = "QUALITY_WEEKLY_DRAFT_ITEM";
    public static final String WEEKLY_FINAL = "QUALITY_WEEKLY_INSPECTION";
    public static final String ISSUE_FINAL = "QUALITY_ISSUE";

    private static final Logger LOGGER = LoggerFactory.getLogger(QualityWeeklyFileService.class);
    private static final Set<String> ALLOWED_PENDING_TYPES = Set.of(
            WEEKLY_OVERVIEW_PENDING, WEEKLY_ITEM_PENDING);
    private static final Set<String> ALLOWED_DRAFT_TYPES = Set.of(
            WEEKLY_DRAFT, WEEKLY_DRAFT_ITEM);
    private static final Set<String> ALLOWED_FINAL_TYPES = Set.of(
            WEEKLY_FINAL, ISSUE_FINAL);

    private final FileResourceMapper fileMapper;
    private final FileStorageManager storageManager;
    private final ProjectPermissionService permissionService;

    public QualityWeeklyFileService(FileResourceMapper fileMapper,
                                    FileStorageManager storageManager,
                                    ProjectPermissionService permissionService) {
        this.fileMapper = fileMapper;
        this.storageManager = storageManager;
        this.permissionService = permissionService;
    }

    /**
     * Replaces the complete file selection for one draft owner (inspection or
     * item). Existing bound files may be retained by any project quality
     * manager; newly uploaded staging files must belong to the current editor.
     */
    public List<Long> syncDraftFiles(SysUser currentUser,
                                     Long projectId,
                                     List<Long> requestedIds,
                                     String pendingType,
                                     String draftType,
                                     Long draftBusinessId) {
        requireManage(currentUser, projectId);
        String pending = requireType(pendingType, ALLOWED_PENDING_TYPES, "不支持的周检暂存附件类型");
        String draft = requireType(draftType, ALLOWED_DRAFT_TYPES, "不支持的周检草稿附件类型");
        requireMatchingStage(pending, draft);
        if (draftBusinessId == null || draftBusinessId <= 0) {
            throw new BusinessException("周检草稿附件绑定参数不完整");
        }

        List<Long> requested = normalizeIds(requestedIds);
        List<FileResource> existing = fileMapper.selectWeeklyDraftFilesForUpdate(
                projectId, draft, draftBusinessId);
        List<FileResource> requestedFiles = requested.isEmpty()
                ? List.of()
                : fileMapper.selectByIdsForUpdate(requested);
        if (requestedFiles.size() != requested.size()) {
            throw new BusinessException("部分周检草稿附件不存在");
        }

        for (FileResource file : requestedFiles) {
            validateProjectAndStatus(file, projectId);
            String actualType = normalize(file.getBusinessType());
            if (draft.equals(actualType) && Objects.equals(draftBusinessId, file.getBusinessId())) {
                continue;
            }
            if (!pending.equals(actualType) || file.getBusinessId() != null) {
                throw new BusinessException("周检草稿附件已关联其他业务记录");
            }
            if (!Objects.equals(currentUser.getId(), file.getUploaderId())) {
                throw BusinessException.forbidden("只能把本人刚上传的照片加入周检草稿");
            }
            if (fileMapper.bindWeeklyPendingFile(file.getId(), projectId, pending, draft,
                    draftBusinessId, currentUser.getId()) != 1) {
                throw BusinessException.of(409, "周检草稿附件状态已变化，请重新上传后保存");
            }
        }

        Set<Long> retained = new LinkedHashSet<>(requested);
        List<FileResource> removed = existing.stream()
                .filter(file -> !retained.contains(file.getId()))
                .toList();
        stageForDeletion(removed, projectId, draft, draftBusinessId);
        registerCommittedPurge(removed);
        return requested;
    }

    /** Moves every bound file for a draft owner to its immutable final owner. */
    public List<Long> transferDraftFiles(Long projectId,
                                         String draftType,
                                         Long draftBusinessId,
                                         String targetType,
                                         Long targetBusinessId) {
        String draft = requireType(draftType, ALLOWED_DRAFT_TYPES, "不支持的周检草稿附件类型");
        String target = requireType(targetType, ALLOWED_FINAL_TYPES, "不支持的周检正式附件类型");
        requireMatchingTarget(draft, target);
        if (projectId == null || draftBusinessId == null || targetBusinessId == null) {
            throw new BusinessException("周检附件提交参数不完整");
        }
        List<FileResource> files = fileMapper.selectWeeklyDraftFilesForUpdate(
                projectId, draft, draftBusinessId);
        for (FileResource file : files) {
            if (fileMapper.transferWeeklyDraftFile(file.getId(), projectId, draft,
                    draftBusinessId, target, targetBusinessId) != 1) {
                throw BusinessException.of(409, "周检附件状态已变化，请刷新草稿后重试");
            }
        }
        return files.stream().map(FileResource::getId).toList();
    }

    public List<Long> listFileIds(String businessType, Long businessId) {
        if (!StringUtils.hasText(businessType) || businessId == null) return List.of();
        return fileMapper.selectList(new LambdaQueryWrapper<FileResource>()
                        .eq(FileResource::getBusinessType, normalize(businessType))
                        .eq(FileResource::getBusinessId, businessId)
                        .eq(FileResource::getStatus, "UPLOADED")
                        .orderByAsc(FileResource::getCreateTime)
                        .orderByAsc(FileResource::getId))
                .stream().map(FileResource::getId).toList();
    }

    /** Discards all attachments belonging to the supplied draft owners. */
    public void discardDraftFiles(Long projectId,
                                  String draftType,
                                  Collection<Long> businessIds) {
        String draft = requireType(draftType, ALLOWED_DRAFT_TYPES, "不支持的周检草稿附件类型");
        if (projectId == null || businessIds == null || businessIds.isEmpty()) return;
        List<Long> ids = businessIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .distinct()
                .sorted()
                .toList();
        List<FileResource> removed = new ArrayList<>();
        for (Long businessId : ids) {
            List<FileResource> files = fileMapper.selectWeeklyDraftFilesForUpdate(
                    projectId, draft, businessId);
            stageForDeletion(files, projectId, draft, businessId);
            removed.addAll(files);
        }
        registerCommittedPurge(removed);
    }

    private void requireManage(SysUser user, Long projectId) {
        if (user == null || user.getId() == null || projectId == null) {
            throw new BusinessException("周检附件操作参数不完整");
        }
        permissionService.checkProjectPermission(user.getId(), projectId);
        permissionService.requireSystemPermission(
                user.getId(), projectId, SystemPermissionCodes.QUALITY_VIEW);
        permissionService.requireSystemPermission(
                user.getId(), projectId, SystemPermissionCodes.QUALITY_MANAGE);
    }

    private List<Long> normalizeIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        List<Long> normalized = ids.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .toList();
        if (normalized.size() != ids.size()
                || new LinkedHashSet<>(normalized).size() != normalized.size()) {
            throw new BusinessException("周检附件列表包含重复或无效文件");
        }
        return normalized;
    }

    private void validateProjectAndStatus(FileResource file, Long projectId) {
        if (!Objects.equals(projectId, file.getProjectId())) {
            throw new BusinessException("周检附件不属于当前项目");
        }
        if (!"UPLOADED".equalsIgnoreCase(file.getStatus())) {
            throw BusinessException.of(409, "周检附件当前不可用，请重新上传");
        }
    }

    private void stageForDeletion(List<FileResource> files,
                                  Long projectId,
                                  String draftType,
                                  Long draftBusinessId) {
        for (FileResource file : files) {
            if (fileMapper.stageWeeklyDraftFileForDelete(file.getId(), projectId,
                    draftType, draftBusinessId) != 1) {
                throw BusinessException.of(409, "周检草稿附件状态已变化，请刷新后重试");
            }
            file.setDeleted(1);
            file.setStatus("PENDING_DELETE");
            file.setUpdateTime(LocalDateTime.now());
        }
    }

    private void registerCommittedPurge(List<FileResource> files) {
        if (files == null || files.isEmpty()) return;
        Runnable purge = () -> files.forEach(this::purgePhysicalFile);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            purge.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                purge.run();
            }
        });
    }

    private void purgePhysicalFile(FileResource file) {
        try {
            storageManager.delete(file);
            if (fileMapper.purgeById(file.getId()) != 1) {
                LOGGER.warn("周检草稿物理文件已删除，但元数据清理未完成: fileId={}", file.getId());
            }
        } catch (RuntimeException exception) {
            fileMapper.markPhysicalDeleteFailed(file.getId());
            LOGGER.error("周检草稿附件物理清理失败，保留 DELETE_FAILED 元数据: fileId={}",
                    file.getId(), exception);
        }
    }

    private String requireType(String value, Set<String> allowed, String message) {
        String normalized = normalize(value);
        if (!allowed.contains(normalized)) throw new BusinessException(message);
        return normalized;
    }

    private void requireMatchingStage(String pending, String draft) {
        boolean valid = (WEEKLY_OVERVIEW_PENDING.equals(pending) && WEEKLY_DRAFT.equals(draft))
                || (WEEKLY_ITEM_PENDING.equals(pending) && WEEKLY_DRAFT_ITEM.equals(draft));
        if (!valid) throw new BusinessException("周检附件暂存类型与草稿阶段不匹配");
    }

    private void requireMatchingTarget(String draft, String target) {
        boolean valid = (WEEKLY_DRAFT.equals(draft) && WEEKLY_FINAL.equals(target))
                || (WEEKLY_DRAFT_ITEM.equals(draft) && ISSUE_FINAL.equals(target));
        if (!valid) throw new BusinessException("周检草稿附件与正式业务类型不匹配");
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
