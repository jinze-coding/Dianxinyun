package com.example.siteplatform.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.common.PageResult;
import com.example.siteplatform.project.entity.ProjectInfo;
import com.example.siteplatform.project.mapper.ProjectInfoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProjectService {

    @Autowired
    private ProjectInfoMapper projectMapper;

    @Autowired
    private ProjectPermissionService projectPermissionService;

    public List<ProjectInfo> getProjectList(SysUser currentUser) {
        // 平台管理员可以看到所有项目
        if (projectPermissionService.isPlatformAdmin(currentUser.getId())) {
            return projectMapper.selectList(null);
        }
        // 其他用户只能看到有权限的项目
        return projectPermissionService.getUserProjects(currentUser.getId());
    }

    public ProjectInfo getProjectById(Long projectId, SysUser currentUser) {
        // 校验项目权限
        projectPermissionService.checkProjectPermission(currentUser.getId(), projectId);

        ProjectInfo project = projectMapper.selectById(projectId);
        if (project == null) {
            throw BusinessException.notFound("项目不存在");
        }
        return project;
    }

    public PageResult<ProjectInfo> getProjectPage(Integer pageNo, Integer pageSize, SysUser currentUser) {
        Page<ProjectInfo> page = new Page<>(pageNo, pageSize);

        LambdaQueryWrapper<ProjectInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(ProjectInfo::getCreateTime);

        Page<ProjectInfo> result = projectMapper.selectPage(page, wrapper);

        return PageResult.of(
                (int) result.getCurrent(),
                (int) result.getSize(),
                result.getTotal(),
                result.getRecords()
        );
    }

    public ProjectInfo addProject(ProjectInfo project, SysUser currentUser) {
        // 平台管理员才能添加项目
        if (!projectPermissionService.isPlatformAdmin(currentUser.getId())) {
            throw BusinessException.of(403, "只有平台管理员才能添加项目");
        }
        projectMapper.insert(project);
        return project;
    }

    public void deleteProject(Long projectId, SysUser currentUser) {
        // 平台管理员才能删除项目
        if (!projectPermissionService.isPlatformAdmin(currentUser.getId())) {
            throw BusinessException.of(403, "只有平台管理员才能删除项目");
        }
        projectMapper.deleteById(projectId);
    }

    public ProjectInfo updateProject(Long projectId, ProjectInfo project, SysUser currentUser) {
        // 平台管理员才能更新项目
        if (!projectPermissionService.isPlatformAdmin(currentUser.getId())) {
            throw BusinessException.of(403, "只有平台管理员才能更新项目");
        }
        ProjectInfo existing = projectMapper.selectById(projectId);
        if (existing == null) {
            throw BusinessException.notFound("项目不存在");
        }
        // 更新字段
        project.setId(projectId);
        projectMapper.updateById(project);
        return projectMapper.selectById(projectId);
    }
}
