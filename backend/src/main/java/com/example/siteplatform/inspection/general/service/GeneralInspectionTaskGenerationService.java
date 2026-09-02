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

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

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
        List<GeneralInspectionPlan> plans = planMapper.selectList(new LambdaQueryWrapper<GeneralInspectionPlan>()
                .eq(GeneralInspectionPlan::getPlanCode, EdgeInspectionConfigService.EDGE_PLAN_CODE)
                .eq(GeneralInspectionPlan::getStatus, "PUBLISHED")
                .eq(GeneralInspectionPlan::getDeleted, 0));
        for (GeneralInspectionPlan plan : plans) {
            try {
                // @Scheduled 与 generatePlan 位于同一 Bean，直接自调用不会经过
                // @Transactional 代理；显式模板确保每个计划的行锁、任务/快照和游标原子提交。
                // 不复用扫描列表前取得的旧时间；每个计划进入事务并取得计划行锁后
                // 再读取业务时钟，避免计划较多时跨过截止点仍按旧 now 生成任务。
                transactionTemplate.execute(status -> generatePlan(plan.getId(), null, null));
            } catch (RuntimeException ex) {
                log.error("临边巡检计划任务生成失败，planId={}", plan.getId(), ex);
            }
        }
    }

    @Transactional
    public int generatePlan(Long planId, LocalDateTime now, LocalDateTime horizon) {
        GeneralInspectionPlan plan = planMapper.selectByIdForUpdate(planId);
        if (plan == null || !"PUBLISHED".equals(plan.getStatus()) || !Integer.valueOf(0).equals(plan.getDeleted())) return 0;
        if (now == null) now = LocalDateTime.now(BUSINESS_ZONE);
        if (horizon == null) horizon = now.plusHours(25);

        List<GeneralInspectionPlanVersion> versions = planVersionMapper.selectList(
                new LambdaQueryWrapper<GeneralInspectionPlanVersion>()
                        .eq(GeneralInspectionPlanVersion::getPlanId, planId)
                        .le(GeneralInspectionPlanVersion::getEffectiveTime, horizon)
                        .orderByAsc(GeneralInspectionPlanVersion::getEffectiveTime)
                        .orderByAsc(GeneralInspectionPlanVersion::getVersionNo));
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
                    LocalDateTime due = dueTime(date, slot);
                    if (!eligibleForActivationWindow(edgePlan, generationLowerBound,
                            taskStart, due, now)) continue;
                    if (taskStart.isAfter(horizon)) continue;
                    for (GeneralInspectionPlanConfig.PointAssignment assignment : assignments(plan, config)) {
                        if (materialize(plan, versions, version, config, slot, assignment, date,
                                taskStart, due, now, generationLowerBound)) created++;
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

    boolean materialize(GeneralInspectionPlan plan, List<GeneralInspectionPlanVersion> planVersions,
                        GeneralInspectionPlanVersion planVersion,
                        GeneralInspectionPlanConfig config, GeneralInspectionPlanConfig.Slot slot,
                        GeneralInspectionPlanConfig.PointAssignment assignment, LocalDate date,
                        LocalDateTime start, LocalDateTime due, LocalDateTime now,
                        LocalDateTime generationLowerBound) {
        // 与点位停用共用同一行锁：生成事务读到 ACTIVE 后一直持锁到任务、快照和
        // 游标提交，停用随后必能扫描并取消该任务；反之生成会看到 INACTIVE 并跳过。
        GeneralInspectionPoint point = pointMapper.selectByIdForUpdate(assignment.getPointId());
        if (point == null || !Objects.equals(point.getProjectId(), plan.getProjectId()) || !"ACTIVE".equals(point.getStatus())) return false;
        boolean edgePlan = EdgeInspectionConfigService.EDGE_PLAN_CODE.equals(plan.getPlanCode());
        if (edgePlan && !StringUtils.hasText(point.getPointTypeCode())) return false;
        LocalDateTime activeSince = point.getEdgeActiveSinceTime() == null
                ? point.getCreateTime() : point.getEdgeActiveSinceTime();
        // 新建点位只进入创建后的任务，不能借“当前时段尚未截止”补成一项
        // 创建前已经开始的应检任务；放宽规则只适用于计划首次/恢复启用。
        if (!eligibleForPointActivation(edgePlan, activeSince, start)) return false;
        LocalDateTime versionReferenceTime = activationAwareVersionReference(edgePlan, start,
                generationLowerBound, activeSince);
        GeneralInspectionPlanVersion effectiveVersion = latestPlanVersion(planVersions, versionReferenceTime);
        if (effectiveVersion == null || !Objects.equals(effectiveVersion.getId(), planVersion.getId())) return false;
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
        if (EdgeInspectionConfigService.EDGE_PLAN_CODE.equals(plan.getPlanCode())) {
            // 临边时段最长可跨午夜 24 小时。25 小时预生成游标在深夜可能已经
            // 跨到后天，仅按“游标日期 - 1 天”会漏掉仍在执行中的前一日跨夜任务。
            // 因此每轮至少回看当前业务日期的前一天，之后仍由启用/点位激活
            // 下界和截止时间判断阻止补出已截止的历史任务。
            LocalDate openWindowStart = now.toLocalDate().minusDays(1);
            LocalDate edgeStart = configuredStart.isAfter(openWindowStart)
                    ? configuredStart : openWindowStart;
            if (edgeStart.isBefore(result)) result = edgeStart;
        }
        LocalDate safetyFloor = now.toLocalDate().minusDays(366);
        return result.isBefore(safetyFloor) ? safetyFloor : result;
    }

    static boolean eligibleForActivationWindow(boolean edgePlan, LocalDateTime activationTime,
                                               LocalDateTime taskStart, LocalDateTime dueTime,
                                               LocalDateTime now) {
        if (!edgePlan || activationTime == null || taskStart == null
                || !taskStart.isBefore(activationTime)) return true;
        // 首次/恢复启用计划落在已开始的时段内时，
        // 只要调度实际执行时仍未逾期就允许生成。「截止时刻」与提交
        // 的逾期判定保持一致：只有 now.isAfter(dueTime) 才算已逾期。
        return dueTime != null && now != null
                && !activationTime.isAfter(dueTime) && !now.isAfter(dueTime);
    }

    static LocalDateTime activationAwareVersionReference(boolean edgePlan, LocalDateTime taskStart,
                                                         LocalDateTime generationLowerBound,
                                                         LocalDateTime pointActiveSince) {
        if (!edgePlan || taskStart == null) return taskStart;
        LocalDateTime reference = taskStart;
        if (generationLowerBound != null && generationLowerBound.isAfter(reference)) {
            reference = generationLowerBound;
        }
        if (pointActiveSince != null && pointActiveSince.isAfter(reference)) {
            reference = pointActiveSince;
        }
        return reference;
    }

    GeneralInspectionPlanVersion latestPlanVersion(List<GeneralInspectionPlanVersion> versions,
                                                    LocalDateTime referenceTime) {
        GeneralInspectionPlanVersion result = null;
        for (GeneralInspectionPlanVersion version : versions) {
            if (!version.getEffectiveTime().isAfter(referenceTime)) result = version;
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

    private LocalDateTime dueTime(LocalDate date, GeneralInspectionPlanConfig.Slot slot) {
        return LocalDateTime.of(date.plusDays(value(slot.getDueDayOffset(), 0)), slot.getDueTime());
    }

    private BusinessException conflict(String message) {
        return BusinessException.of(409, message);
    }
}
