package com.example.siteplatform.workflow.service;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.project.entity.SysUserProject;
import com.example.siteplatform.project.mapper.ProjectInfoMapper;
import com.example.siteplatform.project.mapper.SysUserProjectMapper;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.seal.entity.SealDefinition;
import com.example.siteplatform.seal.mapper.SealDefinitionMapper;
import com.example.siteplatform.workflow.dto.ApprovalConfigSaveRequest;
import com.example.siteplatform.workflow.entity.WorkflowApprovalConfig;
import com.example.siteplatform.workflow.entity.WorkflowApprovalConfigUser;
import com.example.siteplatform.workflow.mapper.WorkflowApprovalConfigMapper;
import com.example.siteplatform.workflow.mapper.WorkflowApprovalConfigUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowApprovalConfigServiceTest {

    @Mock private WorkflowApprovalConfigMapper configMapper;
    @Mock private WorkflowApprovalConfigUserMapper configUserMapper;
    @Mock private SealDefinitionMapper sealMapper;
    @Mock private SysUserProjectMapper userProjectMapper;
    @Mock private SysUserMapper userMapper;
    @Mock private ProjectInfoMapper projectMapper;
    @Mock private ProjectPermissionService permissionService;

    private WorkflowApprovalConfigService service;

    @BeforeEach
    void setUp() {
        service = new WorkflowApprovalConfigService(configMapper, configUserMapper, sealMapper,
                userProjectMapper, userMapper, projectMapper, permissionService);
    }

    @Test
    void disabledConfigurationContributesNoDefaultCcRecipients() {
        WorkflowApprovalConfig disabled = config(81L, 0);
        when(configMapper.selectOne(any())).thenReturn(disabled);

        List<Long> defaults = service.defaultCcUserIds(9L, 3L);

        assertTrue(defaults.isEmpty());
        verify(configUserMapper, never()).selectList(any());
    }

    @Test
    void enabledConfigurationReturnsOnlyConfiguredDefaultCcRelations() {
        WorkflowApprovalConfig enabled = config(81L, 1);
        WorkflowApprovalConfigUser first = relation(81L, 11L, "DEFAULT_CC");
        WorkflowApprovalConfigUser second = relation(81L, 12L, "DEFAULT_CC");
        when(configMapper.selectOne(any())).thenReturn(enabled);
        when(configUserMapper.selectList(any())).thenReturn(List.of(first, second));

        assertEquals(List.of(11L, 12L), service.defaultCcUserIds(9L, 3L));
    }

    @Test
    void submissionSnapshotLocksConfigVersionBeforeReadingApprovers() {
        WorkflowApprovalConfig enabled = config(81L, 1);
        WorkflowApprovalConfigUser approver = relation(81L, 11L, "APPROVER");
        when(configMapper.selectForUpdate(WorkflowApprovalConfigService.SEAL_BUSINESS_CODE, 9L, 3L))
                .thenReturn(enabled);
        when(configUserMapper.selectList(any())).thenReturn(List.of(approver));

        var snapshot = service.requireEnabledSnapshot(9L, 3L);

        assertEquals(4, snapshot.config().getConfigVersion());
        assertEquals(List.of(11L), snapshot.approvers().stream()
                .map(WorkflowApprovalConfigUser::getUserId).toList());
        verify(configMapper).selectForUpdate(
                WorkflowApprovalConfigService.SEAL_BUSINESS_CODE, 9L, 3L);
    }

    @Test
    void saveLocksCandidatesInStableOrderBeforeConfiguration() {
        WorkflowApprovalConfig existing = config(81L, 1);
        SealDefinition seal = new SealDefinition();
        seal.setId(3L);
        seal.setProjectId(9L);
        SysUser first = activeUser(11L);
        SysUser second = activeUser(12L);
        SysUserProject firstMembership = activeMembership(9L, 11L);
        SysUserProject secondMembership = activeMembership(9L, 12L);
        when(sealMapper.selectById(3L)).thenReturn(seal);
        when(userMapper.selectByIdForUpdate(11L)).thenReturn(first);
        when(userMapper.selectByIdForUpdate(12L)).thenReturn(second);
        when(userProjectMapper.selectByProjectAndUserForUpdate(9L, 11L)).thenReturn(firstMembership);
        when(userProjectMapper.selectByProjectAndUserForUpdate(9L, 12L)).thenReturn(secondMembership);
        when(configMapper.selectForUpdate(WorkflowApprovalConfigService.SEAL_BUSINESS_CODE, 9L, 3L))
                .thenReturn(existing);
        when(configUserMapper.selectList(any())).thenReturn(List.of());
        when(configUserMapper.delete(any())).thenReturn(0);
        when(configUserMapper.insert(any())).thenReturn(1);
        when(configMapper.updateById(existing)).thenReturn(1);
        when(configMapper.selectById(81L)).thenReturn(existing);
        ApprovalConfigSaveRequest request = new ApprovalConfigSaveRequest();
        request.setProjectId(9L);
        request.setSealId(3L);
        request.setApproverUserIds(List.of(12L, 11L));
        SysUser operator = activeUser(99L);

        service.save(request, operator);

        InOrder order = inOrder(userMapper, userProjectMapper, configMapper);
        order.verify(userMapper).selectByIdForUpdate(11L);
        order.verify(userProjectMapper).selectByProjectAndUserForUpdate(9L, 11L);
        order.verify(userMapper).selectByIdForUpdate(12L);
        order.verify(userProjectMapper).selectByProjectAndUserForUpdate(9L, 12L);
        order.verify(configMapper).selectForUpdate(
                WorkflowApprovalConfigService.SEAL_BUSINESS_CODE, 9L, 3L);
    }

    private WorkflowApprovalConfig config(Long id, int enabled) {
        WorkflowApprovalConfig config = new WorkflowApprovalConfig();
        config.setId(id);
        config.setProjectId(9L);
        config.setSealId(3L);
        config.setBusinessCode(WorkflowApprovalConfigService.SEAL_BUSINESS_CODE);
        config.setEnabled(enabled);
        config.setConfigVersion(4);
        return config;
    }

    private WorkflowApprovalConfigUser relation(Long configId, Long userId, String type) {
        WorkflowApprovalConfigUser relation = new WorkflowApprovalConfigUser();
        relation.setConfigId(configId);
        relation.setProjectId(9L);
        relation.setUserId(userId);
        relation.setAssignmentType(type);
        return relation;
    }

    private SysUser activeUser(Long id) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setStatus(1);
        return user;
    }

    private SysUserProject activeMembership(Long projectId, Long userId) {
        SysUserProject membership = new SysUserProject();
        membership.setProjectId(projectId);
        membership.setUserId(userId);
        membership.setStatus("ACTIVE");
        return membership;
    }
}
