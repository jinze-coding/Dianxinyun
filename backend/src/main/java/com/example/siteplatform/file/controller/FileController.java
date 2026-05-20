package com.example.siteplatform.file.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.common.Result;
import com.example.siteplatform.file.entity.FileResource;
import com.example.siteplatform.file.mapper.FileResourceMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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

    @Value("${file.upload.path:./uploads}")
    private String uploadPath;

    @Operation(summary = "获取文件列表")
    @GetMapping
    public Result<List<FileResource>> getFileList(
            @Parameter(description = "项目ID") @RequestParam(required = false) Long projectId,
            @Parameter(description = "文件类型") @RequestParam(required = false) String fileType,
            @Parameter(description = "状态") @RequestParam(required = false) String status,
            @Parameter(description = "搜索关键字") @RequestParam(required = false) String keyword,
            @RequestHeader(value = "Authorization", required = false) String token) {
        authService.getCurrentUser(token);

        LambdaQueryWrapper<FileResource> wrapper = new LambdaQueryWrapper<>();
        if (projectId != null) {
            wrapper.eq(FileResource::getProjectId, projectId);
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
        wrapper.orderByDesc(FileResource::getCreateTime);

        List<FileResource> list = fileMapper.selectList(wrapper);
        return Result.success(list);
    }

    @Operation(summary = "获取文件详情")
    @GetMapping("/{id}")
    public Result<FileResource> getFileById(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {
        authService.getCurrentUser(token);

        FileResource file = fileMapper.selectById(id);
        if (file == null) {
            return Result.error("文件不存在");
        }
        return Result.success(file);
    }

    @Operation(summary = "下载文件")
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {
        authService.getCurrentUser(token);

        FileResource file = fileMapper.selectById(id);
        if (file == null || file.getFilePath() == null) {
            return ResponseEntity.notFound().build();
        }

        File physicalFile = new File(file.getFilePath());
        if (!physicalFile.exists()) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(physicalFile);
        String filename = file.getFileName();

        try {
            String contentType = Files.probeContentType(physicalFile.toPath());
            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .body(resource);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
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
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);

        if (file.isEmpty()) {
            return Result.error("文件不能为空");
        }

        // 检查同名文件是否已存在（同一项目下不允许同名）
        String finalFileName = fileName != null ? fileName : file.getOriginalFilename();
        LambdaQueryWrapper<FileResource> existWrapper = new LambdaQueryWrapper<>();
        existWrapper.eq(FileResource::getProjectId, projectId)
                    .eq(FileResource::getFileName, finalFileName)
                    .eq(FileResource::getDeleted, 0);
        if (fileMapper.selectCount(existWrapper) > 0) {
            return Result.error("该项目下已存在同名文件：" + finalFileName);
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
        fileResource.setFileName(fileName != null ? fileName : originalFilename);
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
        return Result.success(fileResource);
    }

    @Operation(summary = "更新文件信息")
    @PutMapping("/{id}")
    public Result<FileResource> updateFile(
            @PathVariable Long id,
            @RequestBody FileResource file,
            @RequestHeader(value = "Authorization", required = false) String token) {
        authService.getCurrentUser(token);

        FileResource existing = fileMapper.selectById(id);
        if (existing == null) {
            return Result.error("文件不存在");
        }

        file.setId(id);
        file.setUpdateTime(LocalDateTime.now());
        fileMapper.updateById(file);
        return Result.success(file);
    }

    @Operation(summary = "更新文件状态")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(
            @PathVariable Long id,
            @RequestParam String status,
            @RequestHeader(value = "Authorization", required = false) String token) {
        authService.getCurrentUser(token);

        LambdaUpdateWrapper<FileResource> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(FileResource::getId, id)
               .set(FileResource::getStatus, status)
               .set(FileResource::getUpdateTime, LocalDateTime.now());
        fileMapper.update(null, wrapper);
        return Result.success();
    }

    @Operation(summary = "删除文件")
    @DeleteMapping("/{id}")
    public Result<Void> deleteFile(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {
        authService.getCurrentUser(token);

        FileResource file = fileMapper.selectById(id);
        if (file != null && file.getFilePath() != null) {
            // 删除物理文件
            File physicalFile = new File(file.getFilePath());
            if (physicalFile.exists()) {
                physicalFile.delete();
            }
        }

        fileMapper.deleteById(id);
        return Result.success();
    }
}
