package com.example.siteplatform.inspection.general.service;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.inspection.general.entity.GeneralInspectionProjectSetting;
import com.example.siteplatform.inspection.general.mapper.GeneralInspectionProjectSettingMapper;
import com.example.siteplatform.project.constant.InspectionPermissionCodes;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.system.constant.SystemPermissionCodes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GeneralInspectionPermissionService {

    private final ProjectPermissionService projectPermissionService;
    private final GeneralInspectionProjectSettingMapper settingMapper;

    public boolean isPlatformAdmin(SysUser user) {
        return user != null && user.getId() != null && projectPermissionService.isPlatformAdmin(user.getId());
    }

    public void requirePlatformAdmin(SysUser user) {
        requireUser(user);
        if (!isPlatformAdmin(user)) {
            throw BusinessException.forbidden("仅平台管理员可启停通用巡检试点");
        }
    }

    public GeneralInspectionProjectSetting getSetting(Long projectId, SysUser user) {
        requireProjectAccess(projectId, user);
        GeneralInspectionProjectSetting setting = settingMapper.selectById(projectId);
        if (setting == null) {
            setting = new GeneralInspectionProjectSetting();
            setting.setProjectId(projectId);
            setting.setEnabled(0);
            setting.setVersion(0);
        }
        return setting;
    }

    public void requireEnabled(Long projectId, SysUser user) {
        requireProjectAccess(projectId, user);
        GeneralInspectionProjectSetting setting = settingMapper.selectById(projectId);
        if (setting == null || !Integer.valueOf(1).equals(setting.getEnabled())) {
            throw BusinessException.forbidden("当前项目尚未启用通用巡检");
        }
    }

    public void requireView(Long projectId, SysUser user) {
        requireEnabled(projectId, user);
        requireSystem(projectId, user, SystemPermissionCodes.INSPECTION_VIEW, "无通用巡检查看权限");
    }

    public void requireRecordView(Long projectId, SysUser user) {
        requireView(projectId, user);
        if (!projectPermissionService.hasInspectionPermission(user.getId(), projectId,
                InspectionPermissionCodes.INSPECTION_RECORD_VIEW)) {
            throw BusinessException.forbidden("无通用巡检记录查看权限");
        }
    }

    public void requireSummaryView(Long projectId, SysUser user) {
        requireView(projectId, user);
        if (!projectPermissionService.hasInspectionPermission(user.getId(), projectId,
                InspectionPermissionCodes.SUMMARY_VIEW)) {
            throw BusinessException.forbidden("无通用巡检看板权限");
        }
    }

    public void requireManage(Long projectId, SysUser user) {
        requireEnabled(projectId, user);
        requireSystem(projectId, user, SystemPermissionCodes.INSPECTION_MANAGE, "无通用巡检管理权限");
    }

    public void requireSubmit(Long projectId, SysUser user) {
        requireEnabled(projectId, user);
        requireSystem(projectId, user, SystemPermissionCodes.INSPECTION_SUBMIT, "无通用巡检执行权限");
        if (!projectPermissionService.hasInspectionPermission(user.getId(), projectId,
                InspectionPermissionCodes.CUSTOM_INSPECTION_SUBMIT)) {
            throw BusinessException.forbidden("未授予通用巡检提交权限");
        }
    }

    public void requireRectify(Long projectId, SysUser user) {
        requireEnabled(projectId, user);
        requireSystem(projectId, user, SystemPermissionCodes.INSPECTION_RECTIFY, "无巡检整改权限");
    }

    public void requireReview(Long projectId, SysUser user) {
        requireEnabled(projectId, user);
        requireSystem(projectId, user, SystemPermissionCodes.INSPECTION_REVIEW, "无巡检复查权限");
    }

    public void requireExport(Long projectId, SysUser user) {
        requireEnabled(projectId, user);
        requireSystem(projectId, user, SystemPermissionCodes.INSPECTION_EXPORT, "无巡检导出权限");
    }

    public boolean canManage(Long projectId, SysUser user) {
        if (user == null || user.getId() == null) return false;
        try {
            requireManage(projectId, user);
            return true;
        } catch (BusinessException ex) {
            return false;
        }
    }

    public boolean hasActiveProjectAccess(Long projectId, Long userId) {
        return projectId != null && userId != null
                && "ACTIVE".equals(projectPermissionService.getProjectAccessStatus(userId, projectId));
    }

    public boolean hasSystemPermission(Long projectId, Long userId, String permissionCode) {
        return projectId != null && userId != null
                && projectPermissionService.hasSystemPermission(userId, projectId, permissionCode);
    }

    public boolean hasInspectionPermission(Long projectId, Long userId, String permissionCode) {
        return projectId != null && userId != null
                && projectPermissionService.hasInspectionPermission(userId, projectId, permissionCode);
    }

    private void requireProjectAccess(Long projectId, SysUser user) {
        requireUser(user);
        if (projectId == null) throw new BusinessException("项目ID不能为空");
        projectPermissionService.checkProjectPermission(user.getId(), projectId);
    }

    private void requireSystem(Long projectId, SysUser user, String permissionCode, String message) {
        if (!projectPermissionService.hasSystemPermission(user.getId(), projectId, permissionCode)) {
            throw BusinessException.forbidden(message);
        }
    }

    private void requireUser(SysUser user) {
        if (user == null || user.getId() == null) {
            throw BusinessException.unauthorized("请先登录");
        }
    }
}
