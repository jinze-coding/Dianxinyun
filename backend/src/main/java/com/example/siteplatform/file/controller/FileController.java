package com.example.siteplatform.file.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.common.Result;
import com.example.siteplatform.file.dto.FileActivityVO;
import com.example.siteplatform.file.dto.FileUpdateRequest;
import com.example.siteplatform.file.entity.FileResource;
import com.example.siteplatform.file.mapper.FileResourceMapper;
import com.example.siteplatform.file.service.FileOperationService;
import com.example.siteplatform.file.service.FileResourceService;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.common.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletRequest;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Tag(name = "文件管理", description = "资料文件上传、查询、管理接口")
@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    @Autowired
    private FileResourceMapper fileMapper;

    @Autowired
    private AuthService authService;

    @Autowired
    private ProjectPermissionService projectPermissionService;

    @Autowired
    private FileResourceService fileResourceService;

    @Autowired
    private FileOperationService fileOperationService;

    @Value("${file.upload.path:./uploads}")
    private String uploadPath;

    @Operation(summary = "获取文件列表")
    @GetMapping
    public Result<List<FileResource>> getFileList(
            @Parameter(description = "项目ID") @RequestParam(required = false) Long projectId,
            @Parameter(description = "文件类型") @RequestParam(required = false) String fileType,
            @Parameter(description = "状态") @RequestParam(required = false) String status,
            @Parameter(description = "搜索关键字") @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String businessType,
            @RequestParam(required = false) Long businessId,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);

        if (fileResourceService.isProjectDocument(businessType)) {
            throw BusinessException.forbidden("工程资料请通过资料管理接口查询");
        }

        LambdaQueryWrapper<FileResource> wrapper = new LambdaQueryWrapper<>();
        if (projectId != null) {
            projectPermissionService.checkProjectPermission(currentUser.getId(), projectId);
            wrapper.eq(FileResource::getProjectId, projectId);
        } else if (!projectPermissionService.isPlatformAdmin(currentUser.getId())) {
            List<Long> projectIds = fileResourceService.authorizedProjectIds(currentUser);
            if (projectIds.isEmpty()) return Result.success(List.of());
            wrapper.in(FileResource::getProjectId, projectIds);
        }
        if (fileType != null && !fileType.isEmpty() && !"全部".equals(fileType)) {
            wrapper.eq(FileResource::getFileType, fileType);
        }
        if (status != null && !status.isEmpty() && !"全部".equals(status)) {
            wrapper.eq(FileResource::getStatus, status);
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(FileResource::getFileName, keyword);
        }
        if (businessType != null && !businessType.isEmpty()) {
            wrapper.eq(FileResource::getBusinessType, businessType);
        }
        wrapper.and(item -> item.isNull(FileResource::getBusinessType)
                .or()
                .ne(FileResource::getBusinessType, "PROJECT_DOCUMENT"));
        if (businessId != null) {
            wrapper.eq(FileResource::getBusinessId, businessId);
        }
        wrapper.orderByDesc(FileResource::getCreateTime);

        List<FileResource> list = fileMapper.selectList(wrapper);
        fileOperationService.enrichUploaderNames(list);
        return Result.success(list);
    }

    @Operation(summary = "获取文件详情")
    @GetMapping("/{id}")
    public Result<FileResource> getFileById(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);

        FileResource file = fileMapper.selectById(id);
        if (file == null) {
            return Result.error("文件不存在");
        }
        fileResourceService.checkRead(currentUser, file);
        fileOperationService.enrichUploaderNames(List.of(file));
        return Result.success(file);
    }

    @Operation(summary = "获取资料操作记录")
    @GetMapping("/activities")
    public Result<List<FileActivityVO>> getFileActivities(
            @RequestParam Long projectId,
            @RequestParam(required = false) Long fileId,
            @RequestParam(required = false, defaultValue = "50") Integer limit,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        return Result.success(fileOperationService.getActivities(currentUser, projectId, fileId, limit));
    }

    @Operation(summary = "下载文件")
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token,
            HttpServletRequest request) {
        SysUser currentUser = authService.getCurrentUser(token);
        FileResource file = fileMapper.selectById(id);
        if (file == null || file.getFilePath() == null) return ResponseEntity.notFound().build();
        fileResourceService.checkRead(currentUser, file);
        ResponseEntity<Resource> response = buildFileResponse(file, false);
        if (response.getStatusCode().is2xxSuccessful()) {
            fileOperationService.record(currentUser, file, "FILE_DOWNLOAD",
                    "下载《" + file.getFileName() + "》", request);
        }
        return response;
    }

    @Operation(summary = "在线预览文件")
    @GetMapping("/{id}/preview")
    public ResponseEntity<Resource> previewFile(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token,
            HttpServletRequest request) {
        SysUser currentUser = authService.getCurrentUser(token);
        FileResource file = fileMapper.selectById(id);
        if (file == null || file.getFilePath() == null) return ResponseEntity.notFound().build();
        fileResourceService.checkRead(currentUser, file);
        ResponseEntity<Resource> response = buildFileResponse(file, true);
        if (response.getStatusCode().is2xxSuccessful()) {
            fileOperationService.record(currentUser, file, "FILE_PREVIEW",
                    "预览《" + file.getFileName() + "》", request);
        }
        return response;
    }

    @Operation(summary = "上传文件")
    @PostMapping
    public Result<FileResource> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "projectId", required = false) Long projectId,
            @RequestParam(value = "fileName", required = false) String fileName,
            @RequestParam(value = "fileType", defaultValue = "其他") String fileType,
            @RequestParam(value = "businessType", required = false) String businessType,
            @RequestParam(value = "businessId", required = false) Long businessId,
            @RequestParam(value = "remark", required = false) String remark,
            @RequestHeader(value = "Authorization", required = false) String token,
            HttpServletRequest request) {
        SysUser currentUser = authService.getCurrentUser(token);

        if (file.isEmpty()) {
            return Result.error("文件不能为空");
        }
        if (projectId == null) {
            throw new BusinessException("项目ID不能为空");
        }
        if (fileResourceService.isProjectDocument(businessType)) {
            throw new BusinessException("工程资料请通过资料管理模块上传");
        }
        projectPermissionService.checkProjectPermission(currentUser.getId(), projectId);

        // 独立资料保留同名校验；业务暂存附件允许现场反复上传相同相机文件名。
        String finalFileName = normalizeFileName(fileName != null ? fileName : file.getOriginalFilename());
        if (!fileResourceService.allowsDuplicateNameForUpload(businessType, businessId)) {
            LambdaQueryWrapper<FileResource> existWrapper = new LambdaQueryWrapper<>();
            existWrapper.eq(FileResource::getProjectId, projectId)
                    .eq(FileResource::getFileName, finalFileName)
                    .eq(FileResource::getDeleted, 0);
            if (fileMapper.selectCount(existWrapper) > 0) {
                return Result.error("该项目下已存在同名文件：" + finalFileName);
            }
        }

        // 生成唯一文件名
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String newFilename = UUID.randomUUID().toString() + extension;
        Path uploadDir = Paths.get(uploadPath).toAbsolutePath().normalize();
        Path targetPath = uploadDir.resolve(newFilename).normalize();

        try {
            Files.createDirectories(uploadDir);
        } catch (IOException e) {
            e.printStackTrace();
            return Result.error("上传目录创建失败");
        }

        try {
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            e.printStackTrace();
            return Result.error("文件保存失败: " + e.getMessage());
        }

        FileResource fileResource = new FileResource();
        fileResource.setProjectId(projectId);
        fileResource.setFileName(finalFileName);
        fileResource.setFileType(fileType);
        fileResource.setFilePath(targetPath.toString());
        fileResource.setFileSize(file.getSize());
        fileResource.setBusinessType(businessType);
        fileResource.setBusinessId(businessId);
        fileResource.setUploaderId(currentUser.getId());
        fileResource.setStatus("已上传");
        fileResource.setRemark(remark);
        fileResource.setCreateTime(LocalDateTime.now());
        fileResource.setUpdateTime(LocalDateTime.now());

        fileMapper.insert(fileResource);
        fileOperationService.enrichUploaderNames(List.of(fileResource));
        fileOperationService.record(currentUser, fileResource, "FILE_UPLOAD",
                "上传《" + fileResource.getFileName() + "》", request);
        return Result.success(fileResource);
    }

    @Operation(summary = "更新文件信息")
    @PutMapping("/{id}")
    public Result<FileResource> updateFile(
            @PathVariable Long id,
            @RequestBody FileUpdateRequest requestBody,
            @RequestHeader(value = "Authorization", required = false) String token,
            HttpServletRequest request) {
        SysUser currentUser = authService.getCurrentUser(token);

        FileResource existing = fileMapper.selectById(id);
        if (existing == null) {
            return Result.error("文件不存在");
        }
        fileResourceService.checkWrite(currentUser, existing);

        if (requestBody.getFileName() != null) {
            String nextName = normalizeFileName(requestBody.getFileName());
            LambdaQueryWrapper<FileResource> duplicate = new LambdaQueryWrapper<FileResource>()
                    .eq(FileResource::getProjectId, existing.getProjectId())
                    .eq(FileResource::getFileName, nextName)
                    .ne(FileResource::getId, id);
            if (fileMapper.selectCount(duplicate) > 0) {
                return Result.error(409, "该项目下已存在同名文件：" + nextName);
            }
            existing.setFileName(nextName);
        }
        if (requestBody.getFileType() != null) existing.setFileType(requestBody.getFileType().trim());
        if (requestBody.getStatus() != null) existing.setStatus(requestBody.getStatus().trim());
        if (requestBody.getRemark() != null) existing.setRemark(requestBody.getRemark().trim());
        existing.setUpdateTime(LocalDateTime.now());
        fileMapper.updateById(existing);
        fileOperationService.enrichUploaderNames(List.of(existing));
        fileOperationService.record(currentUser, existing, "FILE_UPDATE",
                "修改《" + existing.getFileName() + "》的资料信息", request);
        return Result.success(existing);
    }

    @Operation(summary = "替换文件内容")
    @PutMapping("/{id}/content")
    public Result<FileResource> replaceFileContent(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile replacement,
            @RequestHeader(value = "Authorization", required = false) String token,
            HttpServletRequest request) {
        SysUser currentUser = authService.getCurrentUser(token);
        FileResource existing = fileMapper.selectById(id);
        if (existing == null) return Result.error("文件不存在");
        fileResourceService.checkWrite(currentUser, existing);
        if (replacement.isEmpty()) return Result.error("替换文件不能为空");

        Path uploadDir = Paths.get(uploadPath).toAbsolutePath().normalize();
        Path targetPath = uploadDir.resolve(UUID.randomUUID() + fileExtension(replacement.getOriginalFilename())).normalize();
        try {
            Files.createDirectories(uploadDir);
            Files.copy(replacement.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            return Result.error("替换文件保存失败: " + e.getMessage());
        }

        String previousPath = existing.getFilePath();
        existing.setFilePath(targetPath.toString());
        existing.setFileSize(replacement.getSize());
        existing.setUpdateTime(LocalDateTime.now());
        fileMapper.updateById(existing);
        deletePhysicalFile(previousPath);
        fileOperationService.enrichUploaderNames(List.of(existing));
        fileOperationService.record(currentUser, existing, "FILE_REPLACE",
                "替换《" + existing.getFileName() + "》的文件内容", request);
        return Result.success(existing);
    }

    @Operation(summary = "更新文件状态")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(
            @PathVariable Long id,
            @RequestParam String status,
            @RequestHeader(value = "Authorization", required = false) String token,
            HttpServletRequest request) {
        SysUser currentUser = authService.getCurrentUser(token);

        FileResource file = fileMapper.selectById(id);
        fileResourceService.checkWrite(currentUser, file);

        LambdaUpdateWrapper<FileResource> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(FileResource::getId, id)
               .set(FileResource::getStatus, status)
               .set(FileResource::getUpdateTime, LocalDateTime.now());
        fileMapper.update(null, wrapper);
        file.setStatus(status);
        fileOperationService.record(currentUser, file,
                isArchived(status) ? "FILE_ARCHIVE" : "FILE_UPDATE",
                (isArchived(status) ? "归档《" : "更新《") + file.getFileName() + "》", request);
        return Result.success();
    }

    @Operation(summary = "删除文件")
    @DeleteMapping("/{id}")
    public Result<Void> deleteFile(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token,
            HttpServletRequest request) {
        SysUser currentUser = authService.getCurrentUser(token);

        FileResource file = fileMapper.selectById(id);
        fileResourceService.checkWrite(currentUser, file);
        fileOperationService.record(currentUser, file, "FILE_DELETE",
                "删除《" + file.getFileName() + "》", request);
        deletePhysicalFile(file.getFilePath());
        fileMapper.deleteById(id);
        return Result.success();
    }

    private ResponseEntity<Resource> buildFileResponse(FileResource file, boolean inline) {
        File physicalFile = new File(file.getFilePath());
        if (!physicalFile.exists()) return ResponseEntity.notFound().build();

        try {
            String contentType = Files.probeContentType(physicalFile.toPath());
            if (contentType == null) contentType = "application/octet-stream";
            ContentDisposition disposition = inline
                    ? ContentDisposition.inline().filename(file.getFileName(), StandardCharsets.UTF_8).build()
                    : ContentDisposition.attachment().filename(file.getFileName(), StandardCharsets.UTF_8).build();
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                    .body(new FileSystemResource(physicalFile));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    private String normalizeFileName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            throw new BusinessException("文件名不能为空");
        }
        String normalized = fileName.trim();
        if (normalized.length() > 200) throw new BusinessException("文件名不能超过200个字符");
        return normalized;
    }

    private String fileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) return "";
        return fileName.substring(fileName.lastIndexOf("."));
    }

    private void deletePhysicalFile(String filePath) {
        if (filePath == null) return;
        try {
            Files.deleteIfExists(Paths.get(filePath));
        } catch (IOException ignored) {
            // 元数据操作不因历史物理文件清理失败而中断。
        }
    }

    private boolean isArchived(String status) {
        return "已归档".equals(status) || "ARCHIVED".equalsIgnoreCase(status);
    }
}
