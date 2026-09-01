package com.example.siteplatform.inspection.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.electricbox.entity.ElectricBox;
import com.example.siteplatform.electricbox.mapper.ElectricBoxMapper;
import com.example.siteplatform.electricbox.service.ElectricBoxInspectionScopeService;
import com.example.siteplatform.inspection.dto.ProjectInspectionSettingRequest;
import com.example.siteplatform.inspection.entity.ProjectInspectionSetting;
import com.example.siteplatform.inspection.mapper.ProjectInspectionSettingMapper;
import com.example.siteplatform.inspection.vo.InspectionReminderConfigurationIssueVO;
import com.example.siteplatform.inspection.vo.ProjectInspectionSettingVO;
import com.example.siteplatform.log.entity.OperationLog;
import com.example.siteplatform.log.mapper.OperationLogMapper;
import com.example.siteplatform.project.constant.InspectionPermissionCodes;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.system.constant.SystemPermissionCodes;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class ProjectInspectionSettingService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int MAX_CONFIGURATION_ISSUE_DETAILS = 20;

    private final ProjectInspectionSettingMapper mapper;
    private final ProjectPermissionService permissionService;
    private final ElectricBoxMapper electricBoxMapper;
    private final ElectricBoxInspectionScopeService inspectionScopeService;
    private final SysUserMapper userMapper;
    private final OperationLogMapper operationLogMapper;

    public ProjectInspectionSettingService(ProjectInspectionSettingMapper mapper,
                                           ProjectPermissionService permissionService,
                                           ElectricBoxMapper electricBoxMapper,
                                           ElectricBoxInspectionScopeService inspectionScopeService,
                                           SysUserMapper userMapper,
                                           OperationLogMapper operationLogMapper) {
        this.mapper = mapper;
        this.permissionService = permissionService;
        this.electricBoxMapper = electricBoxMapper;
        this.inspectionScopeService = inspectionScopeService;
        this.userMapper = userMapper;
        this.operationLogMapper = operationLogMapper;
    }

    public ProjectInspectionSettingVO get(Long projectId, SysUser currentUser) {
        requireManage(projectId, currentUser);
        return toVO(findOrDefault(projectId));
    }

    public ProjectInspectionSetting findOrDefault(Long projectId) {
        ProjectInspectionSetting setting = mapper.selectOne(new LambdaQueryWrapper<ProjectInspectionSetting>()
                .eq(ProjectInspectionSetting::getProjectId, projectId)
                .last("LIMIT 1"));
        if (setting != null) {
            return setting;
        }
        setting = new ProjectInspectionSetting();
        setting.setProjectId(projectId);
        setting.setDailyCutoffTime(LocalTime.of(18, 0));
        setting.setPreDueReminderMinutes(60);
        setting.setReviewDueHours(24);
        setting.setRectificationDays(3);
        setting.setEnabled(1);
        setting.setSubmissionReminderEnabled(0);
        setting.setVersion(0);
        return setting;
    }

    @Transactional
    public ProjectInspectionSettingVO save(Long projectId, ProjectInspectionSettingRequest request, SysUser currentUser) {
        requireManage(projectId, currentUser);
        if (request == null) {
            throw new BusinessException("巡检设置不能为空");
        }
        ProjectInspectionSetting setting = mapper.selectByProjectIdForUpdate(projectId);
        boolean create = setting == null;
        if (create) {
            setting = findOrDefault(projectId);
        }
        boolean reminderFieldPresent = request.getSubmissionReminderEnabled() != null;
        if (reminderFieldPresent && request.getExpectedVersion() == null) {
            throw BusinessException.of(409, "巡检提醒设置版本缺失，请刷新后重试");
        }
        if (request.getExpectedVersion() != null
                && !Objects.equals(value(setting.getVersion()), request.getExpectedVersion())) {
            throw BusinessException.of(409, "巡检设置版本已变化，请刷新后重试");
        }
        if (request.getDailyCutoffTime() != null) setting.setDailyCutoffTime(request.getDailyCutoffTime());
        if (request.getPreDueReminderMinutes() != null) setting.setPreDueReminderMinutes(between(request.getPreDueReminderMinutes(), 0, 720, "提前提醒分钟数"));
        if (request.getReviewDueHours() != null) setting.setReviewDueHours(between(request.getReviewDueHours(), 1, 168, "复核时限"));
        if (request.getRectificationDays() != null) setting.setRectificationDays(between(request.getRectificationDays(), 1, 30, "整改天数"));
        if (request.getEnabled() != null) setting.setEnabled(Boolean.TRUE.equals(request.getEnabled()) ? 1 : 0);
        if (reminderFieldPresent) {
            boolean wasEnabled = Integer.valueOf(1).equals(setting.getSubmissionReminderEnabled());
            boolean enable = Boolean.TRUE.equals(request.getSubmissionReminderEnabled());
            setting.setSubmissionReminderEnabled(enable ? 1 : 0);
            if (enable && !wasEnabled) {
                setting.setReminderEffectiveTime(LocalDateTime.now(BUSINESS_ZONE));
            } else if (!enable) {
                setting.setReminderEffectiveTime(null);
            }
        }
        if (!create) {
            setting.setVersion(value(setting.getVersion()) + 1);
        }
        int affectedRows;
        try {
            affectedRows = create ? mapper.insert(setting) : mapper.updateById(setting);
        } catch (DuplicateKeyException ex) {
            throw BusinessException.of(409, "巡检设置已被并发创建，请刷新后重试");
        }
        if (affectedRows != 1) {
            throw BusinessException.of(409, "巡检设置未生效，请刷新后重试");
        }
        recordOperation(currentUser, projectId, reminderFieldPresent);
        return toVO(setting);
    }

    private int between(int value, int min, int max, String field) {
        if (value < min || value > max) {
            throw new BusinessException(field + "超出允许范围");
        }
        return value;
    }

    private void requireManage(Long projectId, SysUser currentUser) {
        if (currentUser == null || currentUser.getId() == null) {
            throw BusinessException.unauthorized("请先登录");
        }
        if (!permissionService.hasInspectionPermission(
                currentUser.getId(), projectId, InspectionPermissionCodes.PERMISSION_MANAGE)) {
            throw BusinessException.forbidden("无项目巡检设置管理权限");
        }
    }

    private ProjectInspectionSettingVO toVO(ProjectInspectionSetting setting) {
        ProjectInspectionSettingVO vo = new ProjectInspectionSettingVO();
        BeanUtils.copyProperties(setting, vo);
        vo.setEnabled(Integer.valueOf(1).equals(setting.getEnabled()));
        vo.setSubmissionReminderEnabled(Integer.valueOf(1).equals(setting.getSubmissionReminderEnabled()));
        vo.setVersion(value(setting.getVersion()));
        vo.setNextReminderTime(nextReminderTime(setting, LocalDateTime.now(BUSINESS_ZONE)));
        populateResponsibilityHealth(vo, setting.getProjectId());
        return vo;
    }

    static LocalDateTime nextReminderTime(ProjectInspectionSetting setting, LocalDateTime now) {
        if (setting == null || !Integer.valueOf(1).equals(setting.getSubmissionReminderEnabled())
                || setting.getDailyCutoffTime() == null || setting.getReminderEffectiveTime() == null) {
            return null;
        }
        LocalDate date = now.toLocalDate();
        LocalDateTime candidate = LocalDateTime.of(date, setting.getDailyCutoffTime());
        while (!candidate.isAfter(now)
                || !candidate.toLocalDate().atStartOfDay().isAfter(setting.getReminderEffectiveTime())) {
            candidate = candidate.plusDays(1);
        }
        return candidate;
    }

    private void populateResponsibilityHealth(ProjectInspectionSettingVO vo, Long projectId) {
        List<ElectricBox> boxes = electricBoxMapper.selectList(new LambdaQueryWrapper<ElectricBox>()
                .eq(ElectricBox::getProjectId, projectId)
                .eq(ElectricBox::getStatus, "ACTIVE")
                .orderByAsc(ElectricBox::getBoxCode));
        List<InspectionReminderConfigurationIssueVO> details = new ArrayList<>();
        int invalid = 0;
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        for (ElectricBox box : boxes == null ? List.<ElectricBox>of() : boxes) {
            if (!inspectionScopeService.isRequired(box, today)) continue;
            String reason = invalidResponsibilityReason(box);
            if (reason == null) continue;
            invalid++;
            if (details.size() >= MAX_CONFIGURATION_ISSUE_DETAILS) continue;
            InspectionReminderConfigurationIssueVO issue = new InspectionReminderConfigurationIssueVO();
            issue.setBoxId(box.getId());
            issue.setBoxCode(box.getBoxCode());
            issue.setBoxName(box.getBoxName());
            issue.setResponsibleUserId(box.getResponsibleElectricianId());
            issue.setResponsibleUserName(box.getResponsibleElectricianName());
            issue.setReason(reason);
            details.add(issue);
        }
        vo.setReminderConfigurationHealthy(invalid == 0);
        vo.setInvalidReminderBoxCount(invalid);
        vo.setReminderConfigurationIssues(List.copyOf(details));
    }

    private String invalidResponsibilityReason(ElectricBox box) {
        Long userId = box.getResponsibleElectricianId();
        if (userId == null) return "未配置责任电工";
        SysUser user = userMapper.selectById(userId);
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())
                || Integer.valueOf(1).equals(user.getDeleted())) {
            return "责任电工账号无效或已停用";
        }
        if (!"ACTIVE".equals(permissionService.getProjectAccessStatus(userId, box.getProjectId()))) {
            return "责任电工已失去项目访问权限";
        }
        if (!permissionService.hasSystemPermission(userId, box.getProjectId(), SystemPermissionCodes.INSPECTION_SUBMIT)
                || !permissionService.hasInspectionPermission(userId, box.getProjectId(),
                InspectionPermissionCodes.INSPECTION_DAILY_SUBMIT)) {
            return "责任电工缺少日检提交权限";
        }
        return null;
    }

    private void recordOperation(SysUser user, Long projectId, boolean reminderFieldPresent) {
        OperationLog log = new OperationLog();
        log.setUserId(user.getId());
        log.setUsername(displayName(user));
        log.setOperationType(reminderFieldPresent
                ? "ELECTRIC_INSPECTION_REMINDER_SETTING_UPDATE" : "PROJECT_INSPECTION_SETTING_UPDATE");
        log.setOperationDesc(reminderFieldPresent ? "更新电箱日检未提交站内提醒设置" : "更新项目电箱巡检设置");
        log.setBusinessType("PROJECT_INSPECTION_SETTING");
        log.setBusinessId(projectId);
        log.setCreateTime(LocalDateTime.now(BUSINESS_ZONE));
        if (operationLogMapper.insert(log) != 1) {
            throw BusinessException.of(409, "巡检设置操作日志写入未生效");
        }
    }

    private String displayName(SysUser user) {
        return StringUtils.hasText(user.getRealName()) ? user.getRealName().trim() : user.getUsername();
    }

    private int value(Integer actual) {
        return actual == null ? 0 : actual;
    }
}
