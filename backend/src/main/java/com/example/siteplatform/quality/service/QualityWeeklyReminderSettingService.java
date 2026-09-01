package com.example.siteplatform.quality.service;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.log.entity.OperationLog;
import com.example.siteplatform.log.mapper.OperationLogMapper;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.quality.dto.QualityWeeklyReminderSettingRequest;
import com.example.siteplatform.quality.entity.QualityWeeklyReminderSetting;
import com.example.siteplatform.quality.mapper.QualityAssigneeMapper;
import com.example.siteplatform.quality.mapper.QualityWeeklyReminderSettingMapper;
import com.example.siteplatform.quality.vo.QualityAssigneeVO;
import com.example.siteplatform.quality.vo.QualityWeeklyReminderSettingVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Objects;

@Service
public class QualityWeeklyReminderSettingService {
    public static final int DEFAULT_DAY_OF_WEEK = DayOfWeek.SUNDAY.getValue();
    public static final LocalTime DEFAULT_TRIGGER_TIME = LocalTime.of(18, 0);
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    @Autowired
    private QualityWeeklyReminderSettingMapper settingMapper;

    @Autowired
    private QualityAssigneeMapper assigneeMapper;

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private ProjectPermissionService projectPermissionService;

    @Autowired
    private OperationLogMapper operationLogMapper;

    public QualityWeeklyReminderSettingVO getSetting(Long projectId, SysUser currentUser) {
        requireManage(projectId, currentUser);
        QualityWeeklyReminderSetting setting = settingMapper.selectByProjectId(projectId);
        return setting == null ? defaultSetting(projectId) : toVO(setting, now());
    }

    public List<QualityAssigneeVO> listReminderAssignees(Long projectId, SysUser currentUser) {
        requireManage(projectId, currentUser);
        return assigneeMapper.selectPotentialAssignees(projectId).stream()
                .filter(user -> isEligibleResponsibleUser(user, projectId))
                .map(this::toAssigneeVO)
                .toList();
    }

    @Transactional
    public QualityWeeklyReminderSettingVO updateSetting(Long projectId,
                                                         QualityWeeklyReminderSettingRequest request,
                                                         SysUser currentUser) {
        requireManage(projectId, currentUser);
        validateRequest(request);

        QualityWeeklyReminderSetting existing = settingMapper.selectByProjectIdForUpdate(projectId);
        int currentVersion = existing == null || existing.getVersion() == null ? 0 : existing.getVersion();
        if (!Objects.equals(currentVersion, request.getExpectedVersion())) {
            throw conflict("质量周检提醒设置已被其他人修改，请重新加载");
        }

        SysUser responsible = null;
        if (request.getResponsibleUserId() != null) {
            responsible = requireEligibleResponsibleUser(request.getResponsibleUserId(), projectId);
        }
        if (Boolean.TRUE.equals(request.getEnabled()) && responsible == null) {
            throw new BusinessException("启用质量周检提醒前必须选择责任人");
        }

        LocalDateTime savedAt = now();
        String operatorName = displayName(currentUser);
        Integer enabled = Boolean.TRUE.equals(request.getEnabled()) ? 1 : 0;
        LocalDateTime effectiveTime = enabled == 1
                ? effectiveTimeOnSave(existing, savedAt)
                : null;
        String responsibleName = responsible == null ? null : displayName(responsible);

        QualityWeeklyReminderSetting persisted;
        if (existing == null) {
            persisted = new QualityWeeklyReminderSetting();
            persisted.setProjectId(projectId);
            persisted.setEnabled(enabled);
            persisted.setDayOfWeek(request.getDayOfWeek());
            persisted.setTriggerTime(request.getTriggerTime());
            persisted.setResponsibleUserId(request.getResponsibleUserId());
            persisted.setResponsibleUserName(responsibleName);
            persisted.setEffectiveTime(effectiveTime);
            persisted.setVersion(1);
            persisted.setCreatedById(currentUser.getId());
            persisted.setCreatedByName(operatorName);
            persisted.setUpdatedById(currentUser.getId());
            persisted.setUpdatedByName(operatorName);
            persisted.setCreateTime(savedAt);
            persisted.setUpdateTime(savedAt);
            try {
                requireSingleWrite(settingMapper.insert(persisted), "质量周检提醒设置新增");
            } catch (DuplicateKeyException duplicate) {
                throw conflict("质量周检提醒设置已被其他人创建，请重新加载");
            }
        } else {
            requireSingleWrite(settingMapper.updateSetting(
                    existing.getId(), request.getExpectedVersion(), enabled,
                    request.getDayOfWeek(), request.getTriggerTime(), request.getResponsibleUserId(),
                    responsibleName, effectiveTime, currentUser.getId(), operatorName, savedAt),
                    "质量周检提醒设置保存");
            persisted = existing;
            persisted.setEnabled(enabled);
            persisted.setDayOfWeek(request.getDayOfWeek());
            persisted.setTriggerTime(request.getTriggerTime());
            persisted.setResponsibleUserId(request.getResponsibleUserId());
            persisted.setResponsibleUserName(responsibleName);
            persisted.setEffectiveTime(effectiveTime);
            persisted.setVersion(currentVersion + 1);
            persisted.setUpdatedById(currentUser.getId());
            persisted.setUpdatedByName(operatorName);
            persisted.setUpdateTime(savedAt);
        }

        recordOperation(currentUser, persisted.getId(),
                enabled == 1 ? "启用质量周检未提交站内提醒" : "保存并关闭质量周检未提交站内提醒");
        return toVO(persisted, savedAt);
    }

    /**
     * 调度器在判断及写通知前使用同一项目设置行锁；提交服务也通过 inspectionId 先锁同一行。
     */
    public QualityWeeklyReminderSetting lockForUpdate(Long projectId) {
        return projectId == null ? null : settingMapper.selectByProjectIdForUpdate(projectId);
    }

    public QualityWeeklyReminderSetting lockByInspectionId(Long inspectionId) {
        return inspectionId == null ? null : settingMapper.selectByInspectionIdForUpdate(inspectionId);
    }

    public List<QualityWeeklyReminderSetting> listEnabledSettings(LocalDate weekStart) {
        return weekStart == null ? List.of() : settingMapper.selectEnabledSettings(weekStart);
    }

    public boolean isEligibleResponsibleUser(Long userId, Long projectId) {
        return userId != null && projectId != null
                && isEligibleResponsibleUser(userMapper.selectById(userId), projectId);
    }

    public LocalDateTime scheduledAt(QualityWeeklyReminderSetting setting, LocalDate weekStart) {
        if (setting == null || weekStart == null || setting.getDayOfWeek() == null
                || setting.getTriggerTime() == null) {
            return null;
        }
        LocalDate monday = weekStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return monday.plusDays(setting.getDayOfWeek() - 1L).atTime(setting.getTriggerTime());
    }

    public boolean isOccurrenceEffective(QualityWeeklyReminderSetting setting,
                                         LocalDateTime scheduledAt) {
        LocalDateTime occurrenceWeekStart = scheduledAt == null ? null
                : scheduledAt.toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .atStartOfDay();
        return setting != null
                && Integer.valueOf(1).equals(setting.getEnabled())
                && setting.getEffectiveTime() != null
                && occurrenceWeekStart != null
                && occurrenceWeekStart.isAfter(setting.getEffectiveTime());
    }

    public LocalDateTime nextScheduledAt(QualityWeeklyReminderSetting setting,
                                         LocalDateTime reference) {
        if (setting == null || !Integer.valueOf(1).equals(setting.getEnabled())
                || setting.getDayOfWeek() == null || setting.getTriggerTime() == null
                || setting.getEffectiveTime() == null) {
            return null;
        }
        LocalDateTime base = reference == null ? now() : reference;
        LocalDate monday = base.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDateTime candidate = scheduledAt(setting, monday);
        while (!candidate.isAfter(base)
                || !isOccurrenceEffective(setting, candidate)) {
            candidate = candidate.plusWeeks(1);
        }
        return candidate;
    }

    private LocalDateTime effectiveTimeOnSave(QualityWeeklyReminderSetting existing,
                                               LocalDateTime savedAt) {
        if (existing == null || !Integer.valueOf(1).equals(existing.getEnabled())
                || existing.getEffectiveTime() == null) {
            return savedAt;
        }
        return existing.getEffectiveTime();
    }

    private void validateRequest(QualityWeeklyReminderSettingRequest request) {
        if (request == null || request.getEnabled() == null || request.getExpectedVersion() == null) {
            throw new BusinessException("提醒开关和expectedVersion不能为空");
        }
        if (request.getExpectedVersion() < 0) {
            throw new BusinessException("expectedVersion不能为负数");
        }
        if (request.getDayOfWeek() == null
                || request.getDayOfWeek() < 1 || request.getDayOfWeek() > 7) {
            throw new BusinessException("提醒星期必须在1到7之间");
        }
        if (request.getTriggerTime() == null) {
            throw new BusinessException("提醒时间不能为空");
        }
    }

    private SysUser requireEligibleResponsibleUser(Long userId, Long projectId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null || Integer.valueOf(1).equals(user.getDeleted())) {
            throw BusinessException.notFound("质量周检提醒责任人不存在");
        }
        if (!Integer.valueOf(1).equals(user.getStatus())) {
            throw new BusinessException("质量周检提醒责任人账号未启用");
        }
        if (!"ACTIVE".equals(projectPermissionService.getProjectAccessStatus(userId, projectId))) {
            throw new BusinessException("质量周检提醒责任人没有当前项目的有效访问权限");
        }
        if (!projectPermissionService.canManageQuality(userId, projectId)) {
            throw new BusinessException("质量周检提醒责任人必须具备质量管理权限");
        }
        return user;
    }

    private boolean isEligibleResponsibleUser(SysUser user, Long projectId) {
        return user != null
                && user.getId() != null
                && Integer.valueOf(1).equals(user.getStatus())
                && !Integer.valueOf(1).equals(user.getDeleted())
                && "ACTIVE".equals(projectPermissionService.getProjectAccessStatus(user.getId(), projectId))
                && projectPermissionService.canManageQuality(user.getId(), projectId);
    }

    private void requireManage(Long projectId, SysUser currentUser) {
        if (projectId == null) {
            throw new BusinessException("项目ID不能为空");
        }
        if (currentUser == null || currentUser.getId() == null) {
            throw BusinessException.unauthorized("请先登录");
        }
        projectPermissionService.checkProjectPermission(currentUser.getId(), projectId);
        if (!projectPermissionService.canManageQuality(currentUser.getId(), projectId)) {
            throw BusinessException.forbidden("无质量管理权限");
        }
    }

    private QualityWeeklyReminderSettingVO defaultSetting(Long projectId) {
        QualityWeeklyReminderSettingVO vo = new QualityWeeklyReminderSettingVO();
        vo.setProjectId(projectId);
        vo.setEnabled(false);
        vo.setDayOfWeek(DEFAULT_DAY_OF_WEEK);
        vo.setTriggerTime(DEFAULT_TRIGGER_TIME);
        vo.setVersion(0);
        return vo;
    }

    private QualityWeeklyReminderSettingVO toVO(QualityWeeklyReminderSetting setting,
                                                 LocalDateTime reference) {
        QualityWeeklyReminderSettingVO vo = new QualityWeeklyReminderSettingVO();
        vo.setId(setting.getId());
        vo.setProjectId(setting.getProjectId());
        vo.setEnabled(Integer.valueOf(1).equals(setting.getEnabled()));
        vo.setDayOfWeek(setting.getDayOfWeek());
        vo.setTriggerTime(setting.getTriggerTime());
        vo.setResponsibleUserId(setting.getResponsibleUserId());
        vo.setResponsibleUserName(setting.getResponsibleUserName());
        vo.setReminderEffectiveTime(setting.getEffectiveTime());
        vo.setNextReminderTime(nextScheduledAt(setting, reference));
        vo.setVersion(setting.getVersion() == null ? 0 : setting.getVersion());
        vo.setUpdatedById(setting.getUpdatedById());
        vo.setUpdatedByName(setting.getUpdatedByName());
        vo.setUpdateTime(setting.getUpdateTime());
        return vo;
    }

    private QualityAssigneeVO toAssigneeVO(SysUser user) {
        QualityAssigneeVO vo = new QualityAssigneeVO();
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setDisplayName(displayName(user));
        return vo;
    }

    private void recordOperation(SysUser user, Long businessId, String description) {
        OperationLog log = new OperationLog();
        log.setUserId(user.getId());
        log.setUsername(displayName(user));
        log.setOperationType("QUALITY_WEEKLY_REMINDER_SETTING_UPDATE");
        log.setOperationDesc(description);
        log.setBusinessType("QUALITY_WEEKLY_REMINDER_SETTING");
        log.setBusinessId(businessId);
        log.setCreateTime(now());
        requireSingleWrite(operationLogMapper.insert(log), "质量周检提醒设置操作日志写入");
    }

    private void requireSingleWrite(int affectedRows, String action) {
        if (affectedRows != 1) {
            throw conflict(action + "未生效，请重新加载后重试");
        }
    }

    private BusinessException conflict(String message) {
        return BusinessException.of(409, message);
    }

    private String displayName(SysUser user) {
        return StringUtils.hasText(user.getRealName()) ? user.getRealName() : user.getUsername();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(BUSINESS_ZONE);
    }
}
