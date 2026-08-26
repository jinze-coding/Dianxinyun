package com.example.siteplatform.inspection.general.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.inspection.general.dto.GeneralInspectionPlanConfig;
import com.example.siteplatform.inspection.general.entity.*;
import com.example.siteplatform.inspection.general.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeneralInspectionTaskGenerationService {

    private final GeneralInspectionProjectSettingMapper settingMapper;
    private final GeneralInspectionPlanMapper planMapper;
    private final GeneralInspectionPlanVersionMapper planVersionMapper;
    private final GeneralInspectionTemplateMapper templateMapper;
    private final GeneralInspectionTemplateVersionMapper templateVersionMapper;
    private final GeneralInspectionTemplateItemMapper templateItemMapper;
    private final GeneralInspectionPointMapper pointMapper;
    private final GeneralInspectionTaskMapper taskMapper;
    private final GeneralInspectionTaskItemMapper taskItemMapper;
    private final SysUserMapper userMapper;
    private final GeneralInspectionPermissionService permissionService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    @Scheduled(cron = "${general-inspection.task-generation-cron:0 */5 * * * ?}")
    public void generateScheduledTasks() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime horizon = now.plusHours(25);
        List<Long> enabledProjects = settingMapper.selectList(
                        new LambdaQueryWrapper<GeneralInspectionProjectSetting>()
                                .eq(GeneralInspectionProjectSetting::getEnabled, 1))
                .stream().map(GeneralInspectionProjectSetting::getProjectId).toList();
        if (enabledProjects.isEmpty()) return;
        List<GeneralInspectionPlan> plans = planMapper.selectList(new LambdaQueryWrapper<GeneralInspectionPlan>()
                .in(GeneralInspectionPlan::getProjectId, enabledProjects)
                .eq(GeneralInspectionPlan::getPlanCode, EdgeInspectionConfigService.EDGE_PLAN_CODE)
                .eq(GeneralInspectionPlan::getStatus, "PUBLISHED")
                .eq(GeneralInspectionPlan::getDeleted, 0));
        for (GeneralInspectionPlan plan : plans) {
            try {
                // @Scheduled 与 generatePlan 位于同一 Bean，直接自调用不会经过
                // @Transactional 代理；显式模板确保每个计划的行锁、任务/快照和游标原子提交。
                transactionTemplate.execute(status -> generatePlan(plan.getId(), now, horizon));
            } catch (RuntimeException ex) {
                log.error("临边巡检计划任务生成失败，planId={}", plan.getId(), ex);
            }
        }
    }

    @Transactional
    public int generatePlan(Long planId, LocalDateTime now, LocalDateTime horizon) {
        GeneralInspectionPlan plan = planMapper.selectByIdForUpdate(planId);
        if (plan == null || !"PUBLISHED".equals(plan.getStatus()) || !Integer.valueOf(0).equals(plan.getDeleted())) return 0;
        GeneralInspectionProjectSetting setting = settingMapper.selectById(plan.getProjectId());
        if (setting == null || !Integer.valueOf(1).equals(setting.getEnabled())) return 0;

        List<GeneralInspectionPlanVersion> versions = planVersionMapper.selectList(
                new LambdaQueryWrapper<GeneralInspectionPlanVersion>()
                        .eq(GeneralInspectionPlanVersion::getPlanId, planId)
                        .le(GeneralInspectionPlanVersion::getEffectiveTime, horizon)
                        .orderByAsc(GeneralInspectionPlanVersion::getEffectiveTime));
        if (versions.isEmpty()) return 0;
        Map<Long, GeneralInspectionPlanConfig> configs = versions.stream().collect(Collectors.toMap(
                GeneralInspectionPlanVersion::getId, version -> parseConfig(version.getConfigJson()), (a, b) -> b,
                LinkedHashMap::new));

        LocalDateTime generationLowerBound = plan.getEdgeGenerationLowerBoundTime();
        boolean edgePlan = EdgeInspectionConfigService.EDGE_PLAN_CODE.equals(plan.getPlanCode());
        LocalDate startDate = calculateCatchupStart(plan, configs.values(), now);
        LocalDate endDate = horizon.toLocalDate().plusDays(1);
        int created = 0;
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            for (GeneralInspectionPlanVersion version : versions) {
                GeneralInspectionPlanConfig config = configs.get(version.getId());
                if (!matches(config, date)) continue;
                for (GeneralInspectionPlanConfig.Slot slot : config.getSlots()) {
                    LocalDateTime taskStart = LocalDateTime.of(date, slot.getStartTime());
                    if (!eligibleForGenerationLowerBound(edgePlan, generationLowerBound, taskStart)) continue;
                    GeneralInspectionPlanVersion effectiveVersion = latestPlanVersion(versions, taskStart);
                    if (effectiveVersion == null || !Objects.equals(effectiveVersion.getId(), version.getId())) continue;
                    if (taskStart.isAfter(horizon)) continue;
                    for (GeneralInspectionPlanConfig.PointAssignment assignment : assignments(plan, config)) {
                        if (materialize(plan, version, config, slot, assignment, date, taskStart)) created++;
                    }
                }
            }
        }
        int updated = planMapper.update(null, new LambdaUpdateWrapper<GeneralInspectionPlan>()
                .eq(GeneralInspectionPlan::getId, planId)
                .set(GeneralInspectionPlan::getGeneratedThroughTime, horizon));
        if (updated != 1) throw conflict("临边巡检任务生成游标更新未生效");
        return created;
    }

    public List<Map<String, Object>> preview(GeneralInspectionPlanConfig config, LocalDateTime from, int days) {
        if (days < 1 || days > 31) throw new BusinessException("预览天数必须为1至31天");
        List<Map<String, Object>> rows = new ArrayList<>();
        LocalDate end = from.toLocalDate().plusDays(days - 1L);
        for (LocalDate date = from.toLocalDate(); !date.isAfter(end); date = date.plusDays(1)) {
            if (!matches(config, date)) continue;
            for (GeneralInspectionPlanConfig.Slot slot : config.getSlots()) {
                LocalDateTime start = LocalDateTime.of(date, slot.getStartTime());
                LocalDateTime due = LocalDateTime.of(date.plusDays(value(slot.getDueDayOffset(), 0)), slot.getDueTime());
                for (GeneralInspectionPlanConfig.PointAssignment point : config.getPoints()) {
                    Long assigneeId = point.getAssigneeId() == null ? config.getAssigneeId() : point.getAssigneeId();
                    rows.add(Map.of("date", date, "slotCode", slot.getSlotCode(), "slotName", slot.getSlotName(),
                            "pointId", point.getPointId(), "assigneeId", assigneeId,
                            "availableTime", start.minusMinutes(value(config.getEarlyMinutes(), 30)),
                            "startTime", start, "dueTime", due));
                }
            }
        }
        return rows;
    }

    boolean materialize(GeneralInspectionPlan plan, GeneralInspectionPlanVersion planVersion,
                        GeneralInspectionPlanConfig config, GeneralInspectionPlanConfig.Slot slot,
                        GeneralInspectionPlanConfig.PointAssignment assignment, LocalDate date,
                        LocalDateTime start) {
        // 与点位停用共用同一行锁：生成事务读到 ACTIVE 后一直持锁到任务、快照和
        // 游标提交，停用随后必能扫描并取消该任务；反之生成会看到 INACTIVE 并跳过。
        GeneralInspectionPoint point = pointMapper.selectByIdForUpdate(assignment.getPointId());
        if (point == null || !Objects.equals(point.getProjectId(), plan.getProjectId()) || !"ACTIVE".equals(point.getStatus())) return false;
        boolean edgePlan = EdgeInspectionConfigService.EDGE_PLAN_CODE.equals(plan.getPlanCode());
        if (edgePlan && !StringUtils.hasText(point.getPointTypeCode())) return false;
        LocalDateTime due = LocalDateTime.of(date.plusDays(value(slot.getDueDayOffset(), 0)), slot.getDueTime());
        LocalDateTime activeSince = point.getEdgeActiveSinceTime() == null
                ? point.getCreateTime() : point.getEdgeActiveSinceTime();
        if (!eligibleForPointActivation(edgePlan, activeSince, start)) return false;
        Long templateId = plan.getTemplateId();
        if (edgePlan) {
            GeneralInspectionTemplate template = templateMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<GeneralInspectionTemplate>()
                            .eq(GeneralInspectionTemplate::getScopeType, "SYSTEM")
                            .isNull(GeneralInspectionTemplate::getProjectId)
                            .eq(GeneralInspectionTemplate::getTemplateCode,
                                    EdgeInspectionConfigService.EDGE_TEMPLATE_PREFIX + point.getPointTypeCode())
                            .eq(GeneralInspectionTemplate::getStatus, "PUBLISHED")
                            .eq(GeneralInspectionTemplate::getDeleted, 0)
                            .last("LIMIT 1"));
            if (template == null) return false;
            templateId = template.getId();
        }
        GeneralInspectionTemplateVersion templateVersion = templateVersionMapper.selectOne(
                new LambdaQueryWrapper<GeneralInspectionTemplateVersion>()
                        .eq(GeneralInspectionTemplateVersion::getTemplateId, templateId)
                        .le(GeneralInspectionTemplateVersion::getEffectiveTime, start)
                        .orderByDesc(GeneralInspectionTemplateVersion::getEffectiveTime).last("LIMIT 1"));
        if (templateVersion == null) return false;
        List<GeneralInspectionTemplateItem> templateItems = templateItemMapper.selectList(
                new LambdaQueryWrapper<GeneralInspectionTemplateItem>()
                        .eq(GeneralInspectionTemplateItem::getTemplateVersionId, templateVersion.getId())
                        .orderByAsc(GeneralInspectionTemplateItem::getSortOrder));
        if (templateItems.isEmpty()) return false;

        GeneralInspectionTask task = new GeneralInspectionTask();
        task.setProjectId(plan.getProjectId());
        task.setPlanId(plan.getId());
        task.setPlanVersionId(planVersion.getId());
        task.setTemplateId(templateId);
        task.setTemplateVersionId(templateVersion.getId());
        task.setPointId(point.getId());
        task.setRevisionNo(0);
        task.setPlanName(plan.getPlanName());
        task.setTemplateName(templateVersion.getTemplateName());
        task.setPointCode(point.getPointCode());
        task.setPointName(point.getPointName());
        task.setPointTypeCode(point.getPointTypeCode());
        task.setPointTypeName(point.getPointTypeName());
        task.setBuildingName(point.getBuildingName());
        task.setFloorName(point.getFloorName());
        task.setLocationDesc(point.getLocationDesc());
        task.setSlotCode(slot.getSlotCode());
        task.setSlotName(slot.getSlotName());
        task.setOccurrenceDate(date);
        task.setStartTime(start);
        task.setAvailableTime(start.minusMinutes(value(config.getEarlyMinutes(), 30)));
        task.setDueTime(due);
        Long assigneeId = assignment.getAssigneeId() == null ? config.getAssigneeId() : assignment.getAssigneeId();
        List<Long> backupAssigneeIds = assignment.getBackupAssigneeIds() == null
                ? config.getBackupAssigneeIds() : assignment.getBackupAssigneeIds();
        Long defaultRectifierId = assignment.getDefaultRectifierId() == null
                ? config.getDefaultRectifierId() : assignment.getDefaultRectifierId();
        int rectificationDays = assignment.getRectificationDays() == null
                ? value(config.getRectificationDays(), 3) : assignment.getRectificationDays();
        Long reviewerId = assignment.getReviewerId() == null ? config.getReviewerId() : assignment.getReviewerId();
        List<Long> backupReviewerIds = assignment.getBackupReviewerIds() == null
                ? config.getBackupReviewerIds() : assignment.getBackupReviewerIds();
        if (edgePlan) {
            assigneeId = validEdgeUser(plan.getProjectId(), assigneeId,
                    com.example.siteplatform.project.constant.InspectionPermissionCodes.EDGE_INSPECTION_SUBMIT);
            defaultRectifierId = validEdgeUser(plan.getProjectId(), defaultRectifierId,
                    com.example.siteplatform.project.constant.InspectionPermissionCodes.EDGE_INSPECTION_RECTIFY);
            reviewerId = validEdgeUser(plan.getProjectId(), reviewerId,
                    com.example.siteplatform.project.constant.InspectionPermissionCodes.EDGE_INSPECTION_REVIEW);
            backupAssigneeIds = List.of();
            backupReviewerIds = List.of();
        }
        task.setAssigneeId(assigneeId);
        task.setAssigneeName(userName(assigneeId));
        task.setBackupAssigneeIds(joinIds(backupAssigneeIds));
        task.setDefaultRectifierId(defaultRectifierId);
        task.setDefaultRectifierName(userName(defaultRectifierId));
        task.setDefaultRectificationDays(rectificationDays);
        task.setReviewerId(reviewerId);
        task.setReviewerName(userName(reviewerId));
        task.setBackupReviewerIds(joinIds(backupReviewerIds));
        task.setQrRequired(edgePlan ? 0 : Integer.valueOf(1).equals(point.getQrEnabled()) ? 1 : 0);
        task.setQrVersion(edgePlan ? 0 : value(point.getQrVersion(), 0));
        task.setOverallPhotoMin(edgePlan ? 1 : value(templateVersion.getOverallPhotoMin(), 0));
        task.setOverallPhotoMax(value(templateVersion.getOverallPhotoMax(), 9));
        task.setOverallRemarkRequired(value(templateVersion.getOverallRemarkRequired(), 0));
        task.setStatus("PENDING");
        task.setAbnormalCount(0);
        task.setVersion(0);
        try {
            if (taskMapper.insert(task) != 1) throw conflict("任务生成写入未生效");
        } catch (DuplicateKeyException ex) {
            return false;
        }
        for (GeneralInspectionTemplateItem source : templateItems) {
            GeneralInspectionTaskItem item = new GeneralInspectionTaskItem();
            item.setTaskId(task.getId());
            item.setTemplateItemId(source.getId());
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
            if (taskItemMapper.insert(item) != 1) throw conflict("任务检查项快照写入未生效");
        }
        return true;
    }

    private List<GeneralInspectionPlanConfig.PointAssignment> assignments(
            GeneralInspectionPlan plan, GeneralInspectionPlanConfig config) {
        if (!EdgeInspectionConfigService.EDGE_PLAN_CODE.equals(plan.getPlanCode())) {
            return config.getPoints() == null ? List.of() : config.getPoints();
        }
        return pointMapper.selectList(new LambdaQueryWrapper<GeneralInspectionPoint>()
                        .eq(GeneralInspectionPoint::getProjectId, plan.getProjectId())
                        .eq(GeneralInspectionPoint::getStatus, "ACTIVE")
                        .eq(GeneralInspectionPoint::getDeleted, 0)
                        .isNotNull(GeneralInspectionPoint::getPointTypeCode))
                .stream().map(point -> {
                    GeneralInspectionPlanConfig.PointAssignment assignment = new GeneralInspectionPlanConfig.PointAssignment();
                    assignment.setPointId(point.getId());
                    return assignment;
                }).toList();
    }

    private LocalDate calculateCatchupStart(GeneralInspectionPlan plan,
                                            Collection<GeneralInspectionPlanConfig> configs,
                                            LocalDateTime now) {
        LocalDate configuredStart = configs.stream().map(GeneralInspectionPlanConfig::getEffectiveStart)
                .min(LocalDate::compareTo).orElse(now.toLocalDate());
        LocalDate cursorStart = plan.getGeneratedThroughTime() == null
                ? now.toLocalDate().minusDays(1)
                : plan.getGeneratedThroughTime().toLocalDate().minusDays(1);
        LocalDate result = configuredStart.isAfter(cursorStart) ? configuredStart : cursorStart;
        LocalDate safetyFloor = now.toLocalDate().minusDays(366);
        return result.isBefore(safetyFloor) ? safetyFloor : result;
    }

    static boolean eligibleForGenerationLowerBound(boolean edgePlan, LocalDateTime lowerBound,
                                                    LocalDateTime taskStart) {
        return !edgePlan || lowerBound == null || !taskStart.isBefore(lowerBound);
    }

    private GeneralInspectionPlanVersion latestPlanVersion(List<GeneralInspectionPlanVersion> versions,
                                                            LocalDateTime taskStart) {
        GeneralInspectionPlanVersion result = null;
        for (GeneralInspectionPlanVersion version : versions) {
            if (!version.getEffectiveTime().isAfter(taskStart)) result = version;
            else break;
        }
        return result;
    }

    private boolean matches(GeneralInspectionPlanConfig config, LocalDate date) {
        if (date.isBefore(config.getEffectiveStart())
                || config.getEffectiveEnd() != null && date.isAfter(config.getEffectiveEnd())) return false;
        return switch (config.getFrequency()) {
            case "DAILY" -> true;
            case "WEEKLY" -> config.getWeekdays() != null && config.getWeekdays().contains(date.getDayOfWeek().getValue());
            case "MONTHLY" -> config.getMonthDays() != null && (config.getMonthDays().contains(date.getDayOfMonth())
                    || config.getMonthDays().contains(-1) && date.equals(date.withDayOfMonth(date.lengthOfMonth())));
            default -> false;
        };
    }

    private GeneralInspectionPlanConfig parseConfig(String json) {
        try {
            return objectMapper.readValue(json, GeneralInspectionPlanConfig.class);
        } catch (JsonProcessingException ex) {
            throw conflict("计划版本配置无法解析");
        }
    }

    private String userName(Long userId) {
        if (userId == null) return null;
        SysUser user = userMapper.selectById(userId);
        if (user == null) return "待重新分配";
        return StringUtils.hasText(user.getRealName()) ? user.getRealName() : user.getUsername();
    }

    private Long validEdgeUser(Long projectId, Long userId, String permissionCode) {
        if (userId == null || !permissionService.hasActiveProjectAccess(projectId, userId)
                || !permissionService.hasInspectionPermission(projectId, userId, permissionCode)) return null;
        SysUser user = userMapper.selectById(userId);
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())
                || Integer.valueOf(1).equals(user.getDeleted())) return null;
        return userId;
    }

    private String joinIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return null;
        return ids.stream().filter(Objects::nonNull).distinct().map(String::valueOf).collect(Collectors.joining(","));
    }

    private int value(Integer actual, int fallback) {
        return actual == null ? fallback : actual;
    }

    static boolean eligibleForPointActivation(boolean edgePlan, LocalDateTime activeSinceTime,
                                              LocalDateTime taskStartTime) {
        return !edgePlan || activeSinceTime == null || taskStartTime == null
                || !taskStartTime.isBefore(activeSinceTime);
    }

    private BusinessException conflict(String message) {
        return BusinessException.of(409, message);
    }
}
