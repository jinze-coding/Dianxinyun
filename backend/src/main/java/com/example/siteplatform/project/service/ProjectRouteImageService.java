package com.example.siteplatform.project.service;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.file.constant.FileStatus;
import com.example.siteplatform.file.entity.FileResource;
import com.example.siteplatform.file.mapper.FileResourceMapper;
import com.example.siteplatform.file.security.FileUploadPolicy;
import com.example.siteplatform.file.storage.FileStorageManager;
import com.example.siteplatform.project.dto.ProjectRouteImageVO;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
public class ProjectRouteImageService {
    public static final String PENDING_IMAGE_TYPE = "PROJECT_ROUTE_IMAGE_PENDING";
    public static final String FINAL_IMAGE_TYPE = "PROJECT_ROUTE_IMAGE";
    public static final String ACTION_KEEP = "KEEP";
    public static final String ACTION_REPLACE = "REPLACE";
    public static final String ACTION_REMOVE = "REMOVE";
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

    private final FileResourceMapper fileMapper;
    private final FileStorageManager storageManager;

    public ProjectRouteImageService(FileResourceMapper fileMapper, FileStorageManager storageManager) {
        this.fileMapper = fileMapper;
        this.storageManager = storageManager;
    }

    public ProjectRouteImageVO activeMetadata(Long projectId) {
        FileResource image = activeImage(projectId);
        if (image == null || !validImage(image)) return null;
        ProjectRouteImageVO vo = new ProjectRouteImageVO();
        vo.setFileId(image.getId());
        vo.setFileName(image.getFileName());
        vo.setFileSize(image.getFileSize());
        vo.setMimeType(mediaType(image).toString());
        return vo;
    }

    public boolean hasActiveImage(Long projectId) {
        FileResource image = activeImage(projectId);
        return image != null && validImage(image);
    }

    public PublicProjectRouteImageContent publicImage(Long projectId) {
        FileResource image = activeImage(projectId);
        if (image == null || !validImage(image)) {
            throw BusinessException.notFound("项目到访路线图不存在");
        }
        return new PublicProjectRouteImageContent(
                storageManager.load(image), mediaType(image), extension(image), image.getFileSize());
    }

    /**
     * Must be invoked from the project-location transaction after the project row is locked.
     * The project lock serializes all supported bind/archive operations and keeps one active image.
     */
    public String applyLocationAction(Long projectId, String rawAction, Long routeImageFileId, SysUser currentUser) {
        String action = normalizeAction(rawAction);
        if (ACTION_KEEP.equals(action)) {
            if (routeImageFileId != null) {
                throw new BusinessException("保留到访路线图时不能指定新文件");
            }
            return action;
        }
        if (ACTION_REMOVE.equals(action)) {
            if (routeImageFileId != null) {
                throw new BusinessException("移除到访路线图时不能指定文件");
            }
            archiveActiveImages(projectId);
            return action;
        }
        if (routeImageFileId == null) {
            throw new BusinessException("替换到访路线图时必须选择已上传图片");
        }
        FileResource pending = lockPendingImage(projectId, routeImageFileId, currentUser);
        archiveActiveImages(projectId);
        requireSingleWrite(fileMapper.bindPendingProjectRouteImage(pending.getId(), projectId), "项目到访路线图绑定");
        return action;
    }

    private FileResource lockPendingImage(Long projectId, Long fileId, SysUser currentUser) {
        List<FileResource> files = fileMapper.selectByIdsForUpdate(List.of(fileId));
        if (files.size() != 1) throw new BusinessException("到访路线图不存在或已被清理");
        FileResource file = files.get(0);
        if (!Objects.equals(projectId, file.getProjectId())) {
            throw new BusinessException("到访路线图不属于当前项目");
        }
        if (!Objects.equals(currentUser.getId(), file.getUploaderId())) {
            throw BusinessException.forbidden("只能绑定本人刚上传的到访路线图");
        }
        boolean pending = PENDING_IMAGE_TYPE.equals(file.getBusinessType())
                && file.getBusinessId() == null
                && FileStatus.UPLOADED.equals(FileStatus.normalize(file.getStatus()))
                && validImage(file);
        if (!pending) {
            throw BusinessException.of(409, "到访路线图状态已变化，请重新上传后再保存");
        }
        return file;
    }

    private void archiveActiveImages(Long projectId) {
        for (FileResource current : fileMapper.selectActiveProjectRouteImagesForUpdate(projectId)) {
            requireSingleWrite(fileMapper.archiveProjectRouteImage(current.getId(), projectId), "项目到访路线图归档");
        }
    }

    private FileResource activeImage(Long projectId) {
        if (projectId == null) return null;
        return fileMapper.selectActiveProjectRouteImage(projectId);
    }

    private String normalizeAction(String rawAction) {
        if (!StringUtils.hasText(rawAction)) return ACTION_KEEP;
        String action = rawAction.trim().toUpperCase(Locale.ROOT);
        if (!Set.of(ACTION_KEEP, ACTION_REPLACE, ACTION_REMOVE).contains(action)) {
            throw new BusinessException("到访路线图操作仅支持 KEEP、REPLACE、REMOVE");
        }
        return action;
    }

    private boolean allowedExtension(FileResource image) {
        return IMAGE_EXTENSIONS.contains(extension(image));
    }

    private boolean validImage(FileResource image) {
        return allowedExtension(image)
                && image.getFileSize() != null
                && image.getFileSize() >= 0
                && image.getFileSize() <= FileUploadPolicy.MAX_IMAGE_BYTES;
    }

    private String extension(FileResource image) {
        String extension = image.getFileExtension();
        if (!StringUtils.hasText(extension)) {
            extension = FileUploadPolicy.extensionOf(
                    image.getOriginalFileName(), image.getFileName(), image.getFilePath());
        }
        return extension == null ? "" : extension.trim().toLowerCase(Locale.ROOT);
    }

    private MediaType mediaType(FileResource image) {
        return FileUploadPolicy.responseMediaType(
                image.getOriginalFileName(), image.getFileName(), image.getFilePath());
    }

    private void requireSingleWrite(int affectedRows, String operation) {
        if (affectedRows != 1) {
            throw BusinessException.of(409, operation + "未生效，请刷新后重试");
        }
    }

    public record PublicProjectRouteImageContent(Resource resource,
                                                 MediaType mediaType,
                                                 String extension,
                                                 Long fileSize) {
    }
}
