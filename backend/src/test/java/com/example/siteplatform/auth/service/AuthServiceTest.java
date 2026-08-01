package com.example.siteplatform.auth.service;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.dto.LoginRequest;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.config.JwtConfig;
import com.example.siteplatform.project.mapper.ProjectInfoMapper;
import com.example.siteplatform.project.mapper.SysUserProjectMapper;
import com.example.siteplatform.project.service.ProjectPermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock private SysUserMapper userMapper;
    @Mock private JwtConfig jwtConfig;
    @Mock private ProjectPermissionService permissionService;
    @Mock private SysUserProjectMapper userProjectMapper;
    @Mock private ProjectInfoMapper projectInfoMapper;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOperations;
    @Mock private SetOperations<String, Object> setOperations;

    private AuthService service;
    private SysUser user;

    @BeforeEach
    void setUp() {
        service = new AuthService();
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "jwtConfig", jwtConfig);
        ReflectionTestUtils.setField(service, "projectPermissionService", permissionService);
        ReflectionTestUtils.setField(service, "userProjectMapper", userProjectMapper);
        ReflectionTestUtils.setField(service, "projectInfoMapper", projectInfoMapper);
        ReflectionTestUtils.setField(service, "redisTemplate", redisTemplate);
        ReflectionTestUtils.setField(service, "passwordCredentialService", new PasswordCredentialService());
        user = new SysUser();
        user.setId(1L);
        user.setUsername("admin");
        user.setRealName("系统管理员");
        user.setStatus(1);
    }

    @Test
    void verifiesBcryptAndRejectsWrongOrLegacyPassword() {
        PasswordCredentialService credentials = new PasswordCredentialService();
        user.setPassword(credentials.encode("Admin1234"));
        user.setPasswordLoginEnabled(1);
        user.setCredentialVersion(1);
        user.setPasswordResetRequired(0);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(userMapper.selectOne(any())).thenReturn(user);
        when(jwtConfig.generateToken(1L, "admin", 1)).thenReturn("token");

        LoginRequest correct = new LoginRequest();
        correct.setUsername("admin");
        correct.setPassword("Admin1234");
        assertNotNull(service.login(correct));

        LoginRequest wrong = new LoginRequest();
        wrong.setUsername("admin");
        wrong.setPassword("Wrong1234");
        assertEquals(401, assertThrows(BusinessException.class, () -> service.login(wrong)).getCode());

        user.setPassword("legacy-plaintext");
        assertEquals(401, assertThrows(BusinessException.class, () -> service.login(correct)).getCode());
    }

    @Test
    void verifiesPasswordBeforeRevealingAccountState() {
        PasswordCredentialService credentials = new PasswordCredentialService();
        user.setPassword(credentials.encode("Admin1234"));
        user.setPasswordLoginEnabled(1);
        user.setPasswordResetRequired(0);
        when(userMapper.selectOne(any())).thenReturn(user);

        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("Wrong1234");

        user.setStatus(0);
        BusinessException disabledWithWrongPassword =
                assertThrows(BusinessException.class, () -> service.login(request));
        assertEquals(401, disabledWithWrongPassword.getCode());
        assertEquals("用户名或密码错误", disabledWithWrongPassword.getMessage());

        request.setPassword("Admin1234");
        assertEquals(403, assertThrows(BusinessException.class, () -> service.login(request)).getCode());
    }

    @Test
    void unknownAccountUsesTheSameGenericError() {
        when(userMapper.selectOne(any())).thenReturn(null);
        LoginRequest request = new LoginRequest();
        request.setUsername("missing");
        request.setPassword("Whatever123");

        BusinessException exception = assertThrows(BusinessException.class, () -> service.login(request));
        assertEquals(401, exception.getCode());
        assertEquals("用户名或密码错误", exception.getMessage());
    }

    @Test
    void keepsWebAndMiniProgramSessionsValidForTheSameAccount() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(jwtConfig.generateToken(1L, "admin", 1)).thenReturn("web-token", "mini-token");
        String webToken = service.issueToken(user);
        String miniToken = service.issueToken(user);

        when(jwtConfig.validateToken(anyString())).thenReturn(true);
        when(jwtConfig.getUserIdFromToken(anyString())).thenReturn(1L);
        when(jwtConfig.getCredentialVersionFromToken(anyString())).thenReturn(1);
        when(valueOperations.get(argThat(key -> String.valueOf(key).startsWith("auth:session:")))).thenReturn(1L);
        when(userMapper.selectById(1L)).thenReturn(user);

        assertSame(user, service.getCurrentUser(webToken));
        assertSame(user, service.getCurrentUser(miniToken));

        service.logoutSession(webToken);
        assertSame(user, service.getCurrentUser(miniToken));
        verify(setOperations, atLeastOnce()).add(argThat(key -> key.equals("auth:user-sessions:1")), anyString());
        verify(setOperations).remove(argThat(key -> key.equals("auth:user-sessions:1")), anyString());
    }

    @Test
    void quickRegistrationAccountCanOnlyUseTheInitialPasswordSetupPath() {
        user.setPassword("$2a$10$placeholder");
        user.setPasswordLoginEnabled(0);
        user.setPasswordResetRequired(1);
        user.setCredentialVersion(1);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(jwtConfig.validateToken("quick-token")).thenReturn(true);
        when(jwtConfig.getUserIdFromToken("quick-token")).thenReturn(1L);
        when(jwtConfig.getCredentialVersionFromToken("quick-token")).thenReturn(1);
        when(valueOperations.get(anyString())).thenReturn(1L);
        when(userMapper.selectById(1L)).thenReturn(user);

        BusinessException blocked = assertThrows(BusinessException.class,
                () -> service.getCurrentUser("quick-token"));

        assertEquals(403, blocked.getCode());
        assertSame(user, service.getCurrentUserAllowInitialPasswordSetup("quick-token"));
    }

    @Test
    void initialPasswordSetupEnablesPasswordLoginInvalidatesOldSessionsAndIssuesFreshToken() {
        user.setPassword("$2a$10$placeholder");
        user.setPasswordLoginEnabled(0);
        user.setPasswordResetRequired(1);
        user.setCredentialVersion(1);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(jwtConfig.validateToken("quick-token")).thenReturn(true);
        when(jwtConfig.getUserIdFromToken("quick-token")).thenReturn(1L);
        when(jwtConfig.getCredentialVersionFromToken("quick-token")).thenReturn(1);
        when(valueOperations.get(anyString())).thenReturn(1L);
        when(userMapper.selectById(1L)).thenReturn(user);
        when(userMapper.updateById(user)).thenReturn(1);
        when(jwtConfig.generateToken(1L, "admin", 2)).thenReturn("fresh-token");

        var response = service.setupInitialPassword("quick-token", "QuickPass1");

        assertEquals("fresh-token", response.getToken());
        assertEquals(1, user.getPasswordLoginEnabled());
        assertEquals(0, user.getPasswordResetRequired());
        assertEquals(2, user.getCredentialVersion());
        assertTrue(new PasswordCredentialService().matches("QuickPass1", user.getPassword()));
        verify(userMapper).updateById(user);
    }

    @Test
    void initialPasswordSetupDoesNotIssueTokenWhenPasswordWriteAffectsNoRow() {
        user.setPassword("$2a$10$placeholder");
        user.setPasswordLoginEnabled(0);
        user.setPasswordResetRequired(1);
        user.setCredentialVersion(1);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(jwtConfig.validateToken("quick-token")).thenReturn(true);
        when(jwtConfig.getUserIdFromToken("quick-token")).thenReturn(1L);
        when(jwtConfig.getCredentialVersionFromToken("quick-token")).thenReturn(1);
        when(valueOperations.get(anyString())).thenReturn(1L);
        when(userMapper.selectById(1L)).thenReturn(user);
        when(userMapper.updateById(user)).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.setupInitialPassword("quick-token", "QuickPass1"));

        assertEquals(409, exception.getCode());
        verify(jwtConfig, never()).generateToken(1L, "admin", 2);
    }

    @Test
    void permissionChangeRepeatsSessionInvalidationAfterTransactionCommit() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members("auth:user-sessions:1")).thenReturn(Set.of("fingerprint"));
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.logout(1L);
            service.repeatLogoutAfterCommit(1L);

            verify(redisTemplate).delete("auth:user-sessions:1");
            assertEquals(1, TransactionSynchronizationManager.getSynchronizations().size());

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);

            verify(redisTemplate, times(2)).delete("auth:user-sessions:1");
            verify(redisTemplate, times(2)).delete("auth:token:1");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
