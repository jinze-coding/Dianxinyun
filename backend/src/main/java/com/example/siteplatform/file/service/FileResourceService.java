package com.example.siteplatform.file.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.file.entity.FileResource;
import com.example.siteplatform.file.mapper.FileResourceMapper;
import com.example.siteplatform.inspection.general.entity.GeneralInspectionProjectSetting;
import com.example.siteplatform.inspection.general.entity.GeneralInspectionRectification;
import com.example.siteplatform.inspection.general.entity.GeneralInspectionTask;
import com.example.siteplatform.inspection.general.mapper.GeneralInspectionProjectSettingMapper;
import com.example.siteplatform.inspection.general.mapper.GeneralInspectionRectificationMapper;
import com.example.siteplatform.inspection.general.mapper.GeneralInspectionTaskMapper;
import com.example.siteplatform.project.entity.ProjectInfo;
import com.example.siteplatform.project.constant.InspectionPermissionCodes;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.system.constant.SystemPermissionCodes;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
public class FileResourceService {
    private static final String BUSINESS_PROJECT_DOCUMENT = "PROJECT_DOCUMENT";
    private static final String BUSINESS_QUALITY_DOCUMENT = "QUALITY_DOCUMENT";
    private static final String BUSINESS_QUALITY_PENDING = "QUALITY_PENDING";
    private static final String BUSINESS_QUALITY_RECTIFICATION_PENDING = "QUALITY_RECTIFICATION_PENDING";
    private static final String BUSINESS_QUALITY_REVIEW_PENDING = "QUALITY_REVIEW_PENDING";
    private static final String BUSINESS_QUALITY_WEEKLY_PENDING = "QUALITY_WEEKLY_PENDING";
    private static final String BUSINESS_QUALITY_WEEKLY_ITEM_PENDING = "QUALITY_WEEKLY_ITEM_PENDING";
    private static final String BUSINESS_QUALITY_WEEKLY_DRAFT = "QUALITY_WEEKLY_DRAFT";
    private static final String BUSINESS_QUALITY_WEEKLY_DRAFT_ITEM = "QUALITY_WEEKLY_DRAFT_ITEM";
    private static final String BUSINESS_QUALITY_WEEKLY_INSPECTION = "QUALITY_WEEKLY_INSPECTION";
    private static final String BUSINESS_QUALITY_ISSUE = "QUALITY_ISSUE";
    private static final String BUSINESS_QUALITY_RECTIFICATION = "QUALITY_RECTIFICATION";
    private static final String BUSINESS_QUALITY_REVIEW = "QUALITY_REVIEW";
    private static final String BUSINESS_PROJECT_PROFILE_PENDING = "PROJECT_PROFILE_IMAGE_PENDING";
    private static final String BUSINESS_PROJECT_PROFILE = "PROJECT_PROFILE_IMAGE";
    private static final String BUSINESS_PROJECT_ROUTE_PENDING = "PROJECT_ROUTE_IMAGE_PENDING";
    private static final String BUSINESS_PROJECT_ROUTE = "PROJECT_ROUTE_IMAGE";
    private static final Set<String> GENERAL_INSPECTION_STAGING_TYPES = Set.of(
            "EDGE_INSPECTION_TASK_PENDING",
            "EDGE_INSPECTION_RECTIFICATION_PENDING"
    );
    private static final Set<String> GENERAL_INSPECTION_FINAL_TYPES = Set.of(
            "INSPECTION_CUSTOM_POINT",
            "INSPECTION_CUSTOM_TASK",
            "INSPECTION_CUSTOM_RECTIFICATION",
            "INSPECTION_CUSTOM_EXPORT",
            "EDGE_INSPECTION_TASK",
            "EDGE_INSPECTION_RECTIFICATION"
    );
    private static final Set<String> QUALITY_STAGING_TYPES = Set.of(
            BUSINESS_QUALITY_PENDING,
            BUSINESS_QUALITY_RECTIFICATION_PENDING,
            BUSINESS_QUALITY_REVIEW_PENDING,
            BUSINESS_QUALITY_WEEKLY_PENDING,
            BUSINESS_QUALITY_WEEKLY_ITEM_PENDING
    );
    private static final Set<String> QUALITY_DRAFT_TYPES = Set.of(
            BUSINESS_QUALITY_WEEKLY_DRAFT,
            BUSINESS_QUALITY_WEEKLY_DRAFT_ITEM
    );
    private static final Set<String> QUALITY_WEEKLY_STAGING_TYPES = Set.of(
            BUSINESS_QUALITY_WEEKLY_PENDING,
            BUSINESS_QUALITY_WEEKLY_ITEM_PENDING
    );
    private static final Set<String> QUALITY_FINAL_TYPES = Set.of(
            BUSINESS_QUALITY_ISSUE,
            BUSINESS_QUALITY_RECTIFICATION,
            BUSINESS_QUALITY_REVIEW,
            BUSINESS_QUALITY_WEEKLY_INSPECTION
    );

    private final FileResourceMapper fileMapper;
    private final ProjectPermissionService permissionService;
    private GeneralInspectionProjectSettingMapper generalInspectionSettingMapper;
    private GeneralInspectionTaskMapper generalInspectionTaskMapper;
    private GeneralInspectionRectificationMapper generalInspectionRectificationMapper;

    public FileResourceService(FileResourceMapper fileMapper, ProjectPermissionService permissionService) {
        this.fileMapper = fileMapper;
        this.permissionService = permissionService;
    }

    @Autowired(required = false)
    public void setGeneralInspectionSettingMapper(GeneralInspectionProjectSettingMapper mapper) {
        this.generalInspectionSettingMapper = mapper;
    }

    @Autowired(required = false)
    public void setGeneralInspectionTaskMapper(GeneralInspectionTaskMapper mapper) {
        this.generalInspectionTaskMapper = mapper;
    }

    @Autowired(required = false)
    public void setGeneralInspectionRectificationMapper(GeneralInspectionRectificationMapper mapper) {
        this.generalInspectionRectificationMapper = mapper;
    }

    public void checkRead(SysUser currentUser, FileResource file) {
        if (file == null) {
            throw BusinessException.notFound("文件不存在");
        }
        if (isProjectDocument(file.getBusinessType())) {
            throw BusinessException.forbidden("工程资料请通过资料管理接口访问");
        }
        if (normalizeBusinessType(file.getBusinessType()).startsWith("SEAL_")) {
            throw BusinessException.forbidden("用印附件请通过用印申请专属接口访问");
        }
        if (file.getProjectId() == null) {
            if (!permissionService.isPlatformAdmin(currentUser.getId())) {
                throw BusinessException.forbidden("无文件访问权限");
            }
            return;
        }
        String businessType = normalizeBusinessType(file.getBusinessType());
        if (GENERAL_INSPECTION_STAGING_TYPES.contains(businessType)
                || GENERAL_INSPECTION_FINAL_TYPES.contains(businessType)) {
            requireGeneralInspectionEnabled(file.getProjectId());
        }
        if (BUSINESS_PROJECT_PROFILE_PENDING.equals(businessType)
                && !permissionService.isPlatformAdmin(currentUser.getId())
                && !Objects.equals(file.getUploaderId(), currentUser.getId())) {
            throw BusinessException.forbidden("无项目效果图暂存文件访问权限");
        }
        if (BUSINESS_PROJECT_ROUTE_PENDING.equals(businessType)
                && !permissionService.isPlatformAdmin(currentUser.getId())) {
            throw BusinessException.forbidden("无项目到访路线图暂存文件访问权限");
        }
        if (QUALITY_WEEKLY_STAGING_TYPES.contains(businessType)
                && file.getBusinessId() == null
                && !permissionService.isPlatformAdmin(currentUser.getId())
                && !Objects.equals(file.getUploaderId(), currentUser.getId())) {
            throw BusinessException.forbidden("无其他人员未保存周检照片访问权限");
        }
        permissionService.checkProjectPermission(currentUser.getId(), file.getProjectId());
        if (businessType.startsWith("EDGE_INSPECTION_")) {
            requireEdgeFileRead(currentUser, file, businessType);
        } else {
            requireBusinessRead(currentUser, file.getProjectId(), file.getBusinessType());
        }
    }

    public void checkWrite(SysUser currentUser, FileResource file) {
        checkRead(currentUser, file);
        String businessType = normalizeBusinessType(file.getBusinessType());
        if (BUSINESS_PROJECT_PROFILE.equals(businessType)) {
            throw BusinessException.forbidden("项目效果图请在项目信息编辑页归档或调整");
        }
        if (BUSINESS_PROJECT_ROUTE.equals(businessType)) {
            throw BusinessException.forbidden("项目到访路线图请通过项目定位接口归档或调整");
        }
        if (BUSINESS_PROJECT_PROFILE_PENDING.equals(businessType)) {
            if (!permissionService.isPlatformAdmin(currentUser.getId())) {
                throw BusinessException.forbidden("仅平台管理员可以管理项目效果图");
            }
            if (file.getBusinessId() != null) throw BusinessException.of(409, "项目效果图状态已变化");
            return;
        }
        if (BUSINESS_PROJECT_ROUTE_PENDING.equals(businessType)) {
            if (!permissionService.isPlatformAdmin(currentUser.getId())) {
                throw BusinessException.forbidden("仅平台管理员可以管理项目到访路线图");
            }
            if (file.getBusinessId() != null) throw BusinessException.of(409, "项目到访路线图状态已变化");
            return;
        }
        if (businessType.startsWith("QUALITY_")) {
            checkQualityWrite(currentUser, file, businessType);
            return;
        }
        if (isBoundWorkflowAttachment(file)) {
            throw BusinessException.forbidden("已关联业务记录的附件不能通过通用文件接口修改");
        }
        if (permissionService.isPlatformAdmin(currentUser.getId())
                || Objects.equals(file.getUploaderId(), currentUser.getId())) {
            return;
        }
        if (businessType.startsWith("INSPECTION_")
                && permissionService.canManageInspection(currentUser.getId(), file.getProjectId())) return;
        if ((businessType.startsWith("PERSON_") || businessType.startsWith("SAFETY_"))
                && permissionService.canManagePersonnel(currentUser.getId(), file.getProjectId())) return;
        if (permissionService.canManageProject(currentUser.getId(), file.getProjectId())) return;
        throw BusinessException.forbidden("无文件管理权限");
    }

    /**
     * 通用删除接口只承担表单失败后未绑定暂存附件的自助清理。
     * 正式资料及已绑定业务附件必须由平台管理员走带影响预览的一次性确认流程。
     */
    public void checkTemporaryDelete(SysUser currentUser, FileResource file) {
        checkRead(currentUser, file);
        String businessType = normalizeBusinessType(file.getBusinessType());
        boolean temporary = file.getBusinessId() == null
                && (businessType.endsWith("_PENDING")
                || businessType.equals("INSPECTION_RECORD")
                || businessType.equals("INSPECTION_RECTIFICATION"));
        if (!temporary) {
            throw BusinessException.forbidden("正式资料或已绑定业务附件只能由平台管理员确认影响后删除");
        }
        if (!permissionService.isPlatformAdmin(currentUser.getId())
                && !Objects.equals(file.getUploaderId(), currentUser.getId())) {
            throw BusinessException.forbidden("只能清理本人尚未绑定业务的暂存附件");
        }
        checkWrite(currentUser, file);
    }

    public List<Long> authorizedProjectIds(SysUser currentUser) {
        return permissionService.getUserProjects(currentUser.getId()).stream().map(ProjectInfo::getId).toList();
    }

    /**
     * 通用附件仍属于其原业务模块，不能绕过资料/巡检/质量的跨端模块开关。
     * 未分类的历史附件继续只按项目范围处理。
     */
    public void requireBusinessRead(SysUser currentUser, Long projectId, String businessType) {
        if (currentUser == null || projectId == null) return;
        String normalized = normalizeBusinessType(businessType);
        if (normalized.startsWith("QUALITY_")) {
            permissionService.requireSystemPermission(currentUser.getId(), projectId, SystemPermissionCodes.QUALITY_VIEW);
            if (QUALITY_DRAFT_TYPES.contains(normalized)
                    || QUALITY_WEEKLY_STAGING_TYPES.contains(normalized)) {
                permissionService.requireSystemPermission(
                        currentUser.getId(), projectId, SystemPermissionCodes.QUALITY_MANAGE);
            }
        } else if (normalized.startsWith("EDGE_INSPECTION_")) {
            if (!hasAnyEdgeInspectionPermission(currentUser.getId(), projectId)) {
                throw BusinessException.forbidden("无临边巡检附件查看权限");
            }
        } else if (normalized.startsWith("INSPECTION_")) {
            permissionService.requireSystemPermission(currentUser.getId(), projectId, SystemPermissionCodes.INSPECTION_VIEW);
        }
    }

    /**
     * 列表未指定业务类型时可能混合多个模块，逐条按文件真实项目和业务类型过滤，
     * 避免用户在 A 项目拥有质量权限后读取 B 项目的质量附件元数据。
     */
    public boolean canReadInList(SysUser currentUser, FileResource file) {
        try {
            checkRead(currentUser, file);
            return true;
        } catch (BusinessException exception) {
            if (Objects.equals(exception.getCode(), 403)) {
                return false;
            }
            throw exception;
        }
    }

    /**
     * 校验通用上传通道中的业务类型，并返回需要落库的规范值。
     * 质量流程最终附件只能由质量事务把暂存附件绑定后生成，客户端不能直接注入。
     */
    public String authorizeUpload(SysUser currentUser, Long projectId, String businessType, Long businessId) {
        if (currentUser == null || currentUser.getId() == null || projectId == null) {
            throw new BusinessException("文件上传参数不完整");
        }
        permissionService.checkProjectPermission(currentUser.getId(), projectId);
        String normalized = normalizeBusinessType(businessType);
        if (BUSINESS_PROJECT_PROFILE.equals(normalized)) {
            throw new BusinessException("项目效果图必须先上传暂存类型，再由项目信息保存绑定");
        }
        if (BUSINESS_PROJECT_ROUTE.equals(normalized)) {
            throw new BusinessException("项目到访路线图必须先上传暂存类型，再由项目定位保存绑定");
        }
        if (BUSINESS_PROJECT_PROFILE_PENDING.equals(normalized)) {
            if (!permissionService.isPlatformAdmin(currentUser.getId())) {
                throw BusinessException.forbidden("仅平台管理员可以上传项目效果图");
            }
            if (businessId != null) throw new BusinessException("项目效果图上传时不能直接指定业务记录");
            return normalized;
        }
        if (BUSINESS_PROJECT_ROUTE_PENDING.equals(normalized)) {
            if (!permissionService.isPlatformAdmin(currentUser.getId())) {
                throw BusinessException.forbidden("仅平台管理员可以上传项目到访路线图");
            }
            if (businessId != null) throw new BusinessException("项目到访路线图上传时不能直接指定业务记录");
            return normalized;
        }
        if (BUSINESS_PROJECT_DOCUMENT.equals(normalized)) {
            throw BusinessException.forbidden("工程资料请通过资料管理模块上传");
        }
        if (normalized.startsWith("SEAL_")) {
            throw BusinessException.forbidden("用印附件请通过用印申请专属接口上传");
        }
        if (normalized.startsWith("EDGE_INSPECTION_")) {
            if (GENERAL_INSPECTION_FINAL_TYPES.contains(normalized)) {
                throw new BusinessException("临边巡检正式附件只能由业务提交绑定");
            }
            if (!GENERAL_INSPECTION_STAGING_TYPES.contains(normalized)) {
                throw new BusinessException("不支持的临边巡检附件类型");
            }
            if (businessId != null) throw new BusinessException("临边巡检附件上传时不能直接指定业务记录");
            requireGeneralInspectionEnabled(projectId);
            requireEdgeInspectionPermission(currentUser.getId(), projectId,
                    normalized.contains("RECTIFICATION")
                            ? InspectionPermissionCodes.EDGE_INSPECTION_RECTIFY
                            : InspectionPermissionCodes.EDGE_INSPECTION_SUBMIT,
                    normalized.contains("RECTIFICATION") ? "无临边巡检整改权限" : "无临边巡检执行权限");
            return normalized;
        }
        if (normalized.startsWith("INSPECTION_")) {
            if (GENERAL_INSPECTION_FINAL_TYPES.contains(normalized)) {
                throw new BusinessException("巡检正式附件只能由业务提交绑定");
            }
            if (!Set.of("INSPECTION_RECORD", "INSPECTION_RECTIFICATION").contains(normalized)
                    && !GENERAL_INSPECTION_STAGING_TYPES.contains(normalized)) {
                throw new BusinessException("不支持的巡检附件类型");
            }
            if (businessId != null) {
                throw new BusinessException("巡检附件上传时不能直接指定业务记录");
            }
            boolean customStaging = GENERAL_INSPECTION_STAGING_TYPES.contains(normalized);
            if (customStaging) requireGeneralInspectionEnabled(projectId);
            if (!customStaging) {
                permissionService.requireSystemPermission(currentUser.getId(), projectId,
                        SystemPermissionCodes.INSPECTION_VIEW);
            }
            permissionService.requireSystemPermission(currentUser.getId(), projectId,
                    "INSPECTION_CUSTOM_POINT_PENDING".equals(normalized)
                            ? SystemPermissionCodes.INSPECTION_MANAGE
                            : ("INSPECTION_RECTIFICATION".equals(normalized)
                            || "INSPECTION_CUSTOM_RECTIFICATION_PENDING".equals(normalized))
                            ? SystemPermissionCodes.INSPECTION_RECTIFY
                            : SystemPermissionCodes.INSPECTION_SUBMIT);
            return normalized;
        }
        if (!normalized.startsWith("QUALITY_")) {
            return businessType;
        }
        if (QUALITY_FINAL_TYPES.contains(normalized)) {
            throw new BusinessException("质量流程附件只能先上传暂存类型，再由质量业务提交绑定");
        }
        if (!BUSINESS_QUALITY_DOCUMENT.equals(normalized) && !QUALITY_STAGING_TYPES.contains(normalized)) {
            throw new BusinessException("不支持的质量附件类型");
        }
        if (businessId != null) {
            throw new BusinessException("质量附件上传时不能直接指定业务记录");
        }
        permissionService.requireSystemPermission(currentUser.getId(), projectId,
                SystemPermissionCodes.QUALITY_VIEW);
        permissionService.requireSystemPermission(currentUser.getId(), projectId,
                qualityWritePermission(normalized));
        return normalized;
    }

    @Transactional
    public void validateAndBind(SysUser currentUser, Long projectId, List<Long> fileIds,
                                String expectedBusinessType, String businessType, Long businessId) {
        if (fileIds == null || fileIds.isEmpty()) return;
        if (currentUser == null || currentUser.getId() == null || projectId == null || businessId == null) {
            throw new BusinessException("附件绑定参数不完整");
        }
        validateQualityBindingTypes(expectedBusinessType, businessType);
        List<Long> distinctFileIds = fileIds.stream().filter(Objects::nonNull).distinct().sorted().toList();
        if (distinctFileIds.size() != fileIds.size()) {
            throw new BusinessException("附件列表包含重复或无效文件");
        }
        List<FileResource> files = fileMapper.selectByIdsForUpdate(distinctFileIds);
        if (files.size() != distinctFileIds.size()) {
            throw new BusinessException("部分附件不存在");
        }
        for (FileResource file : files) {
            if (!Objects.equals(projectId, file.getProjectId())) {
                throw new BusinessException("附件不属于当前项目");
            }
            if (!Objects.equals(currentUser.getId(), file.getUploaderId())) {
                throw BusinessException.forbidden("只能关联本人刚上传的附件");
            }
            if (file.getBusinessId() != null) {
                throw new BusinessException("附件已关联其他业务记录");
            }
            if (!normalizeBusinessType(expectedBusinessType)
                    .equals(normalizeBusinessType(file.getBusinessType()))) {
                throw new BusinessException("附件类型与当前业务不匹配");
            }
            file.setBusinessType(businessType);
            file.setBusinessId(businessId);
            file.setUpdateTime(LocalDateTime.now());
            if (fileMapper.updateById(file) != 1) {
                throw new BusinessException("附件状态已变化，请重新上传后提交");
            }
        }
    }

    public boolean isProjectDocument(String businessType) {
        return BUSINESS_PROJECT_DOCUMENT.equals(normalizeBusinessType(businessType));
    }

    public boolean allowsDuplicateNameForUpload(String businessType, Long businessId) {
        if (businessId != null) return false;
        String normalized = normalizeBusinessType(businessType);
        return normalized.endsWith("_PENDING")
                || normalized.equals("INSPECTION_RECORD")
                || normalized.equals("INSPECTION_RECTIFICATION");
    }

    private boolean isBoundWorkflowAttachment(FileResource file) {
        if (file.getBusinessId() == null) return false;
        String businessType = normalizeBusinessType(file.getBusinessType());
        return businessType.equals("INSPECTION_RECORD")
                || businessType.equals("INSPECTION_RECTIFICATION")
                || GENERAL_INSPECTION_FINAL_TYPES.contains(businessType)
                || businessType.equals("QUALITY_ISSUE")
                || businessType.equals("QUALITY_RECTIFICATION")
                || businessType.equals("QUALITY_REVIEW");
    }

    private void requireEdgeInspectionPermission(Long userId, Long projectId, String permissionCode,
                                                 String message) {
        if (!permissionService.hasInspectionPermission(userId, projectId, permissionCode)) {
            throw BusinessException.forbidden(message);
        }
    }

    /**
     * 临边巡检照片除项目和功能开关外还按业务记录授权。整改人、复查人即使没有
     * EDGE_INSPECTION_VIEW，也必须能看到自己整改单中的现场证据和整改照片。
     */
    private void requireEdgeFileRead(SysUser currentUser, FileResource file, String businessType) {
        Long userId = currentUser.getId();
        Long projectId = file.getProjectId();
        if (permissionService.isPlatformAdmin(userId)) return;

        if (GENERAL_INSPECTION_STAGING_TYPES.contains(businessType)) {
            String stagePermission = businessType.contains("RECTIFICATION")
                    ? InspectionPermissionCodes.EDGE_INSPECTION_RECTIFY
                    : InspectionPermissionCodes.EDGE_INSPECTION_SUBMIT;
            if (Objects.equals(file.getUploaderId(), userId)
                    && hasEdgeInspectionPermission(userId, projectId, stagePermission)) return;
            throw BusinessException.forbidden("无临边巡检暂存附件访问权限");
        }

        if (hasEdgeInspectionPermission(userId, projectId, InspectionPermissionCodes.EDGE_INSPECTION_VIEW)
                || hasEdgeInspectionPermission(userId, projectId, InspectionPermissionCodes.EDGE_INSPECTION_MANAGE)) {
            return;
        }
        if (file.getBusinessId() == null
                || generalInspectionTaskMapper == null
                || generalInspectionRectificationMapper == null) {
            throw BusinessException.forbidden("无临边巡检附件查看权限");
        }

        if ("EDGE_INSPECTION_TASK".equals(businessType)) {
            GeneralInspectionTask task = generalInspectionTaskMapper.selectById(file.getBusinessId());
            if (!isEdgeTaskInProject(task, projectId)) {
                throw BusinessException.forbidden("无临边巡检附件查看权限");
            }
            if (Objects.equals(task.getAssigneeId(), userId)
                    && hasEdgeInspectionPermission(userId, projectId,
                    InspectionPermissionCodes.EDGE_INSPECTION_SUBMIT)) return;
            if (Objects.equals(task.getReviewerId(), userId)
                    && hasEdgeInspectionPermission(userId, projectId,
                    InspectionPermissionCodes.EDGE_INSPECTION_REVIEW)) return;
            List<GeneralInspectionRectification> rectifications = generalInspectionRectificationMapper.selectList(
                    new LambdaQueryWrapper<GeneralInspectionRectification>()
                            .eq(GeneralInspectionRectification::getTaskId, task.getId()));
            if (rectifications.stream().anyMatch(rectification ->
                    Objects.equals(rectification.getAssigneeId(), userId)
                            && hasEdgeInspectionPermission(userId, projectId,
                            InspectionPermissionCodes.EDGE_INSPECTION_RECTIFY)
                            || Objects.equals(rectification.getReviewerId(), userId)
                            && hasEdgeInspectionPermission(userId, projectId,
                            InspectionPermissionCodes.EDGE_INSPECTION_REVIEW))) return;
        } else if ("EDGE_INSPECTION_RECTIFICATION".equals(businessType)) {
            GeneralInspectionRectification rectification =
                    generalInspectionRectificationMapper.selectById(file.getBusinessId());
            GeneralInspectionTask task = rectification == null ? null
                    : generalInspectionTaskMapper.selectById(rectification.getTaskId());
            if (rectification != null
                    && Objects.equals(rectification.getProjectId(), projectId)
                    && isEdgeTaskInProject(task, projectId)
                    && (Objects.equals(rectification.getAssigneeId(), userId)
                    && hasEdgeInspectionPermission(userId, projectId,
                    InspectionPermissionCodes.EDGE_INSPECTION_RECTIFY)
                    || Objects.equals(rectification.getReviewerId(), userId)
                    && hasEdgeInspectionPermission(userId, projectId,
                    InspectionPermissionCodes.EDGE_INSPECTION_REVIEW))) return;
        }
        throw BusinessException.forbidden("无临边巡检附件查看权限");
    }

    private boolean isEdgeTaskInProject(GeneralInspectionTask task, Long projectId) {
        return task != null
                && Objects.equals(task.getProjectId(), projectId)
                && task.getPointTypeCode() != null;
    }

    private boolean hasAnyEdgeInspectionPermission(Long userId, Long projectId) {
        return permissionService.isPlatformAdmin(userId)
                || hasEdgeInspectionPermission(userId, projectId, InspectionPermissionCodes.EDGE_INSPECTION_VIEW)
                || hasEdgeInspectionPermission(userId, projectId, InspectionPermissionCodes.EDGE_INSPECTION_MANAGE)
                || hasEdgeInspectionPermission(userId, projectId, InspectionPermissionCodes.EDGE_INSPECTION_SUBMIT)
                || hasEdgeInspectionPermission(userId, projectId, InspectionPermissionCodes.EDGE_INSPECTION_RECTIFY)
                || hasEdgeInspectionPermission(userId, projectId, InspectionPermissionCodes.EDGE_INSPECTION_REVIEW);
    }

    private boolean hasEdgeInspectionPermission(Long userId, Long projectId, String permissionCode) {
        return permissionService.hasInspectionPermission(userId, projectId, permissionCode);
    }

    private void checkQualityWrite(SysUser currentUser, FileResource file, String businessType) {
        if (QUALITY_FINAL_TYPES.contains(businessType)) {
            throw BusinessException.forbidden("质量流程附件不能通过通用文件接口修改或删除");
        }
        String permissionCode = qualityWritePermission(businessType);
        if (permissionCode == null) {
            throw BusinessException.forbidden("不支持通过通用文件接口管理该质量附件");
        }
        permissionService.requireSystemPermission(currentUser.getId(), file.getProjectId(), permissionCode);
        if (BUSINESS_QUALITY_DOCUMENT.equals(businessType)) {
            return;
        }
        if (file.getBusinessId() != null) {
            throw BusinessException.forbidden("已关联业务记录的附件不能通过通用文件接口修改");
        }
        if (permissionService.isPlatformAdmin(currentUser.getId())
                || Objects.equals(file.getUploaderId(), currentUser.getId())) {
            return;
        }
        throw BusinessException.forbidden("只能管理本人上传的质量暂存附件");
    }

    private String qualityWritePermission(String businessType) {
        return switch (businessType) {
            case BUSINESS_QUALITY_DOCUMENT,
                 BUSINESS_QUALITY_PENDING,
                 BUSINESS_QUALITY_WEEKLY_PENDING,
                 BUSINESS_QUALITY_WEEKLY_ITEM_PENDING,
                 BUSINESS_QUALITY_WEEKLY_DRAFT,
                 BUSINESS_QUALITY_WEEKLY_DRAFT_ITEM -> SystemPermissionCodes.QUALITY_MANAGE;
            case BUSINESS_QUALITY_RECTIFICATION_PENDING -> SystemPermissionCodes.QUALITY_RECTIFY;
            case BUSINESS_QUALITY_REVIEW_PENDING -> SystemPermissionCodes.QUALITY_REVIEW;
            default -> null;
        };
    }

    private void validateQualityBindingTypes(String expectedBusinessType, String targetBusinessType) {
        String expected = normalizeBusinessType(expectedBusinessType);
        String target = normalizeBusinessType(targetBusinessType);
        String requiredStagingType = switch (target) {
            case BUSINESS_QUALITY_ISSUE -> BUSINESS_QUALITY_PENDING;
            case BUSINESS_QUALITY_RECTIFICATION -> BUSINESS_QUALITY_RECTIFICATION_PENDING;
            case BUSINESS_QUALITY_REVIEW -> BUSINESS_QUALITY_REVIEW_PENDING;
            case "EDGE_INSPECTION_TASK" -> "EDGE_INSPECTION_TASK_PENDING";
            case "EDGE_INSPECTION_RECTIFICATION" -> "EDGE_INSPECTION_RECTIFICATION_PENDING";
            default -> null;
        };
        if (requiredStagingType != null && !requiredStagingType.equals(expected)) {
            throw new BusinessException("附件暂存类型与流程阶段不匹配");
        }
    }

    private String normalizeBusinessType(String businessType) {
        return businessType == null ? "" : businessType.trim().toUpperCase(Locale.ROOT);
    }

    private void requireGeneralInspectionEnabled(Long projectId) {
        if (generalInspectionSettingMapper == null) return;
        GeneralInspectionProjectSetting setting = generalInspectionSettingMapper.selectById(projectId);
        if (setting == null || !Integer.valueOf(1).equals(setting.getEnabled())) {
            throw BusinessException.forbidden("当前项目尚未启用临边巡检");
        }
    }
}
