package com.example.siteplatform.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.file.constant.FileStatus;
import com.example.siteplatform.file.entity.FileResource;
import com.example.siteplatform.file.mapper.FileResourceMapper;
import com.example.siteplatform.log.entity.OperationLog;
import com.example.siteplatform.log.mapper.OperationLogMapper;
import com.example.siteplatform.project.dto.ProjectProfileDetailVO;
import com.example.siteplatform.project.dto.ProjectProfileImageVO;
import com.example.siteplatform.project.dto.ProjectProfileUpdateRequest;
import com.example.siteplatform.project.entity.ProjectInfo;
import com.example.siteplatform.project.mapper.ProjectInfoMapper;
import com.example.siteplatform.system.service.SystemPermissionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class ProjectProfileService {
    public static final String PENDING_IMAGE_TYPE = "PROJECT_PROFILE_IMAGE_PENDING";
    public static final String FINAL_IMAGE_TYPE = "PROJECT_PROFILE_IMAGE";
    private static final int MAX_IMAGES = 20;
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

    private final ProjectInfoMapper projectMapper;
    private final FileResourceMapper fileMapper;
    private final ProjectPermissionService projectPermissionService;
    private final SystemPermissionService systemPermissionService;
    private final OperationLogMapper operationLogMapper;

    public ProjectProfileService(ProjectInfoMapper projectMapper,
                                 FileResourceMapper fileMapper,
                                 ProjectPermissionService projectPermissionService,
                                 SystemPermissionService systemPermissionService,
                                 OperationLogMapper operationLogMapper) {
        this.projectMapper = projectMapper;
        this.fileMapper = fileMapper;
        this.projectPermissionService = projectPermissionService;
        this.systemPermissionService = systemPermissionService;
        this.operationLogMapper = operationLogMapper;
    }

    public ProjectProfileDetailVO getProfile(Long projectId, SysUser currentUser) {
        requireProjectAccess(projectId, currentUser);
        ProjectInfo project = projectMapper.selectById(projectId);
        if (project == null) throw BusinessException.notFound("项目不存在");
        return toDetail(project, currentUser);
    }

    @Transactional
    public ProjectProfileDetailVO updateProfile(Long projectId,
                                                ProjectProfileUpdateRequest request,
                                                SysUser currentUser) {
        systemPermissionService.requirePlatformAdmin(currentUser);
        if (request == null || request.getExpectedVersion() == null) {
            throw new BusinessException("项目信息和版本号不能为空");
        }
        ProjectInfo project = projectMapper.selectByIdForUpdate(projectId);
        if (project == null) throw BusinessException.notFound("项目不存在");
        int actualVersion = project.getProfileVersion() == null ? 0 : project.getProfileVersion();
        if (actualVersion != request.getExpectedVersion()) {
            throw BusinessException.of(409, "项目信息已被其他管理员更新，请重新加载后再保存");
        }

        List<Long> imageIds = validateImageIds(request.getImageFileIds());
        List<Long> previousImageIds = finalImages(projectId, true).stream().map(FileResource::getId).toList();
        Map<Long, FileResource> requestedFiles = lockAndValidateImages(projectId, imageIds, currentUser);
        validateRequest(request, imageIds);

        List<String> changedFields = applyFields(project, request);
        project.setArea(decimalText(project.getBuildingArea()));
        project.setProfileVersion(actualVersion + 1);
        project.setUpdateTime(LocalDateTime.now());
        requireSingleWrite(projectMapper.updateById(project), "项目档案更新");

        bindAndArchiveImages(projectId, imageIds, requestedFiles);
        if (!previousImageIds.equals(imageIds)) changedFields.add("效果图");
        recordAudit(project, currentUser, changedFields);
        return toDetail(project, currentUser);
    }

    private void requireProjectAccess(Long projectId, SysUser currentUser) {
        if (projectId == null || currentUser == null || currentUser.getId() == null) {
            throw BusinessException.forbidden("无项目查看权限");
        }
        projectPermissionService.checkProjectPermission(currentUser.getId(), projectId);
    }

    private List<Long> validateImageIds(List<Long> values) {
        List<Long> ids = values == null ? List.of() : values.stream().filter(Objects::nonNull).toList();
        if (ids.isEmpty()) throw new BusinessException("请至少上传一张项目效果图");
        if (ids.size() > MAX_IMAGES) throw new BusinessException("项目效果图最多上传20张");
        if (new LinkedHashSet<>(ids).size() != ids.size()) throw new BusinessException("效果图列表不能包含重复文件");
        return ids;
    }

    private Map<Long, FileResource> lockAndValidateImages(Long projectId, List<Long> ids, SysUser currentUser) {
        List<FileResource> files = fileMapper.selectByIdsForUpdate(ids);
        if (files.size() != ids.size()) throw new BusinessException("部分效果图不存在或已被清理");
        Map<Long, FileResource> byId = new LinkedHashMap<>();
        for (FileResource file : files) {
            if (!Objects.equals(projectId, file.getProjectId())) throw new BusinessException("效果图不属于当前项目");
            String extension = file.getFileExtension() == null ? "" : file.getFileExtension().toLowerCase();
            if (!IMAGE_EXTENSIONS.contains(extension)) throw new BusinessException("项目效果图仅支持 JPEG、PNG、WebP");
            boolean pending = PENDING_IMAGE_TYPE.equals(file.getBusinessType())
                    && file.getBusinessId() == null
                    && FileStatus.UPLOADED.equals(FileStatus.normalize(file.getStatus()))
                    && Objects.equals(currentUser.getId(), file.getUploaderId());
            boolean existing = FINAL_IMAGE_TYPE.equals(file.getBusinessType())
                    && Objects.equals(projectId, file.getBusinessId())
                    && (FileStatus.UPLOADED.equals(FileStatus.normalize(file.getStatus()))
                    || FileStatus.ARCHIVED.equals(FileStatus.normalize(file.getStatus())));
            if (!pending && !existing) throw BusinessException.of(409, "效果图状态已变化，请刷新后重试");
            byId.put(file.getId(), file);
        }
        return byId;
    }

    private void validateRequest(ProjectProfileUpdateRequest r, List<Long> imageIds) {
        required(r.getProjectName(), 200, "项目全称");
        required(r.getShortName(), 12, "项目简称");
        required(r.getDirectCompany(), 200, "直属公司");
        required(r.getManager(), 50, "项目经理");
        required(r.getManagerPhone(), 30, "经理联系方式");
        required(r.getAddress(), 500, "项目地点");
        required(r.getEngineeringType(), 100, "工程类型");
        if (r.getStartDate() == null || r.getEndDate() == null) throw new BusinessException("计划开竣工日期不能为空");
        if (r.getEndDate().isBefore(r.getStartDate())) throw new BusinessException("计划竣工日期不能早于计划开工日期");
        if (r.getActualStartDate() != null && r.getActualEndDate() != null
                && r.getActualEndDate().isBefore(r.getActualStartDate())) {
            throw new BusinessException("实际竣工日期不能早于实际开工日期");
        }
        required(r.getPhase(), 50, "工程状态");
        if (imageIds.isEmpty()) throw new BusinessException("请至少上传一张项目效果图");
        required(r.getOwnerUnit(), 200, "建设单位");
        required(r.getSupervisionUnit(), 200, "监理单位");
        required(r.getDesignUnit(), 200, "设计单位");
        required(r.getContractor(), 200, "施工单位");
        requireNonNegative(r.getBuildingArea(), "建筑面积", true);
        requireNonNegative(r.getLandArea(), "用地面积", true);
        requireNonNegative(r.getBuildingHeight(), "建筑高度", true);
        requireNonNegative(r.getContractAmount(), "合同金额", false);
        requireNonNegative(r.getExcavationDepth(), "开挖深度", false);
        requireNonNegative(r.getUndergroundFloorCount(), "地下层数");
        requireNonNegative(r.getAbovegroundFloorCount(), "地上层数");
        requireNonNegative(r.getManagementStaffCount(), "管理人数");
        requireNonNegative(r.getAttendanceCount(), "考勤人数");
        requireNonNegative(r.getPartyMemberCount(), "党员人数");
        optional(r.getSpaceCapacity(), 100, "空间容量");
        optional(r.getDescription(), 5000, "项目简介");
        optional(r.getContractorCreditCode(), 32, "施工单位统一社会信用代码");
        optional(r.getContractorLicenseNumber(), 100, "施工单位安全生产许可证");
        optional(r.getGeneralContractNumber(), 100, "总承包合同编号");
        optional(r.getProjectClassification(), 100, "项目分类");
        optional(r.getInvestmentEntity(), 100, "投资主体");
        optional(r.getContractingMode(), 100, "承建模式");
        optional(r.getProjectScale(), 100, "项目规模");
        optional(r.getProjectTarget(), 200, "项目目标");
        optional(r.getProjectCategory(), 100, "项目类别");
        optional(r.getQualityGoal(), 500, "质量目标");
        optional(r.getSafetyGoal(), 500, "安全目标");
        optional(r.getGreenConstructionGoal(), 500, "绿色建造目标");
        optional(r.getProjectLevel(), 100, "项目级别");
        validateIp(r.getFixedIpAddress());
    }

    private List<String> applyFields(ProjectInfo p, ProjectProfileUpdateRequest r) {
        List<String> changed = new ArrayList<>();
        assign(p::getProjectName, p::setProjectName, required(r.getProjectName(), 200, "项目全称"), "项目全称", changed);
        assign(p::getShortName, p::setShortName, required(r.getShortName(), 12, "项目简称"), "项目简称", changed);
        assign(p::getDirectCompany, p::setDirectCompany, required(r.getDirectCompany(), 200, "直属公司"), "直属公司", changed);
        assign(p::getManager, p::setManager, required(r.getManager(), 50, "项目经理"), "项目经理", changed);
        assign(p::getManagerPhone, p::setManagerPhone, required(r.getManagerPhone(), 30, "经理联系方式"), "经理联系方式", changed);
        assign(p::getSpaceCapacity, p::setSpaceCapacity, optional(r.getSpaceCapacity(), 100, "空间容量"), "空间容量", changed);
        assign(p::getAddress, p::setAddress, required(r.getAddress(), 500, "项目地点"), "项目地点", changed);
        assign(p::getEngineeringType, p::setEngineeringType, required(r.getEngineeringType(), 100, "工程类型"), "工程类型", changed);
        assign(p::getStartDate, p::setStartDate, r.getStartDate(), "计划开工日期", changed);
        assign(p::getEndDate, p::setEndDate, r.getEndDate(), "计划竣工日期", changed);
        assign(p::getActualStartDate, p::setActualStartDate, r.getActualStartDate(), "实际开工日期", changed);
        assign(p::getActualEndDate, p::setActualEndDate, r.getActualEndDate(), "实际竣工日期", changed);
        assign(p::getPhase, p::setPhase, required(r.getPhase(), 50, "工程状态"), "工程状态", changed);
        assign(p::getDescription, p::setDescription, optional(r.getDescription(), 5000, "项目简介"), "项目简介", changed);
        assign(p::getOwnerUnit, p::setOwnerUnit, required(r.getOwnerUnit(), 200, "建设单位"), "建设单位", changed);
        assign(p::getSupervisionUnit, p::setSupervisionUnit, required(r.getSupervisionUnit(), 200, "监理单位"), "监理单位", changed);
        assign(p::getDesignUnit, p::setDesignUnit, required(r.getDesignUnit(), 200, "设计单位"), "设计单位", changed);
        assign(p::getContractor, p::setContractor, required(r.getContractor(), 200, "施工单位"), "施工单位", changed);
        assign(p::getContractorCreditCode, p::setContractorCreditCode, optional(r.getContractorCreditCode(), 32, "施工单位统一社会信用代码"), "施工单位统一社会信用代码", changed);
        assign(p::getContractorLicenseNumber, p::setContractorLicenseNumber, optional(r.getContractorLicenseNumber(), 100, "施工单位安全生产许可证"), "施工单位证照", changed);
        assign(p::getGeneralContractNumber, p::setGeneralContractNumber, optional(r.getGeneralContractNumber(), 100, "总承包合同编号"), "总承包合同", changed);
        assign(p::getProjectClassification, p::setProjectClassification, optional(r.getProjectClassification(), 100, "项目分类"), "项目分类", changed);
        assign(p::getInvestmentEntity, p::setInvestmentEntity, optional(r.getInvestmentEntity(), 100, "投资主体"), "投资主体", changed);
        assign(p::getContractingMode, p::setContractingMode, optional(r.getContractingMode(), 100, "承建模式"), "承建模式", changed);
        assign(p::getContractAmount, p::setContractAmount, r.getContractAmount(), "合同金额", changed);
        assign(p::getBuildingArea, p::setBuildingArea, r.getBuildingArea(), "建筑面积", changed);
        assign(p::getProjectScale, p::setProjectScale, optional(r.getProjectScale(), 100, "项目规模"), "项目规模", changed);
        assign(p::getProjectTarget, p::setProjectTarget, optional(r.getProjectTarget(), 200, "项目目标"), "项目目标", changed);
        assign(p::getLandArea, p::setLandArea, r.getLandArea(), "用地面积", changed);
        assign(p::getBuildingHeight, p::setBuildingHeight, r.getBuildingHeight(), "建筑高度", changed);
        assign(p::getProjectCategory, p::setProjectCategory, optional(r.getProjectCategory(), 100, "项目类别"), "项目类别", changed);
        assign(p::getExcavationDepth, p::setExcavationDepth, r.getExcavationDepth(), "开挖深度", changed);
        assign(p::getUndergroundFloorCount, p::setUndergroundFloorCount, r.getUndergroundFloorCount(), "地下层数", changed);
        assign(p::getAbovegroundFloorCount, p::setAbovegroundFloorCount, r.getAbovegroundFloorCount(), "地上层数", changed);
        assign(p::getQualityGoal, p::setQualityGoal, optional(r.getQualityGoal(), 500, "质量目标"), "质量目标", changed);
        assign(p::getSafetyGoal, p::setSafetyGoal, optional(r.getSafetyGoal(), 500, "安全目标"), "安全目标", changed);
        assign(p::getGreenConstructionGoal, p::setGreenConstructionGoal, optional(r.getGreenConstructionGoal(), 500, "绿色建造目标"), "绿色建造目标", changed);
        assign(p::getProjectLevel, p::setProjectLevel, optional(r.getProjectLevel(), 100, "项目级别"), "项目级别", changed);
        assign(p::getManagementStaffCount, p::setManagementStaffCount, r.getManagementStaffCount(), "管理人数", changed);
        assign(p::getAttendanceCount, p::setAttendanceCount, r.getAttendanceCount(), "考勤人数", changed);
        assign(p::getPartyMemberCount, p::setPartyMemberCount, r.getPartyMemberCount(), "党员人数", changed);
        assign(p::getFixedIpAddress, p::setFixedIpAddress, optional(r.getFixedIpAddress(), 45, "固定IP"), "固定IP", changed);
        return changed;
    }

    private void bindAndArchiveImages(Long projectId, List<Long> requestedIds,
                                      Map<Long, FileResource> requestedFiles) {
        Set<Long> keep = Set.copyOf(requestedIds);
        List<FileResource> current = finalImages(projectId, false);
        for (FileResource image : current) {
            if (!keep.contains(image.getId())) {
                int updated = fileMapper.archiveProjectProfileImage(image.getId(), projectId);
                requireSingleWrite(updated, "项目效果图归档");
            }
        }
        for (Long id : requestedIds) {
            FileResource file = requestedFiles.get(id);
            if (PENDING_IMAGE_TYPE.equals(file.getBusinessType())) {
                int updated = fileMapper.bindPendingProjectProfileImage(id, projectId);
                requireSingleWrite(updated, "项目效果图绑定");
            } else if (FileStatus.ARCHIVED.equals(FileStatus.normalize(file.getStatus()))) {
                int updated = fileMapper.restoreProjectProfileImage(id, projectId);
                requireSingleWrite(updated, "项目效果图恢复");
            }
        }
    }

    private List<FileResource> finalImages(Long projectId, boolean activeOnly) {
        LambdaQueryWrapper<FileResource> query = new LambdaQueryWrapper<FileResource>()
                .eq(FileResource::getProjectId, projectId)
                .eq(FileResource::getBusinessType, FINAL_IMAGE_TYPE)
                .eq(FileResource::getBusinessId, projectId)
                .eq(FileResource::getDeleted, 0);
        if (activeOnly) query.eq(FileResource::getStatus, FileStatus.UPLOADED);
        query.orderByAsc(FileResource::getCreateTime).orderByAsc(FileResource::getId);
        return fileMapper.selectList(query);
    }

    private ProjectProfileDetailVO toDetail(ProjectInfo p, SysUser user) {
        ProjectProfileDetailVO vo = new ProjectProfileDetailVO();
        vo.setProjectId(p.getId());
        vo.setProjectName(p.getProjectName()); vo.setShortName(p.getShortName());
        vo.setDirectCompany(p.getDirectCompany()); vo.setManager(p.getManager()); vo.setManagerPhone(p.getManagerPhone());
        vo.setSpaceCapacity(p.getSpaceCapacity()); vo.setAddress(p.getAddress()); vo.setEngineeringType(p.getEngineeringType());
        vo.setStartDate(p.getStartDate()); vo.setEndDate(p.getEndDate()); vo.setActualStartDate(p.getActualStartDate()); vo.setActualEndDate(p.getActualEndDate());
        vo.setPhase(p.getPhase()); vo.setDescription(p.getDescription());
        vo.setOwnerUnit(p.getOwnerUnit()); vo.setSupervisionUnit(p.getSupervisionUnit()); vo.setDesignUnit(p.getDesignUnit()); vo.setContractor(p.getContractor());
        vo.setContractorCreditCode(p.getContractorCreditCode()); vo.setContractorLicenseNumber(p.getContractorLicenseNumber());
        vo.setGeneralContractNumber(p.getGeneralContractNumber()); vo.setProjectClassification(p.getProjectClassification());
        vo.setInvestmentEntity(p.getInvestmentEntity()); vo.setContractingMode(p.getContractingMode()); vo.setContractAmount(p.getContractAmount());
        vo.setBuildingArea(p.getBuildingArea()); vo.setProjectScale(p.getProjectScale()); vo.setProjectTarget(p.getProjectTarget()); vo.setLandArea(p.getLandArea());
        vo.setBuildingHeight(p.getBuildingHeight()); vo.setProjectCategory(p.getProjectCategory()); vo.setExcavationDepth(p.getExcavationDepth());
        vo.setUndergroundFloorCount(p.getUndergroundFloorCount()); vo.setAbovegroundFloorCount(p.getAbovegroundFloorCount());
        vo.setQualityGoal(p.getQualityGoal()); vo.setSafetyGoal(p.getSafetyGoal()); vo.setGreenConstructionGoal(p.getGreenConstructionGoal());
        vo.setProjectLevel(p.getProjectLevel()); vo.setManagementStaffCount(p.getManagementStaffCount()); vo.setAttendanceCount(p.getAttendanceCount());
        vo.setPartyMemberCount(p.getPartyMemberCount()); vo.setFixedIpAddress(p.getFixedIpAddress());
        vo.setProfileVersion(p.getProfileVersion() == null ? 0 : p.getProfileVersion()); vo.setUpdateTime(p.getUpdateTime());
        vo.setCanEdit(systemPermissionService.isPlatformAdmin(user.getId()));
        List<FileResource> images = finalImages(p.getId(), true);
        List<ProjectProfileImageVO> imageVos = new ArrayList<>();
        for (int i = 0; i < images.size(); i++) {
            FileResource image = images.get(i);
            ProjectProfileImageVO item = new ProjectProfileImageVO();
            item.setFileId(image.getId()); item.setFileName(image.getFileName()); item.setMimeType(image.getMimeType());
            item.setFileSize(image.getFileSize()); item.setSortOrder(i); item.setCover(i == 0);
            imageVos.add(item);
        }
        vo.setImages(imageVos);
        return vo;
    }

    private void recordAudit(ProjectInfo project, SysUser user, List<String> changedFields) {
        OperationLog log = new OperationLog();
        log.setUserId(user.getId()); log.setUsername(user.getUsername());
        log.setOperationType("UPDATE_PROJECT_PROFILE");
        String summary = changedFields.isEmpty() ? "未改变字段" : String.join("、", new LinkedHashSet<>(changedFields));
        log.setOperationDesc("更新项目信息，变更字段：" + summary);
        log.setBusinessType("PROJECT"); log.setBusinessId(project.getId()); log.setCreateTime(LocalDateTime.now());
        requireSingleWrite(operationLogMapper.insert(log), "项目档案审计日志写入");
    }

    private String required(String value, int max, String label) {
        String normalized = optional(value, max, label);
        if (normalized == null) throw new BusinessException(label + "不能为空");
        return normalized;
    }

    private String optional(String value, int max, String label) {
        if (value == null) return null;
        String normalized = value.trim();
        if (normalized.isEmpty()) return null;
        if (normalized.length() > max) throw new BusinessException(label + "不能超过" + max + "个字符");
        return normalized;
    }

    private void requireNonNegative(BigDecimal value, String label, boolean required) {
        if (value == null) {
            if (required) throw new BusinessException(label + "不能为空");
            return;
        }
        if (value.signum() < 0) throw new BusinessException(label + "不能为负数");
    }

    private void requireNonNegative(Integer value, String label) {
        if (value != null && value < 0) throw new BusinessException(label + "不能为负数");
    }

    private void validateIp(String value) {
        String ip = optional(value, 45, "固定IP");
        if (ip == null) return;
        try {
            if (ip.contains(":")) {
                if (ip.contains("%") || !(InetAddress.getByName(ip) instanceof Inet6Address)) throw new IllegalArgumentException();
                return;
            }
            String[] parts = ip.split("\\.", -1);
            if (parts.length != 4) throw new IllegalArgumentException();
            for (String part : parts) {
                if (!part.matches("0|[1-9]\\d{0,2}") || Integer.parseInt(part) > 255) throw new IllegalArgumentException();
            }
        } catch (Exception exception) {
            throw new BusinessException("固定IP格式不正确");
        }
    }

    private String decimalText(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private <T> void assign(java.util.function.Supplier<T> getter, java.util.function.Consumer<T> setter,
                            T value, String label, List<String> changed) {
        if (!Objects.equals(getter.get(), value)) changed.add(label);
        setter.accept(value);
    }

    private void requireSingleWrite(int affected, String action) {
        if (affected != 1) throw BusinessException.of(409, action + "失败，数据状态已变化");
    }
}
