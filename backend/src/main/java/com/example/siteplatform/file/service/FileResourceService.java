package com.example.siteplatform.file.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.file.entity.FileResource;
import com.example.siteplatform.file.mapper.FileResourceMapper;
import com.example.siteplatform.project.entity.ProjectInfo;
import com.example.siteplatform.project.service.ProjectPermissionService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class FileResourceService {
    private static final String BUSINESS_PROJECT_DOCUMENT = "PROJECT_DOCUMENT";

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
    }

    public void checkWrite(SysUser currentUser, FileResource file) {
        checkRead(currentUser, file);
        if (isBoundWorkflowAttachment(file)) {
            throw BusinessException.forbidden("已关联业务记录的附件不能通过通用文件接口修改");
        }
        if (permissionService.isPlatformAdmin(currentUser.getId())
                || Objects.equals(file.getUploaderId(), currentUser.getId())) {
            return;
        }
        String businessType = normalizeBusinessType(file.getBusinessType());
        if (businessType.startsWith("QUALITY_")
                && permissionService.canManageQuality(currentUser.getId(), file.getProjectId())) return;
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

    public void validateAndBind(SysUser currentUser, Long projectId, List<Long> fileIds,
                                String expectedBusinessType, String businessType, Long businessId) {
        if (fileIds == null || fileIds.isEmpty()) return;
        if (currentUser == null || currentUser.getId() == null || projectId == null || businessId == null) {
            throw new BusinessException("附件绑定参数不完整");
        }
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
            fileMapper.updateById(file);
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

    private String normalizeBusinessType(String businessType) {
        return businessType == null ? "" : businessType.trim().toUpperCase(Locale.ROOT);
    }
}
