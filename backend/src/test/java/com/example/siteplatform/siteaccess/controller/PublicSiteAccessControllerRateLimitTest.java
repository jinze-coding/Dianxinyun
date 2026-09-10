package com.example.siteplatform.siteaccess.controller;

import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.common.GlobalExceptionHandler;
import com.example.siteplatform.common.RedisRateLimitService;
import com.example.siteplatform.siteaccess.security.PublicSiteAccessRequestGuardFilter;
import com.example.siteplatform.siteaccess.service.SiteAccessService;
import com.example.siteplatform.siteaccess.service.GuardVisitService;
import com.example.siteplatform.siteaccess.service.MeetingVisitService;
import com.example.siteplatform.siteaccess.service.MeetingCheckinService;
import com.example.siteplatform.project.service.ProjectProfileService;
import com.example.siteplatform.project.service.ProjectRouteImageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PublicSiteAccessControllerRateLimitTest {
    private static final String CLIENT_IP = "203.0.113.9";
    private static final String BASE = "/api/v1/public/site-access";

    @Mock private SiteAccessService service;
    @Mock private GuardVisitService guardVisitService;
    @Mock private MeetingVisitService meetingVisitService;
    @Mock private MeetingCheckinService meetingCheckinService;
    @Mock private RedisRateLimitService rateLimitService;

    private MockMvc mockMvc;
    private PublicSiteAccessRequestGuardFilter filter;

    @BeforeEach
    void setUp() {
        PublicSiteAccessController controller = new PublicSiteAccessController(
                service, guardVisitService, meetingVisitService, meetingCheckinService);
        filter = new PublicSiteAccessRequestGuardFilter(
                rateLimitService, new ObjectMapper().findAndRegisterModules());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(filter)
                .build();
    }

    @Test
    void guardStateAndMeetingChoicesAreLimitedBeforeBusinessReads() throws Exception {
        for (String path : java.util.List.of("/guard/state", "/guard/meetings")) {
            mockMvc.perform(withClient(post(BASE + path).header("X-Visitor-Session", "short-session")
                    .contentType(MediaType.APPLICATION_JSON).content("{}"))).andExpect(status().isOk());
        }
        verify(rateLimitService).check("public-site-guard-state", CLIENT_IP, 600, Duration.ofMinutes(10));
        verify(rateLimitService).check("public-site-guard-meetings", CLIENT_IP, 120, Duration.ofMinutes(10));
        org.mockito.Mockito.reset(guardVisitService);
        org.mockito.Mockito.doThrow(com.example.siteplatform.common.BusinessException.of(429, "请稍后重试"))
                .when(rateLimitService).check(anyString(), anyString(), anyInt(), any(Duration.class));
        mockMvc.perform(withClient(post(BASE + "/guard/state").header("X-Visitor-Session", "short-session")))
                .andExpect(status().isTooManyRequests());
        verifyNoInteractions(guardVisitService);
    }

    @Test
    void existingEndpointsKeepOriginalQuotasBeforeMvcBinding() throws Exception {
        when(service.publicProjectProfileImage(anyString(), anyInt())).thenReturn(
                new ProjectProfileService.PublicProjectProfileImageContent(
                        new ByteArrayResource(new byte[]{1}), MediaType.IMAGE_JPEG, "jpg", 1L));
        when(guardVisitService.publicProjectProfileImage(anyString(), anyInt())).thenReturn(
                new ProjectProfileService.PublicProjectProfileImageContent(
                        new ByteArrayResource(new byte[]{1}), MediaType.IMAGE_JPEG, "jpg", 1L));
        when(service.publicProjectRouteImage(anyString())).thenReturn(
                new ProjectRouteImageService.PublicProjectRouteImageContent(
                        new ByteArrayResource(new byte[]{1}), MediaType.IMAGE_PNG, "png", 1L));
        mockMvc.perform(withClient(post(BASE + "/invitations/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteToken\":\"development-token-value\"}")))
                .andExpect(status().isOk());
        mockMvc.perform(withClient(post(BASE + "/invitations/submit")
                        .contentType(MediaType.APPLICATION_JSON).content("{}")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(withClient(post(BASE + "/project-profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteToken\":\"development-token-value\"}")))
                .andExpect(status().isOk());
        mockMvc.perform(withClient(post(BASE + "/project-profile/images")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteToken\":\"development-token-value\",\"imageIndex\":0}")))
                .andExpect(status().isOk());
        mockMvc.perform(withClient(post(BASE + "/project-location/route-image")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteToken\":\"development-token-value\"}")))
                .andExpect(status().isOk());
        mockMvc.perform(withClient(post(BASE + "/visitor-sessions")
                        .contentType(MediaType.APPLICATION_JSON).content("{}")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(withClient(post(BASE + "/visitor-profiles/list")
                        .header("X-Visitor-Session", "session-token")))
                .andExpect(status().isOk());
        mockMvc.perform(withClient(post(BASE + "/visitor-profiles/detail")
                        .header("X-Visitor-Session", "session-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{}")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(withClient(post(BASE + "/visitor-profiles/disable")
                        .header("X-Visitor-Session", "session-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{}")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(withClient(post(BASE + "/meeting/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteToken\":\"development-token-value\",\"wechatCode\":\"wechat-code\"}")))
                .andExpect(status().isOk());
        mockMvc.perform(withClient(post(BASE + "/meeting/submit")
                        .header("X-Visitor-Session", "session-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{}")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(withClient(post(BASE + "/meeting/profiles/list")
                        .header("X-Visitor-Session", "session-token")))
                .andExpect(status().isOk());
        mockMvc.perform(withClient(post(BASE + "/meeting/profiles/detail")
                        .header("X-Visitor-Session", "session-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{}")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(withClient(post(BASE + "/meeting/profiles/disable")
                        .header("X-Visitor-Session", "session-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{}")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(withClient(post(BASE + "/meeting-check-in/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sceneToken\":\"AbCdEfGhIjKlMnOpQrStUv\",\"wechatCode\":\"wechat-code\"}")))
                .andExpect(status().isOk());
        mockMvc.perform(withClient(post(BASE + "/meeting-check-in/confirm")
                        .header("X-Visitor-Session", "session-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"attendees\":[{\"personId\":1}],\"location\":{\"locationAvailable\":false}}")))
                .andExpect(status().isOk());
        mockMvc.perform(withClient(post(BASE + "/meeting-check-in/walk-in")
                        .header("X-Visitor-Session", "session-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visitorCompany\":\"测试单位\",\"contactName\":\"张三\",\"contactPhone\":\"13800138000\",\"companions\":[],\"travelMode\":\"OTHER\",\"privacyAgreed\":true,\"location\":{\"locationAvailable\":false}}")))
                .andExpect(status().isOk());
        mockMvc.perform(withClient(post(BASE + "/meeting-check-in/profiles/list")
                        .header("X-Visitor-Session", "session-token")))
                .andExpect(status().isOk());
        mockMvc.perform(withClient(post(BASE + "/meeting-check-in/profiles/detail")
                        .header("X-Visitor-Session", "session-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{}")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(withClient(post(BASE + "/meeting-check-in/profiles/disable")
                        .header("X-Visitor-Session", "session-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{}")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(withClient(post(BASE + "/guard/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sceneToken\":\"development-guard-token\",\"wechatCode\":\"wechat-code\"}")))
                .andExpect(status().isOk());
        mockMvc.perform(withClient(post(BASE + "/guard/submit")
                        .header("X-Visitor-Session", "session-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{}")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(withClient(post(BASE + "/guard/project-profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sceneToken\":\"development-guard-token\"}")))
                .andExpect(status().isOk());
        mockMvc.perform(withClient(post(BASE + "/guard/project-profile/images")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sceneToken\":\"development-guard-token\",\"imageIndex\":0}")))
                .andExpect(status().isOk());
        mockMvc.perform(withClient(post(BASE + "/guard/profiles/list")
                        .header("X-Visitor-Session", "session-token")))
                .andExpect(status().isOk());
        mockMvc.perform(withClient(post(BASE + "/guard/profiles/detail")
                        .header("X-Visitor-Session", "session-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{}")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(withClient(post(BASE + "/guard/profiles/disable")
                        .header("X-Visitor-Session", "session-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{}")))
                .andExpect(status().isBadRequest());

        verify(rateLimitService).check("public-site-visit-resolve", CLIENT_IP,
                60, Duration.ofMinutes(10));
        verify(rateLimitService).check("public-site-visit-submit", CLIENT_IP,
                10, Duration.ofMinutes(30));
        verify(rateLimitService).check("public-site-project-profile", CLIENT_IP,
                60, Duration.ofMinutes(10));
        verify(rateLimitService).check("public-site-project-profile-image", CLIENT_IP,
                120, Duration.ofMinutes(10));
        verify(rateLimitService).check("public-site-project-route-image", CLIENT_IP,
                120, Duration.ofMinutes(10));
        verify(rateLimitService).check("public-site-visitor-session", CLIENT_IP,
                20, Duration.ofMinutes(10));
        verify(rateLimitService).check("public-site-visitor-profile-list", CLIENT_IP,
                60, Duration.ofMinutes(10));
        verify(rateLimitService).check("public-site-visitor-profile-detail", CLIENT_IP,
                60, Duration.ofMinutes(10));
        verify(rateLimitService).check("public-site-visitor-profile-disable", CLIENT_IP,
                10, Duration.ofMinutes(30));
        verify(rateLimitService).check("public-site-meeting-session", CLIENT_IP,
                20, Duration.ofMinutes(10));
        verify(rateLimitService).check("public-site-meeting-submit", CLIENT_IP,
                10, Duration.ofMinutes(30));
        verify(rateLimitService).check("public-site-meeting-profile-list", CLIENT_IP,
                60, Duration.ofMinutes(10));
        verify(rateLimitService).check("public-site-meeting-profile-detail", CLIENT_IP,
                60, Duration.ofMinutes(10));
        verify(rateLimitService).check("public-site-meeting-profile-disable", CLIENT_IP,
                10, Duration.ofMinutes(30));
        verify(rateLimitService).check("public-site-meeting-checkin-session", CLIENT_IP,
                20, Duration.ofMinutes(10));
        verify(rateLimitService).check("public-site-meeting-checkin-confirm", CLIENT_IP,
                10, Duration.ofMinutes(30));
        verify(rateLimitService).check("public-site-meeting-checkin-walkin", CLIENT_IP,
                10, Duration.ofMinutes(30));
        verify(rateLimitService).check("public-site-meeting-checkin-profile-list", CLIENT_IP,
                60, Duration.ofMinutes(10));
        verify(rateLimitService).check("public-site-meeting-checkin-profile-detail", CLIENT_IP,
                60, Duration.ofMinutes(10));
        verify(rateLimitService).check("public-site-meeting-checkin-profile-disable", CLIENT_IP,
                10, Duration.ofMinutes(30));
        verify(rateLimitService).check("public-site-guard-session", CLIENT_IP,
                20, Duration.ofMinutes(10));
        verify(rateLimitService).check("public-site-guard-submit", CLIENT_IP,
                10, Duration.ofMinutes(30));
        verify(rateLimitService).check("public-site-guard-project-profile", CLIENT_IP,
                60, Duration.ofMinutes(10));
        verify(rateLimitService).check("public-site-guard-project-profile-image", CLIENT_IP,
                120, Duration.ofMinutes(10));
        verify(rateLimitService).check("public-site-guard-profile-list", CLIENT_IP,
                60, Duration.ofMinutes(10));
        verify(rateLimitService).check("public-site-guard-profile-detail", CLIENT_IP,
                60, Duration.ofMinutes(10));
        verify(rateLimitService).check("public-site-guard-profile-disable", CLIENT_IP,
                10, Duration.ofMinutes(30));
        verify(service).resolvePublic("development-token-value");
        verify(service).publicProjectRouteImage("development-token-value");
    }

    @Test
    void missingVisitorSessionIsRateLimitedThenReturnsHttp401() throws Exception {
        mockMvc.perform(withClient(post(BASE + "/visitor-profiles/list")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("外访临时会话无效，请重新打开邀请"));

        verify(rateLimitService).check("public-site-visitor-profile-list", CLIENT_IP,
                60, Duration.ofMinutes(10));
        verify(service, never()).publicVisitorProfiles(anyString());
    }

    @Test
    void malformedJsonIsRateLimitedThenReturnsControlledHttp400() throws Exception {
        mockMvc.perform(withClient(post(BASE + "/invitations/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("请求体格式错误"));

        verify(rateLimitService).check("public-site-visit-resolve", CLIENT_IP,
                60, Duration.ofMinutes(10));
        verify(service, never()).resolvePublic(anyString());
    }

    @Test
    void wrongMethodIsRateLimitedThenReturnsControlledHttp405() throws Exception {
        mockMvc.perform(withClient(get(BASE + "/invitations/resolve")))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value(405))
                .andExpect(jsonPath("$.message").value("请求方法不支持"));

        verify(rateLimitService).check("public-site-visit-resolve", CLIENT_IP,
                60, Duration.ofMinutes(10));
    }

    @Test
    void unsupportedMediaTypeIsRateLimitedThenReturnsControlledHttp415() throws Exception {
        mockMvc.perform(withClient(post(BASE + "/invitations/resolve")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("{}")))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value(415))
                .andExpect(jsonPath("$.message").value("请求内容类型不支持"));

        verify(rateLimitService).check("public-site-visit-resolve", CLIENT_IP,
                60, Duration.ofMinutes(10));
    }

    @Test
    void oversizedBodyIsRateLimitedThenRejectedBeforeMvcBinding() throws Exception {
        byte[] oversized = new byte[64 * 1024 + 1];
        mockMvc.perform(withClient(post(BASE + "/invitations/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oversized)))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value(413))
                .andExpect(jsonPath("$.message").value("请求体不能超过64KB"));

        verify(rateLimitService).check("public-site-visit-resolve", CLIENT_IP,
                60, Duration.ofMinutes(10));
        verify(service, never()).resolvePublic(anyString());
    }

    @Test
    void exceededQuotaStopsBeforeMvcAndIsNotChargedTwice() throws Exception {
        doThrow(BusinessException.of(429, "操作过于频繁，请稍后再试"))
                .when(rateLimitService).check("public-site-visit-resolve", CLIENT_IP,
                        60, Duration.ofMinutes(10));

        mockMvc.perform(withClient(post(BASE + "/invitations/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(429));

        verify(rateLimitService, times(1)).check("public-site-visit-resolve", CLIENT_IP,
                60, Duration.ofMinutes(10));
        verifyNoInteractions(service);
    }

    @Test
    void unavailableRateLimitBackendFailsClosedWithoutLeakingCause() throws Exception {
        doThrow(new IllegalStateException("redis-password-and-host"))
                .when(rateLimitService).check("public-site-visit-resolve", CLIENT_IP,
                        60, Duration.ofMinutes(10));

        mockMvc.perform(withClient(post(BASE + "/invitations/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteToken\":\"development-token-value\"}")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(503))
                .andExpect(jsonPath("$.message").value("请求校验服务暂时不可用"));

        verifyNoInteractions(service);
    }

    @Test
    void validCorsPreflightDoesNotConsumeBusinessQuota() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS",
                BASE + "/invitations/resolve");
        request.setRemoteAddr(CLIENT_IP);
        request.addHeader("Origin", "http://127.0.0.1:3003");
        request.addHeader("Access-Control-Request-Method", "POST");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        verifyNoInteractions(rateLimitService, service);
    }

    private MockHttpServletRequestBuilder withClient(MockHttpServletRequestBuilder request) {
        return request.with(value -> {
            value.setRemoteAddr(CLIENT_IP);
            return value;
        });
    }
}
