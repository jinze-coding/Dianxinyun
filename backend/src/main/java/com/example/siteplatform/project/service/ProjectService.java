package com.example.siteplatform.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.camera.entity.CameraResource;
import com.example.siteplatform.camera.mapper.CameraResourceMapper;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.device.constant.DeviceStatus;
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
import com.example.siteplatform.system.constant.SystemPermissionCodes;
import com.example.siteplatform.system.service.SystemPermissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class ProjectService {

    private static final String DEFAULT_COORDINATE_TYPE = "BD09";
    private static final List<String> SUPPORTED_COORDINATE_TYPES = List.of("BD09", "GCJ02", "WGS84");
    private static final List<String> SUPPORTED_PROJECT_STATUSES = List.of("normal", "warning", "danger", "stopped");
    private static final int PROJECT_NAME_MAX_LENGTH = 200;
    private static final int SHORT_TEXT_MAX_LENGTH = 50;
    private static final int PERIOD_MAX_LENGTH = 100;
    private static final int GOAL_MAX_LENGTH = 200;
    private static final int CONTRACTOR_MAX_LENGTH = 200;
    private static final int DESCRIPTION_MAX_LENGTH = 5000;
    private static final int REGION_MAX_LENGTH = 64;
    private static final int ADDRESS_MAX_LENGTH = 500;
    private static final List<String> PROJECT_REFERENCE_TABLES = List.of(
            "camera_resource",
            "device_info",
            "document_folder",
            "electric_box",
            "electric_box_inspection_scope",
            "electric_box_qr_log",
            "external_system_config",
            "file_resource",
            "inspection_record",
            "inspection_rectification",
            "inspection_rectification_review_log",
            "inspection_review_log",
            "person_certificate",
            "person_entry_exit_log",
            "project_document",
            "project_inspection_setting",
            "quality_issue",
            "quality_issue_log",
            "safety_education_batch",
            "sys_user_project",
            "sys_user_project_role",
            "temporary_person",
            "video_access_log",
            "video_layout_config",
            "wechat_access_application"
    );

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

    @Autowired
    private SystemPermissionService systemPermissionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public List<ProjectInfo> getProjectList(SysUser currentUser) {
        // 平台管理员与注册审核员需要在账号审批时查看完整项目目录。
        if (projectPermissionService.isPlatformAdmin(currentUser.getId())
                || systemPermissionService.permissionCodes(currentUser.getId())
                .contains(SystemPermissionCodes.REGISTRATION_REVIEW)) {
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

    @Transactional
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
        project.setProvince(optionalText(request.getProvince(), REGION_MAX_LENGTH, "省份"));
        project.setCity(optionalText(request.getCity(), REGION_MAX_LENGTH, "城市"));
        project.setDistrict(optionalText(request.getDistrict(), REGION_MAX_LENGTH, "区县"));
        project.setAddress(optionalText(request.getAddress(), ADDRESS_MAX_LENGTH, "详细地址"));
        project.setCoordinateType(normalizeCoordinateType(request.getCoordinateType()));
        project.setUpdateTime(LocalDateTime.now());
        requireSingleWrite(projectMapper.updateById(project), "项目定位更新");

        recordLocationUpdateLog(projectId, currentUser);
        return buildProjectMapPoint(project);
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

    @Transactional
    public ProjectInfo addProject(ProjectInfo project, SysUser currentUser) {
        // 平台管理员才能添加项目
        if (!projectPermissionService.isPlatformAdmin(currentUser.getId())) {
            throw BusinessException.of(403, "只有平台管理员才能添加项目");
        }
        if (project == null) {
            throw new BusinessException("项目信息不能为空");
        }
        ProjectInfo created = new ProjectInfo();
        copyEditableProjectFields(project, created, true);
        created.setCreateTime(LocalDateTime.now());
        created.setUpdateTime(LocalDateTime.now());
        requireSingleWrite(projectMapper.insert(created), "项目新增");
        return created;
    }

    @Transactional
    public void deleteProject(Long projectId, SysUser currentUser) {
        // 平台管理员才能删除项目
        if (!projectPermissionService.isPlatformAdmin(currentUser.getId())) {
            throw BusinessException.of(403, "只有平台管理员才能删除项目");
        }
        ProjectInfo existing = projectMapper.selectById(projectId);
        if (existing == null) {
            throw BusinessException.notFound("项目不存在");
        }
        List<String> occupiedModules = new ArrayList<>();
        for (String table : PROJECT_REFERENCE_TABLES) {
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM `" + table + "` WHERE project_id = ?",
                    Long.class,
                    projectId);
            if (count != null && count > 0) {
                occupiedModules.add(table);
            }
        }
        if (!occupiedModules.isEmpty()) {
            throw BusinessException.of(409, "项目仍有关联成员或业务数据，禁止直接删除；请先归档并完成数据处置");
        }
        if (projectMapper.deleteById(projectId) != 1) {
            throw BusinessException.of(409, "项目状态已变化，请刷新后重试");
        }
    }

    @Transactional
    public ProjectInfo updateProject(Long projectId, ProjectInfo project, SysUser currentUser) {
        // 平台管理员才能更新项目
        if (!projectPermissionService.isPlatformAdmin(currentUser.getId())) {
            throw BusinessException.of(403, "只有平台管理员才能更新项目");
        }
        ProjectInfo existing = projectMapper.selectById(projectId);
        if (existing == null) {
            throw BusinessException.notFound("项目不存在");
        }
        if (project == null) {
            throw new BusinessException("项目信息不能为空");
        }
        copyEditableProjectFields(project, existing, false);
        existing.setUpdateTime(LocalDateTime.now());
        requireSingleWrite(projectMapper.updateById(existing), "项目更新");
        return existing;
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
            wrapper.in(DeviceInfo::getStatus, DeviceStatus.compatibleQueryValues(status));
        }
        return deviceInfoMapper.selectCount(wrapper);
    }

    private Long countAlarmDevices(Long projectId) {
        LambdaQueryWrapper<DeviceInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceInfo::getProjectId, projectId)
                .in(DeviceInfo::getStatus,
                        DeviceStatus.compatibleQueryValues(DeviceStatus.ABNORMAL));
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
        requireSingleWrite(operationLogMapper.insert(log), "项目定位审计日志新增");
    }

    private void copyEditableProjectFields(ProjectInfo source, ProjectInfo target, boolean requireName) {
        if (requireName || source.getProjectName() != null) {
            target.setProjectName(requireText(source.getProjectName(), PROJECT_NAME_MAX_LENGTH, "项目名称"));
        }
        if (source.getShortName() != null) target.setShortName(optionalText(source.getShortName(), SHORT_TEXT_MAX_LENGTH, "项目简称"));
        if (source.getArea() != null) target.setArea(optionalText(source.getArea(), SHORT_TEXT_MAX_LENGTH, "建筑面积"));
        if (source.getPeriod() != null) target.setPeriod(optionalText(source.getPeriod(), PERIOD_MAX_LENGTH, "工期"));
        if (source.getPhase() != null) target.setPhase(optionalText(source.getPhase(), SHORT_TEXT_MAX_LENGTH, "项目阶段"));
        if (source.getProjectStatus() != null) target.setProjectStatus(normalizeProjectStatus(source.getProjectStatus()));
        if (source.getSafetyGoal() != null) target.setSafetyGoal(optionalText(source.getSafetyGoal(), GOAL_MAX_LENGTH, "安全目标"));
        if (source.getQualityGoal() != null) target.setQualityGoal(optionalText(source.getQualityGoal(), GOAL_MAX_LENGTH, "质量目标"));
        if (source.getManager() != null) target.setManager(optionalText(source.getManager(), SHORT_TEXT_MAX_LENGTH, "项目经理"));
        if (source.getContractor() != null) target.setContractor(optionalText(source.getContractor(), CONTRACTOR_MAX_LENGTH, "施工单位"));
        if (source.getDescription() != null) target.setDescription(optionalText(source.getDescription(), DESCRIPTION_MAX_LENGTH, "项目描述"));
        if (source.getStartDate() != null) target.setStartDate(source.getStartDate());
        if (source.getEndDate() != null) target.setEndDate(source.getEndDate());
        if (source.getLongitude() != null || source.getLatitude() != null) {
            ProjectLocationUpdateRequest location = new ProjectLocationUpdateRequest();
            location.setLongitude(source.getLongitude() == null ? target.getLongitude() : source.getLongitude());
            location.setLatitude(source.getLatitude() == null ? target.getLatitude() : source.getLatitude());
            location.setCoordinateType(source.getCoordinateType() == null ? target.getCoordinateType() : source.getCoordinateType());
            validateLocationRequest(location);
            target.setLongitude(location.getLongitude());
            target.setLatitude(location.getLatitude());
            target.setCoordinateType(location.getCoordinateType());
        } else if (source.getCoordinateType() != null) {
            String coordinateType = normalizeCoordinateType(source.getCoordinateType());
            if (!SUPPORTED_COORDINATE_TYPES.contains(coordinateType)) {
                throw new BusinessException("坐标系类型必须是 BD09、GCJ02、WGS84 之一");
            }
            target.setCoordinateType(coordinateType);
        }
        if (source.getProvince() != null) target.setProvince(optionalText(source.getProvince(), REGION_MAX_LENGTH, "省份"));
        if (source.getCity() != null) target.setCity(optionalText(source.getCity(), REGION_MAX_LENGTH, "城市"));
        if (source.getDistrict() != null) target.setDistrict(optionalText(source.getDistrict(), REGION_MAX_LENGTH, "区县"));
        if (source.getAddress() != null) target.setAddress(optionalText(source.getAddress(), ADDRESS_MAX_LENGTH, "详细地址"));
        if (target.getProjectStatus() == null) target.setProjectStatus("normal");
        if (target.getStartDate() != null && target.getEndDate() != null
                && target.getEndDate().isBefore(target.getStartDate())) {
            throw new BusinessException("项目截止日期不能早于开工日期");
        }
    }

    private String normalizeProjectStatus(String value) {
        String normalized = requireText(value, 20, "项目状态").toLowerCase();
        if (!SUPPORTED_PROJECT_STATUSES.contains(normalized)) {
            throw new BusinessException("项目状态仅支持 normal、warning、danger、stopped");
        }
        return normalized;
    }

    private String requireText(String value, int maxLength, String fieldName) {
        String normalized = optionalText(value, maxLength, fieldName);
        if (normalized == null) throw new BusinessException(fieldName + "不能为空");
        return normalized;
    }

    private String optionalText(String value, int maxLength, String fieldName) {
        if (value == null) return null;
        String normalized = value.trim();
        if (normalized.isEmpty()) return null;
        if (normalized.length() > maxLength) {
            throw new BusinessException(fieldName + "不能超过" + maxLength + "个字符");
        }
        return normalized;
    }

    private void requireSingleWrite(int affectedRows, String operation) {
        if (affectedRows != 1) {
            throw BusinessException.of(409, operation + "未生效，请刷新后重试");
        }
    }
}
