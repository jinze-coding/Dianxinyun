package com.example.siteplatform.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.siteplatform.auth.dto.*;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.entity.SysUserWechatBinding;
import com.example.siteplatform.auth.entity.WechatAccessApplication;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.auth.mapper.SysUserWechatBindingMapper;
import com.example.siteplatform.auth.mapper.WechatAccessApplicationMapper;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.log.entity.OperationLog;
import com.example.siteplatform.log.mapper.OperationLogMapper;
import com.example.siteplatform.project.entity.InspectionPermissionTemplate;
import com.example.siteplatform.project.entity.ProjectInfo;
import com.example.siteplatform.project.entity.SysUserProject;
import com.example.siteplatform.project.mapper.InspectionPermissionTemplateMapper;
import com.example.siteplatform.project.mapper.ProjectInfoMapper;
import com.example.siteplatform.project.mapper.SysUserProjectMapper;
import com.example.siteplatform.project.service.ProjectPermissionService;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class WechatUserManagementService {

    private final SysUserWechatBindingMapper bindingMapper;
    private final SysUserMapper userMapper;
    private final SysUserProjectMapper userProjectMapper;
    private final ProjectInfoMapper projectMapper;
    private final InspectionPermissionTemplateMapper templateMapper;
    private final WechatAccessApplicationMapper applicationMapper;
    private final OperationLogMapper operationLogMapper;
    private final ProjectPermissionService permissionService;
    private final AuthService authService;

    public WechatUserManagementService(SysUserWechatBindingMapper bindingMapper, SysUserMapper userMapper,
                                       SysUserProjectMapper userProjectMapper, ProjectInfoMapper projectMapper,
                                       InspectionPermissionTemplateMapper templateMapper,
                                       WechatAccessApplicationMapper applicationMapper,
                                       OperationLogMapper operationLogMapper, ProjectPermissionService permissionService,
                                       AuthService authService) {
        this.bindingMapper = bindingMapper;
        this.userMapper = userMapper;
        this.userProjectMapper = userProjectMapper;
        this.projectMapper = projectMapper;
        this.templateMapper = templateMapper;
        this.applicationMapper = applicationMapper;
        this.operationLogMapper = operationLogMapper;
        this.permissionService = permissionService;
        this.authService = authService;
    }

    public WechatUserPageVO list(Long projectId, String keyword, String bindingStatus,
                                                 String projectAccessStatus, String projectRoleCode,
                                                 Long permissionTemplateId, Integer pageNo, Integer pageSize,
                                                 SysUser currentUser) {
        requireListPermission(projectId, currentUser);
        boolean platformAdmin = permissionService.isPlatformAdmin(currentUser.getId());
        int currentPage = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int size = pageSize == null ? 20 : Math.max(1, Math.min(pageSize, 100));
        List<SysUserWechatBinding> bindings = bindingMapper.selectList(new LambdaQueryWrapper<SysUserWechatBinding>()
                .eq(SysUserWechatBinding::getDeleted, 0)
                .orderByDesc(SysUserWechatBinding::getLastLoginTime)
                .orderByDesc(SysUserWechatBinding::getId));
        Map<Long, SysUserWechatBinding> currentBindings = new LinkedHashMap<>();
        for (SysUserWechatBinding binding : bindings) {
            currentBindings.merge(binding.getUserId(), binding, this::preferCurrentBinding);
        }
        List<SysUserWechatBinding> selectedBindings = currentBindings.values().stream()
                .sorted(Comparator.comparing(this::bindingSortTime, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(SysUserWechatBinding::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        List<WechatUserListItemVO> items = new ArrayList<>();
        for (SysUserWechatBinding binding : selectedBindings) {
            if (StringUtils.hasText(bindingStatus) && !bindingStatus.trim().equalsIgnoreCase(binding.getStatus())) continue;
            SysUser user = userMapper.selectById(binding.getUserId());
            if (user == null) continue;
            if (StringUtils.hasText(keyword)) {
                String value = keyword.trim().toLowerCase();
                String haystack = String.join(" ", nullToEmpty(user.getUsername()), nullToEmpty(user.getRealName()),
                        nullToEmpty(user.getPhone()), nullToEmpty(binding.getPhone())).toLowerCase();
                if (!haystack.contains(value)) continue;
            }
            List<SysUserProject> projects = userProjectMapper.selectList(new LambdaQueryWrapper<SysUserProject>()
                    .eq(SysUserProject::getUserId, user.getId())
                    .orderByAsc(SysUserProject::getProjectId));
            SysUserProject selected = projectId == null ? projects.stream().findFirst().orElse(null)
                    : projects.stream().filter(item -> Objects.equals(item.getProjectId(), projectId)).findFirst().orElse(null);
            if (projectId != null && selected == null) continue;
            if (StringUtils.hasText(projectAccessStatus)
                    && (selected == null || !projectAccessStatus.trim().equalsIgnoreCase(selected.getStatus()))) continue;
            if (StringUtils.hasText(projectRoleCode)
                    && (selected == null || !projectRoleCode.trim().equalsIgnoreCase(selected.getProjectRoleCode()))) continue;
            if (permissionTemplateId != null
                    && (selected == null || !permissionTemplateId.equals(selected.getInspectionPermissionTemplateId()))) continue;
            List<SysUserProject> visibleProjects = platformAdmin ? projects
                    : projects.stream().filter(item -> Objects.equals(item.getProjectId(), projectId)).toList();
            items.add(toListItem(binding, user, visibleProjects, selected));
        }
        int from = Math.min((currentPage - 1) * size, items.size());
        int to = Math.min(from + size, items.size());
        return WechatUserPageVO.of(currentPage, size, (long) items.size(), items.subList(from, to));
    }

    private SysUserWechatBinding preferCurrentBinding(SysUserWechatBinding left, SysUserWechatBinding right) {
        int leftPriority = bindingStatusPriority(left.getStatus());
        int rightPriority = bindingStatusPriority(right.getStatus());
        if (leftPriority != rightPriority) return leftPriority > rightPriority ? left : right;
        LocalDateTime leftTime = bindingSortTime(left);
        LocalDateTime rightTime = bindingSortTime(right);
        if (leftTime == null) return rightTime == null && value(left.getId()) >= value(right.getId()) ? left : right;
        if (rightTime == null) return left;
        int compared = leftTime.compareTo(rightTime);
        return compared == 0 ? (value(left.getId()) >= value(right.getId()) ? left : right) : (compared > 0 ? left : right);
    }

    private int bindingStatusPriority(String status) {
        if ("ACTIVE".equalsIgnoreCase(status)) return 3;
        if ("DISABLED".equalsIgnoreCase(status)) return 2;
        return 1;
    }

    private LocalDateTime bindingSortTime(SysUserWechatBinding binding) {
        if (binding.getLastLoginTime() != null) return binding.getLastLoginTime();
        if (binding.getUpdateTime() != null) return binding.getUpdateTime();
        return binding.getBindTime();
    }

    private long value(Long value) { return value == null ? 0L : value; }

    public WechatUserDetailVO detail(Long userId, Long projectId, SysUser currentUser) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) throw BusinessException.notFound("小程序用户不存在");
        boolean platformAdmin = permissionService.isPlatformAdmin(currentUser.getId());
        if (!platformAdmin) {
            if (projectId == null) throw new BusinessException("项目ID不能为空");
            if (!permissionService.canManageProjectMembers(currentUser.getId(), projectId)) {
                throw BusinessException.forbidden("无该项目小程序用户查看权限");
            }
        }
        List<SysUserProject> projects = userProjectMapper.selectList(new LambdaQueryWrapper<SysUserProject>()
                .eq(SysUserProject::getUserId, userId).orderByAsc(SysUserProject::getProjectId));
        if (!platformAdmin && projects.stream().noneMatch(item -> Objects.equals(item.getProjectId(), projectId))) {
            throw BusinessException.forbidden("无该小程序用户查看权限");
        }
        List<SysUserProject> visibleProjects = projectId == null ? projects
                : projects.stream().filter(item -> Objects.equals(item.getProjectId(), projectId)).toList();
        WechatUserDetailVO result = new WechatUserDetailVO();
        result.setUserId(user.getId()); result.setUsername(user.getUsername()); result.setRealName(user.getRealName());
        result.setPhone(user.getPhone()); result.setStatus(user.getStatus());
        result.setBindings(platformAdmin
                ? bindingMapper.selectList(new LambdaQueryWrapper<SysUserWechatBinding>()
                        .eq(SysUserWechatBinding::getUserId, userId).eq(SysUserWechatBinding::getDeleted, 0)
                        .orderByDesc(SysUserWechatBinding::getId)).stream().map(this::toBindingVO).toList()
                : List.of());
        result.setProjects(visibleProjects.stream().map(this::toProjectVO).toList());
        LambdaQueryWrapper<WechatAccessApplication> applicationQuery =
                new LambdaQueryWrapper<WechatAccessApplication>()
                        .eq(WechatAccessApplication::getMatchedUserId, userId)
                        .eq(projectId != null, WechatAccessApplication::getProjectId, projectId)
                        .orderByDesc(WechatAccessApplication::getCreateTime);
        result.setApplications(applicationMapper.selectList(applicationQuery).stream()
                .filter(item -> projectId == null || Objects.equals(item.getProjectId(), projectId))
                .map(this::toApplicationVO).toList());
        result.setOperationLogs(platformAdmin
                ? operationLogMapper.selectList(new LambdaQueryWrapper<OperationLog>()
                        .eq(OperationLog::getBusinessId, userId)
                        .in(OperationLog::getBusinessType, "WECHAT_USER", "WECHAT_PROJECT_ACCESS")
                        .orderByDesc(OperationLog::getCreateTime).last("LIMIT 100"))
                        .stream().map(this::toLogVO).toList()
                : List.of());
        return result;
    }

    @Transactional
    public WechatBindingVO updateBindingStatus(Long userId, Long bindingId, WechatBindingStatusRequest request, SysUser operator) {
        requirePlatformAdmin(operator);
        SysUserWechatBinding binding = requireBinding(userId, bindingId);
        if (request == null || !StringUtils.hasText(request.getStatus())) throw new BusinessException("微信绑定状态不能为空");
        String status = request.getStatus().trim().toUpperCase();
        if (!"ACTIVE".equals(status) && !"DISABLED".equals(status)) throw new BusinessException("微信绑定状态只支持 ACTIVE 或 DISABLED");
        if (!StringUtils.hasText(request.getReason())) throw new BusinessException("微信绑定状态变更原因不能为空");
        if ("UNBOUND".equals(binding.getStatus())) throw new BusinessException("已解绑微信需重新申请绑定，不能直接恢复");
        if ("ACTIVE".equals(status)) ensureNoOtherActiveBinding(binding);
        binding.setStatus(status); binding.setUpdateTime(LocalDateTime.now());
        try {
            if (bindingMapper.updateById(binding) != 1) {
                throw BusinessException.of(409, "微信绑定状态已变化，请刷新后重试");
            }
        } catch (DuplicateKeyException exception) {
            throw BusinessException.of(409, "微信或系统账号已有其他有效绑定，请刷新后重试");
        }
        authService.logout(userId);
        authService.repeatLogoutAfterCommit(userId);
        record(userId, operator, "ACTIVE".equals(status) ? "RESTORE_WECHAT_BINDING" : "DISABLE_WECHAT_BINDING",
                "微信绑定" + bindingId + "状态变更为" + status, request.getReason());
        return toBindingVO(binding);
    }

    @Transactional
    public WechatBindingVO unbind(Long userId, Long bindingId, WechatUnbindRequest request, SysUser operator) {
        requirePlatformAdmin(operator);
        if (request == null || !StringUtils.hasText(request.getReason())) throw new BusinessException("解绑原因不能为空");
        SysUserWechatBinding binding = requireBinding(userId, bindingId);
        binding.setStatus("UNBOUND"); binding.setUpdateTime(LocalDateTime.now());
        if (bindingMapper.updateById(binding) != 1) {
            throw BusinessException.of(409, "微信绑定状态已变化，请刷新后重试");
        }
        authService.logout(userId);
        authService.repeatLogoutAfterCommit(userId);
        record(userId, operator, "UNBIND_WECHAT", "解除微信绑定" + bindingId, request.getReason());
        return toBindingVO(binding);
    }

    private void requireListPermission(Long projectId, SysUser user) {
        if (permissionService.isPlatformAdmin(user.getId())) return;
        if (projectId == null) throw new BusinessException("项目ID不能为空");
        if (!permissionService.canManageProjectMembers(user.getId(), projectId)) throw BusinessException.forbidden("无小程序用户管理权限");
    }

    private void requirePlatformAdmin(SysUser user) {
        if (!permissionService.isPlatformAdmin(user.getId())) throw BusinessException.forbidden("只有平台管理员可以管理微信绑定");
    }

    private SysUserWechatBinding requireBinding(Long userId, Long bindingId) {
        SysUserWechatBinding binding = bindingMapper.selectById(bindingId);
        if (binding == null || !Objects.equals(binding.getUserId(), userId) || Integer.valueOf(1).equals(binding.getDeleted())) {
            throw BusinessException.notFound("微信绑定不存在");
        }
        return binding;
    }

    private void ensureNoOtherActiveBinding(SysUserWechatBinding binding) {
        Long count = bindingMapper.selectCount(new LambdaQueryWrapper<SysUserWechatBinding>()
                .eq(SysUserWechatBinding::getUserId, binding.getUserId()).eq(SysUserWechatBinding::getAppId, binding.getAppId())
                .eq(SysUserWechatBinding::getStatus, "ACTIVE").eq(SysUserWechatBinding::getDeleted, 0)
                .ne(SysUserWechatBinding::getId, binding.getId()));
        if (count != null && count > 0) {
            throw BusinessException.of(409, "同一小程序账号已绑定其他微信，请先解绑原微信");
        }
        if (StringUtils.hasText(binding.getUnionid())) {
            Long unionidCount = bindingMapper.selectCount(new LambdaQueryWrapper<SysUserWechatBinding>()
                    .eq(SysUserWechatBinding::getAppId, binding.getAppId())
                    .eq(SysUserWechatBinding::getUnionid, binding.getUnionid())
                    .eq(SysUserWechatBinding::getStatus, "ACTIVE")
                    .eq(SysUserWechatBinding::getDeleted, 0)
                    .ne(SysUserWechatBinding::getId, binding.getId()));
            if (unionidCount != null && unionidCount > 0) {
                throw BusinessException.of(409, "该微信 UnionID 已绑定其他系统账号");
            }
        }
    }

    private WechatUserListItemVO toListItem(SysUserWechatBinding binding, SysUser user, List<SysUserProject> projects, SysUserProject selected) {
        WechatUserListItemVO vo = new WechatUserListItemVO();
        vo.setBindingId(binding.getId()); vo.setUserId(user.getId()); vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName()); vo.setPhone(StringUtils.hasText(binding.getPhone()) ? binding.getPhone() : user.getPhone());
        vo.setBindingStatus(binding.getStatus()); vo.setBindTime(binding.getBindTime()); vo.setLastLoginTime(binding.getLastLoginTime());
        vo.setProjectCount(projects.size()); vo.setRegistrationSource(hasApprovedApplication(user.getId()) ? "小程序审批" : "账号验证绑定");
        if (selected != null) {
            vo.setProjectId(selected.getProjectId()); vo.setProjectRoleCode(selected.getProjectRoleCode());
            vo.setPermissionTemplateId(selected.getInspectionPermissionTemplateId()); vo.setProjectAccessStatus(selected.getStatus());
            ProjectInfo project = projectMapper.selectById(selected.getProjectId());
            if (project != null) vo.setProjectName(project.getProjectName());
            InspectionPermissionTemplate template = selected.getInspectionPermissionTemplateId() == null ? null : templateMapper.selectById(selected.getInspectionPermissionTemplateId());
            if (template != null) vo.setPermissionTemplateName(template.getTemplateName());
        }
        return vo;
    }

    private boolean hasApprovedApplication(Long userId) {
        Long count = applicationMapper.selectCount(new LambdaQueryWrapper<WechatAccessApplication>()
                .eq(WechatAccessApplication::getMatchedUserId, userId).eq(WechatAccessApplication::getStatus, "APPROVED"));
        return count != null && count > 0;
    }

    private WechatBindingVO toBindingVO(SysUserWechatBinding binding) {
        WechatBindingVO vo = new WechatBindingVO();
        vo.setId(binding.getId()); vo.setAppId(binding.getAppId()); vo.setPhone(binding.getPhone());
        vo.setStatus(binding.getStatus()); vo.setBindTime(binding.getBindTime()); vo.setLastLoginTime(binding.getLastLoginTime());
        return vo;
    }

    private WechatUserProjectVO toProjectVO(SysUserProject item) {
        WechatUserProjectVO vo = new WechatUserProjectVO();
        vo.setMemberId(item.getId()); vo.setProjectId(item.getProjectId()); vo.setProjectRoleCode(item.getProjectRoleCode());
        vo.setPermissionTemplateId(item.getInspectionPermissionTemplateId()); vo.setAccessStatus(item.getStatus());
        vo.setStatusReason(item.getStatusReason()); vo.setStatusChangedTime(item.getStatusChangedTime());
        ProjectInfo project = projectMapper.selectById(item.getProjectId()); if (project != null) vo.setProjectName(project.getProjectName());
        InspectionPermissionTemplate template = item.getInspectionPermissionTemplateId() == null ? null : templateMapper.selectById(item.getInspectionPermissionTemplateId());
        if (template != null) vo.setPermissionTemplateName(template.getTemplateName());
        return vo;
    }

    private WechatAccessApplicationVO toApplicationVO(WechatAccessApplication item) {
        WechatAccessApplicationVO vo = new WechatAccessApplicationVO(); BeanUtils.copyProperties(item, vo);
        ProjectInfo project = projectMapper.selectById(item.getProjectId()); if (project != null) vo.setProjectName(project.getProjectName());
        vo.setApplicationType(item.getMatchedUserId() == null ? "NEW_REGISTRATION" : "PROJECT_ACCESS");
        return vo;
    }

    private WechatOperationLogVO toLogVO(OperationLog item) {
        WechatOperationLogVO vo = new WechatOperationLogVO();
        vo.setId(item.getId()); vo.setOperationType(item.getOperationType()); vo.setOperationDesc(item.getOperationDesc());
        vo.setOperatorName(item.getUsername()); vo.setCreateTime(item.getCreateTime()); return vo;
    }

    private void record(Long userId, SysUser operator, String type, String description, String reason) {
        OperationLog log = new OperationLog(); log.setUserId(operator.getId()); log.setUsername(operator.getUsername());
        log.setOperationType(type); log.setOperationDesc(description + (StringUtils.hasText(reason) ? "，原因：" + reason.trim() : ""));
        log.setBusinessType("WECHAT_USER"); log.setBusinessId(userId); log.setCreateTime(LocalDateTime.now()); operationLogMapper.insert(log);
    }

    private String nullToEmpty(String value) { return value == null ? "" : value; }
}
