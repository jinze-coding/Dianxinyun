package com.example.siteplatform.quality.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.quality.entity.QualityIssue;
import com.example.siteplatform.quality.mapper.QualityAssigneeMapper;
import com.example.siteplatform.quality.mapper.QualityIssueMapper;
import com.example.siteplatform.quality.vo.QualityAssigneeVO;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.system.constant.SystemPermissionCodes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class QualityAssigneeService {

    @Autowired
    private QualityAssigneeMapper assigneeMapper;

    @Autowired
    private QualityIssueMapper issueMapper;

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private ProjectPermissionService projectPermissionService;

    public List<QualityAssigneeVO> listEligibleAssignees(Long projectId, SysUser currentUser) {
        requireQualityView(projectId, currentUser);
        return assigneeMapper.selectPotentialAssignees(projectId).stream()
                .filter(user -> isEligibleAssignee(user, projectId))
                .map(this::toVO)
                .toList();
    }

    /**
     * 创建和改派统一走这里，避免候选列表与最终写入使用不同资格规则。
     * 未显式指定时保留旧客户端的“当前用户”默认值，但当前用户也必须满足全部资格。
     */
    public SysUser requireEligibleAssignee(Long assigneeId, Long projectId, SysUser currentUser) {
        Long targetId = assigneeId == null && currentUser != null ? currentUser.getId() : assigneeId;
        if (targetId == null) {
            throw new BusinessException("整改负责人不能为空");
        }
        SysUser assignee = userMapper.selectById(targetId);
        if (assignee == null || Integer.valueOf(1).equals(assignee.getDeleted())) {
            throw BusinessException.notFound("整改负责人不存在");
        }
        if (!Integer.valueOf(1).equals(assignee.getStatus())) {
            throw new BusinessException("整改负责人账号未启用");
        }
        if (!"ACTIVE".equals(projectPermissionService.getProjectAccessStatus(targetId, projectId))) {
            throw new BusinessException("整改负责人没有当前项目的有效访问权限");
        }
        if (!projectPermissionService.hasSystemPermission(
                targetId, projectId, SystemPermissionCodes.QUALITY_VIEW)) {
            throw new BusinessException("整改负责人必须启用质量模块并具备质量查看权限");
        }
        if (!projectPermissionService.hasSystemPermission(
                targetId, projectId, SystemPermissionCodes.QUALITY_RECTIFY)) {
            throw new BusinessException("整改负责人必须具备质量整改权限");
        }
        return assignee;
    }

    public boolean isEligibleAssignee(Long userId, Long projectId) {
        if (userId == null || projectId == null) {
            return false;
        }
        return isEligibleAssignee(userMapper.selectById(userId), projectId);
    }

    public long countOpenAssignments(Long projectId, Long userId) {
        if (projectId == null || userId == null) {
            return 0;
        }
        Long count = issueMapper.selectCount(new LambdaQueryWrapper<QualityIssue>()
                .eq(QualityIssue::getProjectId, projectId)
                .eq(QualityIssue::getAssigneeId, userId)
                .notIn(QualityIssue::getStatus, List.of(
                        QualityIssueService.STATUS_CLOSED,
                        QualityIssueService.STATUS_VOIDED)));
        return count == null ? 0 : count;
    }

    private boolean isEligibleAssignee(SysUser user, Long projectId) {
        if (user == null
                || !Integer.valueOf(1).equals(user.getStatus())
                || Integer.valueOf(1).equals(user.getDeleted())) {
            return false;
        }
        Long userId = user.getId();
        return "ACTIVE".equals(projectPermissionService.getProjectAccessStatus(userId, projectId))
                && projectPermissionService.hasSystemPermission(
                userId, projectId, SystemPermissionCodes.QUALITY_VIEW)
                && projectPermissionService.hasSystemPermission(
                userId, projectId, SystemPermissionCodes.QUALITY_RECTIFY);
    }

    private void requireQualityView(Long projectId, SysUser currentUser) {
        if (projectId == null) {
            throw new BusinessException("项目ID不能为空");
        }
        if (currentUser == null) {
            throw BusinessException.unauthorized("请先登录");
        }
        projectPermissionService.checkProjectPermission(currentUser.getId(), projectId);
        projectPermissionService.requireSystemPermission(
                currentUser.getId(), projectId, SystemPermissionCodes.QUALITY_VIEW);
    }

    private QualityAssigneeVO toVO(SysUser user) {
        QualityAssigneeVO vo = new QualityAssigneeVO();
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setDisplayName(StringUtils.hasText(user.getRealName()) ? user.getRealName() : user.getUsername());
        return vo;
    }
}
