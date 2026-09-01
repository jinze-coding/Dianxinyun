package com.example.siteplatform.inspection.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.electricbox.entity.ElectricBox;
import com.example.siteplatform.electricbox.mapper.ElectricBoxMapper;
import com.example.siteplatform.electricbox.service.ElectricBoxInspectionScopeService;
import com.example.siteplatform.inspection.entity.InspectionRecord;
import com.example.siteplatform.inspection.entity.ProjectInspectionSetting;
import com.example.siteplatform.inspection.general.dto.GeneralInspectionPlanConfig;
import com.example.siteplatform.inspection.general.entity.GeneralInspectionPlan;
import com.example.siteplatform.inspection.general.entity.GeneralInspectionTask;
import com.example.siteplatform.inspection.general.mapper.GeneralInspectionPlanMapper;
import com.example.siteplatform.inspection.general.mapper.GeneralInspectionTaskMapper;
import com.example.siteplatform.inspection.general.service.EdgeInspectionConfigService;
import com.example.siteplatform.inspection.mapper.InspectionRecordMapper;
import com.example.siteplatform.inspection.mapper.ProjectInspectionSettingMapper;
import com.example.siteplatform.notification.service.UserNotificationService;
import com.example.siteplatform.project.constant.InspectionPermissionCodes;
import com.example.siteplatform.project.mapper.ProjectInfoMapper;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.system.constant.SystemPermissionCodes;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Creates persisted station notifications for missed electric-box and fixed-edge submissions.
 * Candidate scans are deliberately shallow; every business condition is checked again while
 * holding the same row lock used by the corresponding submit action.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InspectionSubmissionReminderService {

    private final ProjectInspectionSettingMapper settingMapper;
    private final ElectricBoxMapper electricBoxMapper;
    private final ElectricBoxInspectionScopeService scopeService;
    private final InspectionRecordMapper inspectionRecordMapper;
    private final GeneralInspectionTaskMapper taskMapper;
    private final GeneralInspectionPlanMapper planMapper;
    private final ProjectInfoMapper projectMapper;
    private final SysUserMapper userMapper;
    private final ProjectPermissionService permissionService;
    private final UserNotificationService notificationService;
    private final ObjectMapper objectMapper;

    public List<ElectricReminderCandidate> dueElectricCandidates(LocalDateTime now) {
        List<ProjectInspectionSetting> settings = settingMapper.selectList(
                new LambdaQueryWrapper<ProjectInspectionSetting>()
                        .eq(ProjectInspectionSetting::getSubmissionReminderEnabled, 1)
                        .isNotNull(ProjectInspectionSetting::getReminderEffectiveTime)
                        .le(ProjectInspectionSetting::getDailyCutoffTime, now.toLocalTime())
                        .orderByAsc(ProjectInspectionSetting::getProjectId));
        List<ElectricReminderCandidate> result = new ArrayList<>();
        for (ProjectInspectionSetting setting : settings == null ? List.<ProjectInspectionSetting>of() : settings) {
            if (setting.getProjectId() == null) continue;
            List<ElectricBox> boxes = electricBoxMapper.selectPendingSubmissionReminderBoxes(
                    setting.getProjectId(), now.toLocalDate());
            for (ElectricBox box : boxes == null ? List.<ElectricBox>of() : boxes) {
                result.add(new ElectricReminderCandidate(setting.getProjectId(), box.getId()));
            }
        }
        return List.copyOf(result);
    }

    public List<Long> dueEdgeTaskIds(LocalDateTime now) {
        List<Long> result = new ArrayList<>();
        List<GeneralInspectionPlan> plans = planMapper.selectList(new LambdaQueryWrapper<GeneralInspectionPlan>()
                .eq(GeneralInspectionPlan::getPlanCode, EdgeInspectionConfigService.EDGE_PLAN_CODE)
                .eq(GeneralInspectionPlan::getStatus, "PUBLISHED")
                .eq(GeneralInspectionPlan::getDeleted, 0)
                .orderByAsc(GeneralInspectionPlan::getId));
        for (GeneralInspectionPlan plan : plans == null ? List.<GeneralInspectionPlan>of() : plans) {
            GeneralInspectionPlanConfig config;
            try {
                config = parseConfig(plan.getDraftConfigJson());
            } catch (RuntimeException ex) {
                log.warn("临边巡检提醒候选配置解析失败，planId={}", plan.getId(), ex);
                continue;
            }
            if (!Boolean.TRUE.equals(config.getSubmissionReminderEnabled())
                    || config.getReminderEffectiveTime() == null) continue;
            List<Long> ids = taskMapper.selectPendingReminderCandidateIds(
                    plan.getId(), config.getReminderEffectiveTime(), now);
            if (ids != null) result.addAll(ids.stream().filter(Objects::nonNull).toList());
        }
        return List.copyOf(result);
    }

    @Transactional
    public boolean remindElectricBox(Long projectId, Long boxId, LocalDate date, LocalDateTime now) {
        ProjectInspectionSetting setting = settingMapper.selectByProjectIdForUpdate(projectId);
        if (setting == null || !Integer.valueOf(1).equals(setting.getSubmissionReminderEnabled())
                || setting.getDailyCutoffTime() == null || setting.getReminderEffectiveTime() == null) {
            return false;
        }
        LocalDateTime scheduledAt = LocalDateTime.of(date, setting.getDailyCutoffTime());
        if (now.isBefore(scheduledAt)
                || !date.atStartOfDay().isAfter(setting.getReminderEffectiveTime())) return false;
        if (projectMapper.selectById(projectId) == null) return false;

        ElectricBox box = electricBoxMapper.selectByIdForUpdate(boxId);
        if (box == null || !Objects.equals(projectId, box.getProjectId())
                || !"ACTIVE".equals(box.getStatus()) || !scopeService.isRequired(box, date)) {
            return false;
        }
        Long submitted = inspectionRecordMapper.selectCount(new LambdaQueryWrapper<InspectionRecord>()
                .eq(InspectionRecord::getProjectId, projectId)
                .eq(InspectionRecord::getElectricBoxId, boxId)
                .eq(InspectionRecord::getSource, InspectionService.SOURCE_ELECTRICIAN_DAILY)
                .eq(InspectionRecord::getCheckDate, date)
                .ne(InspectionRecord::getStatus, "DRAFT")
                .eq(InspectionRecord::getDeleted, 0));
        if (submitted != null && submitted > 0) return false;

        Long ownerId = box.getResponsibleElectricianId();
        if (!validElectricOwner(ownerId, projectId)) return false;
        String boxLabel = StringUtils.hasText(box.getBoxCode()) ? box.getBoxCode() : "电箱" + box.getId();
        notificationService.notify(ownerId, projectId, "ELECTRIC_BOX_INSPECTION", box.getId(),
                "ELECTRIC_DAILY_NOT_SUBMITTED", "电箱日检未提交",
                boxLabel + " 今日巡检记录尚未提交，请及时完成。",
                "reminder:ebox:" + box.getId() + ":" + date,
                "INSPECTION_FORM", "{\"boxId\":" + box.getId() + "}");
        return true;
    }

    @Transactional
    public boolean remindEdgeTask(Long taskId, LocalDateTime now) {
        GeneralInspectionTask task = taskMapper.selectByIdForUpdate(taskId);
        if (task == null || !"PENDING".equals(task.getStatus()) || task.getDueTime() == null
                || task.getDueTime().isAfter(now) || !StringUtils.hasText(task.getPointTypeCode())) {
            return false;
        }
        GeneralInspectionPlan plan = planMapper.selectByIdForUpdate(task.getPlanId());
        if (plan == null || !Objects.equals(task.getProjectId(), plan.getProjectId())
                || !EdgeInspectionConfigService.EDGE_PLAN_CODE.equals(plan.getPlanCode())
                || !"PUBLISHED".equals(plan.getStatus()) || !Integer.valueOf(0).equals(plan.getDeleted())) {
            return false;
        }
        GeneralInspectionPlanConfig config = parseConfig(plan.getDraftConfigJson());
        if (!Boolean.TRUE.equals(config.getSubmissionReminderEnabled())
                || config.getReminderEffectiveTime() == null
                || !task.getDueTime().isAfter(config.getReminderEffectiveTime())) {
            return false;
        }
        if (projectMapper.selectById(task.getProjectId()) == null
                || !validEdgeOwner(task.getAssigneeId(), task.getProjectId())) {
            return false;
        }
        String pointLabel = StringUtils.hasText(task.getPointName()) ? task.getPointName() : task.getPointCode();
        if (!StringUtils.hasText(pointLabel)) pointLabel = "临边巡检任务";
        notificationService.notify(task.getAssigneeId(), task.getProjectId(), "EDGE_INSPECTION_TASK", task.getId(),
                "EDGE_INSPECTION_NOT_SUBMITTED", "临边巡检未提交",
                pointLabel + " 已到截止时间，巡检记录尚未提交，请及时处理。",
                "reminder:edge:" + task.getId(),
                "EDGE_INSPECTION_TASK_DETAIL", "{\"taskId\":" + task.getId() + "}");
        return true;
    }

    private boolean validElectricOwner(Long userId, Long projectId) {
        return activeUser(userId)
                && "ACTIVE".equals(permissionService.getProjectAccessStatus(userId, projectId))
                && permissionService.hasSystemPermission(userId, projectId, SystemPermissionCodes.INSPECTION_SUBMIT)
                && permissionService.hasInspectionPermission(userId, projectId,
                InspectionPermissionCodes.INSPECTION_DAILY_SUBMIT);
    }

    private boolean validEdgeOwner(Long userId, Long projectId) {
        return activeUser(userId)
                && "ACTIVE".equals(permissionService.getProjectAccessStatus(userId, projectId))
                && permissionService.hasInspectionPermission(userId, projectId,
                InspectionPermissionCodes.EDGE_INSPECTION_SUBMIT);
    }

    private boolean activeUser(Long userId) {
        if (userId == null) return false;
        SysUser user = userMapper.selectById(userId);
        return user != null && Integer.valueOf(1).equals(user.getStatus())
                && !Integer.valueOf(1).equals(user.getDeleted());
    }

    private GeneralInspectionPlanConfig parseConfig(String json) {
        try {
            return objectMapper.readValue(json, GeneralInspectionPlanConfig.class);
        } catch (Exception ex) {
            throw new IllegalStateException("临边巡检提醒配置无法解析", ex);
        }
    }

    public record ElectricReminderCandidate(Long projectId, Long boxId) {}
}
