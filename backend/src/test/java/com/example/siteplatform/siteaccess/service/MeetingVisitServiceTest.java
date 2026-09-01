package com.example.siteplatform.siteaccess.service;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.common.RedisRateLimitService;
import com.example.siteplatform.log.mapper.OperationLogMapper;
import com.example.siteplatform.project.entity.ProjectInfo;
import com.example.siteplatform.project.mapper.ProjectInfoMapper;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.project.service.ProjectRouteImageService;
import com.example.siteplatform.siteaccess.dto.PublicMeetingVisitSubmitRequest;
import com.example.siteplatform.siteaccess.dto.PublicMeetingVisitorSessionRequest;
import com.example.siteplatform.siteaccess.entity.SiteMeetingVisitRegistration;
import com.example.siteplatform.siteaccess.entity.SiteVisitInvitation;
import com.example.siteplatform.siteaccess.mapper.SiteMeetingVisitAuditLogMapper;
import com.example.siteplatform.siteaccess.mapper.SiteMeetingVisitPersonMapper;
import com.example.siteplatform.siteaccess.mapper.SiteMeetingVisitRegistrationMapper;
import com.example.siteplatform.siteaccess.mapper.SiteVisitInvitationMapper;
import com.example.siteplatform.siteaccess.vo.PublicSiteVisitInvitationVO;
import com.example.siteplatform.siteaccess.vo.PublicVisitorSessionVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeetingVisitServiceTest {
    @Mock private SiteMeetingVisitRegistrationMapper registrationMapper;
    @Mock private SiteMeetingVisitPersonMapper personMapper;
    @Mock private SiteMeetingVisitAuditLogMapper auditMapper;
    @Mock private SiteVisitInvitationMapper invitationMapper;
    @Mock private ProjectInfoMapper projectMapper;
    @Mock private ProjectPermissionService permissionService;
    @Mock private ProjectRouteImageService routeImageService;
    @Mock private VisitorDataCryptoService cryptoService;
    @Mock private VisitorSessionService sessionService;
    @Mock private VisitorProfileService profileService;
    @Mock private SiteAccessService siteAccessService;
    @Mock private RedisRateLimitService rateLimitService;
    @Mock private OperationLogMapper operationLogMapper;
    @Mock private TransactionTemplate transactionTemplate;

    private MeetingVisitService service;

    @BeforeEach
    void setUp() {
        service = new MeetingVisitService(registrationMapper, personMapper, auditMapper,
                invitationMapper, projectMapper, permissionService, routeImageService,
                cryptoService, sessionService, profileService, siteAccessService,
                rateLimitService, operationLogMapper, new ObjectMapper().findAndRegisterModules(),
                transactionTemplate);
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(new SimpleTransactionStatus());
        });
    }

    @Test
    void expiredMeetingDoesNotExchangeWechatCode() {
        SiteVisitInvitation invitation = meeting(LocalDateTime.now().minusMinutes(1));
        when(invitationMapper.selectOne(any())).thenReturn(invitation);
        PublicMeetingVisitorSessionRequest request = new PublicMeetingVisitorSessionRequest();
        request.setInviteToken("M:AbCdEfGhIjKlMnOpQrStUvWx");
        request.setWechatCode("wechat-code");

        assertThatThrownBy(() -> service.createPublicSession(request))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getCode()).isEqualTo(410));
        verify(sessionService, never()).issueMeeting(anyString(), any(), any());
    }

    @Test
    void sameWechatIdentityRescanReturnsOnlyItsActiveRegistration() {
        SiteVisitInvitation invitation = meeting(LocalDateTime.now().plusHours(2));
        ProjectInfo project = project();
        SiteMeetingVisitRegistration existing = registration();
        var context = context();
        when(invitationMapper.selectOne(any())).thenReturn(invitation);
        when(invitationMapper.selectForUpdate(11L)).thenReturn(invitation);
        when(projectMapper.selectById(7L)).thenReturn(project);
        when(sessionService.issueMeeting("wechat-code", 11L, 7L))
                .thenReturn(new PublicVisitorSessionVO("visitor-session", 1800));
        when(sessionService.requireMeeting("visitor-session", 11L, 7L)).thenReturn(context);
        when(sessionService.decryptOpenid(context)).thenReturn("openid-a");
        when(cryptoService.fingerprint(anyString(), anyString())).thenReturn("identity-a");
        when(registrationMapper.selectOne(any())).thenReturn(existing);
        when(siteAccessService.resolvePublic("M:AbCdEfGhIjKlMnOpQrStUvWx"))
                .thenReturn(new PublicSiteVisitInvitationVO());
        when(personMapper.selectList(any())).thenReturn(List.of());
        PublicMeetingVisitorSessionRequest request = new PublicMeetingVisitorSessionRequest();
        request.setInviteToken("M:AbCdEfGhIjKlMnOpQrStUvWx");
        request.setWechatCode("wechat-code");

        var result = service.createPublicSession(request);

        assertThat(result.getPageState()).isEqualTo(MeetingVisitService.PAGE_REGISTERED);
        assertThat(result.getRegistration().getRegistrationNo()).isEqualTo("MVR-EXISTING");
        assertThat(result.getRegistration().getValidUntil()).isEqualTo(invitation.getVisitEndTime());
    }

    @Test
    void meetingVoidedDuringWechatExchangeDoesNotReturnExistingPassOrNavigation() {
        SiteVisitInvitation initiallyOpen = meeting(LocalDateTime.now().plusHours(2));
        SiteVisitInvitation voided = meeting(LocalDateTime.now().plusHours(2));
        voided.setStatus(SiteAccessService.STATUS_VOIDED);
        when(invitationMapper.selectOne(any())).thenReturn(initiallyOpen);
        when(projectMapper.selectById(7L)).thenReturn(project());
        when(sessionService.issueMeeting("wechat-code", 11L, 7L))
                .thenReturn(new PublicVisitorSessionVO("visitor-session", 1800));
        when(invitationMapper.selectForUpdate(11L)).thenReturn(voided);
        PublicMeetingVisitorSessionRequest request = meetingSessionRequest();

        assertThatThrownBy(() -> service.createPublicSession(request))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getCode()).isEqualTo(410));

        verify(sessionService, never()).requireMeeting(anyString(), any(), any());
        verify(registrationMapper, never()).selectOne(any());
        verify(siteAccessService, never()).resolvePublic(anyString());
    }

    @Test
    void meetingExpiredDuringWechatExchangeDoesNotReturnExistingPassOrNavigation() {
        SiteVisitInvitation initiallyOpen = meeting(LocalDateTime.now().plusHours(2));
        SiteVisitInvitation expired = meeting(LocalDateTime.now().minusSeconds(1));
        when(invitationMapper.selectOne(any())).thenReturn(initiallyOpen);
        when(projectMapper.selectById(7L)).thenReturn(project());
        when(sessionService.issueMeeting("wechat-code", 11L, 7L))
                .thenReturn(new PublicVisitorSessionVO("visitor-session", 1800));
        when(invitationMapper.selectForUpdate(11L)).thenReturn(expired);
        PublicMeetingVisitorSessionRequest request = meetingSessionRequest();

        assertThatThrownBy(() -> service.createPublicSession(request))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getCode()).isEqualTo(410));

        verify(sessionService, never()).requireMeeting(anyString(), any(), any());
        verify(registrationMapper, never()).selectOne(any());
        verify(siteAccessService, never()).resolvePublic(anyString());
    }

    @Test
    void repeatedSubmitIsIdempotentAndDoesNotCreateAnotherRegistration() {
        SiteVisitInvitation invitation = meeting(LocalDateTime.now().plusHours(2));
        SiteMeetingVisitRegistration existing = registration();
        var context = context();
        when(sessionService.require("visitor-session")).thenReturn(context);
        when(sessionService.requireMeeting("visitor-session", 11L, 7L)).thenReturn(context);
        when(sessionService.decryptOpenid(context)).thenReturn("openid-a");
        when(cryptoService.fingerprint(anyString(), anyString())).thenReturn("identity-a");
        when(projectMapper.selectByIdForUpdate(7L)).thenReturn(project());
        when(invitationMapper.selectForUpdate(11L)).thenReturn(invitation);
        when(registrationMapper.selectActiveForUpdate(11L, "wx-app", "identity-a"))
                .thenReturn(existing);
        when(personMapper.selectList(any())).thenReturn(List.of());

        var result = service.submitPublic(validSubmit(), "visitor-session");

        assertThat(result.getRegistrationNo()).isEqualTo("MVR-EXISTING");
        verify(registrationMapper, never()).insert(any());
        verify(profileService, never()).applyOnSubmission(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void guardSessionCannotBeDowngradedToAnonymousMeetingRegistration() {
        var guardContext = new VisitorSessionService.VisitorSessionContext(
                null, 7L, "wx-app", "profile-hash", "encrypted-openid",
                VisitorSessionService.SOURCE_GUARD_QR, 99L);
        when(sessionService.require("visitor-session")).thenReturn(guardContext);

        assertThatThrownBy(() -> service.submitPublic(validSubmit(), "visitor-session"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getCode()).isEqualTo(403));
        verify(registrationMapper, never()).insert(any());
    }

    @Test
    void meetingProfileListUsesPurposeSeparatedWechatIdentityRateLimit() {
        SiteVisitInvitation invitation = meeting(LocalDateTime.now().plusHours(2));
        var context = context();
        when(sessionService.require("visitor-session")).thenReturn(context);
        when(invitationMapper.selectById(11L)).thenReturn(invitation);
        when(projectMapper.selectById(7L)).thenReturn(project());
        when(sessionService.requireMeeting("visitor-session", 11L, 7L)).thenReturn(context);
        when(sessionService.decryptOpenid(context)).thenReturn("openid-a");
        when(cryptoService.fingerprint("site-access:meeting-registration:v1", "wx-app:openid-a"))
                .thenReturn("identity-a");
        when(profileService.publicList(context)).thenReturn(List.of());

        assertThat(service.publicProfiles("visitor-session")).isEmpty();

        verify(rateLimitService).check("public-site-meeting-profile-list-identity", "identity-a",
                60, java.time.Duration.ofMinutes(10));
        verify(profileService).publicList(context);
    }

    @Test
    void staleVersionCannotVoidMeetingRegistration() {
        SiteMeetingVisitRegistration existing = registration();
        when(registrationMapper.selectForUpdate(21L)).thenReturn(existing);
        when(invitationMapper.selectForUpdate(11L)).thenReturn(meeting(LocalDateTime.now().plusHours(2)));
        SysUser user = new SysUser();
        user.setId(8L);
        user.setUsername("manager");

        assertThatThrownBy(() -> service.voidRegistration(21L, "信息有误", 2, user))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getCode()).isEqualTo(409));
        verify(registrationMapper, never()).updateById(any());
    }

    private SiteVisitInvitation meeting(LocalDateTime endTime) {
        SiteVisitInvitation invitation = new SiteVisitInvitation();
        invitation.setId(11L);
        invitation.setProjectId(7L);
        invitation.setInviteNo("VIS-MEETING-1");
        invitation.setInviteType(SiteAccessService.INVITE_TYPE_MEETING);
        invitation.setStatus(SiteAccessService.STATUS_OPEN);
        invitation.setPurpose("安全交底会议");
        invitation.setVisitLocation("项目会议室");
        invitation.setHostName("接待人");
        invitation.setVisitStartTime(endTime.minusHours(1));
        invitation.setVisitEndTime(endTime);
        return invitation;
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

    private SiteMeetingVisitRegistration registration() {
        SiteMeetingVisitRegistration registration = new SiteMeetingVisitRegistration();
        registration.setId(21L);
        registration.setInvitationId(11L);
        registration.setProjectId(7L);
        registration.setRegistrationNo("MVR-EXISTING");
        registration.setStatus(MeetingVisitService.STATUS_REGISTERED);
        registration.setVisitorCompany("测试单位");
        registration.setContactName("张三");
        registration.setVisitorCount(1);
        registration.setTravelMode("OTHER");
        registration.setRegisteredTime(LocalDateTime.now().minusMinutes(10));
        registration.setVersion(3);
        return registration;
    }

    private VisitorSessionService.VisitorSessionContext context() {
        return new VisitorSessionService.VisitorSessionContext(
                null, 7L, "wx-app", "profile-hash", "encrypted-openid",
                VisitorSessionService.SOURCE_MEETING_INVITATION, 11L);
    }

    private PublicMeetingVisitSubmitRequest validSubmit() {
        PublicMeetingVisitSubmitRequest request = new PublicMeetingVisitSubmitRequest();
        request.setVisitorCompany("测试单位");
        request.setContactName("张三");
        request.setContactPhone("13800138000");
        request.setCompanions(List.of());
        request.setTravelMode("OTHER");
        request.setPrivacyAgreed(true);
        request.setProfileAction("NONE");
        return request;
    }

    private PublicMeetingVisitorSessionRequest meetingSessionRequest() {
        PublicMeetingVisitorSessionRequest request = new PublicMeetingVisitorSessionRequest();
        request.setInviteToken("M:AbCdEfGhIjKlMnOpQrStUvWx");
        request.setWechatCode("wechat-code");
        return request;
    }
}
