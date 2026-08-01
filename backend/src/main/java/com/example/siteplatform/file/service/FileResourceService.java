package com.example.siteplatform.file.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.file.entity.FileResource;
import com.example.siteplatform.file.mapper.FileResourceMapper;
import com.example.siteplatform.project.entity.ProjectInfo;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.system.constant.SystemPermissionCodes;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
public class FileResourceService {
    private static final String BUSINESS_PROJECT_DOCUMENT = "PROJECT_DOCUMENT";
    private static final String BUSINESS_QUALITY_DOCUMENT = "QUALITY_DOCUMENT";
    private static final String BUSINESS_QUALITY_PENDING = "QUALITY_PENDING";
    private static final String BUSINESS_QUALITY_RECTIFICATION_PENDING = "QUALITY_RECTIFICATION_PENDING";
    private static final String BUSINESS_QUALITY_REVIEW_PENDING = "QUALITY_REVIEW_PENDING";
    private static final String BUSINESS_QUALITY_ISSUE = "QUALITY_ISSUE";
    private static final String BUSINESS_QUALITY_RECTIFICATION = "QUALITY_RECTIFICATION";
    private static final String BUSINESS_QUALITY_REVIEW = "QUALITY_REVIEW";
    private static final Set<String> QUALITY_STAGING_TYPES = Set.of(
            BUSINESS_QUALITY_PENDING,
            BUSINESS_QUALITY_RECTIFICATION_PENDING,
            BUSINESS_QUALITY_REVIEW_PENDING
    );
    private static final Set<String> QUALITY_FINAL_TYPES = Set.of(
            BUSINESS_QUALITY_ISSUE,
            BUSINESS_QUALITY_RECTIFICATION,
            BUSINESS_QUALITY_REVIEW
    );

    private final FileResourceMapper fileMapper;
    private final ProjectPermissionService permissionService;

    public FileResourceService(FileResourceMapper fileMapper, ProjectPermissionService permissionService) {
        this.fileMapper = fileMapper;
        this.permissionService = permissionService;
    }

    public void checkRead(SysUser currentUser, FileResource file) {
        if (file == null) {
            throw BusinessException.notFound("文件不存在");
        }
        if (isProjectDocument(file.getBusinessType())) {
            throw BusinessException.forbidden("工程资料请通过资料管理接口访问");
        }
        if (file.getProjectId() == null) {
            if (!permissionService.isPlatformAdmin(currentUser.getId())) {
                throw BusinessException.forbidden("无文件访问权限");
            }
            return;
        }
        permissionService.checkProjectPermission(currentUser.getId(), file.getProjectId());
        requireBusinessRead(currentUser, file.getProjectId(), file.getBusinessType());
    }

    public void checkWrite(SysUser currentUser, FileResource file) {
        checkRead(currentUser, file);
        String businessType = normalizeBusinessType(file.getBusinessType());
        if (businessType.startsWith("QUALITY_")) {
            checkQualityWrite(currentUser, file, businessType);
            return;
        }
        if (isBoundWorkflowAttachment(file)) {
            throw BusinessException.forbidden("已关联业务记录的附件不能通过通用文件接口修改");
        }
        if (permissionService.isPlatformAdmin(currentUser.getId())
                || Objects.equals(file.getUploaderId(), currentUser.getId())) {
            return;
        }
        if (businessType.startsWith("INSPECTION_")
                && permissionService.canManageInspection(currentUser.getId(), file.getProjectId())) return;
        if ((businessType.startsWith("PERSON_") || businessType.startsWith("SAFETY_"))
                && permissionService.canManagePersonnel(currentUser.getId(), file.getProjectId())) return;
        if (permissionService.canManageProject(currentUser.getId(), file.getProjectId())) return;
        throw BusinessException.forbidden("无文件管理权限");
    }

    public List<Long> authorizedProjectIds(SysUser currentUser) {
        return permissionService.getUserProjects(currentUser.getId()).stream().map(ProjectInfo::getId).toList();
    }

    /**
     * 通用附件仍属于其原业务模块，不能绕过资料/巡检/质量的跨端模块开关。
     * 未分类的历史附件继续只按项目范围处理。
     */
    public void requireBusinessRead(SysUser currentUser, Long projectId, String businessType) {
        if (currentUser == null || projectId == null) return;
        String normalized = normalizeBusinessType(businessType);
        if (normalized.startsWith("QUALITY_")) {
            permissionService.requireSystemPermission(currentUser.getId(), projectId, SystemPermissionCodes.QUALITY_VIEW);
        } else if (normalized.startsWith("INSPECTION_")) {
            permissionService.requireSystemPermission(currentUser.getId(), projectId, SystemPermissionCodes.INSPECTION_VIEW);
        }
    }

    /**
     * 列表未指定业务类型时可能混合多个模块，逐条按文件真实项目和业务类型过滤，
     * 避免用户在 A 项目拥有质量权限后读取 B 项目的质量附件元数据。
     */
    public boolean canReadInList(SysUser currentUser, FileResource file) {
        try {
            checkRead(currentUser, file);
            return true;
        } catch (BusinessException exception) {
            if (Objects.equals(exception.getCode(), 403)) {
                return false;
            }
            throw exception;
        }
    }

    /**
     * 校验通用上传通道中的业务类型，并返回需要落库的规范值。
     * 质量流程最终附件只能由质量事务把暂存附件绑定后生成，客户端不能直接注入。
     */
    public String authorizeUpload(SysUser currentUser, Long projectId, String businessType, Long businessId) {
        if (currentUser == null || currentUser.getId() == null || projectId == null) {
            throw new BusinessException("文件上传参数不完整");
        }
        permissionService.checkProjectPermission(currentUser.getId(), projectId);
        String normalized = normalizeBusinessType(businessType);
        if (BUSINESS_PROJECT_DOCUMENT.equals(normalized)) {
            throw BusinessException.forbidden("工程资料请通过资料管理模块上传");
        }
        if (!normalized.startsWith("QUALITY_")) {
            return businessType;
        }
        if (QUALITY_FINAL_TYPES.contains(normalized)) {
            throw new BusinessException("质量流程附件只能先上传暂存类型，再由质量业务提交绑定");
        }
        if (!BUSINESS_QUALITY_DOCUMENT.equals(normalized) && !QUALITY_STAGING_TYPES.contains(normalized)) {
            throw new BusinessException("不支持的质量附件类型");
        }
        if (businessId != null) {
            throw new BusinessException("质量附件上传时不能直接指定业务记录");
        }
        permissionService.requireSystemPermission(currentUser.getId(), projectId,
                SystemPermissionCodes.QUALITY_VIEW);
        permissionService.requireSystemPermission(currentUser.getId(), projectId,
                qualityWritePermission(normalized));
        return normalized;
    }

    public void validateAndBind(SysUser currentUser, Long projectId, List<Long> fileIds,
                                String expectedBusinessType, String businessType, Long businessId) {
        if (fileIds == null || fileIds.isEmpty()) return;
        if (currentUser == null || currentUser.getId() == null || projectId == null || businessId == null) {
            throw new BusinessException("附件绑定参数不完整");
        }
        validateQualityBindingTypes(expectedBusinessType, businessType);
        List<Long> distinctFileIds = fileIds.stream().filter(Objects::nonNull).distinct().toList();
        if (distinctFileIds.size() != fileIds.size()) {
            throw new BusinessException("附件列表包含重复或无效文件");
        }
        List<FileResource> files = fileMapper.selectList(new LambdaQueryWrapper<FileResource>()
                .in(FileResource::getId, distinctFileIds));
        if (files.size() != distinctFileIds.size()) {
            throw new BusinessException("部分附件不存在");
        }
        for (FileResource file : files) {
            if (!Objects.equals(projectId, file.getProjectId())) {
                throw new BusinessException("附件不属于当前项目");
            }
            if (!Objects.equals(currentUser.getId(), file.getUploaderId())) {
                throw BusinessException.forbidden("只能关联本人刚上传的附件");
            }
            if (file.getBusinessId() != null) {
                throw new BusinessException("附件已关联其他业务记录");
            }
            if (!normalizeBusinessType(expectedBusinessType)
                    .equals(normalizeBusinessType(file.getBusinessType()))) {
                throw new BusinessException("附件类型与当前业务不匹配");
            }
            file.setBusinessType(businessType);
            file.setBusinessId(businessId);
            file.setUpdateTime(LocalDateTime.now());
            if (fileMapper.updateById(file) != 1) {
                throw new BusinessException("附件状态已变化，请重新上传后提交");
            }
        }
    }

    public boolean isProjectDocument(String businessType) {
        return BUSINESS_PROJECT_DOCUMENT.equals(normalizeBusinessType(businessType));
    }

    public boolean allowsDuplicateNameForUpload(String businessType, Long businessId) {
        if (businessId != null) return false;
        String normalized = normalizeBusinessType(businessType);
        return normalized.endsWith("_PENDING")
                || normalized.equals("INSPECTION_RECORD")
                || normalized.equals("INSPECTION_RECTIFICATION");
    }

    private boolean isBoundWorkflowAttachment(FileResource file) {
        if (file.getBusinessId() == null) return false;
        String businessType = normalizeBusinessType(file.getBusinessType());
        return businessType.equals("INSPECTION_RECORD")
                || businessType.equals("INSPECTION_RECTIFICATION")
                || businessType.equals("QUALITY_ISSUE")
                || businessType.equals("QUALITY_RECTIFICATION")
                || businessType.equals("QUALITY_REVIEW");
    }

    private void checkQualityWrite(SysUser currentUser, FileResource file, String businessType) {
        if (QUALITY_FINAL_TYPES.contains(businessType)) {
            throw BusinessException.forbidden("质量流程附件不能通过通用文件接口修改或删除");
        }
        String permissionCode = qualityWritePermission(businessType);
        if (permissionCode == null) {
            throw BusinessException.forbidden("不支持通过通用文件接口管理该质量附件");
        }
        permissionService.requireSystemPermission(currentUser.getId(), file.getProjectId(), permissionCode);
        if (BUSINESS_QUALITY_DOCUMENT.equals(businessType)) {
            return;
        }
        if (file.getBusinessId() != null) {
            throw BusinessException.forbidden("已关联业务记录的附件不能通过通用文件接口修改");
        }
        if (permissionService.isPlatformAdmin(currentUser.getId())
                || Objects.equals(file.getUploaderId(), currentUser.getId())) {
            return;
        }
        throw BusinessException.forbidden("只能管理本人上传的质量暂存附件");
    }

    private String qualityWritePermission(String businessType) {
        return switch (businessType) {
            case BUSINESS_QUALITY_DOCUMENT, BUSINESS_QUALITY_PENDING -> SystemPermissionCodes.QUALITY_MANAGE;
            case BUSINESS_QUALITY_RECTIFICATION_PENDING -> SystemPermissionCodes.QUALITY_RECTIFY;
            case BUSINESS_QUALITY_REVIEW_PENDING -> SystemPermissionCodes.QUALITY_REVIEW;
            default -> null;
        };
    }

    private void validateQualityBindingTypes(String expectedBusinessType, String targetBusinessType) {
        String expected = normalizeBusinessType(expectedBusinessType);
        String target = normalizeBusinessType(targetBusinessType);
        String requiredStagingType = switch (target) {
            case BUSINESS_QUALITY_ISSUE -> BUSINESS_QUALITY_PENDING;
            case BUSINESS_QUALITY_RECTIFICATION -> BUSINESS_QUALITY_RECTIFICATION_PENDING;
            case BUSINESS_QUALITY_REVIEW -> BUSINESS_QUALITY_REVIEW_PENDING;
            default -> null;
        };
        if (requiredStagingType != null && !requiredStagingType.equals(expected)) {
            throw new BusinessException("质量附件暂存类型与流程阶段不匹配");
        }
    }

    private String normalizeBusinessType(String businessType) {
        return businessType == null ? "" : businessType.trim().toUpperCase(Locale.ROOT);
    }
}
