package com.example.siteplatform.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.SharedString;
import com.example.siteplatform.auth.dto.WechatApplicationReviewRequest;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.entity.WechatAccessApplication;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.auth.mapper.WechatAccessApplicationMapper;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.electricbox.mapper.ElectricBoxMapper;
import com.example.siteplatform.log.mapper.OperationLogMapper;
import com.example.siteplatform.project.mapper.ProjectInfoMapper;
import com.example.siteplatform.project.service.ProjectMemberService;
import com.example.siteplatform.project.service.ProjectPermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WechatAccessApplicationServiceTest {

    @Mock private WechatAccessApplicationMapper applicationMapper;
    @Mock private SysUserMapper userMapper;
    @Mock private ProjectInfoMapper projectMapper;
    @Mock private ElectricBoxMapper electricBoxMapper;
    @Mock private ProjectPermissionService permissionService;
    @Mock private ProjectMemberService projectMemberService;
    @Mock private WechatAuthService wechatAuthService;
    @Mock private OperationLogMapper operationLogMapper;

    private WechatAccessApplicationService service;

    @BeforeEach
    void setUp() {
        service = new WechatAccessApplicationService(
                applicationMapper, userMapper, projectMapper, electricBoxMapper,
                permissionService, projectMemberService, wechatAuthService, operationLogMapper);
    }

    @Test
    void reviewLocksPendingRowBeforeChangingItsStatus() {
        WechatAccessApplication application = pendingApplication();
        when(applicationMapper.selectOne(any())).thenReturn(application);
        when(permissionService.canManageProjectMembers(1L, 2L)).thenReturn(true);
        when(applicationMapper.updateById(application)).thenReturn(1);

        WechatApplicationReviewRequest request = new WechatApplicationReviewRequest();
        request.setComment("资料不完整");
        service.reject(9L, request, reviewer());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<WechatAccessApplication>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(applicationMapper).selectOne(captor.capture());
        SharedString lastSql = (SharedString) ReflectionTestUtils.getField(captor.getValue(), "lastSql");
        assertTrue(lastSql != null && lastSql.getStringValue().toUpperCase().contains("FOR UPDATE"));
        assertEquals("REJECTED", application.getStatus());
    }

    @Test
    void staleReviewUpdateReturnsConflictAndDoesNotWriteAuditLog() {
        WechatAccessApplication application = pendingApplication();
        when(applicationMapper.selectOne(any())).thenReturn(application);
        when(permissionService.canManageProjectMembers(1L, 2L)).thenReturn(true);
        when(applicationMapper.updateById(application)).thenReturn(0);
        WechatApplicationReviewRequest request = new WechatApplicationReviewRequest();
        request.setComment("资料不完整");

        BusinessException exception = assertThrows(
                BusinessException.class, () -> service.reject(9L, request, reviewer()));

        assertEquals(409, exception.getCode());
        verify(operationLogMapper, never()).insert(any());
    }

    private WechatAccessApplication pendingApplication() {
        WechatAccessApplication application = new WechatAccessApplication();
        application.setId(9L);
        application.setProjectId(2L);
        application.setStatus("PENDING");
        return application;
    }

    private SysUser reviewer() {
        SysUser reviewer = new SysUser();
        reviewer.setId(1L);
        reviewer.setUsername("admin");
        reviewer.setRealName("系统管理员");
        return reviewer;
    }
}
