package com.example.siteplatform.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.project.entity.ProjectInfo;
import com.example.siteplatform.project.mapper.ProjectInfoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class ProjectPermissionService {

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private ProjectInfoMapper projectMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String USER_PROJECTS_CACHE_PREFIX = "user:projects:";
    private static final String PLATFORM_ADMIN_CACHE_KEY = "platform:admins";

    public boolean isPlatformAdmin(Long userId) {
        // 简化判断：userId为1的是平台管理员
        // 实际应查询sys_user_role表
        return userId == 1L;
    }

    public List<ProjectInfo> getUserProjects(Long userId) {
        String cacheKey = USER_PROJECTS_CACHE_PREFIX + userId;

        @SuppressWarnings("unchecked")
        List<ProjectInfo> cached = (List<ProjectInfo>) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return cached;
        }

        // 实际应从sys_user_project表查询
        // 目前简化：userId为1可以看到所有项目，其他用户只能看到project_id为1的项目
        LambdaQueryWrapper<ProjectInfo> wrapper = new LambdaQueryWrapper<>();
        if (userId != 1L) {
            wrapper.eq(ProjectInfo::getId, 1L);
        }
        List<ProjectInfo> projects = projectMapper.selectList(wrapper);

        redisTemplate.opsForValue().set(cacheKey, projects, 30, TimeUnit.MINUTES);

        return projects;
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
}
