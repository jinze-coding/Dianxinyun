package com.example.siteplatform.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.project.entity.ProjectInfo;
import com.example.siteplatform.project.entity.SysUserProject;
import com.example.siteplatform.project.mapper.ProjectInfoMapper;
import com.example.siteplatform.project.mapper.SysUserProjectMapper;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.seal.entity.SealDefinition;
import com.example.siteplatform.seal.mapper.SealDefinitionMapper;
import com.example.siteplatform.seal.vo.SealUserOptionVO;
import com.example.siteplatform.system.constant.SystemPermissionCodes;
import com.example.siteplatform.workflow.dto.ApprovalConfigSaveRequest;
import com.example.siteplatform.workflow.entity.WorkflowApprovalConfig;
import com.example.siteplatform.workflow.entity.WorkflowApprovalConfigUser;
import com.example.siteplatform.workflow.mapper.WorkflowApprovalConfigMapper;
import com.example.siteplatform.workflow.mapper.WorkflowApprovalConfigUserMapper;
import com.example.siteplatform.workflow.vo.ApprovalConfigVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
public class WorkflowApprovalConfigService {
    public static final String SEAL_BUSINESS_CODE = "SEAL_APPLICATION";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final WorkflowApprovalConfigMapper configMapper;
    private final WorkflowApprovalConfigUserMapper configUserMapper;
    private final SealDefinitionMapper sealMapper;
    private final SysUserProjectMapper userProjectMapper;
    private final SysUserMapper userMapper;
    private final ProjectInfoMapper projectMapper;
    private final ProjectPermissionService permissionService;

    public WorkflowApprovalConfigService(WorkflowApprovalConfigMapper configMapper,
                                         WorkflowApprovalConfigUserMapper configUserMapper,
                                         SealDefinitionMapper sealMapper,
                                         SysUserProjectMapper userProjectMapper,
                                         SysUserMapper userMapper,
                                         ProjectInfoMapper projectMapper,
                                         ProjectPermissionService permissionService) {
        this.configMapper = configMapper;
        this.configUserMapper = configUserMapper;
        this.sealMapper = sealMapper;
        this.userProjectMapper = userProjectMapper;
        this.userMapper = userMapper;
        this.projectMapper = projectMapper;
        this.permissionService = permissionService;
    }

    public List<ApprovalConfigVO> list(String businessCode, Long projectId, SysUser currentUser) {
        String code = normalizeBusinessCode(businessCode);
        requireView(currentUser, projectId);
        return configMapper.selectList(new LambdaQueryWrapper<WorkflowApprovalConfig>()
                        .eq(WorkflowApprovalConfig::getBusinessCode, code)
                        .eq(projectId != null, WorkflowApprovalConfig::getProjectId, projectId)
                        .orderByAsc(WorkflowApprovalConfig::getProjectId)
                        .orderByAsc(WorkflowApprovalConfig::getSealId))
                .stream().map(this::toVO).toList();
    }

    @Transactional
    public ApprovalConfigVO save(ApprovalConfigSaveRequest request, SysUser currentUser) {
        if (request == null) throw new BusinessException("审批配置不能为空");
        String businessCode = normalizeBusinessCode(request.getBusinessCode());
        Long projectId = request.getProjectId();
        requireManage(currentUser, projectId);
        SealDefinition seal = sealMapper.selectById(request.getSealId());
        if (seal == null || !Objects.equals(seal.getProjectId(), projectId)) {
            throw new BusinessException("印章不属于当前项目");
        }
        List<Long> approvers = distinctIds(request.getApproverUserIds());
        List<Long> defaultCc = distinctIds(request.getDefaultCcUserIds());
        if (!Boolean.FALSE.equals(request.getEnabled()) && approvers.isEmpty()) {
            throw new BusinessException("启用审批配置时至少选择一位审批人");
        }
        Set<Long> allUsers = new LinkedHashSet<>(approvers);
        allUsers.addAll(defaultCc);
        // Serialize eligibility with account/member disablement. A stable user-id
        // order avoids deadlocks when two administrators edit overlapping lists.
        allUsers.stream().sorted().forEach(userId -> requireEligibleUserForUpdate(projectId, userId));

        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        WorkflowApprovalConfig config = configMapper.selectForUpdate(businessCode, projectId, request.getSealId());
        if (config == null) {
            config = new WorkflowApprovalConfig();
            config.setBusinessCode(businessCode);
            config.setProjectId(projectId);
            config.setSealId(request.getSealId());
            config.setApprovalMode("ANY_ONE");
            config.setEnabled(Boolean.FALSE.equals(request.getEnabled()) ? 0 : 1);
            config.setConfigVersion(1);
            config.setCreatedBy(currentUser.getId());
            config.setUpdatedBy(currentUser.getId());
            config.setCreateTime(now);
            config.setUpdateTime(now);
            requireSingleWrite(configMapper.insert(config), "审批配置新增");
        } else {
            List<WorkflowApprovalConfigUser> oldUsers = configUserMapper.selectList(
                    new LambdaQueryWrapper<WorkflowApprovalConfigUser>()
                            .eq(WorkflowApprovalConfigUser::getConfigId, config.getId()));
            int deleted = configUserMapper.delete(new LambdaQueryWrapper<WorkflowApprovalConfigUser>()
                    .eq(WorkflowApprovalConfigUser::getConfigId, config.getId()));
            if (deleted != oldUsers.size()) throw BusinessException.of(409, "审批配置人员更新冲突，请重试");
            config.setEnabled(Boolean.FALSE.equals(request.getEnabled()) ? 0 : 1);
            config.setConfigVersion(config.getConfigVersion() + 1);
            config.setUpdatedBy(currentUser.getId());
            config.setUpdateTime(now);
            requireSingleWrite(configMapper.updateById(config), "审批配置更新");
        }
        insertUsers(config, projectId, approvers, "APPROVER", now);
        insertUsers(config, projectId, defaultCc, "DEFAULT_CC", now);
        return toVO(configMapper.selectById(config.getId()));
    }

    public List<SealUserOptionVO> candidates(Long projectId, Long sealId, String keyword,
                                              SysUser currentUser, boolean management) {
        if (management) requireManage(currentUser, projectId);
        else requireActiveMember(currentUser, projectId);
        Set<Long> defaults = defaultCcIds(projectId, sealId);
        String query = StringUtils.hasText(keyword) ? keyword.trim().toLowerCase(Locale.ROOT) : "";
        List<Long> memberIds = userProjectMapper.selectList(new LambdaQueryWrapper<SysUserProject>()
                        .eq(SysUserProject::getProjectId, projectId)
                        .eq(SysUserProject::getStatus, "ACTIVE")
                        .orderByAsc(SysUserProject::getUserId))
                .stream().map(SysUserProject::getUserId).distinct().toList();
        List<SealUserOptionVO> result = new ArrayList<>();
        for (Long userId : memberIds) {
            SysUser user = userMapper.selectById(userId);
            if (user == null || !Integer.valueOf(1).equals(user.getStatus())) continue;
            String haystack = (Objects.toString(user.getRealName(), "") + " "
                    + Objects.toString(user.getUsername(), "") + " "
                    + Objects.toString(user.getPhone(), "")).toLowerCase(Locale.ROOT);
            if (!query.isEmpty() && !haystack.contains(query)) continue;
            SealUserOptionVO option = userOption(user);
            boolean selected = defaults.contains(userId);
            option.setDefaultSelected(selected);
            option.setSelected(selected);
            option.setActiveProjectMember(true);
            result.add(option);
        }
        return result;
    }

    /**
     * Locks the same configuration row used by save(), then reads its approvers.
     * A submitting application therefore snapshots a complete old or new version,
     * never a mixed config-version/candidate set.
     */
    @Transactional
    public ApprovalConfigSnapshot requireEnabledSnapshot(Long projectId, Long sealId) {
        WorkflowApprovalConfig config = configMapper.selectForUpdate(
                SEAL_BUSINESS_CODE, projectId, sealId);
        if (config == null || !Integer.valueOf(1).equals(config.getEnabled())) {
            throw new BusinessException("当前印章尚未启用审批配置");
        }
        List<WorkflowApprovalConfigUser> approvers = configUsers(config.getId(), "APPROVER");
        if (approvers.isEmpty()) {
            throw new BusinessException("当前印章尚未配置审批人");
        }
        return new ApprovalConfigSnapshot(config, List.copyOf(approvers));
    }

    public List<WorkflowApprovalConfigUser> configUsers(Long configId, String type) {
        return configUserMapper.selectList(new LambdaQueryWrapper<WorkflowApprovalConfigUser>()
                .eq(WorkflowApprovalConfigUser::getConfigId, configId)
                .eq(WorkflowApprovalConfigUser::getAssignmentType, type)
                .orderByAsc(WorkflowApprovalConfigUser::getSortOrder));
    }

    public List<Long> defaultCcUserIds(Long projectId, Long sealId) {
        return new ArrayList<>(defaultCcIds(projectId, sealId));
    }

    private ApprovalConfigVO toVO(WorkflowApprovalConfig config) {
        ApprovalConfigVO vo = new ApprovalConfigVO();
        vo.setId(config.getId());
        vo.setBusinessCode(config.getBusinessCode());
        vo.setProjectId(config.getProjectId());
        ProjectInfo project = projectMapper.selectById(config.getProjectId());
        vo.setProjectName(project == null ? null : project.getProjectName());
        vo.setSealId(config.getSealId());
        SealDefinition seal = sealMapper.selectById(config.getSealId());
        vo.setSealName(seal == null ? null : seal.getSealName());
        vo.setApprovalMode(config.getApprovalMode());
        vo.setEnabled(Integer.valueOf(1).equals(config.getEnabled()));
        vo.setConfigVersion(config.getConfigVersion());
        List<WorkflowApprovalConfigUser> users = configUserMapper.selectList(
                new LambdaQueryWrapper<WorkflowApprovalConfigUser>()
                        .eq(WorkflowApprovalConfigUser::getConfigId, config.getId())
                        .orderByAsc(WorkflowApprovalConfigUser::getSortOrder));
        for (WorkflowApprovalConfigUser relation : users) {
            SysUser user = userMapper.selectById(relation.getUserId());
            SealUserOptionVO option = user == null ? deletedUserOption(relation.getUserId()) : userOption(user);
            option.setActiveProjectMember(isActiveMember(relation.getUserId(), config.getProjectId()));
            if ("APPROVER".equals(relation.getAssignmentType())) {
                vo.getApproverUserIds().add(relation.getUserId());
                vo.getApprovers().add(option);
            } else if ("DEFAULT_CC".equals(relation.getAssignmentType())) {
                option.setDefaultSelected(true);
                option.setSelected(true);
                vo.getDefaultCcUserIds().add(relation.getUserId());
                vo.getDefaultCcUsers().add(option);
            }
        }
        vo.setUpdateTime(config.getUpdateTime());
        return vo;
    }

    private void insertUsers(WorkflowApprovalConfig config, Long projectId, List<Long> userIds,
                             String type, LocalDateTime now) {
        for (int i = 0; i < userIds.size(); i++) {
            WorkflowApprovalConfigUser relation = new WorkflowApprovalConfigUser();
            relation.setConfigId(config.getId());
            relation.setProjectId(projectId);
            relation.setUserId(userIds.get(i));
            relation.setAssignmentType(type);
            relation.setSortOrder(i + 1);
            relation.setCreateTime(now);
            requireSingleWrite(configUserMapper.insert(relation), "审批配置人员新增");
        }
    }

    private Set<Long> defaultCcIds(Long projectId, Long sealId) {
        if (sealId == null) return Set.of();
        WorkflowApprovalConfig config = configMapper.selectOne(new LambdaQueryWrapper<WorkflowApprovalConfig>()
                .eq(WorkflowApprovalConfig::getBusinessCode, SEAL_BUSINESS_CODE)
                .eq(WorkflowApprovalConfig::getProjectId, projectId)
                .eq(WorkflowApprovalConfig::getSealId, sealId).last("LIMIT 1"));
        if (config == null || !Integer.valueOf(1).equals(config.getEnabled())) return Set.of();
        return new LinkedHashSet<>(configUsers(config.getId(), "DEFAULT_CC").stream()
                .map(WorkflowApprovalConfigUser::getUserId).toList());
    }

    private void requireEligibleUserForUpdate(Long projectId, Long userId) {
        SysUser user = userMapper.selectByIdForUpdate(userId);
        SysUserProject membership = userProjectMapper.selectByProjectAndUserForUpdate(projectId, userId);
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())
                || membership == null || !"ACTIVE".equals(membership.getStatus())) {
            throw new BusinessException("所选用户不是当前项目有效成员: " + userId);
        }
    }

    private boolean isActiveMember(Long userId, Long projectId) {
        return userProjectMapper.selectCount(new LambdaQueryWrapper<SysUserProject>()
                .eq(SysUserProject::getUserId, userId)
                .eq(SysUserProject::getProjectId, projectId)
                .eq(SysUserProject::getStatus, "ACTIVE")) > 0;
    }

    private void requireManage(SysUser user, Long projectId) {
        if (user == null || projectId == null) throw BusinessException.forbidden("无审批配置管理权限");
        permissionService.checkProjectPermission(user.getId(), projectId);
        permissionService.requireSystemPermission(user.getId(), projectId, SystemPermissionCodes.APPROVAL_MANAGE);
    }

    private void requireView(SysUser user, Long projectId) {
        if (user == null || projectId == null) throw BusinessException.forbidden("无审批配置查看权限");
        permissionService.checkProjectPermission(user.getId(), projectId);
        if (!permissionService.hasSystemPermission(user.getId(), projectId, SystemPermissionCodes.APPROVAL_VIEW)
                && !permissionService.hasSystemPermission(user.getId(), projectId, SystemPermissionCodes.APPROVAL_MANAGE)) {
            throw BusinessException.forbidden("无审批配置查看权限");
        }
    }

    private void requireActiveMember(SysUser user, Long projectId) {
        if (user == null) throw BusinessException.unauthorized("请先登录");
        permissionService.checkProjectPermission(user.getId(), projectId);
        if (!"ACTIVE".equals(permissionService.getProjectAccessStatus(user.getId(), projectId))) {
            throw BusinessException.forbidden("仅当前项目有效成员可选择抄送人");
        }
    }

    private String normalizeBusinessCode(String value) {
        String code = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : SEAL_BUSINESS_CODE;
        if (!SEAL_BUSINESS_CODE.equals(code)) throw new BusinessException("一期仅支持用印申请审批配置");
        return code;
    }

    private List<Long> distinctIds(List<Long> values) {
        if (values == null) return List.of();
        return values.stream().filter(Objects::nonNull).distinct().toList();
    }

    private SealUserOptionVO userOption(SysUser user) {
        SealUserOptionVO option = new SealUserOptionVO();
        option.setUserId(user.getId());
        option.setRealName(user.getRealName());
        option.setUsername(user.getUsername());
        option.setPhone(user.getPhone());
        option.setDisplayName(StringUtils.hasText(user.getRealName()) ? user.getRealName() : user.getUsername());
        return option;
    }

    private SealUserOptionVO deletedUserOption(Long userId) {
        SealUserOptionVO option = new SealUserOptionVO();
        option.setUserId(userId);
        option.setDisplayName("已删除用户 " + userId);
        option.setActiveProjectMember(false);
        return option;
    }

    public record ApprovalConfigSnapshot(WorkflowApprovalConfig config,
                                         List<WorkflowApprovalConfigUser> approvers) { }

    private void requireSingleWrite(int affectedRows, String operation) {
        if (affectedRows != 1) throw BusinessException.of(409, operation + "未生效，请刷新后重试");
    }
}
