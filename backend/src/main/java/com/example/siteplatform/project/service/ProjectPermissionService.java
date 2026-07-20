package com.example.siteplatform.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.project.constant.InspectionPermissionCodes;
import com.example.siteplatform.project.entity.InspectionPermissionTemplate;
import com.example.siteplatform.project.entity.ProjectInfo;
import com.example.siteplatform.project.entity.SysUserProject;
import com.example.siteplatform.project.mapper.InspectionPermissionTemplateMapper;
import com.example.siteplatform.project.mapper.ProjectInfoMapper;
import com.example.siteplatform.project.mapper.SysUserProjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class ProjectPermissionService {

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private ProjectInfoMapper projectMapper;

    @Autowired
    private SysUserProjectMapper userProjectMapper;

    @Autowired
    private InspectionPermissionTemplateMapper permissionTemplateMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String USER_PROJECTS_CACHE_PREFIX = "user:projects:";
    public static final String ROLE_PLATFORM_ADMIN = "PLATFORM_ADMIN";
    public static final String ROLE_PROJECT_ADMIN = "PROJECT_ADMIN";
    public static final String ROLE_SAFETY_ADMIN = "SAFETY_ADMIN";
    public static final String ROLE_USER = "USER";

    public boolean isPlatformAdmin(Long userId) {
        // 保留开发期 userId=1 兜底，同时支持从角色表判断。
        return userId == 1L || hasRole(userId, ROLE_PLATFORM_ADMIN);
    }

    public boolean isProjectAdmin(Long userId) {
        return hasRole(userId, ROLE_PROJECT_ADMIN);
    }

    public boolean canManageProject(Long userId, Long projectId) {
        if (isPlatformAdmin(userId)) {
            return true;
        }
        return hasProjectRole(userId, projectId, ROLE_PROJECT_ADMIN)
                || (isProjectAdmin(userId) && hasProjectPermission(userId, projectId));
    }

    public boolean canManagePersonnel(Long userId, Long projectId) {
        if (isPlatformAdmin(userId)) {
            return true;
        }
        return hasProjectRole(userId, projectId, ROLE_PROJECT_ADMIN, ROLE_SAFETY_ADMIN)
                || ((isProjectAdmin(userId) || isSafetyAdmin(userId)) && hasProjectPermission(userId, projectId));
    }

    public boolean canManageQuality(Long userId, Long projectId) {
        return canManagePersonnel(userId, projectId);
    }

    public boolean hasRole(Long userId, String roleCode) {
        List<String> roleCodes = userMapper.selectRoleCodesByUserId(userId);
        return roleCodes != null && roleCodes.contains(roleCode);
    }

    public boolean isSafetyAdmin(Long userId) {
        return hasRole(userId, ROLE_SAFETY_ADMIN);
    }

    public boolean canManageInspection(Long userId, Long projectId) {
        if (isPlatformAdmin(userId)) {
            return true;
        }
        return hasAnyInspectionPermission(userId, projectId,
                InspectionPermissionCodes.BOX_VIEW,
                InspectionPermissionCodes.BOX_MANAGE,
                InspectionPermissionCodes.BOX_QR_MANAGE,
                InspectionPermissionCodes.BOX_PUBLIC_ACCESS,
                InspectionPermissionCodes.INSPECTION_REVIEW,
                InspectionPermissionCodes.INSPECTION_RECORD_VIEW,
                InspectionPermissionCodes.RECTIFICATION_VIEW,
                InspectionPermissionCodes.RECTIFICATION_REVIEW,
                InspectionPermissionCodes.SUMMARY_VIEW,
                InspectionPermissionCodes.SUMMARY_EXPORT,
                InspectionPermissionCodes.PERMISSION_MANAGE
        );
    }

    public boolean canManageProjectMembers(Long userId, Long projectId) {
        if (isPlatformAdmin(userId)) {
            return true;
        }
        return hasInspectionPermission(userId, projectId, InspectionPermissionCodes.PERMISSION_MANAGE);
    }

    public boolean hasInspectionPermission(Long userId, Long projectId, String permissionCode) {
        if (!StringUtils.hasText(permissionCode)) {
            return false;
        }
        if (isPlatformAdmin(userId)) {
            return true;
        }
        return getInspectionPermissionCodes(userId, projectId).contains(permissionCode.trim().toUpperCase());
    }

    public boolean hasAnyInspectionPermission(Long userId, Long projectId, String... permissionCodes) {
        if (permissionCodes == null || permissionCodes.length == 0) {
            return false;
        }
        if (isPlatformAdmin(userId)) {
            return true;
        }
        Set<String> userCodes = Set.copyOf(getInspectionPermissionCodes(userId, projectId));
        return Arrays.stream(permissionCodes)
                .filter(StringUtils::hasText)
                .map(code -> code.trim().toUpperCase())
                .anyMatch(userCodes::contains);
    }

    public List<String> getInspectionPermissionCodes(Long userId, Long projectId) {
        if (userId == null || projectId == null) {
            return List.of();
        }
        if (isPlatformAdmin(userId)) {
            return InspectionPermissionCodes.ALL_CODES;
        }
        SysUserProject userProject = findUserProject(userId, projectId);
        if (userProject == null) {
            return List.of();
        }
        if (userProject.getInspectionPermissionTemplateId() != null) {
            InspectionPermissionTemplate template = permissionTemplateMapper.selectById(userProject.getInspectionPermissionTemplateId());
            if (template != null
                    && (template.getDeleted() == null || template.getDeleted() == 0)
                    && (template.getEnabled() != null && template.getEnabled() == 1)) {
                return InspectionPermissionCodes.parse(template.getPermissionCodes());
            }
        }
        return InspectionPermissionCodes.defaultCodesForProjectRole(normalizeProjectRoleCode(userProject.getProjectRoleCode()));
    }

    public String getProjectRoleCode(Long userId, Long projectId) {
        if (userId == null || projectId == null) {
            return null;
        }
        SysUserProject userProject = findUserProject(userId, projectId);
        return userProject == null ? null : normalizeProjectRoleCode(userProject.getProjectRoleCode());
    }

    public String getProjectAccessStatus(Long userId, Long projectId) {
        if (userId == null || projectId == null) return null;
        if (isPlatformAdmin(userId)) return "ACTIVE";
        SysUserProject userProject = userProjectMapper.selectOne(new LambdaQueryWrapper<SysUserProject>()
                .eq(SysUserProject::getUserId, userId)
                .eq(SysUserProject::getProjectId, projectId)
                .last("LIMIT 1"));
        return userProject == null ? null : userProject.getStatus();
    }

    private SysUserProject findUserProject(Long userId, Long projectId) {
        return userProjectMapper.selectOne(new LambdaQueryWrapper<SysUserProject>()
                .eq(SysUserProject::getUserId, userId)
                .eq(SysUserProject::getProjectId, projectId)
                .eq(SysUserProject::getStatus, "ACTIVE")
                .last("LIMIT 1"));
    }

    public boolean hasProjectRole(Long userId, Long projectId, String... roleCodes) {
        if (isPlatformAdmin(userId)) {
            return true;
        }
        String projectRoleCode = getProjectRoleCode(userId, projectId);
        if (!StringUtils.hasText(projectRoleCode)) {
            return false;
        }
        return Arrays.asList(roleCodes).contains(projectRoleCode);
    }

    public String normalizeProjectRoleCode(String roleCode) {
        if (!StringUtils.hasText(roleCode)) {
            return ROLE_USER;
        }
        String normalized = roleCode.trim().toUpperCase();
        if (ROLE_PROJECT_ADMIN.equals(normalized) || ROLE_SAFETY_ADMIN.equals(normalized) || ROLE_USER.equals(normalized)) {
            return normalized;
        }
        throw BusinessException.of(400, "项目内职责只支持 PROJECT_ADMIN、SAFETY_ADMIN、USER");
    }

    public List<ProjectInfo> getUserProjects(Long userId) {
        String cacheKey = USER_PROJECTS_CACHE_PREFIX + userId;

        Object cachedProjectIds = redisTemplate.opsForValue().get(cacheKey);
        List<Long> projectIds = parseCachedProjectIds(cachedProjectIds);
        if (cachedProjectIds != null && projectIds == null) {
            redisTemplate.delete(cacheKey);
        }

        if (projectIds == null) {
            projectIds = userMapper.selectProjectIdsByUserId(userId);
            if (projectIds == null || projectIds.isEmpty()) {
                return List.of();
            }
            redisTemplate.opsForValue().set(cacheKey, projectIds, 30, TimeUnit.MINUTES);
        }

        if (projectIds.isEmpty()) {
            return List.of();
        }

        return projectMapper.selectList(new LambdaQueryWrapper<ProjectInfo>()
                .in(ProjectInfo::getId, projectIds));
    }

    private List<Long> parseCachedProjectIds(Object cachedProjectIds) {
        if (cachedProjectIds == null) {
            return null;
        }
        if (!(cachedProjectIds instanceof List<?> items)) {
            return null;
        }
        List<Long> projectIds = new ArrayList<>();
        for (Object item : items) {
            if (!(item instanceof Number number)) {
                return null;
            }
            projectIds.add(number.longValue());
        }
        return projectIds;
    }

    public void checkProjectPermission(Long userId, Long projectId) {
        if (isPlatformAdmin(userId)) {
            return;
        }

        List<ProjectInfo> userProjects = getUserProjects(userId);
        boolean hasPermission = userProjects.stream()
                .anyMatch(p -> p.getId().equals(projectId));

        if (!hasPermission) {
            throw BusinessException.forbidden("无项目访问权限");
        }
    }

    public boolean hasProjectPermission(Long userId, Long projectId) {
        try {
            checkProjectPermission(userId, projectId);
            return true;
        } catch (BusinessException e) {
            return false;
        }
    }

    public void clearUserProjectsCache(Long userId) {
        if (userId != null) {
            redisTemplate.delete(USER_PROJECTS_CACHE_PREFIX + userId);
        }
    }
}
