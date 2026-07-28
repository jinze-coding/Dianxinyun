package com.example.siteplatform.registration.service;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.auth.service.CaptchaService;
import com.example.siteplatform.auth.service.WechatAuthService;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.log.mapper.OperationLogMapper;
import com.example.siteplatform.project.mapper.SysUserProjectMapper;
import com.example.siteplatform.project.service.InspectionPermissionTemplateService;
import com.example.siteplatform.registration.dto.RegistrationApplicationVO;
import com.example.siteplatform.registration.dto.RegistrationReviewRequest;
import com.example.siteplatform.registration.dto.RegistrationSubmitRequest;
import com.example.siteplatform.registration.dto.RegistrationSubmitResponse;
import com.example.siteplatform.registration.entity.RegistrationApplication;
import com.example.siteplatform.registration.mapper.RegistrationApplicationMapper;
import com.example.siteplatform.system.entity.SystemRole;
import com.example.siteplatform.system.mapper.SystemRoleMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrationApplicationServiceTest {

    @Mock private RegistrationApplicationMapper applicationMapper;
    @Mock private SysUserMapper userMapper;
    @Mock private SystemRoleMapper roleMapper;
    @Mock private SysUserProjectMapper userProjectMapper;
    @Mock private InspectionPermissionTemplateService inspectionTemplateService;
    @Mock private AuthService authService;
    @Mock private CaptchaService captchaService;
    @Mock private WechatAuthService wechatAuthService;
    @Mock private OperationLogMapper operationLogMapper;

    private RegistrationApplicationService service;

    @BeforeEach
    void setUp() {
        service = new RegistrationApplicationService(
                applicationMapper, userMapper, roleMapper, userProjectMapper,
                inspectionTemplateService, authService, captchaService,
                wechatAuthService, operationLogMapper, new ObjectMapper());
    }

    @Test
    void webSubmissionStoresOnlyPasswordHashAndDoesNotCreateUser() {
        RegistrationSubmitRequest request = validWebRequest();
        request.setDesiredProjectIds(List.of(1L, 1L, 2L));
        request.setDesiredProjectText("智慧工地综合演示项目");
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(authService.hashPassword("StrongPass1")).thenReturn("$2a$10$encoded");
        doAnswer(invocation -> {
            RegistrationApplication application = invocation.getArgument(0);
            application.setId(15L);
            return 1;
        }).when(applicationMapper).insert(any(RegistrationApplication.class));

        RegistrationSubmitResponse response = service.submit(request);

        assertEquals(15L, response.getApplicationId());
        assertEquals("PENDING", response.getStatus());
        assertNotNull(response.getStatusToken());
        ArgumentCaptor<RegistrationApplication> captor = ArgumentCaptor.forClass(RegistrationApplication.class);
        verify(applicationMapper).insert(captor.capture());
        RegistrationApplication stored = captor.getValue();
        assertEquals("$2a$10$encoded", stored.getPasswordHash());
        assertEquals("[1,2]", stored.getDesiredProjectIds());
        assertEquals("智慧工地综合演示项目", stored.getDesiredProjectText());
        assertEquals("MANUAL_REVIEW", stored.getPhoneVerificationType());
        assertNull(stored.getOpenid());
        verify(captchaService).verifyAndConsume("captcha-id", "ABCD");
        verify(userMapper, never()).insert(any(SysUser.class));
    }

    @Test
    void miniSubmissionRequiresExplicitWechatIdentity() {
        RegistrationSubmitRequest request = validWebRequest();
        request.setSourceType("MINI");
        request.setCaptchaId(null);
        request.setCaptchaCode(null);
        request.setWechatSessionToken("wechat-session");
        when(wechatAuthService.consumePendingIdentity("wechat-session"))
                .thenReturn(new WechatAuthService.PendingWechatIdentity("wx-app", "openid-1", "unionid-1"));
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(authService.hashPassword("StrongPass1")).thenReturn("$2a$10$encoded");
        doAnswer(invocation -> {
            RegistrationApplication application = invocation.getArgument(0);
            application.setId(16L);
            return 1;
        }).when(applicationMapper).insert(any(RegistrationApplication.class));

        service.submit(request);

        ArgumentCaptor<RegistrationApplication> captor = ArgumentCaptor.forClass(RegistrationApplication.class);
        verify(applicationMapper).insert(captor.capture());
        assertEquals("wx-app", captor.getValue().getAppId());
        assertEquals("openid-1", captor.getValue().getOpenid());
        assertEquals("unionid-1", captor.getValue().getUnionid());
        verify(captchaService, never()).verifyAndConsume(any(), any());
    }

    @Test
    void concurrentPendingReservationReturnsConflict() {
        RegistrationSubmitRequest request = validWebRequest();
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(authService.hashPassword("StrongPass1")).thenReturn("$2a$10$encoded");
        doThrow(new DuplicateKeyException("uk_registration_pending_phone"))
                .when(applicationMapper).insert(any(RegistrationApplication.class));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.submit(request));

        assertEquals(409, exception.getCode());
        assertTrue(exception.getMessage().contains("待审批"));
        verify(userMapper, never()).insert(any(SysUser.class));
    }

    @Test
    void reviewerCanLoadApplicationDetailById() {
        RegistrationApplication application = pendingApplication();
        application.setDesiredProjectText("智慧工地综合演示项目");
        when(applicationMapper.selectById(9L)).thenReturn(application);

        RegistrationApplicationVO detail = service.detail(9L);

        assertEquals(9L, detail.getId());
        assertEquals("PENDING", detail.getStatus());
        assertEquals("智慧工地综合演示项目", detail.getDesiredProjectText());
    }

    @Test
    void approvalRechecksPhoneBeforeCreatingAccount() {
        when(applicationMapper.selectOne(any())).thenReturn(pendingApplication());
        when(userMapper.selectCount(any())).thenReturn(0L, 1L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.approve(9L, reviewRequest(), reviewer()));

        assertEquals(409, exception.getCode());
        assertTrue(exception.getMessage().contains("手机号"));
        verify(userMapper, never()).insert(any(SysUser.class));
        verify(applicationMapper, never()).update(any(), any());
    }

    @Test
    void approvalClearsPasswordOnlyAfterUserRoleAndWechatBindingSucceed() {
        RegistrationApplication application = pendingApplication();
        when(applicationMapper.selectOne(any())).thenReturn(application);
        when(userMapper.selectCount(any())).thenReturn(0L);
        doAnswer(invocation -> {
            SysUser user = invocation.getArgument(0);
            user.setId(88L);
            return 1;
        }).when(userMapper).insert(any(SysUser.class));
        SystemRole defaultRole = new SystemRole();
        defaultRole.setId(3L);
        defaultRole.setRoleCode("USER");
        defaultRole.setScopeType("PLATFORM");
        when(roleMapper.selectOne(any())).thenReturn(defaultRole);
        when(roleMapper.selectById(3L)).thenReturn(defaultRole);
        when(applicationMapper.update(any(), any())).thenReturn(1);

        RegistrationApplicationVO result = service.approve(9L, reviewRequest(), reviewer());

        assertEquals("APPROVED", result.getStatus());
        assertEquals(88L, result.getCreatedUserId());
        assertNull(application.getPasswordHash());
        verify(userMapper).insertUserRole(88L, 3L);
        verify(wechatAuthService).bind(
                any(SysUser.class), eq("wx-app"), eq("openid-1"), eq("unionid-1"), eq("13800138000"));
        verify(applicationMapper).update(any(), any());
        verify(operationLogMapper).insert(any());
    }

    @Test
    void bindingConflictStopsApprovalBeforeApplicationIsMarkedApproved() {
        RegistrationApplication application = pendingApplication();
        when(applicationMapper.selectOne(any())).thenReturn(application);
        when(userMapper.selectCount(any())).thenReturn(0L);
        doAnswer(invocation -> {
            SysUser user = invocation.getArgument(0);
            user.setId(88L);
            return 1;
        }).when(userMapper).insert(any(SysUser.class));
        SystemRole defaultRole = new SystemRole();
        defaultRole.setId(3L);
        defaultRole.setRoleCode("USER");
        defaultRole.setScopeType("PLATFORM");
        when(roleMapper.selectOne(any())).thenReturn(defaultRole);
        when(roleMapper.selectById(3L)).thenReturn(defaultRole);
        doThrow(BusinessException.of(409, "该微信已绑定其他系统账号"))
                .when(wechatAuthService).bind(any(), any(), any(), any(), any());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.approve(9L, reviewRequest(), reviewer()));

        assertEquals(409, exception.getCode());
        assertEquals("PENDING", application.getStatus());
        assertNotNull(application.getPasswordHash());
        verify(applicationMapper, never()).update(any(), any());
        verify(operationLogMapper, never()).insert(any());
    }

    @Test
    void disabledPlatformRoleCannotBeGrantedDuringApproval() {
        RegistrationApplication application = pendingApplication();
        when(applicationMapper.selectOne(any())).thenReturn(application);
        when(userMapper.selectCount(any())).thenReturn(0L);
        doAnswer(invocation -> {
            SysUser user = invocation.getArgument(0);
            user.setId(88L);
            return 1;
        }).when(userMapper).insert(any(SysUser.class));
        SystemRole disabledRole = new SystemRole();
        disabledRole.setId(3L);
        disabledRole.setRoleCode("USER");
        disabledRole.setScopeType("PLATFORM");
        disabledRole.setEnabled(0);
        disabledRole.setDeleted(0);
        when(roleMapper.selectOne(any())).thenReturn(disabledRole);
        when(roleMapper.selectById(3L)).thenReturn(disabledRole);

        BusinessException exception = assertThrows(
                BusinessException.class, () -> service.approve(9L, reviewRequest(), reviewer()));

        assertTrue(exception.getMessage().contains("平台角色不存在"));
        verify(userMapper, never()).insertUserRole(any(), any());
    }

    @Test
    void alreadyProcessedApplicationReturnsConflictBeforeAnyWrite() {
        RegistrationApplication application = pendingApplication();
        application.setStatus("APPROVED");
        when(applicationMapper.selectOne(any())).thenReturn(application);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.approve(9L, reviewRequest(), reviewer()));

        assertEquals(409, exception.getCode());
        verify(userMapper, never()).insert(any(SysUser.class));
    }

    @Test
    void applicantCanCancelPendingApplicationAndPasswordHashIsCleared() {
        RegistrationApplication application = pendingApplication();
        when(applicationMapper.selectOne(any())).thenReturn(application);
        when(applicationMapper.update(any(), any())).thenReturn(1);

        RegistrationApplicationVO result = service.cancel("status-token");

        assertEquals("CANCELLED", result.getStatus());
        assertEquals("申请人主动取消", result.getReviewComment());
        assertNull(application.getPasswordHash());
        assertNotNull(application.getReviewTime());
        verify(applicationMapper).update(any(), any());
        verify(userMapper, never()).insert(any(SysUser.class));
    }

    private RegistrationSubmitRequest validWebRequest() {
        RegistrationSubmitRequest request = new RegistrationSubmitRequest();
        request.setUsername("new_user");
        request.setPassword("StrongPass1");
        request.setRealName("新用户");
        request.setPhone("13800138000");
        request.setEmail("user@example.com");
        request.setSourceType("WEB");
        request.setCaptchaId("captcha-id");
        request.setCaptchaCode("ABCD");
        return request;
    }

    private RegistrationApplication pendingApplication() {
        RegistrationApplication application = new RegistrationApplication();
        application.setId(9L);
        application.setUsername("new_user");
        application.setPasswordHash("$2a$10$encoded");
        application.setRealName("新用户");
        application.setPhone("13800138000");
        application.setEmail("user@example.com");
        application.setAppId("wx-app");
        application.setOpenid("openid-1");
        application.setUnionid("unionid-1");
        application.setStatus("PENDING");
        return application;
    }

    private RegistrationReviewRequest reviewRequest() {
        RegistrationReviewRequest request = new RegistrationReviewRequest();
        request.setReviewComment("资料核验通过");
        return request;
    }

    private SysUser reviewer() {
        SysUser reviewer = new SysUser();
        reviewer.setId(1L);
        reviewer.setUsername("admin");
        reviewer.setRealName("系统管理员");
        return reviewer;
    }
}
