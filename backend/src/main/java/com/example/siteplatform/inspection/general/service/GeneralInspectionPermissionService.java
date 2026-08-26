package com.example.siteplatform.inspection.general.service;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.inspection.general.entity.GeneralInspectionProjectSetting;
import com.example.siteplatform.inspection.general.mapper.GeneralInspectionProjectSettingMapper;
import com.example.siteplatform.project.constant.InspectionPermissionCodes;
import com.example.siteplatform.project.service.ProjectPermissionService;
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
            throw BusinessException.forbidden("仅平台管理员可启停临边巡检试点");
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

    public void requireFeatureView(Long projectId, SysUser user) {
        requireProjectAccess(projectId, user);
        if (isPlatformAdmin(user)) return;
        requireInspection(projectId, user, InspectionPermissionCodes.EDGE_INSPECTION_VIEW,
                "无临边巡检查看权限");
    }

    public void requireEnabled(Long projectId, SysUser user) {
        requireProjectAccess(projectId, user);
        GeneralInspectionProjectSetting setting = settingMapper.selectById(projectId);
        if (setting == null || !Integer.valueOf(1).equals(setting.getEnabled())) {
            throw BusinessException.forbidden("当前项目尚未启用临边巡检");
        }
    }

    public void requireView(Long projectId, SysUser user) {
        requireEnabled(projectId, user);
        requireInspection(projectId, user, InspectionPermissionCodes.EDGE_INSPECTION_VIEW, "无临边巡检查看权限");
    }

    public void requireRecordView(Long projectId, SysUser user) {
        requireView(projectId, user);
    }

    public void requireSummaryView(Long projectId, SysUser user) {
        requireView(projectId, user);
    }

    public void requireManage(Long projectId, SysUser user) {
        requireEnabled(projectId, user);
        requireInspection(projectId, user, InspectionPermissionCodes.EDGE_INSPECTION_MANAGE, "无临边巡检管理权限");
    }

    public void requireSubmit(Long projectId, SysUser user) {
        requireEnabled(projectId, user);
        requireInspection(projectId, user, InspectionPermissionCodes.EDGE_INSPECTION_SUBMIT, "无临边巡检执行权限");
    }

    public void requireRectify(Long projectId, SysUser user) {
        requireEnabled(projectId, user);
        requireInspection(projectId, user, InspectionPermissionCodes.EDGE_INSPECTION_RECTIFY, "无临边巡检整改权限");
    }

    public void requireReview(Long projectId, SysUser user) {
        requireEnabled(projectId, user);
        requireInspection(projectId, user, InspectionPermissionCodes.EDGE_INSPECTION_REVIEW, "无临边巡检复查权限");
    }

    public void requireExport(Long projectId, SysUser user) {
        throw BusinessException.forbidden("固定式临边巡检首版不提供导出");
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

    private void requireInspection(Long projectId, SysUser user, String permissionCode, String message) {
        if (!projectPermissionService.hasInspectionPermission(user.getId(), projectId, permissionCode)) {
            throw BusinessException.forbidden(message);
        }
    }

    private void requireUser(SysUser user) {
        if (user == null || user.getId() == null) {
            throw BusinessException.unauthorized("请先登录");
        }
    }
}
