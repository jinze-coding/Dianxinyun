package com.example.siteplatform.project.service;

import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.project.entity.SysUserProject;
import com.example.siteplatform.project.mapper.SysUserProjectMapper;
import com.example.siteplatform.project.mapper.SysUserProjectRoleMapper;
import com.example.siteplatform.system.constant.BusinessModuleCodes;
import com.example.siteplatform.system.constant.SystemPermissionCodes;
import com.example.siteplatform.system.service.SystemPermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectPermissionServiceRbacTest {

    @Mock private SysUserMapper userMapper;
    @Mock private SysUserProjectMapper userProjectMapper;
    @Mock private SysUserProjectRoleMapper userProjectRoleMapper;
    @Mock private SystemPermissionService systemPermissionService;
    @Mock private RedisTemplate<String, Object> redisTemplate;

    private ProjectPermissionService service;

    @BeforeEach
    void setUp() {
        service = new ProjectPermissionService();
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "userProjectMapper", userProjectMapper);
        ReflectionTestUtils.setField(service, "userProjectRoleMapper", userProjectRoleMapper);
        ReflectionTestUtils.setField(service, "systemPermissionService", systemPermissionService);
        ReflectionTestUtils.setField(service, "redisTemplate", redisTemplate);
        lenient().when(userMapper.selectRoleCodesByUserId(7L)).thenReturn(List.of());
    }

    @Test
    void projectManagerWithMemberManagementPermissionCanManageThatProject() {
        when(userProjectMapper.selectOne(any())).thenReturn(activeMembership("PROJECT_ADMIN"));
        when(userProjectRoleMapper.countEnabledProjectManagerRoles(7L, 2L)).thenReturn(1L);
        when(systemPermissionService.hasProjectPermission(7L, 2L, "project.member.manage")).thenReturn(true);

        assertTrue(service.canManageProjectMembers(7L, 2L));
    }

    @Test
    void ordinaryProjectRoleCannotBypassProjectManagerProtection() {
        SysUserProject membership = activeMembership("USER");
        membership.setInspectionPermissionTemplateId(88L);
        when(userProjectMapper.selectOne(any())).thenReturn(membership);
        when(userProjectRoleMapper.countEnabledProjectManagerRoles(7L, 2L)).thenReturn(0L);

        assertFalse(service.canManageProjectMembers(7L, 2L));
    }

    @Test
    void projectManagerStillNeedsTheExactMemberManagementPermission() {
        when(userProjectMapper.selectOne(any())).thenReturn(activeMembership("PROJECT_ADMIN"));
        when(userProjectRoleMapper.countEnabledProjectManagerRoles(7L, 2L)).thenReturn(1L);
        when(systemPermissionService.hasProjectPermission(7L, 2L, "project.member.manage")).thenReturn(false);

        assertFalse(service.canManageProjectMembers(7L, 2L));
    }

    @Test
    void platformProjectManagePermissionStillRequiresProjectMembership() {
        when(userProjectMapper.selectOne(any())).thenReturn(null);

        assertFalse(service.canManageProjectMembers(7L, 2L));
    }

    @Test
    void ordinaryProjectRoleWithQualityManagePermissionCanManageQuality() {
        when(systemPermissionService.hasBusinessModule(7L, 2L, BusinessModuleCodes.QUALITY))
                .thenReturn(true);
        when(systemPermissionService.hasProjectPermission(
                7L, 2L, SystemPermissionCodes.QUALITY_MANAGE)).thenReturn(true);

        assertTrue(service.canManageQuality(7L, 2L));
    }

    @Test
    void qualityModuleMustRemainEnabledForEveryUser() {
        when(systemPermissionService.hasBusinessModule(1L, 2L, BusinessModuleCodes.QUALITY))
                .thenReturn(false);

        assertFalse(service.canManageQuality(1L, 2L));
        verify(systemPermissionService, never()).hasProjectPermission(
                1L, 2L, SystemPermissionCodes.QUALITY_MANAGE);
    }

    @Test
    void projectCacheIsClearedAgainAfterTransactionCommit() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.clearUserProjectsCache(7L);

            verify(redisTemplate).delete("user:projects:7");
            assertTrue(TransactionSynchronizationManager.isSynchronizationActive());

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);

            verify(redisTemplate, times(2)).delete("user:projects:7");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private SysUserProject activeMembership(String roleCode) {
        SysUserProject membership = new SysUserProject();
        membership.setUserId(7L);
        membership.setProjectId(2L);
        membership.setProjectRoleCode(roleCode);
        membership.setStatus("ACTIVE");
        return membership;
    }
}
