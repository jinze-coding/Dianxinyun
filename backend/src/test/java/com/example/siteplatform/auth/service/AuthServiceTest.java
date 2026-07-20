package com.example.siteplatform.auth.service;

import com.example.siteplatform.auth.entity.SysUser;
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

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
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
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);

        user = new SysUser();
        user.setId(1L);
        user.setUsername("admin");
        user.setRealName("系统管理员");
        user.setStatus(1);
    }

    @Test
    void keepsWebAndMiniProgramSessionsValidForTheSameAccount() {
        when(jwtConfig.generateToken(1L, "admin")).thenReturn("web-token", "mini-token");
        String webToken = service.issueToken(user);
        String miniToken = service.issueToken(user);

        when(jwtConfig.validateToken(anyString())).thenReturn(true);
        when(jwtConfig.getUserIdFromToken(anyString())).thenReturn(1L);
        when(valueOperations.get(argThat(key -> String.valueOf(key).startsWith("auth:session:")))).thenReturn(1L);
        when(userMapper.selectById(1L)).thenReturn(user);

        assertSame(user, service.getCurrentUser(webToken));
        assertSame(user, service.getCurrentUser(miniToken));

        service.logoutSession(webToken);
        assertSame(user, service.getCurrentUser(miniToken));
        verify(setOperations, atLeastOnce()).add(argThat(key -> key.equals("auth:user-sessions:1")), anyString());
        verify(setOperations).remove(argThat(key -> key.equals("auth:user-sessions:1")), anyString());
    }
}
