package com.example.siteplatform.file.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.file.dto.FileActivityVO;
import com.example.siteplatform.file.entity.FileResource;
import com.example.siteplatform.file.mapper.FileResourceMapper;
import com.example.siteplatform.log.entity.OperationLog;
import com.example.siteplatform.log.mapper.OperationLogMapper;
import com.example.siteplatform.project.service.ProjectPermissionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FileOperationService {
    private static final String BUSINESS_TYPE_PREFIX = "FILE_PROJECT_";

    private final OperationLogMapper operationLogMapper;
    private final FileResourceMapper fileMapper;
    private final SysUserMapper userMapper;
    private final ProjectPermissionService permissionService;

    public FileOperationService(OperationLogMapper operationLogMapper,
                                FileResourceMapper fileMapper,
                                SysUserMapper userMapper,
                                ProjectPermissionService permissionService) {
        this.operationLogMapper = operationLogMapper;
        this.fileMapper = fileMapper;
        this.userMapper = userMapper;
        this.permissionService = permissionService;
    }

    public void enrichUploaderNames(List<FileResource> files) {
        if (files == null || files.isEmpty()) return;
        List<Long> uploaderIds = files.stream()
                .map(FileResource::getUploaderId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (uploaderIds.isEmpty()) return;

        Map<Long, SysUser> users = userMapper.selectBatchIds(uploaderIds).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        files.forEach(file -> {
            SysUser uploader = users.get(file.getUploaderId());
            if (uploader != null) file.setUploaderName(displayName(uploader));
        });
    }

    public void record(SysUser operator, FileResource file, String operationType,
                       String operationDesc, HttpServletRequest request) {
        if (operator == null || file == null || file.getProjectId() == null) return;
        OperationLog log = new OperationLog();
        log.setUserId(operator.getId());
        log.setUsername(displayName(operator));
        log.setOperationType(operationType);
        log.setOperationDesc(operationDesc);
        log.setBusinessType(businessType(file.getProjectId()));
        log.setBusinessId(file.getId());
        log.setIpAddress(resolveIp(request));
        log.setCreateTime(LocalDateTime.now());
        operationLogMapper.insert(log);
    }

    public List<FileActivityVO> getActivities(SysUser currentUser, Long projectId, Long fileId, Integer limit) {
        permissionService.checkProjectPermission(currentUser.getId(), projectId);
        int safeLimit = Math.max(1, Math.min(limit == null ? 50 : limit, 100));

        if (fileId != null) {
            FileResource file = fileMapper.selectById(fileId);
            if (file == null || !Objects.equals(file.getProjectId(), projectId)) {
                return Collections.emptyList();
            }
        }

        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<OperationLog>()
                .eq(OperationLog::getBusinessType, businessType(projectId))
                .orderByDesc(OperationLog::getCreateTime)
                .last("LIMIT " + safeLimit);
        if (fileId != null) wrapper.eq(OperationLog::getBusinessId, fileId);
        List<OperationLog> logs = operationLogMapper.selectList(wrapper);

        List<Long> fileIds = logs.stream().map(OperationLog::getBusinessId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, FileResource> files = fileIds.isEmpty()
                ? Collections.emptyMap()
                : fileMapper.selectBatchIds(fileIds).stream()
                .collect(Collectors.toMap(FileResource::getId, Function.identity()));

        return logs.stream().map(log -> {
            FileActivityVO item = new FileActivityVO();
            item.setId(log.getId());
            item.setFileId(log.getBusinessId());
            FileResource file = files.get(log.getBusinessId());
            item.setFileName(file == null ? null : file.getFileName());
            item.setOperationType(log.getOperationType());
            item.setOperationLabel(operationLabel(log.getOperationType()));
            item.setOperationDesc(log.getOperationDesc());
            item.setOperatorId(log.getUserId());
            item.setOperatorName(log.getUsername());
            item.setCreateTime(log.getCreateTime());
            return item;
        }).toList();
    }

    private String businessType(Long projectId) {
        return BUSINESS_TYPE_PREFIX + projectId;
    }

    private String operationLabel(String operationType) {
        if (operationType == null) return "操作";
        return switch (operationType) {
            case "FILE_UPLOAD" -> "上传";
            case "FILE_PREVIEW" -> "预览";
            case "FILE_DOWNLOAD" -> "下载";
            case "FILE_UPDATE" -> "修改";
            case "FILE_REPLACE" -> "替换文件";
            case "FILE_ARCHIVE" -> "归档";
            case "FILE_DELETE" -> "删除";
            default -> "操作";
        };
    }

    private String displayName(SysUser user) {
        return StringUtils.hasText(user.getRealName()) ? user.getRealName() : user.getUsername();
    }

    private String resolveIp(HttpServletRequest request) {
        if (request == null) return null;
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) return forwarded.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}
