package com.example.siteplatform.camera.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.camera.entity.CameraResource;
import com.example.siteplatform.camera.mapper.CameraResourceMapper;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.common.Result;
import com.example.siteplatform.project.entity.ProjectInfo;
import com.example.siteplatform.project.service.ProjectPermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "摄像头管理", description = "摄像头资源管理接口")
@RestController
@RequestMapping("/api/v1/cameras")
public class CameraController {

    private static final int NAME_MAX_LENGTH = 100;
    private static final int CODE_MAX_LENGTH = 100;
    private static final int AREA_MAX_LENGTH = 50;
    private static final int TYPE_MAX_LENGTH = 50;
    private static final int RTSP_URL_MAX_LENGTH = 500;

    @Autowired
    private CameraResourceMapper cameraMapper;

    @Autowired
    private AuthService authService;

    @Autowired
    private ProjectPermissionService projectPermissionService;

    @Operation(summary = "获取摄像头列表")
    @GetMapping
    public Result<List<Map<String, Object>>> getCameraList(
            @Parameter(description = "项目ID") @RequestParam(required = false) Long projectId,
            @Parameter(description = "在线状态") @RequestParam(required = false) Integer onlineStatus,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        List<Long> readableProjectIds = resolveReadableProjectIds(currentUser, projectId);
        if (readableProjectIds != null && readableProjectIds.isEmpty()) {
            return Result.success(List.of());
        }

        LambdaQueryWrapper<CameraResource> wrapper = new LambdaQueryWrapper<>();
        if (readableProjectIds != null) {
            wrapper.in(CameraResource::getProjectId, readableProjectIds);
        }
        if (onlineStatus != null) {
            wrapper.eq(CameraResource::getOnlineStatus, onlineStatus);
        }
        wrapper.orderByAsc(CameraResource::getArea)
               .orderByAsc(CameraResource::getCameraName);

        List<CameraResource> cameras = cameraMapper.selectList(wrapper);

        // 转换为前端需要的格式
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (CameraResource camera : cameras) {
            result.add(toResponse(camera, currentUser));
        }

        return Result.success(result);
    }

    @Operation(summary = "获取摄像头详情")
    @GetMapping("/{id}")
    public Result<Map<String, Object>> getCameraById(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);

        CameraResource camera = cameraMapper.selectById(id);
        if (camera == null) {
            throw BusinessException.notFound("摄像头不存在");
        }
        projectPermissionService.checkProjectPermission(currentUser.getId(), camera.getProjectId());

        return Result.success(toResponse(camera, currentUser));
    }

    @Operation(summary = "创建摄像头")
    @PostMapping
    public Result<CameraResource> createCamera(
            @RequestBody CameraResource camera,
            @RequestHeader(value = "Authorization", required = false) String token) {
        if (camera == null) {
            throw BusinessException.of(400, "摄像头信息不能为空");
        }
        SysUser currentUser = authService.getCurrentUser(token);
        requireManagePermission(currentUser, camera.getProjectId());

        CameraResource created = new CameraResource();
        created.setProjectId(camera.getProjectId());
        created.setCameraName(requireText(camera.getCameraName(), NAME_MAX_LENGTH, "摄像头名称"));
        created.setCameraCode(optionalText(camera.getCameraCode(), CODE_MAX_LENGTH, "摄像头编号"));
        created.setArea(optionalText(camera.getArea(), AREA_MAX_LENGTH, "所属区域"));
        created.setCameraType(optionalText(camera.getCameraType(), TYPE_MAX_LENGTH, "摄像头类型"));
        created.setRtspUrl(optionalText(camera.getRtspUrl(), RTSP_URL_MAX_LENGTH, "RTSP地址"));
        created.setOnlineStatus(requireOnlineStatus(camera.getOnlineStatus() == null ? 1 : camera.getOnlineStatus()));
        created.setCreateTime(LocalDateTime.now());
        created.setUpdateTime(LocalDateTime.now());
        requireSingleWrite(cameraMapper.insert(created), "摄像头新增");
        return Result.success(created);
    }

    @Operation(summary = "更新摄像头")
    @PutMapping("/{id}")
    public Result<Void> updateCamera(
            @PathVariable Long id,
            @RequestBody CameraResource camera,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);

        CameraResource existing = cameraMapper.selectById(id);
        if (existing == null) {
            throw BusinessException.notFound("摄像头不存在");
        }
        requireManagePermission(currentUser, existing.getProjectId());

        if (camera.getCameraName() != null) {
            existing.setCameraName(requireText(camera.getCameraName(), NAME_MAX_LENGTH, "摄像头名称"));
        }
        if (camera.getCameraCode() != null) {
            existing.setCameraCode(optionalText(camera.getCameraCode(), CODE_MAX_LENGTH, "摄像头编号"));
        }
        if (camera.getArea() != null) {
            existing.setArea(optionalText(camera.getArea(), AREA_MAX_LENGTH, "所属区域"));
        }
        if (camera.getCameraType() != null) {
            existing.setCameraType(optionalText(camera.getCameraType(), TYPE_MAX_LENGTH, "摄像头类型"));
        }
        if (camera.getRtspUrl() != null) {
            existing.setRtspUrl(optionalText(camera.getRtspUrl(), RTSP_URL_MAX_LENGTH, "RTSP地址"));
        }
        if (camera.getOnlineStatus() != null) {
            existing.setOnlineStatus(requireOnlineStatus(camera.getOnlineStatus()));
        }
        existing.setUpdateTime(LocalDateTime.now());
        requireSingleWrite(cameraMapper.updateById(existing), "摄像头更新");
        return Result.success();
    }

    @Operation(summary = "删除摄像头")
    @DeleteMapping("/{id}")
    public Result<Void> deleteCamera(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        CameraResource existing = cameraMapper.selectById(id);
        if (existing == null) {
            throw BusinessException.notFound("摄像头不存在");
        }
        requireManagePermission(currentUser, existing.getProjectId());
        requireSingleWrite(cameraMapper.deleteById(id), "摄像头删除");
        return Result.success();
    }

    private List<Long> resolveReadableProjectIds(SysUser currentUser, Long requestedProjectId) {
        if (requestedProjectId != null) {
            projectPermissionService.checkProjectPermission(currentUser.getId(), requestedProjectId);
            return List.of(requestedProjectId);
        }
        if (projectPermissionService.isPlatformAdmin(currentUser.getId())) {
            return null;
        }
        return projectPermissionService.getUserProjects(currentUser.getId()).stream()
                .map(ProjectInfo::getId)
                .distinct()
                .toList();
    }

    private Map<String, Object> toResponse(CameraResource camera, SysUser currentUser) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", camera.getId());
        item.put("name", camera.getCameraName());
        item.put("code", camera.getCameraCode());
        item.put("area", camera.getArea());
        item.put("type", camera.getCameraType());
        boolean rtspConfigured = camera.getRtspUrl() != null && !camera.getRtspUrl().isBlank();
        item.put("rtspConfigured", rtspConfigured);
        item.put("rtspUrl", projectPermissionService.canManageProject(
                currentUser.getId(), camera.getProjectId()) ? camera.getRtspUrl() : null);
        item.put("online", camera.getOnlineStatus() != null && camera.getOnlineStatus() == 1);
        return item;
    }

    private void requireManagePermission(SysUser currentUser, Long projectId) {
        if (projectId == null) {
            throw BusinessException.of(400, "项目ID不能为空");
        }
        projectPermissionService.checkProjectPermission(currentUser.getId(), projectId);
        if (!projectPermissionService.canManageProject(currentUser.getId(), projectId)) {
            throw BusinessException.forbidden("仅平台管理员或项目经理可管理摄像头");
        }
    }

    private Integer requireOnlineStatus(Integer value) {
        if (value == null || (value != 0 && value != 1)) {
            throw BusinessException.of(400, "摄像头在线状态仅支持0或1");
        }
        return value;
    }

    private String requireText(String value, int maxLength, String fieldName) {
        String normalized = optionalText(value, maxLength, fieldName);
        if (normalized == null) {
            throw BusinessException.of(400, fieldName + "不能为空");
        }
        return normalized;
    }

    private String optionalText(String value, int maxLength, String fieldName) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > maxLength) {
            throw BusinessException.of(400, fieldName + "不能超过" + maxLength + "个字符");
        }
        return normalized;
    }

    private void requireSingleWrite(int affectedRows, String operation) {
        if (affectedRows != 1) {
            throw BusinessException.of(409, operation + "未生效，请刷新后重试");
        }
    }
}
