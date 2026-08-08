package com.example.siteplatform.seal.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.document.service.DocumentImportResult;
import com.example.siteplatform.document.service.ProjectDocumentService;
import com.example.siteplatform.file.constant.FileStatus;
import com.example.siteplatform.file.entity.FileResource;
import com.example.siteplatform.file.mapper.FileResourceMapper;
import com.example.siteplatform.file.security.FileUploadPolicy;
import com.example.siteplatform.file.storage.FileStorageManager;
import com.example.siteplatform.file.storage.StoredFile;
import com.example.siteplatform.seal.dto.SealArchiveRequest;
import com.example.siteplatform.seal.entity.SealApplication;
import com.example.siteplatform.seal.entity.SealApplicationFile;
import com.example.siteplatform.seal.entity.SealApplicationItem;
import com.example.siteplatform.seal.mapper.SealApplicationFileMapper;
import com.example.siteplatform.seal.mapper.SealApplicationItemMapper;
import com.example.siteplatform.seal.vo.SealApplicationFileVO;
import com.example.siteplatform.seal.vo.SealApplicationVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class SealApplicationFileService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final SealApplicationService applicationService;
    private final SealApplicationFileMapper relationMapper;
    private final SealApplicationItemMapper itemMapper;
    private final FileResourceMapper fileMapper;
    private final FileStorageManager storageManager;
    private final ProjectDocumentService documentService;

    public SealApplicationFileService(SealApplicationService applicationService,
                                      SealApplicationFileMapper relationMapper,
                                      SealApplicationItemMapper itemMapper,
                                      FileResourceMapper fileMapper,
                                      FileStorageManager storageManager,
                                      ProjectDocumentService documentService) {
        this.applicationService = applicationService;
        this.relationMapper = relationMapper;
        this.itemMapper = itemMapper;
        this.fileMapper = fileMapper;
        this.storageManager = storageManager;
        this.documentService = documentService;
    }

    @Transactional
    public SealApplicationFileVO upload(Long applicationId, String fileRole, Long itemId, MultipartFile file,
                                        SysUser currentUser, HttpServletRequest request) {
        String role = normalizeRole(fileRole);
        // All mutating attachment operations lock the application before status
        // validation. In particular this prevents SOURCE upload from racing submit().
        SealApplication application = applicationService.requireApplicationForUpdate(applicationId);
        applicationService.requireReadable(application, currentUser);
        if ("SOURCE".equals(role)) {
            if (!SealApplicationService.DRAFT.equals(application.getStatus())
                    || !Objects.equals(application.getApplicantId(), currentUser.getId())) {
                throw BusinessException.forbidden("只有申请人可以给草稿上传待盖章资料");
            }
        } else {
            SealApplicationVO detail = applicationService.detail(applicationId, currentUser);
            if (!Boolean.TRUE.equals(detail.getCanUploadStampedResult())) {
                throw BusinessException.forbidden("审批通过后仅申请人或用印管理员可补传盖章件");
            }
        }
        if (itemId != null) requireItem(application, itemId);
        FileUploadPolicy.validateBusinessUpload(file, "SEAL_" + role);
        String safeName = FileUploadPolicy.safeOriginalFileName(file.getOriginalFilename());
        String storageKey = objectKey(application, role, safeName);
        StoredFile stored = storageManager.store(storageKey, file);
        registerRollbackCleanup(stored);
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        FileResource resource = new FileResource();
        resource.setProjectId(application.getProjectId());
        resource.setFileName(safeName);
        resource.setFileType(role);
        resource.setFilePath(stored.storageKey());
        resource.setFileSize(stored.size());
        resource.setBusinessType("SEAL_" + role);
        resource.setBusinessId(applicationId);
        resource.setUploaderId(currentUser.getId());
        resource.setStorageProvider(stored.provider());
        resource.setStorageKey(stored.storageKey());
        resource.setOriginalFileName(stored.originalFileName());
        resource.setMimeType(stored.mimeType());
        resource.setFileExtension(stored.extension());
        resource.setSha256(stored.sha256());
        resource.setStatus(FileStatus.UPLOADED);
        resource.setDeleted(0);
        resource.setCreateTime(now);
        resource.setUpdateTime(now);
        requireSingleWrite(fileMapper.insert(resource), "用印附件元数据新增");
        SealApplicationFile relation = new SealApplicationFile();
        relation.setApplicationId(applicationId);
        relation.setItemId(itemId);
        relation.setProjectId(application.getProjectId());
        relation.setFileResourceId(resource.getId());
        relation.setFileRole(role);
        relation.setUploaderId(currentUser.getId());
        relation.setUploaderName(displayName(currentUser));
        relation.setDeleted(0);
        relation.setCreateTime(now);
        relation.setUpdateTime(now);
        requireSingleWrite(relationMapper.insert(relation), "用印附件关联新增");
        applicationService.recordExternalAction(application, "UPLOAD", currentUser, null,
                "上传" + ("SOURCE".equals(role) ? "待盖章资料" : "盖章结果") + "《" + safeName + "》", request);
        if ("STAMPED_RESULT".equals(role)) {
            applicationService.notifyStampedResult(
                    application, relation.getId(), safeName, currentUser.getId());
        }
        return applicationService.fileVO(relation, application, currentUser);
    }

    @Transactional
    public SealFileContent content(Long applicationId, Long relationId, boolean preview, SysUser currentUser,
                                   HttpServletRequest request) {
        SealApplication application = applicationService.requireApplication(applicationId);
        applicationService.requireReadable(application, currentUser);
        SealApplicationFile relation = requireRelation(applicationId, relationId);
        FileResource file = requireFile(relation.getFileResourceId());
        String name = StringUtils.hasText(file.getOriginalFileName()) ? file.getOriginalFileName() : file.getFileName();
        MediaType type = FileUploadPolicy.responseMediaType(name, file.getMimeType());
        boolean inline = preview && FileUploadPolicy.canPreviewInline(name, file.getMimeType());
        Resource resource = storageManager.load(file);
        applicationService.recordExternalAction(application, preview ? "PREVIEW_FILE" : "DOWNLOAD_FILE",
                currentUser, null, (preview ? "预览" : "下载") + "附件《" + name + "》", request);
        return new SealFileContent(resource, name, type, inline);
    }

    @Transactional
    public void delete(Long applicationId, Long relationId, SysUser currentUser, HttpServletRequest request) {
        // Uses the same application row lock as submit(), so deleting the last
        // SOURCE cannot race the submit-time source-file count check.
        SealApplication application = applicationService.requireApplicationForUpdate(applicationId);
        applicationService.requireReadable(application, currentUser);
        SealApplicationFile relation = requireRelation(applicationId, relationId);
        if (relation.getArchivedDocumentId() != null) throw new BusinessException("已归档附件不能删除");
        boolean allowed = "SOURCE".equals(relation.getFileRole())
                && SealApplicationService.DRAFT.equals(application.getStatus())
                && Objects.equals(application.getApplicantId(), currentUser.getId());
        if ("STAMPED_RESULT".equals(relation.getFileRole())
                && SealApplicationService.APPROVED.equals(application.getStatus())) {
            allowed = Objects.equals(relation.getUploaderId(), currentUser.getId())
                    || applicationService.canManage(currentUser, application.getProjectId());
        }
        if (!allowed) throw BusinessException.forbidden("无权删除该用印附件");
        FileResource file = requireFile(relation.getFileResourceId());
        requireSingleWrite(relationMapper.deleteById(relationId), "用印附件关联删除");
        requireSingleWrite(fileMapper.deleteById(file.getId()), "用印附件元数据删除");
        deletePhysicalAfterCommit(file);
        applicationService.recordExternalAction(application, "DELETE_FILE", currentUser, null,
                "删除附件《" + file.getOriginalFileName() + "》", request);
    }

    @Transactional
    public SealApplicationVO archive(Long applicationId, SealArchiveRequest archiveRequest,
                                     SysUser currentUser, HttpServletRequest request) {
        if (archiveRequest == null) throw new BusinessException("归档信息不能为空");
        SealApplication application = applicationService.requireApplicationForUpdate(applicationId);
        SealApplicationVO detail = applicationService.detail(applicationId, currentUser);
        if (!SealApplicationService.APPROVED.equals(application.getStatus())) {
            throw new BusinessException("只有审批通过的申请可以归档盖章件");
        }
        if (!Boolean.TRUE.equals(detail.getCanArchive())) throw BusinessException.forbidden("无资料归档权限");
        SealApplicationFile relation = requireRelation(applicationId, archiveRequest.getFileId());
        if (!"STAMPED_RESULT".equals(relation.getFileRole())) throw new BusinessException("只能归档盖章结果文件");
        if (relation.getArchivedDocumentId() != null) throw BusinessException.of(409, "该盖章件已经归档");
        String mode = normalizeArchiveMode(archiveRequest.getArchiveMode());
        if ("NEW_DOCUMENT".equals(mode)) {
            if (!StringUtils.hasText(archiveRequest.getTitle())) throw new BusinessException("新增资料必须填写资料标题");
            if (archiveRequest.getDocumentId() != null) throw new BusinessException("新增资料不能指定现有资料ID");
        } else {
            if (archiveRequest.getDocumentId() == null) throw new BusinessException("新增版本必须指定现有资料");
        }
        FileResource source = requireFile(relation.getFileResourceId());
        String targetKey = projectDocumentObjectKey(application.getProjectId(), source);
        StoredFile copied = storageManager.copy(source, targetKey);
        DocumentImportResult result = documentService.importCopiedSealFile(mode, application.getProjectId(),
                archiveRequest.getFolderId(), archiveRequest.getDocumentId(), archiveRequest.getDocumentNo(),
                archiveRequest.getTitle(), archiveRequest.getChangeNote(), copied, currentUser, request);
        requireSingleWrite(relationMapper.markArchived(relation.getId(), applicationId, result.documentId(),
                result.versionId(), LocalDateTime.now(BUSINESS_ZONE)), "盖章件归档关联更新");
        applicationService.recordExternalAction(application, "ARCHIVE", currentUser, null,
                "盖章件物理复制归档到资料 " + result.documentId() + " / 版本 " + result.versionId(), request);
        applicationService.notifyArchived(application, relation.getId(), result.documentId(),
                result.versionId(), currentUser.getId());
        return applicationService.detail(applicationId, currentUser);
    }

    private SealApplicationFile requireRelation(Long applicationId, Long relationId) {
        SealApplicationFile relation = relationId == null ? null : relationMapper.selectById(relationId);
        if (relation == null || !Objects.equals(relation.getApplicationId(), applicationId)) {
            throw BusinessException.notFound("用印附件不存在");
        }
        return relation;
    }

    private FileResource requireFile(Long fileId) {
        FileResource file = fileMapper.selectById(fileId);
        if (file == null || !StringUtils.hasText(file.getBusinessType())
                || !file.getBusinessType().toUpperCase(Locale.ROOT).startsWith("SEAL_")) {
            throw BusinessException.notFound("用印附件文件不存在");
        }
        return file;
    }

    private void requireItem(SealApplication application, Long itemId) {
        SealApplicationItem item = itemMapper.selectById(itemId);
        if (item == null || !Objects.equals(item.getApplicationId(), application.getId())) {
            throw new BusinessException("附件关联的用印文件明细不存在");
        }
    }

    private String normalizeRole(String value) {
        String role = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
        if (!"SOURCE".equals(role) && !"STAMPED_RESULT".equals(role)) {
            throw new BusinessException("附件角色必须为 SOURCE 或 STAMPED_RESULT");
        }
        return role;
    }

    private String normalizeArchiveMode(String value) {
        String mode = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
        if (!"NEW_DOCUMENT".equals(mode) && !"NEW_VERSION".equals(mode)) {
            throw new BusinessException("归档方式必须为 NEW_DOCUMENT 或 NEW_VERSION");
        }
        return mode;
    }

    private String objectKey(SealApplication application, String role, String name) {
        String extension = FileUploadPolicy.extensionOf(name);
        return "seal-applications/" + application.getProjectId() + "/" + application.getId() + "/"
                + role.toLowerCase(Locale.ROOT) + "/" + UUID.randomUUID()
                + (extension.isEmpty() ? "" : "." + extension);
    }

    private String projectDocumentObjectKey(Long projectId, FileResource source) {
        String extension = FileUploadPolicy.extensionOf(source.getOriginalFileName(), source.getFileName());
        return "project-documents/" + projectId + "/" + LocalDate.now(BUSINESS_ZONE) + "/" + UUID.randomUUID()
                + (extension.isEmpty() ? "" : "." + extension);
    }

    private void registerRollbackCleanup(StoredFile stored) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) storageManager.deleteQuietly(stored.provider(), stored.storageKey());
            }
        });
    }

    private void deletePhysicalAfterCommit(FileResource file) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            storageManager.delete(file);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    storageManager.delete(file);
                } catch (RuntimeException ignored) {
                    // Metadata is already inaccessible; storage cleanup can be retried operationally.
                }
            }
        });
    }

    private String displayName(SysUser user) {
        return StringUtils.hasText(user.getRealName()) ? user.getRealName() : user.getUsername();
    }

    private void requireSingleWrite(int rows, String operation) {
        if (rows != 1) throw BusinessException.of(409, operation + "未生效，请刷新后重试");
    }
}
