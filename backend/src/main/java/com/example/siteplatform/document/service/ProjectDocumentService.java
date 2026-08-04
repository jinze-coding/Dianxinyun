package com.example.siteplatform.document.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.common.PageResult;
import com.example.siteplatform.document.dto.ProjectDocumentBatchRequest;
import com.example.siteplatform.document.dto.ProjectDocumentClientActionRequest;
import com.example.siteplatform.document.dto.ProjectDocumentUpdateRequest;
import com.example.siteplatform.document.entity.DocumentFolder;
import com.example.siteplatform.document.entity.ProjectDocument;
import com.example.siteplatform.document.entity.ProjectDocumentVersion;
import com.example.siteplatform.document.mapper.DocumentFolderMapper;
import com.example.siteplatform.document.mapper.ProjectDocumentMapper;
import com.example.siteplatform.document.mapper.ProjectDocumentVersionMapper;
import com.example.siteplatform.document.vo.ProjectDocumentActivityVO;
import com.example.siteplatform.document.vo.ProjectDocumentDetailVO;
import com.example.siteplatform.document.vo.ProjectDocumentSummaryVO;
import com.example.siteplatform.document.vo.ProjectDocumentVO;
import com.example.siteplatform.document.vo.ProjectDocumentVersionVO;
import com.example.siteplatform.file.constant.FileStatus;
import com.example.siteplatform.file.entity.FileResource;
import com.example.siteplatform.file.mapper.FileResourceMapper;
import com.example.siteplatform.file.security.FileUploadPolicy;
import com.example.siteplatform.file.storage.FileStorageManager;
import com.example.siteplatform.file.storage.StoredFile;
import com.example.siteplatform.log.entity.OperationLog;
import com.example.siteplatform.log.mapper.OperationLogMapper;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.system.constant.SystemPermissionCodes;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class ProjectDocumentService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProjectDocumentService.class);
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_ARCHIVED = "ARCHIVED";
    private static final int DOCUMENT_NO_MAX_LENGTH = 100;
    private static final int REMARK_MAX_LENGTH = 500;
    private static final int CHANGE_NOTE_MAX_LENGTH = 500;
    private static final List<String> CATEGORIES = List.of(
            "PROJECT_DATA", "DRAWING", "FORM", "CONSTRUCTION_RECORD", "MEETING", "OTHER");

    private final ProjectDocumentMapper documentMapper;
    private final ProjectDocumentVersionMapper versionMapper;
    private final DocumentFolderMapper folderMapper;
    private final FileResourceMapper fileMapper;
    private final FileStorageManager storageManager;
    private final ProjectPermissionService permissionService;
    private final DocumentFolderService folderService;
    private final OperationLogMapper operationLogMapper;
    private final SysUserMapper userMapper;

    public ProjectDocumentService(ProjectDocumentMapper documentMapper,
                                  ProjectDocumentVersionMapper versionMapper,
                                  DocumentFolderMapper folderMapper,
                                  FileResourceMapper fileMapper,
                                  FileStorageManager storageManager,
                                  ProjectPermissionService permissionService,
                                  DocumentFolderService folderService,
                                  OperationLogMapper operationLogMapper,
                                  SysUserMapper userMapper) {
        this.documentMapper = documentMapper;
        this.versionMapper = versionMapper;
        this.folderMapper = folderMapper;
        this.fileMapper = fileMapper;
        this.storageManager = storageManager;
        this.permissionService = permissionService;
        this.folderService = folderService;
        this.operationLogMapper = operationLogMapper;
        this.userMapper = userMapper;
    }

    public PageResult<ProjectDocumentVO> list(Long projectId, Long folderId, String keyword,
                                               String category, String status, LocalDate startDate,
                                               LocalDate endDate, Integer pageNo, Integer pageSize,
                                               SysUser currentUser) {
        permissionService.checkProjectPermission(currentUser.getId(), projectId);
        permissionService.requireSystemPermission(currentUser.getId(), projectId, SystemPermissionCodes.DOCUMENT_VIEW);
        int page = pageNo == null ? 1 : Math.max(1, pageNo);
        int size = pageSize == null ? 20 : Math.max(1, Math.min(pageSize, 100));
        LambdaQueryWrapper<ProjectDocument> wrapper = new LambdaQueryWrapper<ProjectDocument>()
                .eq(ProjectDocument::getProjectId, projectId);
        if (folderId != null) wrapper.eq(ProjectDocument::getFolderId, folderId);
        if (StringUtils.hasText(keyword)) {
            String query = keyword.trim();
            wrapper.and(item -> item.like(ProjectDocument::getTitle, query)
                    .or().like(ProjectDocument::getDocumentNo, query)
                    .or().like(ProjectDocument::getRemark, query));
        }
        if (StringUtils.hasText(category)) wrapper.eq(ProjectDocument::getCategory, normalizeCategory(category));
        if (StringUtils.hasText(status)) wrapper.eq(ProjectDocument::getStatus, normalizeStatus(status));
        if (startDate != null) wrapper.ge(ProjectDocument::getUpdateTime, startDate.atStartOfDay());
        if (endDate != null) wrapper.lt(ProjectDocument::getUpdateTime, endDate.plusDays(1).atStartOfDay());
        wrapper.orderByDesc(ProjectDocument::getUpdateTime);
        Page<ProjectDocument> result = documentMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(page, size, result.getTotal(), result.getRecords().stream()
                .map(document -> toVO(document, currentUser)).toList());
    }

    public PageResult<ProjectDocumentVO> recycleBin(Long projectId, String keyword, Integer pageNo,
                                                     Integer pageSize, SysUser currentUser) {
        permissionService.checkProjectPermission(currentUser.getId(), projectId);
        permissionService.requireSystemPermission(currentUser.getId(), projectId, SystemPermissionCodes.DOCUMENT_VIEW);
        int page = pageNo == null ? 1 : Math.max(1, pageNo);
        int size = pageSize == null ? 20 : Math.max(1, Math.min(pageSize, 100));
        String query = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        List<ProjectDocument> filtered = documentMapper.selectDeletedByProject(projectId).stream()
                .filter(item -> query.isEmpty() || (item.getTitle() + " " + Objects.toString(item.getDocumentNo(), ""))
                        .toLowerCase(Locale.ROOT).contains(query))
                .toList();
        int from = Math.min((page - 1) * size, filtered.size());
        int to = Math.min(from + size, filtered.size());
        return PageResult.of(page, size, (long) filtered.size(), filtered.subList(from, to).stream()
                .map(document -> toVO(document, currentUser)).toList());
    }

    public ProjectDocumentSummaryVO summary(Long projectId, SysUser currentUser) {
        permissionService.checkProjectPermission(currentUser.getId(), projectId);
        permissionService.requireSystemPermission(currentUser.getId(), projectId, SystemPermissionCodes.DOCUMENT_VIEW);
        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
        ProjectDocumentSummaryVO summary = new ProjectDocumentSummaryVO();
        summary.setTotal(count(projectId, null, null));
        summary.setActive(count(projectId, null, STATUS_ACTIVE));
        summary.setDrawings(count(projectId, "DRAWING", null));
        summary.setForms(count(projectId, "FORM", null));
        summary.setArchived(count(projectId, null, STATUS_ARCHIVED));
        summary.setRecentUpdates(documentMapper.selectCount(new LambdaQueryWrapper<ProjectDocument>()
                .eq(ProjectDocument::getProjectId, projectId)
                .ge(ProjectDocument::getUpdateTime, weekAgo)));
        summary.setCanManage(canManage(currentUser, projectId));
        return summary;
    }

    @Transactional
    public ProjectDocumentDetailVO create(Long projectId, Long folderId, String documentNo, String title,
                                           String category, String remark, String changeNote, MultipartFile file,
                                           SysUser currentUser, HttpServletRequest request) {
        permissionService.checkProjectPermission(currentUser.getId(), projectId);
        permissionService.requireSystemPermission(currentUser.getId(), projectId, SystemPermissionCodes.DOCUMENT_UPLOAD);
        if (file == null || file.isEmpty()) throw new BusinessException("请选择需要上传的文件");
        FileUploadPolicy.validateProjectDocument(file);
        long targetFolderId = folderId == null ? 0L : folderId;
        folderService.validateFolder(projectId, targetFolderId);
        String normalizedTitle = normalizeTitle(title);
        String normalizedNo = normalizeOptional(documentNo, DOCUMENT_NO_MAX_LENGTH, "资料编号");
        String normalizedRemark = normalizeOptional(remark, REMARK_MAX_LENGTH, "资料备注");
        String normalizedChangeNote = normalizeOptional(changeNote, CHANGE_NOTE_MAX_LENGTH, "版本说明");
        assertDocumentIdentityAvailable(projectId, targetFolderId, normalizedTitle, normalizedNo, null);

        String storageKey = objectKey(projectId, file.getOriginalFilename());
        StoredFile stored = storageManager.store(storageKey, file);
        registerRollbackCleanup(stored);
        FileResource resource = createFileResource(projectId, normalizedTitle, normalizeCategory(category),
                normalizedRemark, currentUser, stored);
        requireSingleWrite(fileMapper.insert(resource), "资料文件元数据新增");

        ProjectDocument document = new ProjectDocument();
        document.setProjectId(projectId);
        document.setFolderId(targetFolderId);
        document.setDocumentNo(normalizedNo);
        document.setTitle(normalizedTitle);
        document.setCategory(normalizeCategory(category));
        document.setStatus(STATUS_ACTIVE);
        document.setCreatedBy(currentUser.getId());
        document.setCreatedByName(displayName(currentUser));
        document.setRemark(normalizedRemark);
        document.setDeleted(0);
        document.setCreateTime(LocalDateTime.now());
        document.setUpdateTime(LocalDateTime.now());
        requireSingleWrite(documentMapper.insert(document), "资料新增");

        ProjectDocumentVersion version = createVersion(
                document.getId(), 1, resource.getId(), normalizedChangeNote, currentUser);
        requireSingleWrite(versionMapper.insert(version), "资料版本新增");
        document.setCurrentVersionId(version.getId());
        requireSingleWrite(documentMapper.updateById(document), "资料当前版本更新");
        record(currentUser, document, "DOCUMENT_UPLOAD", "上传资料《" + document.getTitle() + "》V1", request);
        return detail(document.getId(), currentUser);
    }

    @Transactional
    public ProjectDocumentDetailVO uploadVersion(Long id, String changeNote, MultipartFile file,
                                                  SysUser currentUser, HttpServletRequest request) {
        if (file == null || file.isEmpty()) throw new BusinessException("请选择新版本文件");
        ProjectDocument document = requireDocument(id);
        checkRead(currentUser, document);
        permissionService.requireSystemPermission(currentUser.getId(), document.getProjectId(),
                SystemPermissionCodes.DOCUMENT_UPLOAD);
        if (!STATUS_ACTIVE.equals(document.getStatus())) throw new BusinessException("归档资料不能上传新版本");
        FileUploadPolicy.validateProjectDocument(file);
        String normalizedChangeNote = normalizeOptional(changeNote, CHANGE_NOTE_MAX_LENGTH, "版本说明");
        document = documentMapper.selectForUpdate(id);
        if (document == null) throw BusinessException.notFound("资料不存在");
        int nextVersion = versionMapper.selectMaxVersionNo(id) + 1;
        String storageKey = objectKey(document.getProjectId(), file.getOriginalFilename());
        StoredFile stored = storageManager.store(storageKey, file);
        registerRollbackCleanup(stored);
        FileResource resource = createFileResource(document.getProjectId(), document.getTitle(),
                document.getCategory(), document.getRemark(), currentUser, stored);
        requireSingleWrite(fileMapper.insert(resource), "资料文件元数据新增");
        ProjectDocumentVersion version = createVersion(
                id, nextVersion, resource.getId(), normalizedChangeNote, currentUser);
        requireSingleWrite(versionMapper.insert(version), "资料版本新增");
        document.setCurrentVersionId(version.getId());
        document.setUpdateTime(LocalDateTime.now());
        requireSingleWrite(documentMapper.updateById(document), "资料当前版本更新");
        record(currentUser, document, "DOCUMENT_VERSION", "上传《" + document.getTitle() + "》V" + nextVersion, request);
        return detail(id, currentUser);
    }

    @Transactional
    public ProjectDocumentDetailVO update(Long id, ProjectDocumentUpdateRequest update,
                                           SysUser currentUser, HttpServletRequest request) {
        ProjectDocument document = requireDocument(id);
        checkWrite(currentUser, document);
        if (update == null) throw new BusinessException("资料信息不能为空");
        if (!STATUS_ACTIVE.equals(document.getStatus())) throw new BusinessException("归档资料不能修改");
        long folderId = update.getFolderId() == null ? document.getFolderId() : update.getFolderId();
        folderService.validateFolder(document.getProjectId(), folderId);
        String title = update.getTitle() == null ? document.getTitle() : normalizeTitle(update.getTitle());
        String documentNo = update.getDocumentNo() == null ? document.getDocumentNo()
                : normalizeOptional(update.getDocumentNo(), DOCUMENT_NO_MAX_LENGTH, "资料编号");
        String remark = update.getRemark() == null ? document.getRemark()
                : normalizeOptional(update.getRemark(), REMARK_MAX_LENGTH, "资料备注");
        assertDocumentIdentityAvailable(document.getProjectId(), folderId, title, documentNo, id);
        document.setFolderId(folderId);
        document.setTitle(title);
        document.setDocumentNo(documentNo);
        if (update.getCategory() != null) document.setCategory(normalizeCategory(update.getCategory()));
        document.setRemark(remark);
        document.setUpdateTime(LocalDateTime.now());
        requireSingleWrite(documentMapper.updateById(document), "资料编辑");
        record(currentUser, document, "DOCUMENT_UPDATE", "修改资料《" + document.getTitle() + "》", request);
        return detail(id, currentUser);
    }

    @Transactional
    public void archive(Long id, SysUser currentUser, HttpServletRequest request) {
        ProjectDocument document = requireDocument(id);
        checkWrite(currentUser, document);
        document.setStatus(STATUS_ARCHIVED);
        document.setUpdateTime(LocalDateTime.now());
        requireSingleWrite(documentMapper.updateById(document), "资料归档");
        record(currentUser, document, "DOCUMENT_ARCHIVE", "归档资料《" + document.getTitle() + "》", request);
    }

    @Transactional
    public void unarchive(Long id, SysUser currentUser, HttpServletRequest request) {
        ProjectDocument document = requireDocument(id);
        checkManage(currentUser, document.getProjectId());
        document.setStatus(STATUS_ACTIVE);
        document.setUpdateTime(LocalDateTime.now());
        requireSingleWrite(documentMapper.updateById(document), "资料取消归档");
        record(currentUser, document, "DOCUMENT_UNARCHIVE", "恢复归档资料《" + document.getTitle() + "》", request);
    }

    @Transactional
    public void delete(Long id, SysUser currentUser, HttpServletRequest request) {
        ProjectDocument document = requireDocument(id);
        checkWrite(currentUser, document);
        document.setUpdateTime(LocalDateTime.now());
        requireSingleWrite(documentMapper.updateById(document), "资料删除时间更新");
        requireSingleWrite(documentMapper.deleteById(id), "资料移入回收站");
        record(currentUser, document, "DOCUMENT_DELETE", "将资料《" + document.getTitle() + "》移入回收站", request);
    }

    @Transactional
    public void restore(Long id, SysUser currentUser, HttpServletRequest request) {
        ProjectDocument document = requireDeletedDocument(id);
        checkManage(currentUser, document.getProjectId());
        folderService.validateFolder(document.getProjectId(), document.getFolderId());
        assertDocumentIdentityAvailable(document.getProjectId(), document.getFolderId(), document.getTitle(), document.getDocumentNo(), id);
        requireSingleWrite(documentMapper.restoreById(id), "资料恢复");
        record(currentUser, document, "DOCUMENT_RESTORE", "从回收站恢复《" + document.getTitle() + "》", request);
    }

    @Transactional
    public void purge(Long id, SysUser currentUser, HttpServletRequest request) {
        ProjectDocument document = requireDeletedDocument(id);
        checkManage(currentUser, document.getProjectId());
        List<ProjectDocumentVersion> versions = versionMapper.selectList(new LambdaQueryWrapper<ProjectDocumentVersion>()
                .eq(ProjectDocumentVersion::getDocumentId, id));
        List<FileResource> resources = new java.util.ArrayList<>();
        for (ProjectDocumentVersion version : versions) {
            FileResource resource = fileMapper.selectById(version.getFileResourceId());
            if (resource != null) {
                if (fileMapper.deleteById(resource.getId()) != 1) {
                    throw BusinessException.of(409, "资料文件状态已变化，请刷新后重试");
                }
                resources.add(resource);
            }
        }
        int deletedVersions = versionMapper.deleteByDocumentId(id);
        if (deletedVersions != versions.size()) {
            throw BusinessException.of(409, "资料版本状态已变化，请刷新后重试");
        }
        requireSingleWrite(documentMapper.purgeById(id), "资料永久删除");
        record(currentUser, document, "DOCUMENT_PURGE", "永久删除资料《" + document.getTitle() + "》", request);
        registerCommittedPurge(resources);
    }

    @Transactional
    public void batch(ProjectDocumentBatchRequest batch, SysUser currentUser, HttpServletRequest request) {
        String action = batch.getAction().trim().toUpperCase(Locale.ROOT);
        for (Long id : batch.getIds().stream().filter(Objects::nonNull).distinct().toList()) {
            if ("MOVE".equals(action)) {
                ProjectDocument document = requireDocument(id);
                checkWrite(currentUser, document);
                if (!STATUS_ACTIVE.equals(document.getStatus())) throw new BusinessException("归档资料不能移动");
                long folderId = batch.getFolderId() == null ? 0L : batch.getFolderId();
                folderService.validateFolder(document.getProjectId(), folderId);
                assertDocumentIdentityAvailable(document.getProjectId(), folderId, document.getTitle(), document.getDocumentNo(), id);
                document.setFolderId(folderId);
                document.setUpdateTime(LocalDateTime.now());
                requireSingleWrite(documentMapper.updateById(document), "资料移动");
                record(currentUser, document, "DOCUMENT_MOVE", "移动资料《" + document.getTitle() + "》", request);
            } else if ("ARCHIVE".equals(action)) {
                archive(id, currentUser, request);
            } else if ("DELETE".equals(action)) {
                delete(id, currentUser, request);
            } else {
                throw new BusinessException("不支持的批量操作");
            }
        }
    }

    public ProjectDocumentDetailVO detail(Long id, SysUser currentUser) {
        ProjectDocument document = requireDocument(id);
        checkRead(currentUser, document);
        ProjectDocumentDetailVO detail = new ProjectDocumentDetailVO();
        detail.setDocument(toVO(document, currentUser));
        detail.setVersions(versionMapper.selectList(new LambdaQueryWrapper<ProjectDocumentVersion>()
                        .eq(ProjectDocumentVersion::getDocumentId, id)
                        .orderByDesc(ProjectDocumentVersion::getVersionNo))
                .stream().map(this::toVersionVO).toList());
        detail.setActivities(activities(document.getProjectId(), id, 50, currentUser));
        return detail;
    }

    public List<ProjectDocumentActivityVO> activities(Long projectId, Long documentId, Integer limit,
                                                       SysUser currentUser) {
        permissionService.checkProjectPermission(currentUser.getId(), projectId);
        permissionService.requireSystemPermission(currentUser.getId(), projectId, SystemPermissionCodes.DOCUMENT_VIEW);
        int safeLimit = Math.max(1, Math.min(limit == null ? 50 : limit, 100));
        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<OperationLog>()
                .eq(OperationLog::getBusinessType, logBusinessType(projectId))
                .orderByDesc(OperationLog::getCreateTime)
                .last("LIMIT " + safeLimit);
        if (documentId != null) wrapper.eq(OperationLog::getBusinessId, documentId);
        return operationLogMapper.selectList(wrapper).stream().map(this::toActivityVO).toList();
    }

    public ProjectDocumentContent content(Long id, Long versionId, SysUser currentUser,
                                           HttpServletRequest request, boolean preview) {
        ProjectDocument document = requireDocument(id);
        checkRead(currentUser, document);
        ProjectDocumentVersion version = requireVersion(document, versionId);
        FileResource file = fileMapper.selectById(version.getFileResourceId());
        if (file == null) throw BusinessException.notFound("版本文件不存在");
        Resource resource = storageManager.load(file);
        record(currentUser, document, preview ? "DOCUMENT_PREVIEW" : "DOCUMENT_DOWNLOAD",
                (preview ? "预览" : "下载") + "《" + document.getTitle() + "》V" + version.getVersionNo(), request);
        return new ProjectDocumentContent(resource,
                StringUtils.hasText(file.getOriginalFileName()) ? file.getOriginalFileName() : file.getFileName(),
                StringUtils.hasText(file.getMimeType()) ? file.getMimeType() : "application/octet-stream",
                file.getFileSize() == null ? 0L : file.getFileSize());
    }

    @Transactional
    public ProjectDocumentActivityVO recordClientAction(Long id, ProjectDocumentClientActionRequest action,
                                                         SysUser currentUser, HttpServletRequest request) {
        ProjectDocument document = requireDocument(id);
        checkRead(currentUser, document);
        ProjectDocumentVersion version = requireVersion(document, action.getVersionId());
        String normalizedAction = normalizeClientAction(action.getAction());
        String versionLabel = "V" + version.getVersionNo();
        if ("OPEN_SAVE_MENU".equals(normalizedAction)) {
            return record(currentUser, document, "DOCUMENT_SAVE_MENU",
                    "打开《" + document.getTitle() + "》" + versionLabel + "保存菜单", request);
        }
        return record(currentUser, document, "DOCUMENT_SHARE",
                "发送《" + document.getTitle() + "》" + versionLabel + "给微信好友", request);
    }

    private long count(Long projectId, String category, String status) {
        LambdaQueryWrapper<ProjectDocument> wrapper = new LambdaQueryWrapper<ProjectDocument>()
                .eq(ProjectDocument::getProjectId, projectId);
        if (category != null) wrapper.eq(ProjectDocument::getCategory, category);
        if (status != null) wrapper.eq(ProjectDocument::getStatus, status);
        return documentMapper.selectCount(wrapper);
    }

    private ProjectDocument requireDocument(Long id) {
        ProjectDocument document = documentMapper.selectById(id);
        if (document == null) throw BusinessException.notFound("资料不存在");
        return document;
    }

    private ProjectDocument requireDeletedDocument(Long id) {
        ProjectDocument document = documentMapper.selectDeletedById(id);
        if (document == null) throw BusinessException.notFound("回收站资料不存在");
        return document;
    }

    private ProjectDocumentVersion requireVersion(ProjectDocument document, Long versionId) {
        Long selectedVersionId = versionId == null ? document.getCurrentVersionId() : versionId;
        ProjectDocumentVersion version = selectedVersionId == null ? null : versionMapper.selectById(selectedVersionId);
        if (version == null || !document.getId().equals(version.getDocumentId())) {
            throw BusinessException.notFound("资料版本不存在");
        }
        return version;
    }

    private void checkRead(SysUser currentUser, ProjectDocument document) {
        permissionService.checkProjectPermission(currentUser.getId(), document.getProjectId());
        permissionService.requireSystemPermission(currentUser.getId(), document.getProjectId(),
                SystemPermissionCodes.DOCUMENT_VIEW);
    }

    private void checkWrite(SysUser currentUser, ProjectDocument document) {
        checkRead(currentUser, document);
        permissionService.requireSystemPermission(currentUser.getId(), document.getProjectId(),
                SystemPermissionCodes.DOCUMENT_MANAGE);
        if (canManage(currentUser, document.getProjectId()) || currentUser.getId().equals(document.getCreatedBy())) return;
        throw BusinessException.forbidden("无资料管理权限");
    }

    private void checkManage(SysUser currentUser, Long projectId) {
        permissionService.checkProjectPermission(currentUser.getId(), projectId);
        permissionService.requireSystemPermission(currentUser.getId(), projectId, SystemPermissionCodes.DOCUMENT_MANAGE);
        if (!canManage(currentUser, projectId)) throw BusinessException.forbidden("仅项目管理员可执行此操作");
    }

    private boolean canManage(SysUser currentUser, Long projectId) {
        return permissionService.isPlatformAdmin(currentUser.getId())
                || permissionService.canManageProject(currentUser.getId(), projectId);
    }

    private void registerRollbackCleanup(StoredFile stored) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                    storageManager.deleteQuietly(stored.provider(), stored.storageKey());
                }
            }
        });
    }

    private void registerCommittedPurge(List<FileResource> resources) {
        if (resources.isEmpty()) return;
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            purgePhysicalFiles(resources);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                purgePhysicalFiles(resources);
            }
        });
    }

    private void purgePhysicalFiles(List<FileResource> resources) {
        for (FileResource resource : resources) {
            try {
                storageManager.delete(resource);
                if (fileMapper.purgeById(resource.getId()) != 1) {
                    LOGGER.warn("资料物理文件已删除，但文件元数据清理未完成: fileId={}", resource.getId());
                }
            } catch (RuntimeException exception) {
                LOGGER.error("资料永久删除后的物理文件清理失败，保留逻辑删除元数据: fileId={}",
                        resource.getId(), exception);
            }
        }
    }

    private void assertDocumentIdentityAvailable(Long projectId, Long folderId, String title,
                                                 String documentNo, Long excludedId) {
        LambdaQueryWrapper<ProjectDocument> titleWrapper = new LambdaQueryWrapper<ProjectDocument>()
                .eq(ProjectDocument::getProjectId, projectId)
                .eq(ProjectDocument::getFolderId, folderId)
                .eq(ProjectDocument::getTitle, title);
        if (excludedId != null) titleWrapper.ne(ProjectDocument::getId, excludedId);
        if (documentMapper.selectCount(titleWrapper) > 0) throw new BusinessException("当前目录已存在同名资料");
        if (StringUtils.hasText(documentNo)) {
            LambdaQueryWrapper<ProjectDocument> noWrapper = new LambdaQueryWrapper<ProjectDocument>()
                    .eq(ProjectDocument::getProjectId, projectId)
                    .eq(ProjectDocument::getDocumentNo, documentNo);
            if (excludedId != null) noWrapper.ne(ProjectDocument::getId, excludedId);
            if (documentMapper.selectCount(noWrapper) > 0) throw new BusinessException("当前作业区域已存在相同资料编号");
        }
    }

    private FileResource createFileResource(Long projectId, String title, String category, String remark,
                                            SysUser user, StoredFile stored) {
        FileResource resource = new FileResource();
        resource.setProjectId(projectId);
        resource.setFileName(stored.originalFileName() == null ? title : stored.originalFileName());
        resource.setFileType(category);
        resource.setFilePath(stored.storageKey());
        resource.setFileSize(stored.size());
        resource.setBusinessType("PROJECT_DOCUMENT");
        resource.setUploaderId(user.getId());
        resource.setStorageProvider(stored.provider());
        resource.setStorageKey(stored.storageKey());
        resource.setOriginalFileName(stored.originalFileName());
        resource.setMimeType(stored.mimeType());
        resource.setFileExtension(stored.extension());
        resource.setSha256(stored.sha256());
        resource.setStatus(FileStatus.UPLOADED);
        resource.setRemark(trimToNull(remark));
        resource.setDeleted(0);
        resource.setCreateTime(LocalDateTime.now());
        resource.setUpdateTime(LocalDateTime.now());
        return resource;
    }

    private ProjectDocumentVersion createVersion(Long documentId, int versionNo, Long fileResourceId,
                                                 String changeNote, SysUser user) {
        ProjectDocumentVersion version = new ProjectDocumentVersion();
        version.setDocumentId(documentId);
        version.setVersionNo(versionNo);
        version.setFileResourceId(fileResourceId);
        version.setChangeNote(trimToNull(changeNote));
        version.setCreatedBy(user.getId());
        version.setCreatedByName(displayName(user));
        version.setCreateTime(LocalDateTime.now());
        return version;
    }

    private ProjectDocumentVO toVO(ProjectDocument document, SysUser currentUser) {
        ProjectDocumentVO vo = new ProjectDocumentVO();
        vo.setId(document.getId());
        vo.setProjectId(document.getProjectId());
        vo.setFolderId(document.getFolderId());
        if (document.getFolderId() != null && document.getFolderId() != 0L) {
            DocumentFolder folder = folderMapper.selectById(document.getFolderId());
            vo.setFolderName(folder == null ? "已删除目录" : folder.getFolderName());
        } else {
            vo.setFolderName("根目录");
        }
        vo.setDocumentNo(document.getDocumentNo());
        vo.setTitle(document.getTitle());
        vo.setCategory(document.getCategory());
        vo.setStatus(document.getStatus());
        vo.setRemark(document.getRemark());
        vo.setCreatedBy(document.getCreatedBy());
        vo.setCreatedByName(currentAccountName(document.getCreatedBy(), document.getCreatedByName()));
        vo.setCreateTime(document.getCreateTime());
        vo.setUpdateTime(document.getUpdateTime());
        ProjectDocumentVersion currentVersion = document.getCurrentVersionId() == null
                ? null : versionMapper.selectById(document.getCurrentVersionId());
        vo.setCurrentVersion(currentVersion == null ? null : toVersionVO(currentVersion));
        boolean manager = canManage(currentUser, document.getProjectId());
        vo.setCanManage(manager);
        vo.setCanEdit(STATUS_ACTIVE.equals(document.getStatus())
                && (manager || currentUser.getId().equals(document.getCreatedBy())));
        return vo;
    }

    private ProjectDocumentVersionVO toVersionVO(ProjectDocumentVersion version) {
        ProjectDocumentVersionVO vo = new ProjectDocumentVersionVO();
        vo.setId(version.getId());
        vo.setVersionNo(version.getVersionNo());
        vo.setVersionLabel("V" + version.getVersionNo());
        vo.setFileResourceId(version.getFileResourceId());
        vo.setChangeNote(version.getChangeNote());
        vo.setCreatedBy(version.getCreatedBy());
        vo.setCreatedByName(currentAccountName(version.getCreatedBy(), version.getCreatedByName()));
        vo.setCreateTime(version.getCreateTime());
        FileResource file = fileMapper.selectById(version.getFileResourceId());
        if (file != null) {
            vo.setFileName(StringUtils.hasText(file.getOriginalFileName()) ? file.getOriginalFileName() : file.getFileName());
            vo.setMimeType(file.getMimeType());
            vo.setFileExtension(file.getFileExtension());
            vo.setFileSize(file.getFileSize());
            vo.setSha256(file.getSha256());
        }
        return vo;
    }

    private ProjectDocumentActivityVO toActivityVO(OperationLog log) {
        ProjectDocumentActivityVO vo = new ProjectDocumentActivityVO();
        vo.setId(log.getId());
        vo.setDocumentId(log.getBusinessId());
        vo.setOperationType(log.getOperationType());
        vo.setOperationLabel(activityLabel(log.getOperationType()));
        vo.setDescription(log.getOperationDesc());
        vo.setOperatorId(log.getUserId());
        vo.setOperatorName(log.getUsername());
        vo.setCreateTime(log.getCreateTime());
        return vo;
    }

    private ProjectDocumentActivityVO record(SysUser user, ProjectDocument document, String type, String description,
                                             HttpServletRequest request) {
        OperationLog log = new OperationLog();
        log.setUserId(user.getId());
        log.setUsername(displayName(user));
        log.setOperationType(type);
        log.setOperationDesc(description);
        log.setBusinessType(logBusinessType(document.getProjectId()));
        log.setBusinessId(document.getId());
        log.setIpAddress(resolveIp(request));
        log.setCreateTime(LocalDateTime.now());
        requireSingleWrite(operationLogMapper.insert(log), "资料操作日志写入");
        return toActivityVO(log);
    }

    private String activityLabel(String type) {
        return switch (Objects.toString(type, "")) {
            case "DOCUMENT_UPLOAD" -> "上传";
            case "DOCUMENT_PREVIEW" -> "预览";
            case "DOCUMENT_DOWNLOAD" -> "下载";
            case "DOCUMENT_SAVE_MENU" -> "打开保存菜单";
            case "DOCUMENT_SHARE" -> "发送文件";
            case "DOCUMENT_UPDATE" -> "修改";
            case "DOCUMENT_MOVE" -> "移动";
            case "DOCUMENT_VERSION" -> "新版本";
            case "DOCUMENT_ARCHIVE" -> "归档";
            case "DOCUMENT_UNARCHIVE" -> "恢复归档";
            case "DOCUMENT_DELETE" -> "移入回收站";
            case "DOCUMENT_RESTORE" -> "恢复";
            case "DOCUMENT_PURGE" -> "永久删除";
            default -> "操作";
        };
    }

    private String normalizeClientAction(String value) {
        String action = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
        if (!"OPEN_SAVE_MENU".equals(action) && !"SHARE_WECHAT_FILE".equals(action)) {
            throw new BusinessException("不支持的客户端资料操作");
        }
        return action;
    }

    private String normalizeTitle(String value) {
        String title = value == null ? "" : value.trim();
        if (title.isEmpty()) throw new BusinessException("资料名称不能为空");
        if (title.length() > 200) throw new BusinessException("资料名称不能超过200个字符");
        return title;
    }

    private String normalizeCategory(String value) {
        String category = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "PROJECT_DATA";
        if (!CATEGORIES.contains(category)) throw new BusinessException("不支持的资料分类");
        return category;
    }

    private String normalizeStatus(String value) {
        String status = value.trim().toUpperCase(Locale.ROOT);
        if (!STATUS_ACTIVE.equals(status) && !STATUS_ARCHIVED.equals(status)) throw new BusinessException("不支持的资料状态");
        return status;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeOptional(String value, int maxLength, String fieldName) {
        String normalized = trimToNull(value);
        if (normalized != null && normalized.length() > maxLength) {
            throw new BusinessException(fieldName + "不能超过" + maxLength + "个字符");
        }
        return normalized;
    }

    private void requireSingleWrite(int affectedRows, String operation) {
        if (affectedRows != 1) {
            throw BusinessException.of(409, operation + "未生效，请刷新后重试");
        }
    }

    private String objectKey(Long projectId, String originalName) {
        String extension = "";
        if (StringUtils.hasText(originalName) && originalName.lastIndexOf('.') >= 0) {
            extension = originalName.substring(originalName.lastIndexOf('.')).toLowerCase(Locale.ROOT);
        }
        return "project-documents/" + projectId + "/" + LocalDate.now() + "/" + UUID.randomUUID() + extension;
    }

    private String displayName(SysUser user) {
        return StringUtils.hasText(user.getRealName()) ? user.getRealName() : user.getUsername();
    }

    private String currentAccountName(Long userId, String snapshotName) {
        if (userId != null) {
            SysUser user = userMapper.selectById(userId);
            if (user != null) return displayName(user);
        }
        if (StringUtils.hasText(snapshotName)) return snapshotName;
        return userId == null ? "未知用户" : "用户 " + userId;
    }

    private String logBusinessType(Long projectId) {
        return "PROJECT_DOCUMENT_" + projectId;
    }

    private String resolveIp(HttpServletRequest request) {
        if (request == null) return null;
        return request.getRemoteAddr();
    }
}
