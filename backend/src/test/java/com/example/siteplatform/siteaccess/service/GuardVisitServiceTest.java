package com.example.siteplatform.siteaccess.service;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.service.WechatPlatformClient;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.common.RedisRateLimitService;
import com.example.siteplatform.log.mapper.OperationLogMapper;
import com.example.siteplatform.project.entity.ProjectInfo;
import com.example.siteplatform.project.mapper.ProjectInfoMapper;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.project.service.ProjectProfileService;
import com.example.siteplatform.project.dto.PublicProjectProfileVO;
import com.example.siteplatform.siteaccess.dto.PublicGuardVisitSubmitRequest;
import com.example.siteplatform.siteaccess.dto.PublicGuardVisitorSessionRequest;
import com.example.siteplatform.siteaccess.entity.SiteGuardVisitQr;
import com.example.siteplatform.siteaccess.entity.SiteGuardVisitRegistration;
import com.example.siteplatform.siteaccess.mapper.SiteGuardVisitAuditLogMapper;
import com.example.siteplatform.siteaccess.mapper.SiteGuardVisitPersonMapper;
import com.example.siteplatform.siteaccess.mapper.SiteGuardVisitQrMapper;
import com.example.siteplatform.siteaccess.mapper.SiteGuardVisitRegistrationMapper;
import com.example.siteplatform.siteaccess.vo.PublicVisitorSessionVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GuardVisitServiceTest {
    @Mock private SiteGuardVisitQrMapper qrMapper;
    @Mock private SiteGuardVisitRegistrationMapper registrationMapper;
    @Mock private SiteGuardVisitPersonMapper personMapper;
    @Mock private SiteGuardVisitAuditLogMapper auditMapper;
    @Mock private ProjectInfoMapper projectMapper;
    @Mock private ProjectPermissionService permissionService;
    @Mock private ProjectProfileService projectProfileService;
    @Mock private VisitorSessionService sessionService;
    @Mock private VisitorProfileService profileService;
    @Mock private VisitorPersonalProfileService personalProfiles;
    @Mock private GuardVisitorMatchingService matching;
    @Mock private GuardMeetingChoiceService meetingChoices;
    @Mock private MeetingVisitService meetingVisits;
    @Mock private com.example.siteplatform.siteaccess.mapper.SiteGuardMeetingRegistrationMapper meetingLinks;
    @Mock private WechatPlatformClient wechatPlatformClient;
    @Mock private RedisRateLimitService rateLimitService;
    @Mock private OperationLogMapper operationLogMapper;

    private GuardVisitService service;
    private VisitorDataCryptoService crypto;

    @BeforeEach
    void setUp() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("test");
        crypto = new VisitorDataCryptoService("", environment);
        service = new GuardVisitService(qrMapper, registrationMapper, personMapper, auditMapper,
                projectMapper, permissionService, projectProfileService, crypto, sessionService, profileService, personalProfiles, matching, meetingChoices, meetingVisits, meetingLinks,
                wechatPlatformClient, rateLimitService, operationLogMapper,
                new ObjectMapper().findAndRegisterModules(), "pages/public/guard-visitor-register", "develop");
    }

    @Test
    void rotatedSceneReturnsHttp410WithoutCallingWechatLogin() {
        SiteGuardVisitQr qr = qr(GuardVisitService.QR_ROTATED);
        when(qrMapper.selectOne(any())).thenReturn(qr);
        PublicGuardVisitorSessionRequest request = new PublicGuardVisitorSessionRequest();
        request.setSceneToken("G:AbCdEfGhIjKlMnOpQrStUvWxYz1");
        request.setWechatCode("wechat-code");

        assertThatThrownBy(() -> service.createPublicSession(request))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getCode()).isEqualTo(410));
        verify(sessionService, never()).issueGuard(anyString(), any(), any());
    }

    @Test
    void activeIdentityRescanAndConcurrentSubmitReturnExistingPass() {
        ProjectInfo project = project();
        SiteGuardVisitQr qr = qr(GuardVisitService.QR_ENABLED);
        SiteGuardVisitRegistration existing = registration();
        var context = new VisitorSessionService.VisitorSessionContext(
                null, 7L, "wx-app", "profile-owner-hash", "encrypted-openid",
                VisitorSessionService.SOURCE_GUARD_QR, 11L);
        when(sessionService.require("visitor-session")).thenReturn(context);
        when(sessionService.requireGuard("visitor-session", 11L, 7L)).thenReturn(context);
        when(sessionService.decryptOpenid(context)).thenReturn("openid-value");
        when(projectMapper.selectByIdForUpdate(7L)).thenReturn(project);
        when(qrMapper.selectForUpdate(11L)).thenReturn(qr);
        when(registrationMapper.selectActiveForUpdate(any(), anyString(), anyString(), any()))
                .thenReturn(existing);

        var result = service.submitPublic(validSubmit(), "visitor-session");

        assertThat(result.getRegistrationNo()).isEqualTo("GVR-EXISTING");
        assertThat(result.getStatus()).isEqualTo(GuardVisitService.STATUS_REGISTERED);
        verify(registrationMapper, never()).insert(any());
        verify(profileService, never()).applyOnSubmission(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void sessionForSameIdentityReturnsRegisteredState() {
        ProjectInfo project = project();
        SiteGuardVisitQr qr = qr(GuardVisitService.QR_ENABLED);
        SiteGuardVisitRegistration existing = registration();
        var context = new VisitorSessionService.VisitorSessionContext(
                null, 7L, "wx-app", "profile-owner-hash", "encrypted-openid",
                VisitorSessionService.SOURCE_GUARD_QR, 11L);
        when(qrMapper.selectOne(any())).thenReturn(qr);
        org.mockito.Mockito.lenient().when(qrMapper.selectById(11L)).thenReturn(qr);
        org.mockito.Mockito.lenient().when(sessionService.requireGuard("visitor-session", 11L, 7L)).thenAnswer(call -> sessionService.require("visitor-session"));
        when(projectMapper.selectById(7L)).thenReturn(project);
        when(sessionService.issueGuard("wechat-code", 11L, 7L))
                .thenReturn(new PublicVisitorSessionVO("visitor-session", 1800));
        when(sessionService.require("visitor-session")).thenReturn(context);
        when(sessionService.decryptOpenid(context)).thenReturn("openid-value");
        when(registrationMapper.selectOne(any())).thenReturn(existing);
        PublicGuardVisitorSessionRequest request = new PublicGuardVisitorSessionRequest();
        request.setSceneToken("G:AbCdEfGhIjKlMnOpQrStUvWxYz1");
        request.setWechatCode("wechat-code");

        var result = service.createPublicSession(request);

        assertThat(result.getPageState()).isEqualTo(GuardVisitService.PAGE_REGISTERED);
        assertThat(result.getRegistration().getRegistrationNo()).isEqualTo("GVR-EXISTING");
    }

    @Test
    void enabledGuardSceneCanReadOnlyExposeSanitizedProjectProfile() {
        SiteGuardVisitQr qr = qr(GuardVisitService.QR_ENABLED);
        ProjectInfo project = project();
        PublicProjectProfileVO profile = new PublicProjectProfileVO();
        profile.setProjectName("测试项目");
        when(qrMapper.selectOne(any())).thenReturn(qr);
        org.mockito.Mockito.lenient().when(qrMapper.selectById(11L)).thenReturn(qr);
        org.mockito.Mockito.lenient().when(sessionService.requireGuard("visitor-session", 11L, 7L)).thenAnswer(call -> sessionService.require("visitor-session"));
        when(projectMapper.selectById(7L)).thenReturn(project);
        when(projectProfileService.getPublicProfile(7L)).thenReturn(profile);

        PublicProjectProfileVO result = service.publicProjectProfile("G:AbCdEfGhIjKlMnOpQrStUvWxYz1");

        assertThat(result.getProjectName()).isEqualTo("测试项目");
        verify(projectProfileService).getPublicProfile(7L);
    }

    @Test
    void disabledGuardSceneCannotReadProjectProfile() {
        when(qrMapper.selectOne(any())).thenReturn(qr(GuardVisitService.QR_DISABLED));

        assertThatThrownBy(() -> service.publicProjectProfile("G:AbCdEfGhIjKlMnOpQrStUvWxYz1"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getCode()).isEqualTo(410));
        verify(projectProfileService, never()).getPublicProfile(any());
    }

    private SiteGuardVisitQr qr(String status) {
        SiteGuardVisitQr qr = new SiteGuardVisitQr();
        qr.setId(11L);
        qr.setProjectId(7L);
        qr.setQrStatus(status);
        qr.setQrVersion(3);
        qr.setVersion(2);
        return qr;
    }

    private ProjectInfo project() {
        ProjectInfo project = new ProjectInfo();
        project.setId(7L);
        project.setProjectName("测试项目");
        project.setShortName("测试");
        project.setProjectStatus("normal");
        project.setDeleted(0);
        return project;
    }

    private SiteGuardVisitRegistration registration() {
        SiteGuardVisitRegistration registration = new SiteGuardVisitRegistration();
        registration.setId(21L);
        registration.setRegistrationNo("GVR-EXISTING");
        registration.setProjectId(7L);
        registration.setStatus(GuardVisitService.STATUS_REGISTERED);
        registration.setVisitorCompany("测试单位");
        registration.setContactName("张三");
        registration.setVisitorCount(1);
        registration.setTravelMode("OTHER");
        registration.setRegisteredTime(LocalDateTime.now().minusHours(1));
        registration.setValidUntil(LocalDateTime.now().plusHours(23));
        return registration;
    }

    private PublicGuardVisitSubmitRequest validSubmit() {
        PublicGuardVisitSubmitRequest request = new PublicGuardVisitSubmitRequest();
        request.setVisitorCompany("测试单位");
        request.setContactName("张三");
        request.setContactPhone("13800138000");
        request.setCompanions(List.of());
        request.setTravelMode("OTHER");
        request.setPrivacyAgreed(true);
        request.setProfileAction("NONE");
        return request;
    }
}
