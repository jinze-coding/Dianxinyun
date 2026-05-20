package com.example.siteplatform.camera.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.camera.entity.CameraResource;
import com.example.siteplatform.camera.mapper.CameraResourceMapper;
import com.example.siteplatform.common.Result;
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

    @Autowired
    private CameraResourceMapper cameraMapper;

    @Autowired
    private AuthService authService;

    @Operation(summary = "获取摄像头列表")
    @GetMapping
    public Result<List<Map<String, Object>>> getCameraList(
            @Parameter(description = "项目ID") @RequestParam(required = false) Long projectId,
            @Parameter(description = "在线状态") @RequestParam(required = false) Integer onlineStatus,
            @RequestHeader(value = "Authorization", required = false) String token) {
        authService.getCurrentUser(token);

        LambdaQueryWrapper<CameraResource> wrapper = new LambdaQueryWrapper<>();
        if (projectId != null) {
            wrapper.eq(CameraResource::getProjectId, projectId);
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
            Map<String, Object> item = new HashMap<>();
            item.put("id", camera.getId());
            item.put("name", camera.getCameraName());
            item.put("code", camera.getCameraCode());
            item.put("area", camera.getArea());
            item.put("type", camera.getCameraType());
            item.put("rtspUrl", camera.getRtspUrl());
            item.put("online", camera.getOnlineStatus() != null && camera.getOnlineStatus() == 1);
            result.add(item);
        }

        return Result.success(result);
    }

    @Operation(summary = "获取摄像头详情")
    @GetMapping("/{id}")
    public Result<Map<String, Object>> getCameraById(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {
        authService.getCurrentUser(token);

        CameraResource camera = cameraMapper.selectById(id);
        if (camera == null) {
            return Result.error("摄像头不存在");
        }

        Map<String, Object> item = new HashMap<>();
        item.put("id", camera.getId());
        item.put("name", camera.getCameraName());
        item.put("code", camera.getCameraCode());
        item.put("area", camera.getArea());
        item.put("type", camera.getCameraType());
        item.put("rtspUrl", camera.getRtspUrl());
        item.put("online", camera.getOnlineStatus() != null && camera.getOnlineStatus() == 1);

        return Result.success(item);
    }

    @Operation(summary = "创建摄像头")
    @PostMapping
    public Result<CameraResource> createCamera(
            @RequestBody CameraResource camera,
            @RequestHeader(value = "Authorization", required = false) String token) {
        authService.getCurrentUser(token);
        camera.setCreateTime(LocalDateTime.now());
        camera.setUpdateTime(LocalDateTime.now());
        if (camera.getOnlineStatus() == null) {
            camera.setOnlineStatus(1);
        }
        cameraMapper.insert(camera);
        return Result.success(camera);
    }

    @Operation(summary = "更新摄像头")
    @PutMapping("/{id}")
    public Result<Void> updateCamera(
            @PathVariable Long id,
            @RequestBody CameraResource camera,
            @RequestHeader(value = "Authorization", required = false) String token) {
        authService.getCurrentUser(token);

        CameraResource existing = cameraMapper.selectById(id);
        if (existing == null) {
            return Result.error("摄像头不存在");
        }

        camera.setId(id);
        camera.setUpdateTime(LocalDateTime.now());
        cameraMapper.updateById(camera);
        return Result.success();
    }

    @Operation(summary = "删除摄像头")
    @DeleteMapping("/{id}")
    public Result<Void> deleteCamera(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {
        authService.getCurrentUser(token);
        cameraMapper.deleteById(id);
        return Result.success();
    }
}
