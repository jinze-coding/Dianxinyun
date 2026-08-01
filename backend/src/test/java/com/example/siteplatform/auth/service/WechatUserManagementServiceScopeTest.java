package com.example.siteplatform.auth.service;

import com.example.siteplatform.auth.dto.WechatBindingStatusRequest;
import com.example.siteplatform.auth.dto.WechatUnbindRequest;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.entity.SysUserWechatBinding;
import com.example.siteplatform.auth.entity.WechatAccessApplication;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.auth.mapper.SysUserWechatBindingMapper;
import com.example.siteplatform.auth.mapper.WechatAccessApplicationMapper;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.log.mapper.OperationLogMapper;
import com.example.siteplatform.project.entity.SysUserProject;
import com.example.siteplatform.project.mapper.InspectionPermissionTemplateMapper;
import com.example.siteplatform.project.mapper.ProjectInfoMapper;
import com.example.siteplatform.project.mapper.SysUserProjectMapper;
import com.example.siteplatform.project.service.ProjectPermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WechatUserManagementServiceScopeTest {

    @Mock private SysUserWechatBindingMapper bindingMapper;
    @Mock private SysUserMapper userMapper;
    @Mock private SysUserProjectMapper userProjectMapper;
    @Mock private ProjectInfoMapper projectMapper;
    @Mock private InspectionPermissionTemplateMapper templateMapper;
    @Mock private WechatAccessApplicationMapper applicationMapper;
    @Mock private OperationLogMapper operationLogMapper;
    @Mock private ProjectPermissionService permissionService;
    @Mock private AuthService authService;

    private WechatUserManagementService service;
    private SysUser manager;

    @BeforeEach
    void setUp() {
        service = new WechatUserManagementService(
                bindingMapper, userMapper, userProjectMapper, projectMapper, templateMapper,
                applicationMapper, operationLogMapper, permissionService, authService);
        manager = user(1L);
    }

    @Test
    void projectManagerListDoesNotRevealOtherProjectCount() {
        when(permissionService.canManageProjectMembers(1L, 9L)).thenReturn(true);
        when(bindingMapper.selectList(any())).thenReturn(List.of(binding(20L, 2L)));
        when(userMapper.selectById(2L)).thenReturn(user(2L));
        when(userProjectMapper.selectList(any())).thenReturn(List.of(member(9L), member(10L)));
        when(applicationMapper.selectCount(any())).thenReturn(0L);

        var result = service.list(9L, null, null, null, null, null, 1, 20, manager);

        assertEquals(1, result.getRecords().size());
        assertEquals(1, result.getRecords().get(0).getProjectCount());
        assertEquals(9L, result.getRecords().get(0).getProjectId());
    }

    @Test
    void projectManagerDetailRequiresExplicitAuthorizedProjectAndHidesGlobalData() {
        when(userMapper.selectById(2L)).thenReturn(user(2L));

        assertThrows(BusinessException.class, () -> service.detail(2L, null, manager));

        when(permissionService.canManageProjectMembers(1L, 9L)).thenReturn(true);
        when(userProjectMapper.selectList(any())).thenReturn(List.of(member(9L), member(10L)));
        WechatAccessApplication visible = application(30L, 9L);
        WechatAccessApplication hidden = application(31L, 10L);
        when(applicationMapper.selectList(any())).thenReturn(List.of(visible, hidden));

        var result = service.detail(2L, 9L, manager);

        assertEquals(1, result.getProjects().size());
        assertEquals(9L, result.getProjects().get(0).getProjectId());
        assertEquals(1, result.getApplications().size());
        assertEquals(9L, result.getApplications().get(0).getProjectId());
        assertTrue(result.getBindings().isEmpty());
        assertTrue(result.getOperationLogs().isEmpty());
        verify(bindingMapper, never()).selectList(any());
        verify(operationLogMapper, never()).selectList(any());
    }

    @Test
    void bindingStatusWriteFailureReturnsConflictWithoutRevokingSessionsOrAuditingSuccess() {
        SysUserWechatBinding binding = binding(20L, 2L);
        WechatBindingStatusRequest request = new WechatBindingStatusRequest();
        request.setStatus("DISABLED");
        request.setReason("安全停用");
        when(permissionService.isPlatformAdmin(1L)).thenReturn(true);
        when(bindingMapper.selectById(20L)).thenReturn(binding);
        when(bindingMapper.updateById(binding)).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateBindingStatus(2L, 20L, request, manager));

        assertEquals(409, exception.getCode());
        verify(authService, never()).logout(2L);
        verify(operationLogMapper, never()).insert(any());
    }

    @Test
    void unbindWriteFailureReturnsConflictWithoutRevokingSessionsOrAuditingSuccess() {
        SysUserWechatBinding binding = binding(20L, 2L);
        WechatUnbindRequest request = new WechatUnbindRequest();
        request.setReason("解除绑定");
        when(permissionService.isPlatformAdmin(1L)).thenReturn(true);
        when(bindingMapper.selectById(20L)).thenReturn(binding);
        when(bindingMapper.updateById(binding)).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.unbind(2L, 20L, request, manager));

        assertEquals(409, exception.getCode());
        verify(authService, never()).logout(2L);
        verify(operationLogMapper, never()).insert(any());
    }

    private SysUser user(Long id) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername("user_" + id);
        user.setStatus(1);
        return user;
    }

    private SysUserWechatBinding binding(Long id, Long userId) {
        SysUserWechatBinding binding = new SysUserWechatBinding();
        binding.setId(id);
        binding.setUserId(userId);
        binding.setStatus("ACTIVE");
        binding.setDeleted(0);
        return binding;
    }

    private SysUserProject member(Long projectId) {
        SysUserProject relation = new SysUserProject();
        relation.setId(projectId + 100);
        relation.setProjectId(projectId);
        relation.setUserId(2L);
        relation.setProjectRoleCode("USER");
        relation.setStatus("ACTIVE");
        return relation;
    }

    private WechatAccessApplication application(Long id, Long projectId) {
        WechatAccessApplication application = new WechatAccessApplication();
        application.setId(id);
        application.setProjectId(projectId);
        application.setMatchedUserId(2L);
        application.setStatus("APPROVED");
        return application;
    }
}
