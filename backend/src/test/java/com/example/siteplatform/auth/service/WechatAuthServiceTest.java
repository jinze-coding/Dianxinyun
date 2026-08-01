package com.example.siteplatform.auth.service;

import com.example.siteplatform.auth.dto.WechatPhoneRequest;
import com.example.siteplatform.auth.dto.WechatProjectAccessRequest;
import com.example.siteplatform.auth.dto.WechatSessionRequest;
import com.example.siteplatform.auth.dto.WechatSessionResponse;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.entity.SysUserWechatBinding;
import com.example.siteplatform.auth.entity.WechatAccessApplication;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.auth.mapper.SysUserWechatBindingMapper;
import com.example.siteplatform.auth.mapper.WechatAccessApplicationMapper;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.electricbox.entity.ElectricBox;
import com.example.siteplatform.electricbox.mapper.ElectricBoxMapper;
import com.example.siteplatform.project.service.ProjectPermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WechatAuthServiceTest {

    @Mock private WechatPlatformClient platformClient;
    @Mock private SysUserWechatBindingMapper bindingMapper;
    @Mock private WechatAccessApplicationMapper applicationMapper;
    @Mock private SysUserMapper userMapper;
    @Mock private ElectricBoxMapper electricBoxMapper;
    @Mock private ProjectPermissionService permissionService;
    @Mock private AuthService authService;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private HashOperations<String, Object, Object> hashOperations;
    @Mock private ValueOperations<String, Object> valueOperations;

    private WechatAuthService service;

    @BeforeEach
    void setUp() {
        service = new WechatAuthService(
                platformClient, bindingMapper, applicationMapper, userMapper,
                electricBoxMapper, permissionService, authService, redisTemplate);
    }

    @Test
    void phoneMatchNeverAutomaticallyBindsAnAccount() {
        WechatPhoneRequest request = new WechatPhoneRequest();
        request.setWechatSessionToken("session-1");
        request.setPhoneCode("phone-code");
        request.setScene("B:public-code");
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries("wechat:login-session:session-1"))
                .thenReturn(Map.of("appId", "wx-app", "openid", "openid-1", "unionid", ""));
        when(platformClient.getPhoneNumber("phone-code", null)).thenReturn("13800138000");
        ElectricBox box = new ElectricBox();
        box.setId(5L);
        box.setProjectId(2L);
        when(electricBoxMapper.selectOne(any())).thenReturn(box);
        SysUser matched = activeUser();
        when(userMapper.selectList(any())).thenReturn(List.of(matched));

        WechatSessionResponse response = service.bindPhone(request);

        assertEquals("BIND_ACCOUNT_REQUIRED", response.getBindingStatus());
        assertNull(response.getToken());
        verify(bindingMapper, never()).insert(any());
        verify(bindingMapper, never()).updateById(any());
        verify(authService, never()).issueToken(any());
    }

    @Test
    void invalidMockPhoneIsRejectedBeforeAccountLookup() {
        WechatPhoneRequest request = new WechatPhoneRequest();
        request.setWechatSessionToken("session-1");
        request.setPhone("123");
        request.setScene("B:public-code");
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries("wechat:login-session:session-1"))
                .thenReturn(Map.of("appId", "wx-app", "openid", "openid-1", "unionid", ""));
        when(platformClient.getPhoneNumber(null, "123")).thenReturn("123");

        BusinessException exception = assertThrows(BusinessException.class, () -> service.bindPhone(request));

        assertEquals(400, exception.getCode());
        verify(userMapper, never()).selectList(any());
    }

    @Test
    void pendingWechatIdentityCanOnlyBeConsumedOnce() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(hashOperations.entries("wechat:login-session:session-1"))
                .thenReturn(Map.of("appId", "wx-app", "openid", "openid-1", "unionid", ""));
        when(valueOperations.setIfAbsent(
                "wechat:login-session-consumed:session-1", "1", 10, TimeUnit.MINUTES))
                .thenReturn(true, false);

        WechatAuthService.PendingWechatIdentity first = service.consumePendingIdentity("session-1");
        BusinessException second = assertThrows(
                BusinessException.class, () -> service.consumePendingIdentity("session-1"));

        assertEquals("openid-1", first.openid());
        assertEquals(409, second.getCode());
        verify(redisTemplate).delete("wechat:login-session:session-1");
    }

    @Test
    void rolledBackRegistrationRestoresClaimedWechatIdentitySession() {
        Map<String, String> session = Map.of(
                "appId", "wx-app", "openid", "openid-1", "unionid", "unionid-1");
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(hashOperations.entries("wechat:login-session:session-1"))
                .thenReturn(Map.copyOf(session));
        when(valueOperations.setIfAbsent(
                "wechat:login-session-consumed:session-1", "1", 10, TimeUnit.MINUTES))
                .thenReturn(true);
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.consumePendingIdentity("session-1");

            TransactionSynchronizationManager.getSynchronizations().forEach(
                    synchronization -> synchronization.afterCompletion(
                            TransactionSynchronization.STATUS_ROLLED_BACK));

            verify(hashOperations).putAll("wechat:login-session:session-1", session);
            verify(redisTemplate).expire(
                    "wechat:login-session:session-1", 10, TimeUnit.MINUTES);
            verify(redisTemplate).delete("wechat:login-session-consumed:session-1");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void committedRegistrationRemovesTemporaryClaimWithoutRestoringSession() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(hashOperations.entries("wechat:login-session:session-1"))
                .thenReturn(Map.of("appId", "wx-app", "openid", "openid-1", "unionid", ""));
        when(valueOperations.setIfAbsent(
                "wechat:login-session-consumed:session-1", "1", 10, TimeUnit.MINUTES))
                .thenReturn(true);
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.consumePendingIdentity("session-1");

            TransactionSynchronizationManager.getSynchronizations().forEach(
                    synchronization -> synchronization.afterCompletion(
                            TransactionSynchronization.STATUS_COMMITTED));

            verify(redisTemplate).delete("wechat:login-session-consumed:session-1");
            verify(hashOperations, never()).putAll(
                    "wechat:login-session:session-1", Map.of(
                            "appId", "wx-app", "openid", "openid-1", "unionid", ""));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void disabledBoundAccountCannotUseWechatLogin() {
        WechatSessionRequest request = new WechatSessionRequest();
        request.setCode("login-code");
        when(platformClient.login("login-code"))
                .thenReturn(new WechatPlatformClient.WechatIdentity("wx-app", "openid-1", "unionid-1"));
        SysUserWechatBinding binding = binding(10L, 7L, "ACTIVE", "openid-1", "unionid-1");
        when(bindingMapper.selectOne(any())).thenReturn(binding);
        SysUser disabled = activeUser();
        disabled.setStatus(0);
        when(userMapper.selectById(7L)).thenReturn(disabled);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.session(request));

        assertEquals(403, exception.getCode());
        verify(authService, never()).issueToken(any());
    }

    @Test
    void sameAppUserConflictReturns409() {
        SysUser user = activeUser();
        when(bindingMapper.selectCount(any())).thenReturn(1L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.bind(user, "wx-app", "new-openid", null, user.getPhone()));

        assertEquals(409, exception.getCode());
        verify(bindingMapper, never()).insert(any());
    }

    @Test
    void disabledSystemAccountCannotBeBound() {
        SysUser user = activeUser();
        user.setStatus(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.bind(user, "wx-app", "openid-1", null, user.getPhone()));

        assertEquals(403, exception.getCode());
        verify(bindingMapper, never()).insert(any());
        verify(bindingMapper, never()).updateById(any());
    }

    @Test
    void sameAppOpenidConflictReturns409() {
        SysUser user = activeUser();
        SysUserWechatBinding conflict = binding(10L, 99L, "ACTIVE", "openid-1", null);
        when(bindingMapper.selectCount(any())).thenReturn(0L);
        when(bindingMapper.selectOne(any())).thenReturn(conflict);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.bind(user, "wx-app", "openid-1", null, user.getPhone()));

        assertEquals(409, exception.getCode());
        verify(bindingMapper, never()).insert(any());
    }

    @Test
    void sameAppUnionidConflictReturns409() {
        SysUser user = activeUser();
        SysUserWechatBinding conflict = binding(10L, 99L, "ACTIVE", "other-openid", "unionid-1");
        when(bindingMapper.selectCount(any())).thenReturn(0L);
        when(bindingMapper.selectOne(any())).thenReturn(null, conflict);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.bind(user, "wx-app", "openid-1", "unionid-1", user.getPhone()));

        assertEquals(409, exception.getCode());
        verify(bindingMapper, never()).insert(any());
    }

    @Test
    void databaseRaceDuringBindReturns409() {
        SysUser user = activeUser();
        when(bindingMapper.selectCount(any())).thenReturn(0L);
        doThrow(new DuplicateKeyException("uk_wechat_binding_active_user"))
                .when(bindingMapper).insert(any(SysUserWechatBinding.class));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.bind(user, "wx-app", "openid-1", null, user.getPhone()));

        assertEquals(409, exception.getCode());
    }

    @Test
    void rebindingSameOpenidWithoutUnionidPreservesKnownUnionid() {
        SysUser user = activeUser();
        SysUserWechatBinding existing = binding(10L, 7L, "UNBOUND", "openid-1", "known-unionid");
        when(bindingMapper.selectCount(any())).thenReturn(0L);
        when(bindingMapper.selectOne(any())).thenReturn(null, existing);
        when(bindingMapper.updateById(existing)).thenReturn(1);

        service.bind(user, "wx-app", "openid-1", null, user.getPhone());

        assertEquals("known-unionid", existing.getUnionid());
        assertEquals("ACTIVE", existing.getStatus());
        verify(bindingMapper).updateById(existing);
    }

    @Test
    void staleBindingUpdateCannotBeReportedAsSuccessful() {
        SysUser user = activeUser();
        SysUserWechatBinding existing = binding(10L, 7L, "UNBOUND", "openid-1", null);
        when(bindingMapper.selectCount(any())).thenReturn(0L);
        when(bindingMapper.selectOne(any())).thenReturn(null, existing);
        when(bindingMapper.updateById(existing)).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.bind(user, "wx-app", "openid-1", null, user.getPhone()));

        assertEquals(409, exception.getCode());
        verify(authService, never()).issueToken(any());
    }

    @Test
    void selfUnbindRequiresEnabledPasswordLogin() {
        SysUser user = activeUser();
        user.setPasswordLoginEnabled(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.unbindCurrent(user, "StrongPass1"));

        assertEquals(403, exception.getCode());
        verify(authService, never()).authenticateCredentials(any(), any());
        verify(bindingMapper, never()).updateById(any());
    }

    @Test
    void selfUnbindRechecksPasswordAndRevokesAllSessions() {
        SysUser user = activeUser();
        user.setPasswordLoginEnabled(1);
        when(authService.authenticateCredentials("bound_user", "StrongPass1")).thenReturn(user);
        when(platformClient.appId()).thenReturn("wx-app");
        SysUserWechatBinding binding = binding(10L, 7L, "ACTIVE", "openid-1", "unionid-1");
        when(bindingMapper.selectOne(any())).thenReturn(binding);
        when(bindingMapper.updateById(binding)).thenReturn(1);

        service.unbindCurrent(user, "StrongPass1");

        assertEquals("UNBOUND", binding.getStatus());
        verify(authService).authenticateCredentials("bound_user", "StrongPass1");
        verify(bindingMapper).updateById(binding);
        verify(authService).logout(7L);
    }

    @Test
    void wrongPasswordDoesNotChangeBindingOrSessions() {
        SysUser user = activeUser();
        user.setPasswordLoginEnabled(1);
        when(authService.authenticateCredentials("bound_user", "wrong"))
                .thenThrow(BusinessException.unauthorized("用户名或密码错误"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.unbindCurrent(user, "wrong"));

        assertEquals(401, exception.getCode());
        verify(bindingMapper, never()).updateById(any());
        verify(authService, never()).logout(any());
    }

    @Test
    void concurrentProjectAccessSubmissionReusesDatabaseReservedPendingApplication() {
        WechatProjectAccessRequest request = new WechatProjectAccessRequest();
        request.setScene("B:public-code");
        SysUser user = activeUser();
        ElectricBox box = new ElectricBox();
        box.setId(5L);
        box.setProjectId(2L);
        when(electricBoxMapper.selectOne(any())).thenReturn(box);
        when(permissionService.getProjectAccessStatus(7L, 2L)).thenReturn(null);
        when(permissionService.getInspectionPermissionCodes(7L, 2L)).thenReturn(List.of());
        when(platformClient.appId()).thenReturn("wx-app");
        when(bindingMapper.selectOne(any())).thenReturn(
                binding(10L, 7L, "ACTIVE", "openid-1", "unionid-1"));
        WechatAccessApplication reserved = new WechatAccessApplication();
        reserved.setId(99L);
        reserved.setAppId("wx-app");
        reserved.setOpenid("openid-1");
        reserved.setProjectId(2L);
        reserved.setStatus("PENDING");
        when(applicationMapper.selectOne(any())).thenReturn(null, reserved);
        doThrow(new DuplicateKeyException("uk_wechat_access_pending"))
                .when(applicationMapper).insert(any(WechatAccessApplication.class));

        WechatSessionResponse response = service.requestProjectAccess(request, user);

        assertEquals("PENDING_APPROVAL", response.getBindingStatus());
        assertEquals("PENDING", response.getApplicationStatus());
        verify(applicationMapper).insert(any(WechatAccessApplication.class));
    }

    private SysUser activeUser() {
        SysUser user = new SysUser();
        user.setId(7L);
        user.setUsername("bound_user");
        user.setPhone("13800138000");
        user.setStatus(1);
        user.setPasswordLoginEnabled(1);
        return user;
    }

    private SysUserWechatBinding binding(Long id, Long userId, String status, String openid, String unionid) {
        SysUserWechatBinding binding = new SysUserWechatBinding();
        binding.setId(id);
        binding.setUserId(userId);
        binding.setAppId("wx-app");
        binding.setOpenid(openid);
        binding.setUnionid(unionid);
        binding.setStatus(status);
        binding.setDeleted(0);
        return binding;
    }
}
