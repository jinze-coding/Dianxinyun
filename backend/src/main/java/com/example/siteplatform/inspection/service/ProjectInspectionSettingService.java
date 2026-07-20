package com.example.siteplatform.inspection.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.inspection.dto.ProjectInspectionSettingRequest;
import com.example.siteplatform.inspection.entity.ProjectInspectionSetting;
import com.example.siteplatform.inspection.mapper.ProjectInspectionSettingMapper;
import com.example.siteplatform.inspection.vo.ProjectInspectionSettingVO;
import com.example.siteplatform.project.constant.InspectionPermissionCodes;
import com.example.siteplatform.project.service.ProjectPermissionService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;

@Service
public class ProjectInspectionSettingService {

    private final ProjectInspectionSettingMapper mapper;
    private final ProjectPermissionService permissionService;

    public ProjectInspectionSettingService(ProjectInspectionSettingMapper mapper,
                                           ProjectPermissionService permissionService) {
        this.mapper = mapper;
        this.permissionService = permissionService;
    }

    public ProjectInspectionSettingVO get(Long projectId, SysUser currentUser) {
        permissionService.checkProjectPermission(currentUser.getId(), projectId);
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
        return setting;
    }

    @Transactional
    public ProjectInspectionSettingVO save(Long projectId, ProjectInspectionSettingRequest request, SysUser currentUser) {
        if (!permissionService.hasInspectionPermission(currentUser.getId(), projectId, InspectionPermissionCodes.PERMISSION_MANAGE)) {
            throw BusinessException.forbidden("无项目巡检设置管理权限");
        }
        if (request == null) {
            throw new BusinessException("巡检设置不能为空");
        }
        ProjectInspectionSetting setting = mapper.selectOne(new LambdaQueryWrapper<ProjectInspectionSetting>()
                .eq(ProjectInspectionSetting::getProjectId, projectId)
                .last("LIMIT 1"));
        boolean create = setting == null;
        if (create) {
            setting = findOrDefault(projectId);
        }
        if (request.getDailyCutoffTime() != null) setting.setDailyCutoffTime(request.getDailyCutoffTime());
        if (request.getPreDueReminderMinutes() != null) setting.setPreDueReminderMinutes(between(request.getPreDueReminderMinutes(), 0, 720, "提前提醒分钟数"));
        if (request.getReviewDueHours() != null) setting.setReviewDueHours(between(request.getReviewDueHours(), 1, 168, "复核时限"));
        if (request.getRectificationDays() != null) setting.setRectificationDays(between(request.getRectificationDays(), 1, 30, "整改天数"));
        if (request.getEnabled() != null) setting.setEnabled(Boolean.TRUE.equals(request.getEnabled()) ? 1 : 0);
        if (create) mapper.insert(setting); else mapper.updateById(setting);
        return toVO(setting);
    }

    private int between(int value, int min, int max, String field) {
        if (value < min || value > max) {
            throw new BusinessException(field + "超出允许范围");
        }
        return value;
    }

    private ProjectInspectionSettingVO toVO(ProjectInspectionSetting setting) {
        ProjectInspectionSettingVO vo = new ProjectInspectionSettingVO();
        BeanUtils.copyProperties(setting, vo);
        vo.setEnabled(Integer.valueOf(1).equals(setting.getEnabled()));
        return vo;
    }
}
