package com.example.siteplatform.device.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.common.Result;
import com.example.siteplatform.device.entity.DeviceInfo;
import com.example.siteplatform.device.mapper.DeviceInfoMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "设备管理", description = "设备信息查询、管理接口")
@RestController
@RequestMapping("/api/v1/devices")
public class DeviceController {

    @Autowired
    private DeviceInfoMapper deviceMapper;

    @Autowired
    private AuthService authService;

    @Operation(summary = "获取设备列表")
    @GetMapping
    public Result<List<DeviceInfo>> getDeviceList(
            @Parameter(description = "项目ID") @RequestParam(required = false) Long projectId,
            @Parameter(description = "设备类型") @RequestParam(required = false) String deviceType,
            @Parameter(description = "状态") @RequestParam(required = false) String status,
            @RequestHeader(value = "Authorization", required = false) String token) {
        authService.getCurrentUser(token);

        LambdaQueryWrapper<DeviceInfo> wrapper = new LambdaQueryWrapper<>();
        if (projectId != null) {
            wrapper.eq(DeviceInfo::getProjectId, projectId);
        }
        if (deviceType != null && !deviceType.isEmpty() && !"全部".equals(deviceType)) {
            wrapper.eq(DeviceInfo::getDeviceType, deviceType);
        }
        if (status != null && !status.isEmpty() && !"全部".equals(status)) {
            wrapper.eq(DeviceInfo::getStatus, status);
        }
        wrapper.orderByDesc(DeviceInfo::getCreateTime);

        List<DeviceInfo> list = deviceMapper.selectList(wrapper);
        return Result.success(list);
    }

    @Operation(summary = "获取设备详情")
    @GetMapping("/{id}")
    public Result<DeviceInfo> getDeviceById(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {
        authService.getCurrentUser(token);

        DeviceInfo device = deviceMapper.selectById(id);
        if (device == null) {
            return Result.error("设备不存在");
        }
        return Result.success(device);
    }

    @Operation(summary = "获取塔吊设备列表")
    @GetMapping("/tower-cranes")
    public Result<List<DeviceInfo>> getTowerCranes(
            @Parameter(description = "项目ID") @RequestParam(required = false) Long projectId,
            @RequestHeader(value = "Authorization", required = false) String token) {
        authService.getCurrentUser(token);

        LambdaQueryWrapper<DeviceInfo> wrapper = new LambdaQueryWrapper<>();
        if (projectId != null) {
            wrapper.eq(DeviceInfo::getProjectId, projectId);
        }
        wrapper.eq(DeviceInfo::getDeviceType, "塔吊");
        wrapper.orderByDesc(DeviceInfo::getCreateTime);

        List<DeviceInfo> list = deviceMapper.selectList(wrapper);
        return Result.success(list);
    }

    @Operation(summary = "创建设备")
    @PostMapping
    public Result<DeviceInfo> createDevice(
            @RequestBody DeviceInfo device,
            @RequestHeader(value = "Authorization", required = false) String token) {
        authService.getCurrentUser(token);
        device.setCreateTime(LocalDateTime.now());
        device.setUpdateTime(LocalDateTime.now());
        deviceMapper.insert(device);
        return Result.success(device);
    }

    @Operation(summary = "更新设备")
    @PutMapping("/{id}")
    public Result<Void> updateDevice(
            @PathVariable Long id,
            @RequestBody DeviceInfo device,
            @RequestHeader(value = "Authorization", required = false) String token) {
        authService.getCurrentUser(token);

        DeviceInfo existing = deviceMapper.selectById(id);
        if (existing == null) {
            return Result.error("设备不存在");
        }

        device.setId(id);
        device.setUpdateTime(LocalDateTime.now());
        deviceMapper.updateById(device);
        return Result.success();
    }

    @Operation(summary = "删除设备")
    @DeleteMapping("/{id}")
    public Result<Void> deleteDevice(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {
        authService.getCurrentUser(token);
        deviceMapper.deleteById(id);
        return Result.success();
    }
}
