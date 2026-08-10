package com.example.siteplatform.siteaccess.service;

import com.example.siteplatform.auth.service.WechatPlatformClient;
import com.example.siteplatform.common.RedisRateLimitService;
import com.example.siteplatform.siteaccess.entity.SiteVisitInvitation;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.env.MockEnvironment;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VisitorSessionServiceTest {
    @Mock private WechatPlatformClient platformClient;
    @Mock private RedisRateLimitService rateLimitService;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private VisitorDataCryptoService crypto;
    private VisitorSessionService service;

    @BeforeEach
    void setUp() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("test");
        crypto = new VisitorDataCryptoService("", environment);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new VisitorSessionService(platformClient, crypto, rateLimitService,
                redisTemplate, new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void sessionStoresOnlyTokenDigestAndEncryptedOpenid() {
        when(platformClient.login("wechat-code"))
                .thenReturn(new WechatPlatformClient.WechatIdentity("wx-app", "external-openid", null));
        SiteVisitInvitation invitation = new SiteVisitInvitation();
        invitation.setId(8L);
        invitation.setProjectId(10L);

        var issued = service.issue("wechat-code", invitation);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(keyCaptor.capture(), valueCaptor.capture(), eq(Duration.ofMinutes(30)));
        assertThat(keyCaptor.getValue()).doesNotContain(issued.getVisitorSessionToken());
        assertThat(valueCaptor.getValue()).doesNotContain("external-openid");
        when(valueOperations.get(anyString())).thenReturn(valueCaptor.getValue());
        var context = service.require(issued.getVisitorSessionToken());
        assertThat(context.invitationId()).isEqualTo(8L);
        assertThat(context.projectId()).isEqualTo(10L);
        assertThat(service.decryptOpenid(context)).isEqualTo("external-openid");
    }
}
