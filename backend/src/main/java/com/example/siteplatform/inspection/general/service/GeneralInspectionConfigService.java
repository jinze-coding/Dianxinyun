package com.example.siteplatform.inspection.general.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.file.service.FileResourceService;
import com.example.siteplatform.electricbox.service.ElectricBoxService;
import com.example.siteplatform.inspection.general.dto.*;
import com.example.siteplatform.inspection.general.entity.*;
import com.example.siteplatform.inspection.general.mapper.*;
import com.example.siteplatform.inspection.general.vo.GeneralInspectionPlanVO;
import com.example.siteplatform.inspection.general.vo.GeneralInspectionTemplateVO;
import com.example.siteplatform.inspection.general.vo.GeneralInspectionUserOptionVO;
import com.example.siteplatform.project.entity.SysUserProject;
import com.example.siteplatform.project.mapper.SysUserProjectMapper;
import com.example.siteplatform.project.constant.InspectionPermissionCodes;
import com.example.siteplatform.system.constant.SystemPermissionCodes;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GeneralInspectionConfigService {

    private static final Set<String> FREQUENCIES = Set.of("DAILY", "WEEKLY", "MONTHLY");
    private static final Set<String> PLAN_STATES = Set.of("PAUSED", "PUBLISHED", "ARCHIVED");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final GeneralInspectionPermissionService permissionService;
    private final GeneralInspectionProjectSettingMapper settingMapper;
    private final GeneralInspectionTemplateMapper templateMapper;
    private final GeneralInspectionTemplateVersionMapper templateVersionMapper;
    private final GeneralInspectionTemplateItemMapper templateItemMapper;
    private final GeneralInspectionPointCategoryMapper categoryMapper;
    private final GeneralInspectionPointMapper pointMapper;
    private final GeneralInspectionPlanMapper planMapper;
    private final GeneralInspectionPlanVersionMapper planVersionMapper;
    private final GeneralInspectionTaskMapper taskMapper;
    private final GeneralInspectionActionLogMapper actionLogMapper;
    private final SysUserMapper userMapper;
    private final ObjectMapper objectMapper;
    private final FileResourceService fileResourceService;
    private final SysUserProjectMapper userProjectMapper;
    private final ElectricBoxService electricBoxService;

    public GeneralInspectionProjectSetting getFeature(Long projectId, SysUser currentUser) {
        return permissionService.getSetting(projectId, currentUser);
    }

    @Transactional
    public GeneralInspectionProjectSetting updateFeature(Long projectId, GeneralInspectionFeatureRequest request,
                                                         SysUser currentUser) {
        permissionService.requirePlatformAdmin(currentUser);
        permissionService.getSetting(projectId, currentUser);
        GeneralInspectionProjectSetting existing = settingMapper.selectById(projectId);
        if (existing == null) {
            if (!Integer.valueOf(0).equals(request.getExpectedVersion())) {
                throw conflict("项目开关版本已变化，请刷新后重试");
            }
            GeneralInspectionProjectSetting setting = new GeneralInspectionProjectSetting();
            setting.setProjectId(projectId);
            setting.setEnabled(Boolean.TRUE.equals(request.getEnabled()) ? 1 : 0);
            setting.setVersion(0);
            setting.setUpdatedById(currentUser.getId());
            setting.setUpdatedByName(userName(currentUser));
            requireOne(settingMapper.insert(setting), "临边巡检项目开关新增");
        } else {
            requireOne(settingMapper.updateFeature(projectId, Boolean.TRUE.equals(request.getEnabled()) ? 1 : 0,
                    request.getExpectedVersion(), currentUser.getId(), userName(currentUser)),
                    "项目开关版本已变化，请刷新后重试");
        }
        record(projectId, "SETTING", projectId, "FEATURE_TOGGLE", currentUser,
                existing == null ? null : String.valueOf(existing.getEnabled()),
                String.valueOf(request.getEnabled()), null, null, null);
        return settingMapper.selectById(projectId);
    }

    public List<GeneralInspectionTemplateVO> listTemplates(Long projectId, SysUser currentUser) {
        permissionService.requireView(projectId, currentUser);
        return templateMapper.selectList(new LambdaQueryWrapper<GeneralInspectionTemplate>()
                        .eq(GeneralInspectionTemplate::getDeleted, 0)
                        .and(w -> w.eq(GeneralInspectionTemplate::getScopeType, "COMPANY")
                                .or().eq(GeneralInspectionTemplate::getProjectId, projectId))
                        .orderByAsc(GeneralInspectionTemplate::getScopeType)
                        .orderByDesc(GeneralInspectionTemplate::getUpdateTime))
                .stream().map(template -> toTemplateVO(template, false, currentUser)).toList();
    }

    public GeneralInspectionTemplateVO getTemplate(Long id, SysUser currentUser) {
        GeneralInspectionTemplate template = requireTemplate(id);
        if ("COMPANY".equals(template.getScopeType())) {
            if (template.getProjectId() != null) throw new BusinessException("公司模板数据异常");
        } else {
            permissionService.requireView(template.getProjectId(), currentUser);
        }
        return toTemplateVO(template, true, currentUser);
    }

    public GeneralInspectionTemplateVO previewTemplate(Long id, SysUser currentUser) {
        GeneralInspectionTemplate template = requireTemplate(id);
        requireTemplateManage(template, currentUser);
        if ("ARCHIVED".equals(template.getStatus())) throw conflict("已归档模板不能再编辑");
        List<GeneralInspectionTemplateItem> items = listDraftItems(id);
        if (items.isEmpty()) throw new BusinessException("模板草稿没有检查项，不能预览");
        return toTemplateVO(template, true, currentUser);
    }

    @Transactional
    public GeneralInspectionTemplateVO createTemplate(GeneralInspectionTemplateSaveRequest request,
                                                       SysUser currentUser) {
        boolean company = request.getProjectId() == null;
        if (company) permissionService.requirePlatformAdmin(currentUser);
        else permissionService.requireManage(request.getProjectId(), currentUser);
        validateTemplate(request);
        GeneralInspectionTemplate template = new GeneralInspectionTemplate();
        template.setScopeType(company ? "COMPANY" : "PROJECT");
        template.setProjectId(request.getProjectId());
        template.setTemplateCode(uniqueCode("TPL"));
        applyTemplateDraft(template, request, currentUser);
        template.setStatus("DRAFT");
        template.setVersion(0);
        template.setCreatedById(currentUser.getId());
        template.setCreatedByName(userName(currentUser));
        template.setDeleted(0);
        try {
            requireOne(templateMapper.insert(template), "巡检模板新增");
        } catch (DuplicateKeyException ex) {
            throw conflict("模板编码冲突，请重试");
        }
        replaceDraftItems(template.getId(), request.getItems());
        record(projectId(template), "TEMPLATE", template.getId(), "CREATE", currentUser,
                null, "DRAFT", null, null, null);
        return toTemplateVO(templateMapper.selectById(template.getId()), true, currentUser);
    }

    @Transactional
    public GeneralInspectionTemplateVO copyTemplate(Long sourceId, GeneralInspectionTemplateCopyRequest request,
                                                     SysUser currentUser) {
        permissionService.requireManage(request.getProjectId(), currentUser);
        GeneralInspectionTemplate source = requireTemplate(sourceId);
        GeneralInspectionTemplateVersion version = requirePublishedTemplateVersion(source);
        List<GeneralInspectionTemplateItem> sourceItems = listVersionItems(source.getId(), version.getId());
        if (sourceItems.isEmpty()) throw new BusinessException("源模板没有已发布检查项");

        GeneralInspectionTemplate copy = new GeneralInspectionTemplate();
        copy.setScopeType("PROJECT");
        copy.setProjectId(request.getProjectId());
        copy.setSourceTemplateId(source.getId());
        copy.setTemplateCode(uniqueCode("TPL"));
        copy.setTemplateName(trimRequired(request.getTemplateName(), "模板名称", 100));
        copy.setCategoryName(version.getCategoryName());
        copy.setStatus("DRAFT");
        copy.setOverallPhotoMin(version.getOverallPhotoMin());
        copy.setOverallPhotoMax(version.getOverallPhotoMax());
        copy.setOverallRemarkRequired(version.getOverallRemarkRequired());
        copy.setRemark(version.getRemark());
        copy.setVersion(0);
        copy.setCreatedById(currentUser.getId());
        copy.setCreatedByName(userName(currentUser));
        copy.setUpdatedById(currentUser.getId());
        copy.setUpdatedByName(userName(currentUser));
        copy.setDeleted(0);
        requireOne(templateMapper.insert(copy), "项目模板复制");
        for (GeneralInspectionTemplateItem sourceItem : sourceItems) {
            GeneralInspectionTemplateItem draft = copyItem(sourceItem, copy.getId(), null);
            requireOne(templateItemMapper.insert(draft), "模板检查项复制");
        }
        record(request.getProjectId(), "TEMPLATE", copy.getId(), "COPY", currentUser,
                null, "DRAFT", "来源模板：" + source.getTemplateName(), null, null);
        return toTemplateVO(templateMapper.selectById(copy.getId()), true, currentUser);
    }

    @Transactional
    public GeneralInspectionTemplateVO saveTemplate(Long id, GeneralInspectionTemplateSaveRequest request,
                                                     SysUser currentUser) {
        GeneralInspectionTemplate template = requireTemplate(id);
        requireTemplateManage(template, currentUser);
        if ("ARCHIVED".equals(template.getStatus())) throw conflict("已归档模板不能再编辑");
        validateTemplate(request);
        if (!Objects.equals(template.getProjectId(), request.getProjectId())) {
            throw new BusinessException("模板所属范围不可改变");
        }
        requireOne(templateMapper.updateDraft(id, request.getExpectedVersion(),
                trimRequired(request.getTemplateName(), "模板名称", 100), trim(request.getCategoryName(), 100),
                value(request.getOverallPhotoMin(), 0), value(request.getOverallPhotoMax(), 9),
                Boolean.TRUE.equals(request.getOverallRemarkRequired()) ? 1 : 0,
                trim(request.getRemark(), 1000), currentUser.getId(), userName(currentUser)),
                "模板版本已变化，请刷新后重试");
        replaceDraftItems(id, request.getItems());
        record(projectId(template), "TEMPLATE", id, "SAVE_DRAFT", currentUser,
                template.getStatus(), template.getStatus(), null, null, null);
        return toTemplateVO(templateMapper.selectById(id), true, currentUser);
    }

    @Transactional
    public GeneralInspectionTemplateVO publishTemplate(Long id, GeneralInspectionPublishRequest request,
                                                        SysUser currentUser) {
        GeneralInspectionTemplate template = templateMapper.selectByIdForUpdate(id);
        if (template == null) throw BusinessException.notFound("巡检模板不存在");
        requireTemplateManage(template, currentUser);
        requireExpected(template.getVersion(), request.getExpectedVersion(), "模板版本已变化，请刷新后重试");
        if ("ARCHIVED".equals(template.getStatus())) throw conflict("模板已经归档");
        if (request.getEffectiveTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException("模板生效时间不能早于当前时间");
        }
        List<GeneralInspectionTemplateItem> draftItems = listDraftItems(id);
        if (draftItems.isEmpty() || draftItems.size() > 50) throw new BusinessException("模板必须包含1至50个检查项");
        int versionNo = nextTemplateVersionNo(id);
        GeneralInspectionTemplateVersion version = new GeneralInspectionTemplateVersion();
        version.setTemplateId(id);
        version.setVersionNo(versionNo);
        version.setEffectiveTime(request.getEffectiveTime());
        version.setTemplateName(template.getTemplateName());
        version.setCategoryName(template.getCategoryName());
        version.setOverallPhotoMin(template.getOverallPhotoMin());
        version.setOverallPhotoMax(template.getOverallPhotoMax());
        version.setOverallRemarkRequired(template.getOverallRemarkRequired());
        version.setRemark(template.getRemark());
        version.setPublishedById(currentUser.getId());
        version.setPublishedByName(userName(currentUser));
        requireOne(templateVersionMapper.insert(version), "模板版本发布");
        for (GeneralInspectionTemplateItem draft : draftItems) {
            requireOne(templateItemMapper.insert(copyItem(draft, id, version.getId())), "模板版本检查项发布");
        }
        template.setCurrentVersionId(version.getId());
        template.setStatus("PUBLISHED");
        template.setUpdatedById(currentUser.getId());
        template.setUpdatedByName(userName(currentUser));
        template.setVersion(template.getVersion() + 1);
        requireOne(templateMapper.updateById(template), "模板发布状态更新");
        record(projectId(template), "TEMPLATE", id, "PUBLISH", currentUser,
                null, "PUBLISHED", "版本 V" + versionNo + "，生效时间 " + request.getEffectiveTime(), null, null);
        return toTemplateVO(template, true, currentUser);
    }

    @Transactional
    public void archiveTemplate(Long id, GeneralInspectionTaskActionRequest request, SysUser currentUser) {
        GeneralInspectionTemplate template = templateMapper.selectByIdForUpdate(id);
        if (template == null) throw BusinessException.notFound("巡检模板不存在");
        requireTemplateManage(template, currentUser);
        requireExpected(template.getVersion(), request.getExpectedVersion(), "模板版本已变化，请刷新后重试");
        if ("ARCHIVED".equals(template.getStatus())) throw conflict("模板已经归档");
        String from = template.getStatus();
        template.setStatus("ARCHIVED");
        template.setVersion(template.getVersion() + 1);
        template.setUpdatedById(currentUser.getId());
        template.setUpdatedByName(userName(currentUser));
        requireOne(templateMapper.updateById(template), "模板归档");
        record(projectId(template), "TEMPLATE", id, "ARCHIVE", currentUser, from, "ARCHIVED",
                trimRequired(request.getReason(), "归档原因", 500), null, null);
    }

    public List<GeneralInspectionPointCategory> listCategories(Long projectId, SysUser currentUser) {
        permissionService.requireView(projectId, currentUser);
        return categoryMapper.selectList(new LambdaQueryWrapper<GeneralInspectionPointCategory>()
                .eq(GeneralInspectionPointCategory::getEnabled, 1)
                .and(w -> w.isNull(GeneralInspectionPointCategory::getProjectId)
                        .or().eq(GeneralInspectionPointCategory::getProjectId, projectId))
                .orderByDesc(GeneralInspectionPointCategory::getBuiltin)
                .orderByAsc(GeneralInspectionPointCategory::getCategoryName));
    }

    @Transactional
    public GeneralInspectionPointCategory createCategory(GeneralInspectionCategoryRequest request,
                                                          SysUser currentUser) {
        permissionService.requireManage(request.getProjectId(), currentUser);
        GeneralInspectionPointCategory category = new GeneralInspectionPointCategory();
        category.setProjectId(request.getProjectId());
        category.setCategoryCode(uniqueCode("CAT"));
        category.setCategoryName(trimRequired(request.getCategoryName(), "类别名称", 100));
        category.setBuiltin(0);
        category.setEnabled(1);
        category.setCreatedById(currentUser.getId());
        try {
            requireOne(categoryMapper.insert(category), "点位类别新增");
        } catch (DuplicateKeyException ex) {
            throw conflict("点位类别编码冲突，请重试");
        }
        return category;
    }

    public List<GeneralInspectionPoint> listPoints(Long projectId, String status, SysUser currentUser) {
        permissionService.requireView(projectId, currentUser);
        LambdaQueryWrapper<GeneralInspectionPoint> query = new LambdaQueryWrapper<GeneralInspectionPoint>()
                .eq(GeneralInspectionPoint::getProjectId, projectId)
                .eq(GeneralInspectionPoint::getDeleted, 0);
        if (StringUtils.hasText(status)) query.eq(GeneralInspectionPoint::getStatus, status.trim().toUpperCase());
        return pointMapper.selectList(query.orderByAsc(GeneralInspectionPoint::getPointCode));
    }

    public List<GeneralInspectionUserOptionVO> listUserOptions(Long projectId, SysUser currentUser) {
        try {
            permissionService.requireManage(projectId, currentUser);
        } catch (BusinessException denied) {
            // 主巡检人提交异常时可以在项目内具备整改资格的成员中逐项指定责任人；
            // 返回值只包含用户标识、显示名及巡检业务能力，不暴露账号联系方式。
            permissionService.requireSubmit(projectId, currentUser);
        }
        return userProjectMapper.selectList(new LambdaQueryWrapper<SysUserProject>()
                        .eq(SysUserProject::getProjectId, projectId)
                        .eq(SysUserProject::getStatus, "ACTIVE"))
                .stream().map(SysUserProject::getUserId).filter(Objects::nonNull).distinct()
                .map(userMapper::selectById).filter(this::isActiveUser)
                .map(user -> {
                    GeneralInspectionUserOptionVO vo = new GeneralInspectionUserOptionVO();
                    vo.setUserId(user.getId());
                    vo.setUserName(userName(user));
                    vo.setCanSubmit(permissionService.hasSystemPermission(projectId, user.getId(), SystemPermissionCodes.INSPECTION_SUBMIT)
                            && permissionService.hasInspectionPermission(projectId, user.getId(), InspectionPermissionCodes.CUSTOM_INSPECTION_SUBMIT));
                    vo.setCanRectify(permissionService.hasSystemPermission(projectId, user.getId(), SystemPermissionCodes.INSPECTION_RECTIFY));
                    vo.setCanReview(permissionService.hasSystemPermission(projectId, user.getId(), SystemPermissionCodes.INSPECTION_REVIEW));
                    return vo;
                }).filter(vo -> Boolean.TRUE.equals(vo.getCanSubmit()) || Boolean.TRUE.equals(vo.getCanRectify())
                        || Boolean.TRUE.equals(vo.getCanReview()))
                .sorted(Comparator.comparing(GeneralInspectionUserOptionVO::getUserName,
                        Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    @Transactional
    public GeneralInspectionPoint savePoint(Long id, GeneralInspectionPointRequest request, SysUser currentUser) {
        permissionService.requireManage(request.getProjectId(), currentUser);
        GeneralInspectionPoint point;
        Set<Long> existingPhotoIds = Set.of();
        if (id == null) {
            point = new GeneralInspectionPoint();
            point.setProjectId(request.getProjectId());
            point.setVersion(0);
            point.setStatus("ACTIVE");
            point.setCreatedById(currentUser.getId());
            point.setCreatedByName(userName(currentUser));
            point.setDeleted(0);
        } else {
            point = pointMapper.selectByIdForUpdate(id);
            if (point == null) throw BusinessException.notFound("巡检点位不存在");
            if (!Objects.equals(point.getProjectId(), request.getProjectId())) throw new BusinessException("点位所属项目不可改变");
            requireExpected(point.getVersion(), request.getExpectedVersion(), "点位版本已变化，请刷新后重试");
            existingPhotoIds = new HashSet<>(parseIds(point.getReferencePhotoFileIds()));
            point.setVersion(point.getVersion() + 1);
        }
        Integer previousQrEnabled = point.getQrEnabled();
        Integer previousQrVersion = point.getQrVersion();
        GeneralInspectionPointCategory category = null;
        if (request.getCategoryId() != null) {
            category = categoryMapper.selectById(request.getCategoryId());
            if (category == null || !Integer.valueOf(1).equals(category.getEnabled())
                    || (category.getProjectId() != null && !Objects.equals(category.getProjectId(), request.getProjectId()))) {
                throw new BusinessException("点位类别无效");
            }
        }
        point.setPointCode(trimRequired(request.getPointCode(), "点位编码", 50).toUpperCase());
        point.setPointName(trimRequired(request.getPointName(), "点位名称", 100));
        point.setCategoryId(category == null ? null : category.getId());
        point.setCategoryName(category == null ? null : category.getCategoryName());
        point.setAreaName(trim(request.getAreaName(), 100));
        point.setBuildingName(trim(request.getBuildingName(), 100));
        point.setFloorName(trim(request.getFloorName(), 100));
        point.setLocationDesc(trim(request.getLocationDesc(), 300));
        point.setRiskNote(trim(request.getRiskNote(), 1000));
        point.setReferencePhotoFileIds(joinIds(request.getReferencePhotoFileIds()));
        point.setPublicAccessEnabled(Boolean.TRUE.equals(request.getPublicAccessEnabled()) ? 1 : 0);
        boolean qrEnabled = Boolean.TRUE.equals(request.getQrEnabled());
        point.setQrEnabled(qrEnabled ? 1 : 0);
        if (!StringUtils.hasText(point.getPublicCode())) point.setPublicCode(randomSceneCode());
        if (qrEnabled && (point.getQrVersion() == null || point.getQrVersion() == 0)) point.setQrVersion(1);
        if (point.getQrVersion() == null) point.setQrVersion(0);
        point.setUpdatedById(currentUser.getId());
        point.setUpdatedByName(userName(currentUser));
        try {
            requireOne(id == null ? pointMapper.insert(point) : pointMapper.updateById(point), id == null ? "点位新增" : "点位更新");
        } catch (DuplicateKeyException ex) {
            throw conflict("同一项目的点位编码不能重复");
        }
        if (!Objects.equals(previousQrEnabled, point.getQrEnabled())
                || !Objects.equals(previousQrVersion, point.getQrVersion())) {
            synchronizePendingTaskQr(point);
        }
        Set<Long> retainedPhotoIds = existingPhotoIds;
        List<Long> newPhotoIds = request.getReferencePhotoFileIds() == null ? List.of()
                : request.getReferencePhotoFileIds().stream().filter(Objects::nonNull)
                .filter(fileId -> !retainedPhotoIds.contains(fileId)).toList();
        fileResourceService.validateAndBind(currentUser, point.getProjectId(), newPhotoIds,
                "INSPECTION_CUSTOM_POINT_PENDING", "INSPECTION_CUSTOM_POINT", point.getId());
        record(point.getProjectId(), "POINT", point.getId(), id == null ? "CREATE" : "UPDATE", currentUser,
                null, point.getStatus(), null, null, null);
        return point;
    }

    @Transactional
    public GeneralInspectionPoint rotatePointCode(Long id, GeneralInspectionPointActionRequest request,
                                                   SysUser currentUser) {
        GeneralInspectionPoint point = pointMapper.selectByIdForUpdate(id);
        if (point == null) throw BusinessException.notFound("巡检点位不存在");
        permissionService.requireManage(point.getProjectId(), currentUser);
        requireExpected(point.getVersion(), request.getExpectedVersion(), "点位版本已变化，请刷新后重试");
        String old = point.getPublicCode();
        point.setPublicCode(randomSceneCode());
        point.setQrEnabled(1);
        point.setQrVersion(value(point.getQrVersion(), 0) + 1);
        point.setVersion(point.getVersion() + 1);
        point.setUpdatedById(currentUser.getId());
        point.setUpdatedByName(userName(currentUser));
        requireOne(pointMapper.updateById(point), "点位二维码换码");
        synchronizePendingTaskQr(point);
        record(point.getProjectId(), "POINT", point.getId(), "ROTATE_QR", currentUser,
                null, null, trim(request.getReason(), 500), old, point.getPublicCode());
        return point;
    }

    public Map<String, Object> getPointQr(Long id, SysUser currentUser) {
        GeneralInspectionPoint point = pointMapper.selectById(id);
        if (point == null || Integer.valueOf(1).equals(point.getDeleted())) {
            throw BusinessException.notFound("巡检点位不存在");
        }
        permissionService.requireManage(point.getProjectId(), currentUser);
        if (!Integer.valueOf(1).equals(point.getQrEnabled()) || !StringUtils.hasText(point.getPublicCode())) {
            throw new BusinessException("该点位尚未启用二维码");
        }
        String sceneCode = "P:" + point.getPublicCode();
        return Map.of(
                "pointCode", point.getPointCode(),
                "pointName", point.getPointName(),
                "qrVersion", value(point.getQrVersion(), 0),
                "sceneCode", sceneCode,
                "svg", electricBoxService.generateQrSvg(sceneCode));
    }

    @Transactional
    public GeneralInspectionPoint changePointStatus(Long id, String targetStatus,
                                                     GeneralInspectionPointActionRequest request,
                                                     SysUser currentUser) {
        GeneralInspectionPoint point = pointMapper.selectByIdForUpdate(id);
        if (point == null) throw BusinessException.notFound("巡检点位不存在");
        permissionService.requireManage(point.getProjectId(), currentUser);
        requireExpected(point.getVersion(), request.getExpectedVersion(), "点位版本已变化，请刷新后重试");
        String normalized = targetStatus == null ? "" : targetStatus.trim().toUpperCase();
        if (!Set.of("ACTIVE", "INACTIVE", "ARCHIVED").contains(normalized)) throw new BusinessException("点位状态无效");
        if ("ARCHIVED".equals(point.getStatus()) && !"ARCHIVED".equals(normalized)) {
            throw conflict("已归档点位不能重新启用");
        }
        String from = point.getStatus();
        point.setStatus(normalized);
        point.setVersion(point.getVersion() + 1);
        point.setUpdatedById(currentUser.getId());
        point.setUpdatedByName(userName(currentUser));
        requireOne(pointMapper.updateById(point), "点位状态更新");
        record(point.getProjectId(), "POINT", point.getId(), "STATUS", currentUser, from, normalized,
                trimRequired(request.getReason(), "状态变更原因", 500), null, null);
        return point;
    }

    public List<GeneralInspectionPlanVO> listPlans(Long projectId, SysUser currentUser) {
        permissionService.requireView(projectId, currentUser);
        return planMapper.selectList(new LambdaQueryWrapper<GeneralInspectionPlan>()
                        .eq(GeneralInspectionPlan::getProjectId, projectId)
                        .eq(GeneralInspectionPlan::getDeleted, 0)
                        .orderByDesc(GeneralInspectionPlan::getUpdateTime))
                .stream().map(this::toPlanVO).toList();
    }

    public GeneralInspectionPlanConfig validatePlanPreview(GeneralInspectionPlanSaveRequest request,
                                                            SysUser currentUser) {
        permissionService.requireManage(request.getProjectId(), currentUser);
        validatePlanConfig(request.getProjectId(), request.getTemplateId(), request.getConfig());
        return request.getConfig();
    }

    @Transactional
    public GeneralInspectionPlanVO savePlan(Long id, GeneralInspectionPlanSaveRequest request,
                                            SysUser currentUser) {
        permissionService.requireManage(request.getProjectId(), currentUser);
        validatePlanConfig(request.getProjectId(), request.getTemplateId(), request.getConfig());
        GeneralInspectionPlan plan;
        if (id == null) {
            plan = new GeneralInspectionPlan();
            plan.setProjectId(request.getProjectId());
            plan.setTemplateId(request.getTemplateId());
            plan.setPlanCode(uniqueCode("PLAN"));
            plan.setStatus("DRAFT");
            plan.setVersion(0);
            plan.setCreatedById(currentUser.getId());
            plan.setCreatedByName(userName(currentUser));
            plan.setDeleted(0);
        } else {
            plan = planMapper.selectByIdForUpdate(id);
            if (plan == null) throw BusinessException.notFound("巡检计划不存在");
            if ("ARCHIVED".equals(plan.getStatus())) throw conflict("已归档计划不能再编辑");
            if (!Objects.equals(plan.getProjectId(), request.getProjectId())) throw new BusinessException("计划所属项目不可改变");
            requireExpected(plan.getVersion(), request.getExpectedVersion(), "计划版本已变化，请刷新后重试");
            plan.setVersion(plan.getVersion() + 1);
        }
        plan.setTemplateId(request.getTemplateId());
        plan.setPlanName(trimRequired(request.getPlanName(), "计划名称", 100));
        plan.setDraftConfigJson(json(request.getConfig()));
        plan.setUpdatedById(currentUser.getId());
        plan.setUpdatedByName(userName(currentUser));
        requireOne(id == null ? planMapper.insert(plan) : planMapper.updateById(plan), id == null ? "计划新增" : "计划更新");
        record(plan.getProjectId(), "PLAN", plan.getId(), id == null ? "CREATE" : "SAVE_DRAFT", currentUser,
                null, plan.getStatus(), null, null, null);
        return toPlanVO(plan);
    }

    @Transactional
    public GeneralInspectionPlanVO publishPlan(Long id, GeneralInspectionPublishRequest request,
                                                SysUser currentUser) {
        GeneralInspectionPlan plan = planMapper.selectByIdForUpdate(id);
        if (plan == null) throw BusinessException.notFound("巡检计划不存在");
        permissionService.requireManage(plan.getProjectId(), currentUser);
        requireExpected(plan.getVersion(), request.getExpectedVersion(), "计划版本已变化，请刷新后重试");
        if ("ARCHIVED".equals(plan.getStatus())) throw conflict("已归档计划不能再发布");
        if (request.getEffectiveTime().isBefore(LocalDateTime.now())) throw new BusinessException("计划生效时间不能早于当前时间");
        GeneralInspectionPlanConfig config = parseConfig(plan.getDraftConfigJson());
        validatePlanConfig(plan.getProjectId(), plan.getTemplateId(), config);
        GeneralInspectionTemplate template = requireTemplate(plan.getTemplateId());
        if (!Objects.equals(template.getProjectId(), plan.getProjectId()) || template.getCurrentVersionId() == null) {
            throw new BusinessException("计划必须关联当前项目已发布模板");
        }
        Long usableTemplateVersion = templateVersionMapper.selectCount(
                new LambdaQueryWrapper<GeneralInspectionTemplateVersion>()
                        .eq(GeneralInspectionTemplateVersion::getTemplateId, template.getId())
                        .le(GeneralInspectionTemplateVersion::getEffectiveTime, request.getEffectiveTime()));
        if (usableTemplateVersion == null || usableTemplateVersion == 0) {
            throw new BusinessException("计划生效时必须已有生效的项目模板版本");
        }
        int versionNo = nextPlanVersionNo(id);
        GeneralInspectionPlanVersion version = new GeneralInspectionPlanVersion();
        version.setPlanId(id);
        version.setVersionNo(versionNo);
        version.setEffectiveTime(request.getEffectiveTime());
        version.setConfigJson(plan.getDraftConfigJson());
        version.setPublishedById(currentUser.getId());
        version.setPublishedByName(userName(currentUser));
        requireOne(planVersionMapper.insert(version), "计划版本发布");
        plan.setCurrentVersionId(version.getId());
        plan.setStatus("PUBLISHED");
        plan.setGeneratedThroughTime(null);
        plan.setVersion(plan.getVersion() + 1);
        plan.setUpdatedById(currentUser.getId());
        plan.setUpdatedByName(userName(currentUser));
        requireOne(planMapper.updateById(plan), "计划发布状态更新");
        record(plan.getProjectId(), "PLAN", id, "PUBLISH", currentUser, null, "PUBLISHED",
                "版本 V" + versionNo + "，生效时间 " + request.getEffectiveTime(), null, null);
        return toPlanVO(plan);
    }

    @Transactional
    public GeneralInspectionPlanVO changePlanState(Long id, String target,
                                                    GeneralInspectionTaskActionRequest request,
                                                    SysUser currentUser) {
        GeneralInspectionPlan plan = planMapper.selectByIdForUpdate(id);
        if (plan == null) throw BusinessException.notFound("巡检计划不存在");
        permissionService.requireManage(plan.getProjectId(), currentUser);
        requireExpected(plan.getVersion(), request.getExpectedVersion(), "计划版本已变化，请刷新后重试");
        String normalized = target == null ? "" : target.trim().toUpperCase();
        if (!PLAN_STATES.contains(normalized)) throw new BusinessException("计划状态无效");
        if ("ARCHIVED".equals(plan.getStatus()) && !"ARCHIVED".equals(normalized)) {
            throw conflict("已归档计划不能恢复");
        }
        if ("PUBLISHED".equals(normalized) && plan.getCurrentVersionId() == null) throw new BusinessException("未发布版本的计划不能恢复");
        String from = plan.getStatus();
        plan.setStatus(normalized);
        plan.setVersion(plan.getVersion() + 1);
        plan.setUpdatedById(currentUser.getId());
        plan.setUpdatedByName(userName(currentUser));
        requireOne(planMapper.updateById(plan), "计划状态更新");
        record(plan.getProjectId(), "PLAN", id, "STATUS", currentUser, from, normalized,
                trimRequired(request.getReason(), "状态变更原因", 500), null, null);
        return toPlanVO(plan);
    }

    private void validateTemplate(GeneralInspectionTemplateSaveRequest request) {
        trimRequired(request.getTemplateName(), "模板名称", 100);
        int min = value(request.getOverallPhotoMin(), 0);
        int max = value(request.getOverallPhotoMax(), 9);
        if (min > max) throw new BusinessException("整体照片最少数量不能大于最多数量");
        if (request.getItems() == null || request.getItems().isEmpty() || request.getItems().size() > 50) {
            throw new BusinessException("模板必须包含1至50个检查项");
        }
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < request.getItems().size(); i++) {
            GeneralInspectionTemplateItemRequest item = request.getItems().get(i);
            trimRequired(item.getItemName(), "检查项名称", 200);
            String key = normalizeItemKey(item.getItemKey(), i);
            if (!keys.add(key)) throw new BusinessException("检查项编码不能重复：" + key);
            int photoMax = value(item.getPhotoMax(), 9);
            if (value(item.getNormalPhotoMin(), 0) > photoMax || value(item.getAbnormalPhotoMin(), 0) > photoMax) {
                throw new BusinessException("检查项最少照片数量不能大于最多数量");
            }
        }
    }

    private void validatePlanConfig(Long projectId, Long templateId, GeneralInspectionPlanConfig config) {
        GeneralInspectionTemplate template = requireTemplate(templateId);
        if (!"PROJECT".equals(template.getScopeType()) || !Objects.equals(template.getProjectId(), projectId)
                || "ARCHIVED".equals(template.getStatus())) throw new BusinessException("计划只能使用本项目未归档模板");
        String frequency = config.getFrequency() == null ? "" : config.getFrequency().trim().toUpperCase();
        if (!FREQUENCIES.contains(frequency)) throw new BusinessException("计划频率仅支持每天、每周或每月");
        config.setFrequency(frequency);
        if (config.getEffectiveEnd() != null && config.getEffectiveEnd().isBefore(config.getEffectiveStart())) {
            throw new BusinessException("计划结束日期不能早于开始日期");
        }
        if ("WEEKLY".equals(frequency) && (config.getWeekdays() == null || config.getWeekdays().isEmpty()
                || config.getWeekdays().stream().anyMatch(day -> day == null || day < 1 || day > 7))) {
            throw new BusinessException("每周计划必须选择1至7之间的星期");
        }
        if ("MONTHLY".equals(frequency) && (config.getMonthDays() == null || config.getMonthDays().isEmpty()
                || config.getMonthDays().stream().anyMatch(day -> day == null || (day != -1 && (day < 1 || day > 28))))) {
            throw new BusinessException("每月计划仅支持1至28日或月末");
        }
        Set<String> slotCodes = new HashSet<>();
        for (int i = 0; i < config.getSlots().size(); i++) {
            GeneralInspectionPlanConfig.Slot slot = config.getSlots().get(i);
            String code = StringUtils.hasText(slot.getSlotCode()) ? slot.getSlotCode().trim().toUpperCase() : "SLOT_" + (i + 1);
            if (!code.matches("[A-Z0-9_-]{1,50}") || !slotCodes.add(code)) throw new BusinessException("时间段编码无效或重复");
            slot.setSlotCode(code);
            slot.setSlotName(trimRequired(slot.getSlotName(), "时间段名称", 100));
            if (slot.getDueDayOffset() == null) {
                slot.setDueDayOffset(slot.getDueTime().isAfter(slot.getStartTime()) ? 0 : 1);
            }
            if (slot.getDueDayOffset() == 0 && !slot.getDueTime().isAfter(slot.getStartTime())) {
                throw new BusinessException("同日时间段的截止时间必须晚于开始时间");
            }
            long durationMinutes = java.time.Duration.between(slot.getStartTime(), slot.getDueTime()).toMinutes()
                    + slot.getDueDayOffset() * 24L * 60L;
            if (durationMinutes <= 0 || durationMinutes > 24L * 60L) {
                throw new BusinessException("时间段必须大于0且最长不超过跨午夜24小时");
            }
        }
        Set<Long> pointIds = new HashSet<>();
        if (config.getAssigneeId() != null) requireSubmitMember(projectId, config.getAssigneeId(), "计划主巡检人");
        validateOptionalSubmitMembers(projectId, config.getBackupAssigneeIds(), "计划备选巡检人");
        if (config.getDefaultRectifierId() != null) {
            requireRectifyMember(projectId, config.getDefaultRectifierId(), "计划默认整改人");
        }
        if (config.getReviewerId() != null) requireReviewMember(projectId, config.getReviewerId(), "计划主复查人");
        validateOptionalReviewMembers(projectId, config.getBackupReviewerIds(), "计划备选复查人");
        if (config.getRectificationDays() == null) config.setRectificationDays(3);
        for (GeneralInspectionPlanConfig.PointAssignment assignment : config.getPoints()) {
            if (!pointIds.add(assignment.getPointId())) throw new BusinessException("同一计划不能重复关联点位");
            GeneralInspectionPoint point = pointMapper.selectById(assignment.getPointId());
            if (point == null || !Objects.equals(point.getProjectId(), projectId) || !"ACTIVE".equals(point.getStatus())) {
                throw new BusinessException("计划包含无效或非本项目点位");
            }
            Long assigneeId = assignment.getAssigneeId() == null ? config.getAssigneeId() : assignment.getAssigneeId();
            List<Long> backupAssigneeIds = assignment.getBackupAssigneeIds() == null
                    ? config.getBackupAssigneeIds() : assignment.getBackupAssigneeIds();
            Long defaultRectifierId = assignment.getDefaultRectifierId() == null
                    ? config.getDefaultRectifierId() : assignment.getDefaultRectifierId();
            Long reviewerId = assignment.getReviewerId() == null ? config.getReviewerId() : assignment.getReviewerId();
            List<Long> backupReviewerIds = assignment.getBackupReviewerIds() == null
                    ? config.getBackupReviewerIds() : assignment.getBackupReviewerIds();
            if (assigneeId == null) {
                throw new BusinessException("计划主巡检人不能为空；可设置计划默认值或逐点覆盖");
            }
            requireSubmitMember(projectId, assigneeId, "主巡检人");
            validateOptionalSubmitMembers(projectId, backupAssigneeIds, "备选巡检人");
            if (defaultRectifierId != null) requireRectifyMember(projectId, defaultRectifierId, "默认整改人");
            if (reviewerId != null) requireReviewMember(projectId, reviewerId, "主复查人");
            validateOptionalReviewMembers(projectId, backupReviewerIds, "备选复查人");
        }
        if (config.getEarlyMinutes() == null) config.setEarlyMinutes(30);
    }

    private void validateOptionalMembers(Long projectId, List<Long> ids, String label) {
        if (ids == null) return;
        for (Long id : ids.stream().filter(Objects::nonNull).distinct().toList()) requireActiveMember(projectId, id, label);
    }

    private void validateOptionalSubmitMembers(Long projectId, List<Long> ids, String label) {
        if (ids == null) return;
        for (Long id : ids.stream().filter(Objects::nonNull).distinct().toList()) requireSubmitMember(projectId, id, label);
    }

    private void validateOptionalReviewMembers(Long projectId, List<Long> ids, String label) {
        if (ids == null) return;
        for (Long id : ids.stream().filter(Objects::nonNull).distinct().toList()) requireReviewMember(projectId, id, label);
    }

    private SysUser requireSubmitMember(Long projectId, Long userId, String label) {
        SysUser user = requireActiveMember(projectId, userId, label);
        if (!permissionService.hasSystemPermission(projectId, userId, SystemPermissionCodes.INSPECTION_SUBMIT)
                || !permissionService.hasInspectionPermission(projectId, userId, InspectionPermissionCodes.CUSTOM_INSPECTION_SUBMIT)) {
            throw new BusinessException(label + "缺少通用巡检提交权限");
        }
        return user;
    }

    private SysUser requireRectifyMember(Long projectId, Long userId, String label) {
        SysUser user = requireActiveMember(projectId, userId, label);
        if (!permissionService.hasSystemPermission(projectId, userId, SystemPermissionCodes.INSPECTION_RECTIFY)) {
            throw new BusinessException(label + "缺少巡检整改权限");
        }
        return user;
    }

    private SysUser requireReviewMember(Long projectId, Long userId, String label) {
        SysUser user = requireActiveMember(projectId, userId, label);
        if (!permissionService.hasSystemPermission(projectId, userId, SystemPermissionCodes.INSPECTION_REVIEW)) {
            throw new BusinessException(label + "缺少巡检复查权限");
        }
        return user;
    }

    private void synchronizePendingTaskQr(GeneralInspectionPoint point) {
        List<GeneralInspectionTask> pendingTasks = taskMapper.selectPendingByPointForUpdate(point.getId());
        for (GeneralInspectionTask task : pendingTasks) {
            task.setQrRequired(Integer.valueOf(1).equals(point.getQrEnabled()) ? 1 : 0);
            task.setQrVersion(value(point.getQrVersion(), 0));
            task.setScanVerifiedBy(null);
            task.setScanVerifiedTime(null);
            task.setVersion(value(task.getVersion(), 0) + 1);
            requireOne(taskMapper.updateById(task), "待执行任务二维码同步");
        }
    }

    private SysUser requireActiveMember(Long projectId, Long userId, String label) {
        if (!permissionService.hasActiveProjectAccess(projectId, userId)) throw new BusinessException(label + "必须是项目有效成员");
        SysUser user = userMapper.selectById(userId);
        if (user == null || user.getDeleted() != null && user.getDeleted() == 1 || !Integer.valueOf(1).equals(user.getStatus())) {
            throw new BusinessException(label + "账号无效");
        }
        return user;
    }

    private boolean isActiveUser(SysUser user) {
        return user != null && Integer.valueOf(1).equals(user.getStatus())
                && !Integer.valueOf(1).equals(user.getDeleted());
    }

    private void replaceDraftItems(Long templateId, List<GeneralInspectionTemplateItemRequest> requests) {
        templateItemMapper.deleteDraftItems(templateId);
        for (int i = 0; i < requests.size(); i++) {
            GeneralInspectionTemplateItemRequest source = requests.get(i);
            GeneralInspectionTemplateItem item = new GeneralInspectionTemplateItem();
            item.setTemplateId(templateId);
            item.setItemKey(normalizeItemKey(source.getItemKey(), i));
            item.setItemName(trimRequired(source.getItemName(), "检查项名称", 200));
            item.setGuidance(trim(source.getGuidance(), 1000));
            item.setStandardReference(trim(source.getStandardReference(), 500));
            item.setAllowNa(Boolean.TRUE.equals(source.getAllowNa()) ? 1 : 0);
            item.setNormalPhotoMin(value(source.getNormalPhotoMin(), 0));
            item.setAbnormalPhotoMin(value(source.getAbnormalPhotoMin(), 0));
            item.setPhotoMax(value(source.getPhotoMax(), 9));
            item.setNormalDescriptionRequired(Boolean.TRUE.equals(source.getNormalDescriptionRequired()) ? 1 : 0);
            item.setAbnormalDescriptionRequired(Boolean.TRUE.equals(source.getAbnormalDescriptionRequired()) ? 1 : 0);
            item.setSortOrder(i + 1);
            requireOne(templateItemMapper.insert(item), "模板草稿检查项保存");
        }
    }

    private GeneralInspectionTemplateItem copyItem(GeneralInspectionTemplateItem source, Long templateId, Long versionId) {
        GeneralInspectionTemplateItem item = new GeneralInspectionTemplateItem();
        item.setTemplateId(templateId);
        item.setTemplateVersionId(versionId);
        item.setItemKey(source.getItemKey());
        item.setItemName(source.getItemName());
        item.setGuidance(source.getGuidance());
        item.setStandardReference(source.getStandardReference());
        item.setAllowNa(source.getAllowNa());
        item.setNormalPhotoMin(source.getNormalPhotoMin());
        item.setAbnormalPhotoMin(source.getAbnormalPhotoMin());
        item.setPhotoMax(source.getPhotoMax());
        item.setNormalDescriptionRequired(source.getNormalDescriptionRequired());
        item.setAbnormalDescriptionRequired(source.getAbnormalDescriptionRequired());
        item.setSortOrder(source.getSortOrder());
        return item;
    }

    private GeneralInspectionTemplateVO toTemplateVO(GeneralInspectionTemplate template, boolean includeItems,
                                                      SysUser currentUser) {
        GeneralInspectionTemplateVO vo = new GeneralInspectionTemplateVO();
        vo.setId(template.getId());
        vo.setScopeType(template.getScopeType());
        vo.setProjectId(template.getProjectId());
        vo.setSourceTemplateId(template.getSourceTemplateId());
        vo.setTemplateCode(template.getTemplateCode());
        vo.setTemplateName(template.getTemplateName());
        vo.setCategoryName(template.getCategoryName());
        vo.setStatus(template.getStatus());
        vo.setCurrentVersionId(template.getCurrentVersionId());
        vo.setOverallPhotoMin(template.getOverallPhotoMin());
        vo.setOverallPhotoMax(template.getOverallPhotoMax());
        vo.setOverallRemarkRequired(Integer.valueOf(1).equals(template.getOverallRemarkRequired()));
        vo.setRemark(template.getRemark());
        vo.setVersion(template.getVersion());
        vo.setCanManage("COMPANY".equals(template.getScopeType())
                ? permissionService.isPlatformAdmin(currentUser)
                : permissionService.canManage(template.getProjectId(), currentUser));
        if (template.getCurrentVersionId() != null) {
            GeneralInspectionTemplateVersion version = templateVersionMapper.selectById(template.getCurrentVersionId());
            if (version != null) {
                vo.setCurrentVersionNo(version.getVersionNo());
                vo.setEffectiveTime(version.getEffectiveTime());
            }
        }
        if (includeItems) {
            List<GeneralInspectionTemplateItem> items = listDraftItems(template.getId());
            if (items.isEmpty() && template.getCurrentVersionId() != null) items = listVersionItems(template.getId(), template.getCurrentVersionId());
            vo.setItems(items);
        }
        return vo;
    }

    private GeneralInspectionPlanVO toPlanVO(GeneralInspectionPlan plan) {
        GeneralInspectionPlanVO vo = new GeneralInspectionPlanVO();
        vo.setId(plan.getId());
        vo.setProjectId(plan.getProjectId());
        vo.setTemplateId(plan.getTemplateId());
        vo.setPlanCode(plan.getPlanCode());
        vo.setPlanName(plan.getPlanName());
        vo.setStatus(plan.getStatus());
        vo.setConfig(parseConfig(plan.getDraftConfigJson()));
        vo.setCurrentVersionId(plan.getCurrentVersionId());
        vo.setGeneratedThroughTime(plan.getGeneratedThroughTime());
        vo.setVersion(plan.getVersion());
        if (plan.getCurrentVersionId() != null) {
            GeneralInspectionPlanVersion version = planVersionMapper.selectById(plan.getCurrentVersionId());
            if (version != null) {
                vo.setCurrentVersionNo(version.getVersionNo());
                vo.setEffectiveTime(version.getEffectiveTime());
            }
        }
        return vo;
    }

    private GeneralInspectionTemplate requireTemplate(Long id) {
        GeneralInspectionTemplate template = templateMapper.selectOne(new LambdaQueryWrapper<GeneralInspectionTemplate>()
                .eq(GeneralInspectionTemplate::getId, id).eq(GeneralInspectionTemplate::getDeleted, 0).last("LIMIT 1"));
        if (template == null) throw BusinessException.notFound("巡检模板不存在");
        return template;
    }

    private GeneralInspectionTemplateVersion requirePublishedTemplateVersion(GeneralInspectionTemplate source) {
        GeneralInspectionTemplateVersion version = templateVersionMapper.selectOne(
                new LambdaQueryWrapper<GeneralInspectionTemplateVersion>()
                        .eq(GeneralInspectionTemplateVersion::getTemplateId, source.getId())
                        .le(GeneralInspectionTemplateVersion::getEffectiveTime, LocalDateTime.now())
                        .orderByDesc(GeneralInspectionTemplateVersion::getEffectiveTime)
                        .last("LIMIT 1"));
        if (version == null) throw new BusinessException("源模板尚无已生效的发布版本");
        return version;
    }

    private void requireTemplateManage(GeneralInspectionTemplate template, SysUser currentUser) {
        if ("COMPANY".equals(template.getScopeType())) permissionService.requirePlatformAdmin(currentUser);
        else permissionService.requireManage(template.getProjectId(), currentUser);
        if ("ARCHIVED".equals(template.getStatus())) throw new BusinessException("已归档模板不可修改");
    }

    private List<GeneralInspectionTemplateItem> listDraftItems(Long templateId) {
        return templateItemMapper.selectList(new LambdaQueryWrapper<GeneralInspectionTemplateItem>()
                .eq(GeneralInspectionTemplateItem::getTemplateId, templateId)
                .isNull(GeneralInspectionTemplateItem::getTemplateVersionId)
                .orderByAsc(GeneralInspectionTemplateItem::getSortOrder));
    }

    private List<GeneralInspectionTemplateItem> listVersionItems(Long templateId, Long versionId) {
        return templateItemMapper.selectList(new LambdaQueryWrapper<GeneralInspectionTemplateItem>()
                .eq(GeneralInspectionTemplateItem::getTemplateId, templateId)
                .eq(GeneralInspectionTemplateItem::getTemplateVersionId, versionId)
                .orderByAsc(GeneralInspectionTemplateItem::getSortOrder));
    }

    private int nextTemplateVersionNo(Long templateId) {
        GeneralInspectionTemplateVersion latest = templateVersionMapper.selectOne(
                new LambdaQueryWrapper<GeneralInspectionTemplateVersion>()
                        .eq(GeneralInspectionTemplateVersion::getTemplateId, templateId)
                        .orderByDesc(GeneralInspectionTemplateVersion::getVersionNo).last("LIMIT 1"));
        return latest == null ? 1 : latest.getVersionNo() + 1;
    }

    private int nextPlanVersionNo(Long planId) {
        GeneralInspectionPlanVersion latest = planVersionMapper.selectOne(
                new LambdaQueryWrapper<GeneralInspectionPlanVersion>()
                        .eq(GeneralInspectionPlanVersion::getPlanId, planId)
                        .orderByDesc(GeneralInspectionPlanVersion::getVersionNo).last("LIMIT 1"));
        return latest == null ? 1 : latest.getVersionNo() + 1;
    }

    private GeneralInspectionPlanConfig parseConfig(String json) {
        try {
            return objectMapper.readValue(json, GeneralInspectionPlanConfig.class);
        } catch (JsonProcessingException ex) {
            throw conflict("计划配置无法解析，请联系管理员");
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("配置序列化失败");
        }
    }

    private void applyTemplateDraft(GeneralInspectionTemplate template, GeneralInspectionTemplateSaveRequest request,
                                    SysUser user) {
        template.setTemplateName(trimRequired(request.getTemplateName(), "模板名称", 100));
        template.setCategoryName(trim(request.getCategoryName(), 100));
        template.setOverallPhotoMin(value(request.getOverallPhotoMin(), 0));
        template.setOverallPhotoMax(value(request.getOverallPhotoMax(), 9));
        template.setOverallRemarkRequired(Boolean.TRUE.equals(request.getOverallRemarkRequired()) ? 1 : 0);
        template.setRemark(trim(request.getRemark(), 1000));
        template.setUpdatedById(user.getId());
        template.setUpdatedByName(userName(user));
    }

    private void record(Long projectId, String businessType, Long businessId, String action, SysUser user,
                        String from, String to, String comment, String before, String after) {
        GeneralInspectionActionLog log = new GeneralInspectionActionLog();
        log.setProjectId(projectId == null ? 0L : projectId);
        log.setBusinessType(businessType);
        log.setBusinessId(businessId);
        log.setActionType(action);
        log.setOperatorId(user == null ? null : user.getId());
        log.setOperatorName(userName(user));
        log.setFromStatus(from);
        log.setToStatus(to);
        log.setComment(comment);
        log.setBeforeJson(before);
        log.setAfterJson(after);
        requireOne(actionLogMapper.insert(log), "通用巡检操作日志写入");
    }

    private String normalizeItemKey(String raw, int index) {
        String key = StringUtils.hasText(raw) ? raw.trim().toUpperCase() : String.format("ITEM_%02d", index + 1);
        if (!key.matches("[A-Z][A-Z0-9_-]{0,49}")) throw new BusinessException("检查项编码需为1至50位字母、数字、下划线或横线");
        return key;
    }

    private String uniqueCode(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }

    private String randomSceneCode() {
        byte[] bytes = new byte[24];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String trimRequired(String value, String label, int max) {
        if (!StringUtils.hasText(value)) throw new BusinessException(label + "不能为空");
        return trim(value, max);
    }

    private String trim(String value, int max) {
        if (!StringUtils.hasText(value)) return null;
        String result = value.trim();
        if (result.length() > max) throw new BusinessException("字段长度不能超过" + max + "个字符");
        return result;
    }

    private String joinIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return null;
        return ids.stream().filter(Objects::nonNull).distinct().map(String::valueOf).collect(Collectors.joining(","));
    }

    private List<Long> parseIds(String csv) {
        if (!StringUtils.hasText(csv)) return List.of();
        try {
            return Arrays.stream(csv.split(",")).map(String::trim).filter(StringUtils::hasText)
                    .map(Long::valueOf).distinct().toList();
        } catch (NumberFormatException ex) {
            throw conflict("历史附件标识格式异常");
        }
    }

    private int value(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private String userName(SysUser user) {
        if (user == null) return null;
        return StringUtils.hasText(user.getRealName()) ? user.getRealName().trim() : user.getUsername();
    }

    private Long projectId(GeneralInspectionTemplate template) {
        return template.getProjectId() == null ? 0L : template.getProjectId();
    }

    private void requireExpected(Integer actual, Integer expected, String message) {
        if (!Objects.equals(actual, expected)) throw conflict(message);
    }

    private void requireOne(int affected, String action) {
        if (affected != 1) throw conflict(action.contains("版本") || action.contains("变化") ? action : action + "未生效");
    }

    private BusinessException conflict(String message) {
        return BusinessException.of(409, message);
    }
}
