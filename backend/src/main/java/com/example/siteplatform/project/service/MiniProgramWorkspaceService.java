package com.example.siteplatform.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.camera.entity.CameraResource;
import com.example.siteplatform.camera.mapper.CameraResourceMapper;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.device.entity.DeviceInfo;
import com.example.siteplatform.device.mapper.DeviceInfoMapper;
import com.example.siteplatform.file.entity.FileResource;
import com.example.siteplatform.file.mapper.FileResourceMapper;
import com.example.siteplatform.person.entity.TemporaryPerson;
import com.example.siteplatform.person.mapper.TemporaryPersonMapper;
import com.example.siteplatform.project.dto.MiniProgramWorkspaceOverviewVO;
import com.example.siteplatform.project.entity.ProjectInfo;
import com.example.siteplatform.project.mapper.ProjectInfoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class MiniProgramWorkspaceService {

    @Autowired
    private ProjectPermissionService projectPermissionService;

    @Autowired
    private ProjectInfoMapper projectInfoMapper;

    @Autowired
    private TemporaryPersonMapper personMapper;

    @Autowired
    private CameraResourceMapper cameraMapper;

    @Autowired
    private FileResourceMapper fileMapper;

    @Autowired
    private DeviceInfoMapper deviceMapper;

    public MiniProgramWorkspaceOverviewVO getOverview(Long projectId, SysUser currentUser) {
        projectPermissionService.checkProjectPermission(currentUser.getId(), projectId);
        ProjectInfo project = projectInfoMapper.selectById(projectId);
        if (project == null) {
            throw BusinessException.notFound("施工区域不存在");
        }

        List<CameraResource> cameras = cameraMapper.selectList(new LambdaQueryWrapper<CameraResource>()
                .eq(CameraResource::getProjectId, projectId)
                .orderByDesc(CameraResource::getOnlineStatus)
                .orderByAsc(CameraResource::getArea)
                .orderByAsc(CameraResource::getCameraName));
        List<FileResource> files = fileMapper.selectList(new LambdaQueryWrapper<FileResource>()
                .eq(FileResource::getProjectId, projectId)
                .orderByDesc(FileResource::getCreateTime)
                .last("LIMIT 3"));
        List<DeviceInfo> devices = deviceMapper.selectList(new LambdaQueryWrapper<DeviceInfo>()
                .eq(DeviceInfo::getProjectId, projectId)
                .orderByDesc(DeviceInfo::getLastReport)
                .orderByDesc(DeviceInfo::getCreateTime)
                .last("LIMIT 4"));

        int onlineCameraCount = (int) cameras.stream()
                .filter(camera -> Integer.valueOf(1).equals(camera.getOnlineStatus()))
                .count();
        int alarmDeviceCount = toInt(deviceMapper.selectCount(new LambdaQueryWrapper<DeviceInfo>()
                .eq(DeviceInfo::getProjectId, projectId)
                .in(DeviceInfo::getStatus, "abnormal", "ABNORMAL", "alarm", "ALARM", "danger", "DANGER", "异常", "告警")));

        MiniProgramWorkspaceOverviewVO overview = new MiniProgramWorkspaceOverviewVO();
        overview.setOnsitePersonCount(countOnsitePeople(projectId));
        overview.setTodayEntryCount(countTodayEntries(projectId));
        overview.setCameraTotal(cameras.size());
        overview.setOnlineCameraCount(onlineCameraCount);
        overview.setFileTotal(toInt(fileMapper.selectCount(new LambdaQueryWrapper<FileResource>()
                .eq(FileResource::getProjectId, projectId))));
        overview.setTodayFileCount(countTodayFiles(projectId));
        overview.setDeviceTotal(toInt(deviceMapper.selectCount(new LambdaQueryWrapper<DeviceInfo>()
                .eq(DeviceInfo::getProjectId, projectId))));
        overview.setAlarmDeviceCount(alarmDeviceCount);
        overview.setProjectProgress(calculateProgress(project));
        overview.setRiskAlert(buildRiskAlert(cameras.size() - onlineCameraCount, alarmDeviceCount));
        overview.setCameras(cameras.stream().limit(4).map(this::toCameraItem).toList());
        overview.setRecentFiles(files.stream().map(this::toFileItem).toList());
        overview.setDevices(devices.stream().map(this::toDeviceItem).toList());
        return overview;
    }

    private Integer countOnsitePeople(Long projectId) {
        LambdaQueryWrapper<TemporaryPerson> wrapper = new LambdaQueryWrapper<TemporaryPerson>()
                .eq(TemporaryPerson::getProjectId, projectId)
                .and(w -> w.isNull(TemporaryPerson::getStatus)
                        .or()
                        .notIn(TemporaryPerson::getStatus, "LEFT", "已离场"));
        return toInt(personMapper.selectCount(wrapper));
    }

    private Integer countTodayEntries(Long projectId) {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        return toInt(personMapper.selectCount(new LambdaQueryWrapper<TemporaryPerson>()
                .eq(TemporaryPerson::getProjectId, projectId)
                .ge(TemporaryPerson::getEntryTime, start)
                .lt(TemporaryPerson::getEntryTime, start.plusDays(1))));
    }

    private Integer countTodayFiles(Long projectId) {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        return toInt(fileMapper.selectCount(new LambdaQueryWrapper<FileResource>()
                .eq(FileResource::getProjectId, projectId)
                .ge(FileResource::getCreateTime, start)
                .lt(FileResource::getCreateTime, start.plusDays(1))));
    }

    private int calculateProgress(ProjectInfo project) {
        if (project.getStartDate() == null || project.getEndDate() == null) {
            return 0;
        }
        long totalDays = Math.max(ChronoUnit.DAYS.between(project.getStartDate(), project.getEndDate()), 1);
        long elapsedDays = ChronoUnit.DAYS.between(project.getStartDate(), LocalDate.now());
        return (int) Math.max(0, Math.min(100, Math.round(elapsedDays * 100.0 / totalDays)));
    }

    private String buildRiskAlert(int offlineCameras, int alarmDevices) {
        if (offlineCameras <= 0 && alarmDevices <= 0) {
            return "当前施工区域暂无设备异常";
        }
        if (offlineCameras > 0 && alarmDevices > 0) {
            return offlineCameras + "路摄像头离线 · " + alarmDevices + "台设备异常";
        }
        return offlineCameras > 0 ? offlineCameras + "路摄像头离线" : alarmDevices + "台设备异常";
    }

    private MiniProgramWorkspaceOverviewVO.CameraItem toCameraItem(CameraResource camera) {
        MiniProgramWorkspaceOverviewVO.CameraItem item = new MiniProgramWorkspaceOverviewVO.CameraItem();
        item.setId(camera.getId());
        item.setName(camera.getCameraName());
        item.setCode(camera.getCameraCode());
        item.setArea(camera.getArea());
        item.setType(camera.getCameraType());
        item.setStreamUrl(camera.getRtspUrl());
        item.setOnline(Integer.valueOf(1).equals(camera.getOnlineStatus()));
        return item;
    }

    private MiniProgramWorkspaceOverviewVO.FileItem toFileItem(FileResource file) {
        MiniProgramWorkspaceOverviewVO.FileItem item = new MiniProgramWorkspaceOverviewVO.FileItem();
        item.setId(file.getId());
        item.setName(file.getFileName());
        item.setType(file.getFileType());
        item.setStatus(file.getStatus());
        item.setCreateTime(file.getCreateTime());
        return item;
    }

    private MiniProgramWorkspaceOverviewVO.DeviceItem toDeviceItem(DeviceInfo device) {
        MiniProgramWorkspaceOverviewVO.DeviceItem item = new MiniProgramWorkspaceOverviewVO.DeviceItem();
        item.setId(device.getId());
        item.setName(device.getDeviceName());
        item.setCode(device.getDeviceCode());
        item.setType(device.getDeviceType());
        item.setStatus(device.getStatus());
        item.setLastReport(device.getLastReport());
        item.setRemark(device.getRemark());
        return item;
    }

    private int toInt(Long value) {
        return value == null ? 0 : Math.toIntExact(value);
    }
}
