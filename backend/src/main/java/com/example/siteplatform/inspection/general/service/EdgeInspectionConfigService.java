package com.example.siteplatform.inspection.general.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.inspection.general.dto.EdgeInspectionPointSaveRequest;
import com.example.siteplatform.inspection.general.dto.EdgeInspectionSettingRequest;
import com.example.siteplatform.inspection.general.dto.GeneralInspectionPlanConfig;
import com.example.siteplatform.inspection.general.dto.GeneralInspectionPointActionRequest;
import com.example.siteplatform.inspection.general.entity.*;
import com.example.siteplatform.inspection.general.mapper.*;
import com.example.siteplatform.inspection.general.vo.EdgeInspectionPointTypeVO;
import com.example.siteplatform.inspection.general.vo.EdgeInspectionPointVO;
import com.example.siteplatform.inspection.general.vo.EdgeInspectionSettingVO;
import com.example.siteplatform.inspection.general.vo.GeneralInspectionUserOptionVO;
import com.example.siteplatform.project.constant.InspectionPermissionCodes;
import com.example.siteplatform.project.entity.SysUserProject;
import com.example.siteplatform.project.mapper.SysUserProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;

/**
 * Fixed edge-inspection configuration facade.
 *
 * <p>The underlying general_inspection tables remain an internal versioned
 * engine. This service intentionally exposes neither template editing nor
 * custom point categories, QR/public access, multiple plans, multiple slots,
 * backup users, nor per-point personnel overrides.</p>
 */
@Service
@RequiredArgsConstructor
public class EdgeInspectionConfigService {

    public static final String EDGE_PLAN_CODE = "EDGE_PROJECT_SCHEDULE";
    public static final String EDGE_TEMPLATE_PREFIX = "EDGE_";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final String EDGE_SLOT_CODE = "EDGE_SLOT";
    private static final LinkedHashMap<String, String> POINT_TYPES = new LinkedHashMap<>();

    static {
        POINT_TYPES.put("FLOOR_BALCONY_EAVE_EDGE", "楼层、阳台及挑檐边");
        POINT_TYPES.put("STAIR_PLATFORM_FLIGHT_EDGE", "楼梯口、平台及梯段边");
        POINT_TYPES.put("ROOF_EDGE", "屋面边");
        POINT_TYPES.put("PIT_TRENCH_EDGE", "基坑、沟槽边");
        POINT_TYPES.put("OPENING_RESERVED_HOLE", "洞口、预留洞");
        POINT_TYPES.put("ELEVATOR_SHAFT", "电梯井口、井道");
        POINT_TYPES.put("HOIST_LANDING_PLATFORM", "升降机、物料提升机停层平台");
        POINT_TYPES.put("LOADING_UNLOADING_PLATFORM", "接料、卸料平台");
    }

    private final GeneralInspectionPermissionService permissionService;
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
    private final SysUserProjectMapper userProjectMapper;
    private final ObjectMapper objectMapper;

    public List<EdgeInspectionPointTypeVO> listPointTypes(Long projectId, SysUser user) {
        permissionService.requireView(projectId, user);
        List<EdgeInspectionPointTypeVO> result = new ArrayList<>();
        for (Map.Entry<String, String> type : POINT_TYPES.entrySet()) {
            GeneralInspectionTemplate template = findSystemTemplate(type.getKey());
            if (template == null || template.getCurrentVersionId() == null) {
                throw conflict("临边巡检系统检查表缺失：" + type.getValue());
            }
            EdgeInspectionPointTypeVO vo = new EdgeInspectionPointTypeVO();
            vo.setCode(type.getKey());
            vo.setName(type.getValue());
            vo.setTemplateName(template.getTemplateName());
            vo.setItems(templateItemMapper.selectList(new LambdaQueryWrapper<GeneralInspectionTemplateItem>()
                            .eq(GeneralInspectionTemplateItem::getTemplateVersionId, template.getCurrentVersionId())
                            .orderByAsc(GeneralInspectionTemplateItem::getSortOrder)).stream()
                    .map(this::toPointTypeItem).toList());
            result.add(vo);
        }
        return result;
    }

    public List<EdgeInspectionPointVO> listPoints(Long projectId, String status, SysUser user) {
        permissionService.requireView(projectId, user);
        LambdaQueryWrapper<GeneralInspectionPoint> query = new LambdaQueryWrapper<GeneralInspectionPoint>()
                .eq(GeneralInspectionPoint::getProjectId, projectId)
                .eq(GeneralInspectionPoint::getDeleted, 0)
                .isNotNull(GeneralInspectionPoint::getPointTypeCode);
        if (StringUtils.hasText(status)) {
            String normalized = normalizeStatus(status);
            query.eq(GeneralInspectionPoint::getStatus, normalized);
        }
        return pointMapper.selectList(query.orderByAsc(GeneralInspectionPoint::getPointCode))
                .stream().map(this::toPointVO).toList();
    }

    @Transactional
    public EdgeInspectionPointVO savePoint(Long id, EdgeInspectionPointSaveRequest request, SysUser user) {
        String typeCode = normalizePointType(request.getPointTypeCode());
        String typeName = POINT_TYPES.get(typeCode);
        GeneralInspectionPointCategory category = requireSystemCategory(typeCode);
        GeneralInspectionPoint point;
        String action;
        if (id == null) {
            permissionService.requireManage(request.getProjectId(), user);
            if (request.getExpectedVersion() != null && request.getExpectedVersion() != 0) {
                throw conflict("新建点位版本必须为0");
            }
            point = new GeneralInspectionPoint();
            point.setProjectId(request.getProjectId());
            point.setPointCode(newPointCode());
            point.setStatus("ACTIVE");
            point.setEdgeActiveSinceTime(LocalDateTime.now());
            point.setVersion(0);
            point.setCreatedById(user.getId());
            point.setCreatedByName(userName(user));
            point.setDeleted(0);
            action = "CREATE";
        } else {
            point = pointMapper.selectByIdForUpdate(id);
            if (point == null || Integer.valueOf(1).equals(point.getDeleted())
                    || !StringUtils.hasText(point.getPointTypeCode())) {
                throw BusinessException.notFound("临边点位不存在");
            }
            permissionService.requireManage(point.getProjectId(), user);
            if (!Objects.equals(point.getProjectId(), request.getProjectId())) {
                throw new BusinessException("点位所属项目不可改变");
            }
            requireExpected(point.getVersion(), request.getExpectedVersion(), "点位版本已变化，请刷新后重试");
            if (!Objects.equals(point.getPointTypeCode(), typeCode)
                    && hasGeneratedTasks(point.getId())) {
                throw conflict("点位已产生巡检任务，类型不可修改；请停用后重新创建点位");
            }
            point.setVersion(value(point.getVersion()) + 1);
            action = "UPDATE";
        }
        point.setPointName(trimRequired(request.getPointName(), "点位名称", 100));
        point.setPointTypeCode(typeCode);
        point.setPointTypeName(typeName);
        point.setCategoryId(category.getId());
        point.setCategoryName(typeName);
        point.setAreaName(null);
        point.setBuildingName(trim(request.getBuildingName(), 100));
        point.setFloorName(trim(request.getFloorName(), 100));
        point.setLocationDesc(trim(request.getLocationDesc(), 300));
        point.setRiskNote(null);
        point.setReferencePhotoFileIds(null);
        point.setQrEnabled(0);
        point.setPublicCode(null);
        point.setQrVersion(0);
        point.setPublicAccessEnabled(0);
        point.setUpdatedById(user.getId());
        point.setUpdatedByName(userName(user));
        try {
            requireOne(id == null ? pointMapper.insert(point) : pointMapper.updateById(point),
                    id == null ? "临边点位新增" : "临边点位更新");
        } catch (DuplicateKeyException ex) {
            throw conflict("临边点位编码冲突，请重试");
        }
        record(point.getProjectId(), "POINT", point.getId(), action, user, null, point.getStatus(), null);
        return toPointVO(point);
    }

    @Transactional
    public EdgeInspectionPointVO changePointStatus(Long id, String target,
                                                    GeneralInspectionPointActionRequest request, SysUser user) {
        GeneralInspectionPoint point = pointMapper.selectByIdForUpdate(id);
        if (point == null || Integer.valueOf(1).equals(point.getDeleted())
                || !StringUtils.hasText(point.getPointTypeCode())) {
            throw BusinessException.notFound("临边点位不存在");
        }
        permissionService.requireManage(point.getProjectId(), user);
        requireExpected(point.getVersion(), request.getExpectedVersion(), "点位版本已变化，请刷新后重试");
        String status = normalizeStatus(target);
        String reason = trimRequired(request.getReason(), "状态变更原因", 500);
        String from = point.getStatus();
        if (Objects.equals(from, status)) return toPointVO(point);
        requirePointStatusTransition(from, status);
        point.setStatus(status);
        point.setVersion(value(point.getVersion()) + 1);
        point.setUpdatedById(user.getId());
        point.setUpdatedByName(userName(user));
        requireOne(pointMapper.updateById(point), "临边点位状态更新");
        if ("INACTIVE".equals(status)) {
            for (GeneralInspectionTask task : taskMapper.selectPendingByPointForUpdate(point.getId())) {
                if (!StringUtils.hasText(task.getPointTypeCode())) continue;
                task.setStatus("CANCELLED");
                task.setCancelReason("点位停用：" + reason);
                task.setCancelledById(user.getId());
                task.setCancelledByName(userName(user));
                task.setCancelledTime(LocalDateTime.now());
                task.setVersion(value(task.getVersion()) + 1);
                requireOne(taskMapper.updateById(task), "点位停用任务取消");
                record(task.getProjectId(), "TASK", task.getId(), "CANCEL", user,
                        "PENDING", "CANCELLED", task.getCancelReason());
            }
        }
        record(point.getProjectId(), "POINT", point.getId(), "STATUS", user, from, status, reason);
        return toPointVO(point);
    }

    public EdgeInspectionSettingVO getSetting(Long projectId, SysUser user) {
        permissionService.requireView(projectId, user);
        GeneralInspectionPlan plan = planMapper.selectOne(new LambdaQueryWrapper<GeneralInspectionPlan>()
                .eq(GeneralInspectionPlan::getProjectId, projectId)
                .eq(GeneralInspectionPlan::getPlanCode, EDGE_PLAN_CODE)
                .eq(GeneralInspectionPlan::getDeleted, 0).last("LIMIT 1"));
        return plan == null ? emptySetting(projectId) : toSettingVO(plan);
    }

    @Transactional
    public EdgeInspectionSettingVO saveSetting(Long projectId, EdgeInspectionSettingRequest request, SysUser user) {
        permissionService.requireManage(projectId, user);
        GeneralInspectionPlan plan = planMapper.selectEdgePlanForUpdate(projectId);
        LocalDateTime savedAt = LocalDateTime.now(BUSINESS_ZONE);
        GeneralInspectionPlanConfig previousConfig = plan == null ? null : parseConfig(plan.getDraftConfigJson());
        GeneralInspectionPlanConfig config = buildAndValidateConfig(projectId, request);
        boolean previousReminderEnabled = previousConfig != null
                && Boolean.TRUE.equals(previousConfig.getSubmissionReminderEnabled());
        boolean reminderEnabled = request.getSubmissionReminderEnabled() == null
                ? previousReminderEnabled : Boolean.TRUE.equals(request.getSubmissionReminderEnabled());
        config.setSubmissionReminderEnabled(reminderEnabled);
        config.setReminderEffectiveTime(resolveReminderEffectiveTime(
                previousReminderEnabled,
                previousConfig == null ? null : previousConfig.getReminderEffectiveTime(),
                request.getSubmissionReminderEnabled(), savedAt));
        boolean resumed = plan != null && !"PUBLISHED".equals(plan.getStatus())
                && Boolean.TRUE.equals(request.getEnabled());
        LocalDateTime previousCursor = plan == null ? null : plan.getGeneratedThroughTime();
        LocalDateTime previousLowerBound = plan == null ? null : plan.getEdgeGenerationLowerBoundTime();
        if (plan == null) {
            if (!Integer.valueOf(0).equals(request.getExpectedVersion())) {
                throw conflict("巡检设置版本已变化，请刷新后重试");
            }
            GeneralInspectionTemplate routingTemplate = requireRoutingTemplate();
            plan = new GeneralInspectionPlan();
            plan.setProjectId(projectId);
            plan.setTemplateId(routingTemplate.getId());
            plan.setPlanCode(EDGE_PLAN_CODE);
            plan.setPlanName("临边巡检周期设置");
            plan.setStatus(Boolean.TRUE.equals(request.getEnabled()) ? "PUBLISHED" : "PAUSED");
            plan.setDraftConfigJson(json(config));
            // 新计划从保存时刻开始生成，不能因开始日期较早而补出历史逾期任务。
            plan.setGeneratedThroughTime(savedAt);
            plan.setEdgeGenerationLowerBoundTime(savedAt);
            plan.setVersion(0);
            plan.setCreatedById(user.getId());
            plan.setCreatedByName(userName(user));
            plan.setUpdatedById(user.getId());
            plan.setUpdatedByName(userName(user));
            plan.setDeleted(0);
            try {
                requireOne(planMapper.insert(plan), "临边巡检设置新增");
            } catch (DuplicateKeyException ex) {
                throw conflict("巡检设置已被并发创建，请刷新后重试");
            }
        } else {
            requireExpected(plan.getVersion(), request.getExpectedVersion(), "巡检设置版本已变化，请刷新后重试");
            if ("ARCHIVED".equals(plan.getStatus())) throw conflict("临边巡检设置已归档，不能恢复");
            plan.setStatus(Boolean.TRUE.equals(request.getEnabled()) ? "PUBLISHED" : "PAUSED");
            plan.setDraftConfigJson(json(config));
            // 普通保存保留游标，以维持已启用期间的停机补偿；暂停后恢复才把
            // 下界推进至恢复时刻，避免补生成暂停期间的逾期任务。
            plan.setGeneratedThroughTime(settingSaveCursor(previousCursor, resumed, savedAt));
            plan.setEdgeGenerationLowerBoundTime(settingSaveCursor(previousLowerBound, resumed, savedAt));
            plan.setVersion(value(plan.getVersion()) + 1);
            plan.setUpdatedById(user.getId());
            plan.setUpdatedByName(userName(user));
            requireOne(planMapper.updateById(plan), "临边巡检设置更新");
        }
        GeneralInspectionPlanVersion version = new GeneralInspectionPlanVersion();
        version.setPlanId(plan.getId());
        version.setVersionNo(nextVersionNo(plan.getId()));
        version.setEffectiveTime(savedAt);
        version.setConfigJson(plan.getDraftConfigJson());
        version.setPublishedById(user.getId());
        version.setPublishedByName(userName(user));
        requireOne(planVersionMapper.insert(version), "临边巡检设置版本保存");
        plan.setCurrentVersionId(version.getId());
        requireOne(planMapper.updateById(plan), "临边巡检设置当前版本更新");
        record(projectId, "PLAN", plan.getId(), "SAVE_EDGE_SETTING", user, null, plan.getStatus(),
                "内部版本V" + version.getVersionNo());
        if (resumed) {
            record(projectId, "PLAN", plan.getId(), "PLAN_RESUME_GENERATION_BOUND", user,
                    String.valueOf(previousLowerBound), String.valueOf(plan.getEdgeGenerationLowerBoundTime()),
                    "临边巡检设置恢复，仅生成恢复时刻之后的任务；游标=" + plan.getGeneratedThroughTime());
        }
        return toSettingVO(plan);
    }

    static LocalDateTime advanceLowerBound(LocalDateTime current, LocalDateTime candidate) {
        if (candidate == null) return current;
        return current == null || current.isBefore(candidate) ? candidate : current;
    }

    static LocalDateTime settingSaveCursor(LocalDateTime current, boolean resumed, LocalDateTime savedAt) {
        return resumed ? advanceLowerBound(current, savedAt) : current;
    }

    public List<GeneralInspectionUserOptionVO> listUserOptions(Long projectId, SysUser user) {
        permissionService.requireManage(projectId, user);
        return userProjectMapper.selectList(new LambdaQueryWrapper<SysUserProject>()
                        .eq(SysUserProject::getProjectId, projectId)
                        .eq(SysUserProject::getStatus, "ACTIVE"))
                .stream().map(SysUserProject::getUserId).filter(Objects::nonNull).distinct()
                .map(userMapper::selectById).filter(this::activeUser)
                .map(candidate -> {
                    GeneralInspectionUserOptionVO vo = new GeneralInspectionUserOptionVO();
                    vo.setUserId(candidate.getId());
                    vo.setUserName(userName(candidate));
                    vo.setCanSubmit(hasEdgePermission(projectId, candidate.getId(), InspectionPermissionCodes.EDGE_INSPECTION_SUBMIT));
                    vo.setCanRectify(hasEdgePermission(projectId, candidate.getId(), InspectionPermissionCodes.EDGE_INSPECTION_RECTIFY));
                    vo.setCanReview(hasEdgePermission(projectId, candidate.getId(), InspectionPermissionCodes.EDGE_INSPECTION_REVIEW));
                    return vo;
                })
                .filter(vo -> Boolean.TRUE.equals(vo.getCanSubmit()) || Boolean.TRUE.equals(vo.getCanRectify())
                        || Boolean.TRUE.equals(vo.getCanReview()))
                .sorted(Comparator.comparing(GeneralInspectionUserOptionVO::getUserName,
                        Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    private GeneralInspectionPlanConfig buildAndValidateConfig(Long projectId, EdgeInspectionSettingRequest request) {
        String frequency = request.getFrequency().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("DAILY", "WEEKLY", "MONTHLY").contains(frequency)) {
            throw new BusinessException("巡检周期仅支持每天、每周或每月");
        }
        List<Integer> weekdays = normalizeWeekdays(request.getWeekdays());
        if ("WEEKLY".equals(frequency) && weekdays.isEmpty()) throw new BusinessException("每周巡检必须选择星期");
        if (!"WEEKLY".equals(frequency) && !weekdays.isEmpty()) throw new BusinessException("仅每周巡检可以选择星期");
        List<Integer> monthDays = List.of();
        if ("MONTHLY".equals(frequency)) {
            boolean monthEnd = Boolean.TRUE.equals(request.getMonthEnd());
            if (monthEnd == (request.getMonthDay() != null)) throw new BusinessException("每月巡检必须且只能选择指定日期或月末");
            if (!monthEnd && (request.getMonthDay() < 1 || request.getMonthDay() > 31)) {
                throw new BusinessException("每月巡检日期必须为1至31日");
            }
            monthDays = List.of(monthEnd ? -1 : request.getMonthDay());
        } else if (request.getMonthDay() != null || Boolean.TRUE.equals(request.getMonthEnd())) {
            throw new BusinessException("仅每月巡检可以设置指定日期或月末");
        }
        if (request.getEffectiveStart().isBefore(LocalDate.now().minusYears(1))) {
            throw new BusinessException("开始日期不能早于一年前");
        }
        int dueDayOffset = request.getDueTime().isAfter(request.getStartTime()) ? 0 : 1;
        long minutes = java.time.Duration.between(request.getStartTime(), request.getDueTime()).toMinutes();
        if (dueDayOffset == 1) minutes += 24L * 60L;
        if (minutes <= 0 || minutes > 24L * 60L) throw new BusinessException("巡检时段必须大于0且不超过24小时");
        requireRoleUser(projectId, request.getAssigneeId(), InspectionPermissionCodes.EDGE_INSPECTION_SUBMIT, "巡检人");
        requireRoleUser(projectId, request.getRectifierId(), InspectionPermissionCodes.EDGE_INSPECTION_RECTIFY, "整改人");
        requireRoleUser(projectId, request.getReviewerId(), InspectionPermissionCodes.EDGE_INSPECTION_REVIEW, "复查人");

        GeneralInspectionPlanConfig.Slot slot = new GeneralInspectionPlanConfig.Slot();
        slot.setSlotCode(EDGE_SLOT_CODE);
        slot.setSlotName("临边巡检时段");
        slot.setStartTime(request.getStartTime());
        slot.setDueTime(request.getDueTime());
        slot.setDueDayOffset(dueDayOffset);

        List<GeneralInspectionPlanConfig.PointAssignment> points = pointMapper.selectList(
                        new LambdaQueryWrapper<GeneralInspectionPoint>()
                                .eq(GeneralInspectionPoint::getProjectId, projectId)
                                .eq(GeneralInspectionPoint::getStatus, "ACTIVE")
                                .eq(GeneralInspectionPoint::getDeleted, 0)
                                .isNotNull(GeneralInspectionPoint::getPointTypeCode))
                .stream().map(point -> {
                    GeneralInspectionPlanConfig.PointAssignment assignment = new GeneralInspectionPlanConfig.PointAssignment();
                    assignment.setPointId(point.getId());
                    return assignment;
                }).toList();

        GeneralInspectionPlanConfig config = new GeneralInspectionPlanConfig();
        config.setFrequency(frequency);
        config.setWeekdays(weekdays);
        config.setMonthDays(monthDays);
        config.setEffectiveStart(request.getEffectiveStart());
        config.setEarlyMinutes(0);
        config.setAssigneeId(request.getAssigneeId());
        config.setBackupAssigneeIds(List.of());
        config.setDefaultRectifierId(request.getRectifierId());
        config.setRectificationDays(request.getRectificationDays());
        config.setReviewerId(request.getReviewerId());
        config.setBackupReviewerIds(List.of());
        config.setSlots(List.of(slot));
        config.setPoints(points);
        return config;
    }

    private EdgeInspectionSettingVO toSettingVO(GeneralInspectionPlan plan) {
        GeneralInspectionPlanConfig config = parseConfig(plan.getDraftConfigJson());
        EdgeInspectionSettingVO vo = new EdgeInspectionSettingVO();
        vo.setId(plan.getId());
        vo.setProjectId(plan.getProjectId());
        vo.setFrequency(config.getFrequency());
        vo.setWeekdays(config.getWeekdays() == null ? List.of() : config.getWeekdays());
        List<Integer> monthDays = config.getMonthDays() == null ? List.of() : config.getMonthDays();
        vo.setMonthEnd(monthDays.contains(-1));
        vo.setMonthDay(monthDays.stream().filter(day -> day != null && day > 0).findFirst().orElse(null));
        vo.setEffectiveStart(config.getEffectiveStart());
        GeneralInspectionPlanConfig.Slot slot = config.getSlots().get(0);
        vo.setStartTime(slot.getStartTime());
        vo.setDueTime(slot.getDueTime());
        vo.setAssigneeId(config.getAssigneeId());
        vo.setAssigneeName(userName(userMapper.selectById(config.getAssigneeId())));
        vo.setRectifierId(config.getDefaultRectifierId());
        vo.setRectifierName(userName(userMapper.selectById(config.getDefaultRectifierId())));
        vo.setReviewerId(config.getReviewerId());
        vo.setReviewerName(userName(userMapper.selectById(config.getReviewerId())));
        vo.setRectificationDays(config.getRectificationDays());
        vo.setEnabled("PUBLISHED".equals(plan.getStatus()));
        vo.setSubmissionReminderEnabled(Boolean.TRUE.equals(config.getSubmissionReminderEnabled()));
        vo.setReminderEffectiveTime(config.getReminderEffectiveTime());
        vo.setNextReminderTime("PUBLISHED".equals(plan.getStatus())
                ? nextReminderTime(config, LocalDateTime.now(BUSINESS_ZONE)) : null);
        vo.setStatus(plan.getStatus());
        vo.setVersion(plan.getVersion());
        return vo;
    }

    private EdgeInspectionSettingVO emptySetting(Long projectId) {
        EdgeInspectionSettingVO vo = new EdgeInspectionSettingVO();
        vo.setProjectId(projectId);
        vo.setFrequency("DAILY");
        vo.setWeekdays(List.of());
        vo.setMonthEnd(false);
        vo.setEffectiveStart(LocalDate.now());
        vo.setStartTime(LocalTime.of(8, 0));
        vo.setDueTime(LocalTime.of(18, 0));
        vo.setRectificationDays(3);
        vo.setEnabled(false);
        vo.setSubmissionReminderEnabled(false);
        vo.setStatus("UNCONFIGURED");
        vo.setVersion(0);
        return vo;
    }

    private EdgeInspectionPointVO toPointVO(GeneralInspectionPoint point) {
        EdgeInspectionPointVO vo = new EdgeInspectionPointVO();
        vo.setId(point.getId());
        vo.setProjectId(point.getProjectId());
        vo.setPointCode(point.getPointCode());
        vo.setPointName(point.getPointName());
        vo.setPointTypeCode(point.getPointTypeCode());
        vo.setPointTypeName(point.getPointTypeName());
        vo.setBuildingName(point.getBuildingName());
        vo.setFloorName(point.getFloorName());
        vo.setLocationDesc(point.getLocationDesc());
        vo.setStatus(point.getStatus());
        vo.setVersion(point.getVersion());
        vo.setHasGeneratedTasks(hasGeneratedTasks(point.getId()));
        vo.setCreateTime(point.getCreateTime());
        vo.setUpdateTime(point.getUpdateTime());
        return vo;
    }

    private EdgeInspectionPointTypeVO.Item toPointTypeItem(GeneralInspectionTemplateItem source) {
        EdgeInspectionPointTypeVO.Item item = new EdgeInspectionPointTypeVO.Item();
        item.setItemKey(source.getItemKey());
        item.setItemName(source.getItemName());
        item.setGuidance(source.getGuidance());
        item.setStandardReference(source.getStandardReference());
        item.setSortOrder(source.getSortOrder());
        return item;
    }

    private GeneralInspectionTemplate findSystemTemplate(String typeCode) {
        return templateMapper.selectOne(new LambdaQueryWrapper<GeneralInspectionTemplate>()
                .eq(GeneralInspectionTemplate::getScopeType, "SYSTEM")
                .isNull(GeneralInspectionTemplate::getProjectId)
                .eq(GeneralInspectionTemplate::getTemplateCode, EDGE_TEMPLATE_PREFIX + typeCode)
                .eq(GeneralInspectionTemplate::getStatus, "PUBLISHED")
                .eq(GeneralInspectionTemplate::getDeleted, 0).last("LIMIT 1"));
    }

    private GeneralInspectionTemplate requireRoutingTemplate() {
        GeneralInspectionTemplate template = findSystemTemplate(POINT_TYPES.keySet().iterator().next());
        if (template == null) throw conflict("临边巡检系统检查表尚未初始化");
        return template;
    }

    private GeneralInspectionPointCategory requireSystemCategory(String typeCode) {
        GeneralInspectionPointCategory category = categoryMapper.selectOne(
                new LambdaQueryWrapper<GeneralInspectionPointCategory>()
                        .isNull(GeneralInspectionPointCategory::getProjectId)
                        .eq(GeneralInspectionPointCategory::getCategoryCode, typeCode)
                        .eq(GeneralInspectionPointCategory::getBuiltin, 1)
                        .eq(GeneralInspectionPointCategory::getEnabled, 1).last("LIMIT 1"));
        if (category == null) throw conflict("临边巡检固定点位类型尚未初始化：" + typeCode);
        return category;
    }

    private void requireRoleUser(Long projectId, Long userId, String permissionCode, String label) {
        if (userId == null || !permissionService.hasActiveProjectAccess(projectId, userId)) {
            throw new BusinessException(label + "必须是项目有效成员");
        }
        SysUser candidate = userMapper.selectById(userId);
        if (!activeUser(candidate) || !hasEdgePermission(projectId, userId, permissionCode)) {
            throw new BusinessException(label + "账号无效或缺少对应临边巡检权限");
        }
    }

    private boolean hasEdgePermission(Long projectId, Long userId, String permissionCode) {
        return permissionService.hasInspectionPermission(projectId, userId, permissionCode);
    }

    private boolean activeUser(SysUser user) {
        return user != null && Integer.valueOf(1).equals(user.getStatus())
                && !Integer.valueOf(1).equals(user.getDeleted());
    }

    private boolean hasGeneratedTasks(Long pointId) {
        if (pointId == null) return false;
        Long count = taskMapper.selectCount(new LambdaQueryWrapper<GeneralInspectionTask>()
                .eq(GeneralInspectionTask::getPointId, pointId));
        return count != null && count > 0;
    }

    private int nextVersionNo(Long planId) {
        GeneralInspectionPlanVersion latest = planVersionMapper.selectOne(
                new LambdaQueryWrapper<GeneralInspectionPlanVersion>()
                        .eq(GeneralInspectionPlanVersion::getPlanId, planId)
                        .orderByDesc(GeneralInspectionPlanVersion::getVersionNo).last("LIMIT 1"));
        return latest == null ? 1 : latest.getVersionNo() + 1;
    }

    private List<Integer> normalizeWeekdays(List<Integer> source) {
        if (source == null) return List.of();
        List<Integer> result = source.stream().filter(Objects::nonNull).distinct().sorted().toList();
        if (result.size() != source.size() || result.stream().anyMatch(day -> day < 1 || day > 7)) {
            throw new BusinessException("星期必须是1至7且不能重复");
        }
        return result;
    }

    private String normalizePointType(String raw) {
        String code = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (!POINT_TYPES.containsKey(code)) throw new BusinessException("不支持的临边点位类型");
        return code;
    }

    private String normalizeStatus(String raw) {
        String status = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ACTIVE", "INACTIVE").contains(status)) throw new BusinessException("点位状态仅支持启用或停用");
        return status;
    }

    static void requirePointStatusTransition(String from, String to) {
        if (!"ACTIVE".equals(from) || !"INACTIVE".equals(to)) {
            throw BusinessException.of(409, "临边点位停用后不可恢复；需要继续巡检时请新建点位");
        }
    }

    private String newPointCode() {
        return "EDGE_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase(Locale.ROOT);
    }

    private GeneralInspectionPlanConfig parseConfig(String json) {
        try {
            return objectMapper.readValue(json, GeneralInspectionPlanConfig.class);
        } catch (JsonProcessingException ex) {
            throw conflict("临边巡检设置快照无法解析");
        }
    }

    static LocalDateTime resolveReminderEffectiveTime(boolean previousEnabled, LocalDateTime previousEffectiveTime,
                                                      Boolean requestedEnabled, LocalDateTime savedAt) {
        boolean enabled = requestedEnabled == null ? previousEnabled : Boolean.TRUE.equals(requestedEnabled);
        if (!enabled) return null;
        if (!previousEnabled) return savedAt;
        return previousEffectiveTime == null ? savedAt : previousEffectiveTime;
    }

    static LocalDateTime nextReminderTime(GeneralInspectionPlanConfig config, LocalDateTime now) {
        if (config == null || now == null || !Boolean.TRUE.equals(config.getSubmissionReminderEnabled())
                || config.getReminderEffectiveTime() == null || config.getSlots() == null
                || config.getSlots().isEmpty() || config.getEffectiveStart() == null) {
            return null;
        }
        LocalDate start = now.toLocalDate().minusDays(1);
        if (start.isBefore(config.getEffectiveStart())) start = config.getEffectiveStart();
        for (LocalDate date = start; !date.isAfter(start.plusDays(370)); date = date.plusDays(1)) {
            if (!matchesSchedule(config, date)) continue;
            for (GeneralInspectionPlanConfig.Slot slot : config.getSlots()) {
                if (slot == null || slot.getDueTime() == null) continue;
                int offset = slot.getDueDayOffset() == null ? 0 : slot.getDueDayOffset();
                LocalDateTime due = LocalDateTime.of(date.plusDays(offset), slot.getDueTime());
                if (due.isAfter(now) && due.isAfter(config.getReminderEffectiveTime())) return due;
            }
        }
        return null;
    }

    private static boolean matchesSchedule(GeneralInspectionPlanConfig config, LocalDate date) {
        if (date.isBefore(config.getEffectiveStart())
                || config.getEffectiveEnd() != null && date.isAfter(config.getEffectiveEnd())) return false;
        return switch (config.getFrequency()) {
            case "DAILY" -> true;
            case "WEEKLY" -> config.getWeekdays() != null
                    && config.getWeekdays().contains(date.getDayOfWeek().getValue());
            case "MONTHLY" -> config.getMonthDays() != null
                    && (config.getMonthDays().contains(date.getDayOfMonth())
                    || config.getMonthDays().contains(-1)
                    && date.getDayOfMonth() == date.lengthOfMonth());
            default -> false;
        };
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("临边巡检设置序列化失败");
        }
    }

    private void record(Long projectId, String businessType, Long businessId, String action, SysUser user,
                        String from, String to, String comment) {
        GeneralInspectionActionLog log = new GeneralInspectionActionLog();
        log.setProjectId(projectId);
        log.setBusinessType(businessType);
        log.setBusinessId(businessId);
        log.setActionType(action);
        log.setOperatorId(user.getId());
        log.setOperatorName(userName(user));
        log.setFromStatus(from);
        log.setToStatus(to);
        log.setComment(comment);
        requireOne(actionLogMapper.insert(log), "临边巡检操作日志写入");
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

    private String userName(SysUser user) {
        if (user == null) return null;
        return StringUtils.hasText(user.getRealName()) ? user.getRealName().trim() : user.getUsername();
    }

    private int value(Integer actual) {
        return actual == null ? 0 : actual;
    }

    private void requireExpected(Integer actual, Integer expected, String message) {
        if (!Objects.equals(actual, expected)) throw conflict(message);
    }

    private void requireOne(int affected, String action) {
        if (affected != 1) throw conflict(action + "未生效");
    }

    private BusinessException conflict(String message) {
        return BusinessException.of(409, message);
    }
}
