package com.example.siteplatform.device.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.common.Result;
import com.example.siteplatform.device.constant.DeviceStatus;
import com.example.siteplatform.device.constant.DeviceType;
import com.example.siteplatform.device.entity.DeviceInfo;
import com.example.siteplatform.device.mapper.DeviceInfoMapper;
import com.example.siteplatform.project.entity.ProjectInfo;
import com.example.siteplatform.project.service.ProjectPermissionService;
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

    private static final int NAME_MAX_LENGTH = 100;
    private static final int CODE_MAX_LENGTH = 100;
    private static final int TYPE_MAX_LENGTH = 50;
    private static final int MEASUREMENT_MAX_LENGTH = 50;
    private static final int REMARK_MAX_LENGTH = 500;

    @Autowired
    private DeviceInfoMapper deviceMapper;

    @Autowired
    private AuthService authService;

    @Autowired
    private ProjectPermissionService projectPermissionService;

    @Operation(summary = "获取设备列表")
    @GetMapping
    public Result<List<DeviceInfo>> getDeviceList(
            @Parameter(description = "项目ID") @RequestParam(required = false) Long projectId,
            @Parameter(description = "设备类型") @RequestParam(required = false) String deviceType,
            @Parameter(description = "状态") @RequestParam(required = false) String status,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        List<Long> readableProjectIds = resolveReadableProjectIds(currentUser, projectId);
        if (readableProjectIds != null && readableProjectIds.isEmpty()) {
            return Result.success(List.of());
        }

        LambdaQueryWrapper<DeviceInfo> wrapper = new LambdaQueryWrapper<>();
        if (readableProjectIds != null) {
            wrapper.in(DeviceInfo::getProjectId, readableProjectIds);
        }
        if (deviceType != null && !deviceType.isBlank() && !"全部".equals(deviceType.trim())) {
            wrapper.in(DeviceInfo::getDeviceType, DeviceType.compatibleQueryValues(deviceType));
        }
        if (status != null && !status.isBlank() && !"全部".equals(status.trim())) {
            wrapper.in(DeviceInfo::getStatus, DeviceStatus.compatibleQueryValues(status));
        }
        wrapper.orderByDesc(DeviceInfo::getCreateTime);

        List<DeviceInfo> list = deviceMapper.selectList(wrapper);
        list.forEach(this::normalizeDeviceStatus);
        return Result.success(list);
    }

    @Operation(summary = "获取设备详情")
    @GetMapping("/{id}")
    public Result<DeviceInfo> getDeviceById(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);

        DeviceInfo device = deviceMapper.selectById(id);
        if (device == null) {
            throw BusinessException.notFound("设备不存在");
        }
        projectPermissionService.checkProjectPermission(currentUser.getId(), device.getProjectId());
        normalizeDeviceStatus(device);
        return Result.success(device);
    }

    @Operation(summary = "获取塔吊设备列表")
    @GetMapping("/tower-cranes")
    public Result<List<DeviceInfo>> getTowerCranes(
            @Parameter(description = "项目ID") @RequestParam(required = false) Long projectId,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        List<Long> readableProjectIds = resolveReadableProjectIds(currentUser, projectId);
        if (readableProjectIds != null && readableProjectIds.isEmpty()) {
            return Result.success(List.of());
        }

        LambdaQueryWrapper<DeviceInfo> wrapper = new LambdaQueryWrapper<>();
        if (readableProjectIds != null) {
            wrapper.in(DeviceInfo::getProjectId, readableProjectIds);
        }
        wrapper.in(DeviceInfo::getDeviceType, DeviceType.compatibleQueryValues(DeviceType.TOWER_CRANE));
        wrapper.orderByDesc(DeviceInfo::getCreateTime);

        List<DeviceInfo> list = deviceMapper.selectList(wrapper);
        list.forEach(this::normalizeDeviceStatus);
        return Result.success(list);
    }

    @Operation(summary = "创建设备")
    @PostMapping
    public Result<DeviceInfo> createDevice(
            @RequestBody DeviceInfo device,
            @RequestHeader(value = "Authorization", required = false) String token) {
        if (device == null) {
            throw BusinessException.of(400, "设备信息不能为空");
        }
        SysUser currentUser = authService.getCurrentUser(token);
        requireManagePermission(currentUser, device.getProjectId());

        DeviceInfo created = new DeviceInfo();
        created.setProjectId(device.getProjectId());
        created.setDeviceName(requireText(device.getDeviceName(), NAME_MAX_LENGTH, "设备名称"));
        created.setDeviceCode(optionalText(device.getDeviceCode(), CODE_MAX_LENGTH, "设备编号"));
        created.setDeviceType(requireDeviceType(device.getDeviceType()));
        created.setStatus(device.getStatus() == null
                ? DeviceStatus.RUNNING
                : requireDeviceStatus(device.getStatus()));
        created.setHeight(optionalText(device.getHeight(), MEASUREMENT_MAX_LENGTH, "设备高度"));
        created.setMaxLoad(optionalText(device.getMaxLoad(), MEASUREMENT_MAX_LENGTH, "最大载重"));
        created.setLastReport(device.getLastReport());
        created.setRemark(optionalText(device.getRemark(), REMARK_MAX_LENGTH, "设备备注"));
        created.setCreateTime(LocalDateTime.now());
        created.setUpdateTime(LocalDateTime.now());
        requireSingleWrite(deviceMapper.insert(created), "设备新增");
        return Result.success(created);
    }

    @Operation(summary = "更新设备")
    @PutMapping("/{id}")
    public Result<Void> updateDevice(
            @PathVariable Long id,
            @RequestBody DeviceInfo device,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);

        DeviceInfo existing = deviceMapper.selectById(id);
        if (existing == null) {
            throw BusinessException.notFound("设备不存在");
        }
        requireManagePermission(currentUser, existing.getProjectId());

        if (device.getDeviceName() != null) {
            existing.setDeviceName(requireText(device.getDeviceName(), NAME_MAX_LENGTH, "设备名称"));
        }
        if (device.getDeviceCode() != null) {
            existing.setDeviceCode(optionalText(device.getDeviceCode(), CODE_MAX_LENGTH, "设备编号"));
        }
        if (device.getDeviceType() != null) {
            existing.setDeviceType(requireDeviceType(device.getDeviceType()));
        }
        if (device.getStatus() != null) {
            existing.setStatus(requireDeviceStatus(device.getStatus()));
        }
        if (device.getHeight() != null) {
            existing.setHeight(optionalText(device.getHeight(), MEASUREMENT_MAX_LENGTH, "设备高度"));
        }
        if (device.getMaxLoad() != null) {
            existing.setMaxLoad(optionalText(device.getMaxLoad(), MEASUREMENT_MAX_LENGTH, "最大载重"));
        }
        if (device.getLastReport() != null) {
            existing.setLastReport(device.getLastReport());
        }
        if (device.getRemark() != null) {
            existing.setRemark(optionalText(device.getRemark(), REMARK_MAX_LENGTH, "设备备注"));
        }
        existing.setUpdateTime(LocalDateTime.now());
        requireSingleWrite(deviceMapper.updateById(existing), "设备更新");
        return Result.success();
    }

    @Operation(summary = "删除设备")
    @DeleteMapping("/{id}")
    public Result<Void> deleteDevice(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        DeviceInfo existing = deviceMapper.selectById(id);
        if (existing == null) {
            throw BusinessException.notFound("设备不存在");
        }
        requireManagePermission(currentUser, existing.getProjectId());
        requireSingleWrite(deviceMapper.deleteById(id), "设备删除");
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

    private void requireManagePermission(SysUser currentUser, Long projectId) {
        if (projectId == null) {
            throw BusinessException.of(400, "项目ID不能为空");
        }
        projectPermissionService.checkProjectPermission(currentUser.getId(), projectId);
        if (!projectPermissionService.canManageProject(currentUser.getId(), projectId)) {
            throw BusinessException.forbidden("仅平台管理员或项目经理可管理设备");
        }
    }

    private String requireDeviceType(String deviceType) {
        String normalized = DeviceType.normalize(deviceType);
        if (normalized == null || normalized.isBlank()) {
            throw BusinessException.of(400, "设备类型不能为空");
        }
        if (normalized.length() > TYPE_MAX_LENGTH) {
            throw BusinessException.of(400, "设备类型不能超过" + TYPE_MAX_LENGTH + "个字符");
        }
        return normalized;
    }

    private String requireDeviceStatus(String status) {
        String normalized = DeviceStatus.normalize(status);
        if (!DeviceStatus.isSupported(normalized)) {
            throw BusinessException.of(
                    400,
                    "设备状态仅支持 running、stopped、abnormal、maintenance");
        }
        return normalized;
    }

    private void normalizeDeviceStatus(DeviceInfo device) {
        if (device != null && device.getStatus() != null) {
            device.setStatus(DeviceStatus.normalize(device.getStatus()));
        }
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
