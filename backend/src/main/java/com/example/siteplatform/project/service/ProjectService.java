package com.example.siteplatform.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.camera.entity.CameraResource;
import com.example.siteplatform.camera.mapper.CameraResourceMapper;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.device.entity.DeviceInfo;
import com.example.siteplatform.device.mapper.DeviceInfoMapper;
import com.example.siteplatform.electricbox.entity.ElectricBox;
import com.example.siteplatform.electricbox.mapper.ElectricBoxMapper;
import com.example.siteplatform.electricbox.service.ElectricBoxInspectionScopeService;
import com.example.siteplatform.file.mapper.FileResourceMapper;
import com.example.siteplatform.inspection.entity.InspectionRecord;
import com.example.siteplatform.inspection.entity.InspectionRectification;
import com.example.siteplatform.inspection.mapper.InspectionRecordMapper;
import com.example.siteplatform.inspection.mapper.InspectionRectificationMapper;
import com.example.siteplatform.log.entity.OperationLog;
import com.example.siteplatform.log.mapper.OperationLogMapper;
import com.example.siteplatform.common.PageResult;
import com.example.siteplatform.project.constant.InspectionPermissionCodes;
import com.example.siteplatform.project.dto.MiniProgramProjectVO;
import com.example.siteplatform.project.dto.ProjectLocationUpdateRequest;
import com.example.siteplatform.project.dto.ProjectMapPointVO;
import com.example.siteplatform.project.entity.ProjectInfo;
import com.example.siteplatform.project.mapper.ProjectInfoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class ProjectService {

    private static final String DEFAULT_COORDINATE_TYPE = "BD09";
    private static final List<String> SUPPORTED_COORDINATE_TYPES = List.of("BD09", "GCJ02", "WGS84");

    @Autowired
    private ProjectInfoMapper projectMapper;

    @Autowired
    private ProjectPermissionService projectPermissionService;

    @Autowired
    private CameraResourceMapper cameraResourceMapper;

    @Autowired
    private DeviceInfoMapper deviceInfoMapper;

    @Autowired
    private FileResourceMapper fileResourceMapper;

    @Autowired
    private ElectricBoxMapper electricBoxMapper;

    @Autowired
    private InspectionRecordMapper inspectionRecordMapper;

    @Autowired
    private ElectricBoxInspectionScopeService inspectionScopeService;

    @Autowired
    private InspectionRectificationMapper inspectionRectificationMapper;

    @Autowired
    private OperationLogMapper operationLogMapper;

    public List<ProjectInfo> getProjectList(SysUser currentUser) {
        // 平台管理员可以看到所有项目
        if (projectPermissionService.isPlatformAdmin(currentUser.getId())) {
            return projectMapper.selectList(null);
        }
        // 其他用户只能看到有权限的项目
        return projectPermissionService.getUserProjects(currentUser.getId());
    }

    public ProjectInfo getProjectById(Long projectId, SysUser currentUser) {
        // 校验项目权限
        projectPermissionService.checkProjectPermission(currentUser.getId(), projectId);

        ProjectInfo project = projectMapper.selectById(projectId);
        if (project == null) {
            throw BusinessException.notFound("项目不存在");
        }
        return project;
    }

    public List<MiniProgramProjectVO> getMiniProgramProjectList(SysUser currentUser) {
        return getProjectList(currentUser).stream()
                .map(project -> buildMiniProgramProject(project, currentUser))
                .toList();
    }

    public MiniProgramProjectVO getMiniProgramProjectById(Long projectId, SysUser currentUser) {
        return buildMiniProgramProject(getProjectById(projectId, currentUser), currentUser);
    }

    public List<ProjectMapPointVO> getProjectMapPoints(SysUser currentUser) {
        return getProjectList(currentUser).stream()
                .map(this::buildProjectMapPoint)
                .toList();
    }

    public ProjectMapPointVO getProjectMapDetail(Long projectId, SysUser currentUser) {
        ProjectInfo project = getProjectById(projectId, currentUser);
        return buildProjectMapPoint(project);
    }

    public ProjectMapPointVO updateProjectLocation(Long projectId, ProjectLocationUpdateRequest request, SysUser currentUser) {
        if (!projectPermissionService.canManageProject(currentUser.getId(), projectId)) {
            throw BusinessException.forbidden("只有平台管理员或项目管理员可以更新项目定位");
        }

        ProjectInfo project = projectMapper.selectById(projectId);
        if (project == null) {
            throw BusinessException.notFound("项目不存在");
        }

        validateLocationRequest(request);

        project.setLongitude(request.getLongitude());
        project.setLatitude(request.getLatitude());
        project.setProvince(trimToNull(request.getProvince()));
        project.setCity(trimToNull(request.getCity()));
        project.setDistrict(trimToNull(request.getDistrict()));
        project.setAddress(trimToNull(request.getAddress()));
        project.setCoordinateType(normalizeCoordinateType(request.getCoordinateType()));
        project.setUpdateTime(LocalDateTime.now());
        projectMapper.updateById(project);

        recordLocationUpdateLog(projectId, currentUser);
        return buildProjectMapPoint(projectMapper.selectById(projectId));
    }

    public PageResult<ProjectInfo> getProjectPage(Integer pageNo, Integer pageSize, SysUser currentUser) {
        Page<ProjectInfo> page = new Page<>(pageNo, pageSize);

        LambdaQueryWrapper<ProjectInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(ProjectInfo::getCreateTime);

        Page<ProjectInfo> result = projectMapper.selectPage(page, wrapper);

        return PageResult.of(
                (int) result.getCurrent(),
                (int) result.getSize(),
                result.getTotal(),
                result.getRecords()
        );
    }

    public ProjectInfo addProject(ProjectInfo project, SysUser currentUser) {
        // 平台管理员才能添加项目
        if (!projectPermissionService.isPlatformAdmin(currentUser.getId())) {
            throw BusinessException.of(403, "只有平台管理员才能添加项目");
        }
        projectMapper.insert(project);
        return project;
    }

    public void deleteProject(Long projectId, SysUser currentUser) {
        // 平台管理员才能删除项目
        if (!projectPermissionService.isPlatformAdmin(currentUser.getId())) {
            throw BusinessException.of(403, "只有平台管理员才能删除项目");
        }
        projectMapper.deleteById(projectId);
    }

    public ProjectInfo updateProject(Long projectId, ProjectInfo project, SysUser currentUser) {
        // 平台管理员才能更新项目
        if (!projectPermissionService.isPlatformAdmin(currentUser.getId())) {
            throw BusinessException.of(403, "只有平台管理员才能更新项目");
        }
        ProjectInfo existing = projectMapper.selectById(projectId);
        if (existing == null) {
            throw BusinessException.notFound("项目不存在");
        }
        // 更新字段
        project.setId(projectId);
        projectMapper.updateById(project);
        return projectMapper.selectById(projectId);
    }

    private ProjectMapPointVO buildProjectMapPoint(ProjectInfo project) {
        ProjectMapPointVO vo = new ProjectMapPointVO();
        vo.setProjectId(project.getId());
        vo.setId(project.getId());
        vo.setProjectName(project.getProjectName());
        vo.setShortName(project.getShortName());
        vo.setProjectStatus(project.getProjectStatus());
        vo.setCurrentStage(project.getPhase());
        vo.setLongitude(project.getLongitude());
        vo.setLatitude(project.getLatitude());
        vo.setCoordinateType(StringUtils.hasText(project.getCoordinateType())
                ? project.getCoordinateType()
                : DEFAULT_COORDINATE_TYPE);
        vo.setProvince(project.getProvince());
        vo.setCity(project.getCity());
        vo.setDistrict(project.getDistrict());
        vo.setAddress(project.getAddress());
        vo.setHasLocation(project.getLongitude() != null && project.getLatitude() != null);
        vo.setCameraTotal(countCameras(project.getId(), null));
        vo.setOnlineCameraCount(countCameras(project.getId(), 1));
        vo.setDeviceTotal(countDevices(project.getId(), null));
        vo.setAlarmDeviceCount(countAlarmDevices(project.getId()));
        vo.setFileTotal(countFiles(project.getId()));
        vo.setLastUpdateTime(project.getUpdateTime());
        return vo;
    }

    private Long countCameras(Long projectId, Integer onlineStatus) {
        LambdaQueryWrapper<CameraResource> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CameraResource::getProjectId, projectId);
        if (onlineStatus != null) {
            wrapper.eq(CameraResource::getOnlineStatus, onlineStatus);
        }
        return cameraResourceMapper.selectCount(wrapper);
    }

    private Long countDevices(Long projectId, String status) {
        LambdaQueryWrapper<DeviceInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceInfo::getProjectId, projectId);
        if (StringUtils.hasText(status)) {
            wrapper.eq(DeviceInfo::getStatus, status);
        }
        return deviceInfoMapper.selectCount(wrapper);
    }

    private Long countAlarmDevices(Long projectId) {
        LambdaQueryWrapper<DeviceInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceInfo::getProjectId, projectId)
                .in(DeviceInfo::getStatus, "abnormal", "alarm", "ALARM");
        return deviceInfoMapper.selectCount(wrapper);
    }

    private Long countFiles(Long projectId) {
        LambdaQueryWrapper<com.example.siteplatform.file.entity.FileResource> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(com.example.siteplatform.file.entity.FileResource::getProjectId, projectId);
        return fileResourceMapper.selectCount(wrapper);
    }

    private MiniProgramProjectVO buildMiniProgramProject(ProjectInfo project, SysUser currentUser) {
        Long projectId = project.getId();
        boolean manager = projectPermissionService.isPlatformAdmin(currentUser.getId())
                || projectPermissionService.canManageProject(currentUser.getId(), projectId)
                || projectPermissionService.hasInspectionPermission(currentUser.getId(), projectId,
                InspectionPermissionCodes.INSPECTION_RECORD_VIEW)
                || projectPermissionService.hasInspectionPermission(currentUser.getId(), projectId,
                InspectionPermissionCodes.SUMMARY_VIEW);
        List<ElectricBox> visibleBoxes = queryVisibleActiveBoxes(projectId, currentUser, manager);
        int pendingInspection = (int) visibleBoxes.stream()
                .filter(box -> inspectionScopeService.isRequired(box, LocalDate.now()))
                .filter(box -> !hasTodayDailyRecord(projectId, box.getId()))
                .count();

        MiniProgramProjectVO vo = new MiniProgramProjectVO();
        vo.setId(projectId);
        vo.setProjectName(project.getProjectName());
        vo.setShortName(project.getShortName());
        vo.setArea(project.getArea());
        vo.setPeriod(project.getPeriod());
        vo.setPhase(project.getPhase());
        vo.setProjectStatus(project.getProjectStatus());
        vo.setSafetyGoal(project.getSafetyGoal());
        vo.setQualityGoal(project.getQualityGoal());
        vo.setAddress(project.getAddress());
        vo.setManager(project.getManager());
        vo.setContractor(project.getContractor());
        vo.setDescription(project.getDescription());
        vo.setStartDate(project.getStartDate());
        vo.setEndDate(project.getEndDate());
        vo.setProvince(project.getProvince());
        vo.setCity(project.getCity());
        vo.setDistrict(project.getDistrict());
        vo.setLongitude(project.getLongitude());
        vo.setLatitude(project.getLatitude());
        vo.setCoordinateType(project.getCoordinateType());
        vo.setStatus(project.getProjectStatus());
        vo.setStage(project.getPhase());
        vo.setElectricBoxTotal(visibleBoxes.size());
        vo.setTodayInspectionCount(countTodayInspections(projectId, currentUser, manager));
        vo.setPendingReviewCount(0);
        vo.setPendingRectificationCount(0);
        vo.setPendingTodoCount(pendingInspection);
        return vo;
    }

    private List<ElectricBox> queryVisibleActiveBoxes(Long projectId, SysUser currentUser, boolean manager) {
        LambdaQueryWrapper<ElectricBox> wrapper = new LambdaQueryWrapper<ElectricBox>()
                .eq(ElectricBox::getProjectId, projectId)
                .eq(ElectricBox::getStatus, "ACTIVE")
                .orderByAsc(ElectricBox::getBoxCode);
        if (!manager) {
            wrapper.eq(ElectricBox::getResponsibleElectricianId, currentUser.getId());
        }
        return electricBoxMapper.selectList(wrapper);
    }

    private int countPendingReviews(Long projectId, SysUser currentUser) {
        if (!projectPermissionService.hasInspectionPermission(currentUser.getId(), projectId, InspectionPermissionCodes.INSPECTION_REVIEW)) {
            return 0;
        }
        LambdaQueryWrapper<InspectionRecord> wrapper = new LambdaQueryWrapper<InspectionRecord>()
                .eq(InspectionRecord::getProjectId, projectId)
                .eq(InspectionRecord::getStatus, "REVIEW_PENDING");
        boolean canSeeAllReview = projectPermissionService.isPlatformAdmin(currentUser.getId())
                || projectPermissionService.canManageProject(currentUser.getId(), projectId)
                || projectPermissionService.hasInspectionPermission(currentUser.getId(), projectId, InspectionPermissionCodes.PERMISSION_MANAGE);
        if (!canSeeAllReview) {
            wrapper.and(w -> w.isNull(InspectionRecord::getAssignedReviewerId)
                    .or()
                    .eq(InspectionRecord::getAssignedReviewerId, currentUser.getId()));
        }
        return toInt(inspectionRecordMapper.selectCount(wrapper));
    }

    private boolean hasTodayDailyRecord(Long projectId, Long electricBoxId) {
        return inspectionRecordMapper.selectCount(new LambdaQueryWrapper<InspectionRecord>()
                .eq(InspectionRecord::getProjectId, projectId)
                .eq(InspectionRecord::getElectricBoxId, electricBoxId)
                .eq(InspectionRecord::getSource, "ELECTRICIAN_DAILY")
                .eq(InspectionRecord::getCheckDate, LocalDate.now())) > 0;
    }

    private Integer countTodayInspections(Long projectId, SysUser currentUser, boolean manager) {
        LambdaQueryWrapper<InspectionRecord> wrapper = new LambdaQueryWrapper<InspectionRecord>()
                .eq(InspectionRecord::getProjectId, projectId)
                .eq(InspectionRecord::getSource, "ELECTRICIAN_DAILY")
                .eq(InspectionRecord::getCheckDate, LocalDate.now());
        if (!manager) {
            wrapper.eq(InspectionRecord::getInspectorId, currentUser.getId());
        }
        return toInt(inspectionRecordMapper.selectCount(wrapper));
    }

    private int countRectifications(Long projectId, SysUser currentUser, boolean manager, List<String> statuses) {
        LambdaQueryWrapper<InspectionRectification> wrapper = new LambdaQueryWrapper<InspectionRectification>()
                .eq(InspectionRectification::getProjectId, projectId)
                .in(InspectionRectification::getStatus, statuses);
        if (!manager) {
            wrapper.eq(InspectionRectification::getAssigneeId, currentUser.getId());
        }
        return toInt(inspectionRectificationMapper.selectCount(wrapper));
    }

    private int toInt(Long value) {
        return value == null ? 0 : Math.toIntExact(value);
    }

    private void validateLocationRequest(ProjectLocationUpdateRequest request) {
        if (request == null) {
            throw BusinessException.of(400, "定位信息不能为空");
        }
        if (request.getLongitude() == null || request.getLatitude() == null) {
            throw BusinessException.of(400, "经度和纬度不能为空");
        }
        if (request.getLongitude().compareTo(BigDecimal.valueOf(-180)) < 0
                || request.getLongitude().compareTo(BigDecimal.valueOf(180)) > 0) {
            throw BusinessException.of(400, "经度范围必须在 -180 到 180 之间");
        }
        if (request.getLatitude().compareTo(BigDecimal.valueOf(-90)) < 0
                || request.getLatitude().compareTo(BigDecimal.valueOf(90)) > 0) {
            throw BusinessException.of(400, "纬度范围必须在 -90 到 90 之间");
        }

        String coordinateType = normalizeCoordinateType(request.getCoordinateType());
        if (!SUPPORTED_COORDINATE_TYPES.contains(coordinateType)) {
            throw BusinessException.of(400, "坐标系类型必须是 BD09、GCJ02、WGS84 之一");
        }
        request.setCoordinateType(coordinateType);
    }

    private String normalizeCoordinateType(String coordinateType) {
        if (!StringUtils.hasText(coordinateType)) {
            return DEFAULT_COORDINATE_TYPE;
        }
        return coordinateType.trim().toUpperCase();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private void recordLocationUpdateLog(Long projectId, SysUser currentUser) {
        OperationLog log = new OperationLog();
        log.setUserId(currentUser.getId());
        log.setUsername(currentUser.getUsername());
        log.setOperationType("UPDATE_PROJECT_LOCATION");
        log.setOperationDesc("更新项目定位信息");
        log.setBusinessType("PROJECT");
        log.setBusinessId(projectId);
        log.setCreateTime(LocalDateTime.now());
        operationLogMapper.insert(log);
    }
}
