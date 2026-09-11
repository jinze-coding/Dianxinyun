package com.example.siteplatform.siteaccess.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.auth.service.WechatPlatformClient;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.log.mapper.OperationLogMapper;
import com.example.siteplatform.project.entity.ProjectInfo;
import com.example.siteplatform.project.dto.PublicProjectProfileVO;
import com.example.siteplatform.project.mapper.ProjectInfoMapper;
import com.example.siteplatform.project.mapper.SysUserProjectMapper;
import com.example.siteplatform.project.entity.SysUserProject;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.project.service.ProjectProfileService;
import com.example.siteplatform.project.service.ProjectRouteImageService;
import com.example.siteplatform.siteaccess.dto.PublicSiteVisitSubmitRequest;
import com.example.siteplatform.siteaccess.dto.SiteVisitInvitationCreateRequest;
import com.example.siteplatform.siteaccess.dto.SiteVisitPersonRequest;
import com.example.siteplatform.siteaccess.entity.SiteVisitAuditLog;
import com.example.siteplatform.siteaccess.entity.SiteVisitInvitation;
import com.example.siteplatform.siteaccess.entity.SiteVisitPerson;
import com.example.siteplatform.siteaccess.mapper.SiteVisitAuditLogMapper;
import com.example.siteplatform.siteaccess.mapper.SiteVisitInvitationMapper;
import com.example.siteplatform.siteaccess.mapper.SiteVisitPersonMapper;
import com.example.siteplatform.siteaccess.mapper.SiteMeetingVisitRegistrationMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SiteAccessServiceTest {
    private static final String TOKEN = "abcdefghijklmnopqrstuv";
    private static final String TEST_PHONE = syntheticPhone("138");
    private static final String HOST_PHONE = syntheticPhone("139");
    private static final String COMPANION_PHONE = syntheticPhone("137");
    private static final String VALID_ID_CARD = syntheticIdCard(1);

    @Mock private SiteVisitInvitationMapper invitationMapper;
    @Mock private SiteVisitPersonMapper personMapper;
    @Mock private SiteVisitAuditLogMapper auditLogMapper;
    @Mock private SiteMeetingVisitRegistrationMapper meetingRegistrationMapper;
    @Mock private ProjectInfoMapper projectInfoMapper;
    @Mock private SysUserMapper userMapper;
    @Mock private SysUserProjectMapper userProjectMapper;
    @Mock private ProjectPermissionService projectPermissionService;
    @Mock private ProjectProfileService projectProfileService;
    @Mock private ProjectRouteImageService projectRouteImageService;
    @Mock private WechatPlatformClient wechatPlatformClient;
    @Mock private VisitorSessionService visitorSessionService;
    @Mock private VisitorProfileService visitorProfileService;
    @Mock private VisitorPersonalProfileService personalProfileService;
    @Mock private MeetingCheckinQrProvisioner meetingCheckinQrProvisioner;
    @Mock private OperationLogMapper operationLogMapper;

    private VisitorDataCryptoService crypto;
    private SiteAccessService service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), SiteVisitInvitationMapper.class.getName()),
                SiteVisitInvitation.class);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("test");
        crypto = new VisitorDataCryptoService("", environment);
        service = new SiteAccessService(invitationMapper, personMapper, auditLogMapper, meetingRegistrationMapper,
                projectInfoMapper, userMapper, userProjectMapper, projectPermissionService,
                projectProfileService, projectRouteImageService,
                crypto, wechatPlatformClient, visitorSessionService, visitorProfileService, personalProfileService,
                meetingCheckinQrProvisioner, operationLogMapper,
                new ObjectMapper().findAndRegisterModules(), "pages/public/visitor-invite",
                "pages/public/meeting-invite", "release");
    }

    @Test
    void invitationNumberUsesPlannedVisitTimeRange() {
        String invitationNumber = service.generateInviteNo(
                LocalDateTime.of(2026, 8, 8, 12, 0),
                LocalDateTime.of(2026, 8, 8, 14, 0));

        assertThat(invitationNumber)
                .matches("VIS-202608081200-202608081400-[0-9A-F]{8}")
                .hasSize(38);
    }

    @Test
    void changedVisitTimeRebuildsInvitationNumberAndKeepsUniqueSuffix() {
        String invitationNumber = service.rebuildInviteNo(
                LocalDateTime.of(2026, 8, 9, 9, 30),
                LocalDateTime.of(2026, 8, 9, 11, 0),
                "VIS-202608081200-202608081400-A1B2C3D4");

        assertThat(invitationNumber).isEqualTo("VIS-202608090930-202608091100-A1B2C3D4");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void invitationPageKeywordMatchesTopicAndKeepsExistingFields() {
        when(invitationMapper.selectPage(any(Page.class), any(Wrapper.class))).thenAnswer(invocation -> {
            Page<SiteVisitInvitation> page = invocation.getArgument(0);
            page.setTotal(0);
            page.setRecords(List.of());
            return page;
        });
        when(projectInfoMapper.selectById(10L)).thenReturn(project());
        SysUser currentUser = new SysUser();
        currentUser.setId(7L);

        service.page(10L, null, null, "  安全交底会  ", null, null, 1, 20, currentUser);

        ArgumentCaptor<Wrapper<SiteVisitInvitation>> captor =
                (ArgumentCaptor) ArgumentCaptor.forClass(Wrapper.class);
        verify(invitationMapper).selectPage(any(Page.class), captor.capture());
        LambdaQueryWrapper<SiteVisitInvitation> wrapper =
                (LambdaQueryWrapper<SiteVisitInvitation>) captor.getValue();
        assertThat(wrapper.getSqlSegment())
                .contains("invite_no", "purpose", "visitor_company", "contact_name", "vehicle_plate", "host_name");
        assertThat(wrapper.getParamNameValuePairs().values()).contains("%安全交底会%");
    }

    @Test
    void creatingMeetingInvitationAlsoCreatesIndependentVenueCheckinQr() {
        SiteVisitInvitationCreateRequest request = new SiteVisitInvitationCreateRequest();
        request.setInviteType(SiteAccessService.INVITE_TYPE_MEETING);
        request.setProjectId(10L);
        request.setVisitStartTime(LocalDateTime.now().plusHours(2));
        request.setVisitEndTime(LocalDateTime.now().plusHours(4));
        request.setPurpose("安全交底会");
        request.setVisitLocation("项目会议室");
        request.setHostUserId(8L);
        SysUser operator = new SysUser();
        operator.setId(7L);
        operator.setUsername("manager");
        SysUser host = new SysUser();
        host.setId(8L);
        host.setUsername("host");
        host.setRealName("接待人");
        host.setPhone(HOST_PHONE);
        host.setStatus(1);
        host.setDeleted(0);
        SysUserProject membership = new SysUserProject();
        membership.setProjectId(10L);
        membership.setUserId(8L);
        membership.setStatus("ACTIVE");
        when(projectInfoMapper.selectByIdForUpdate(10L)).thenReturn(project());
        when(userMapper.selectById(8L)).thenReturn(host);
        when(userProjectMapper.selectOne(any(Wrapper.class))).thenReturn(membership);
        when(invitationMapper.insert(any())).thenAnswer(invocation -> {
            SiteVisitInvitation value = invocation.getArgument(0);
            value.setId(11L);
            return 1;
        });
        when(auditLogMapper.insert(any())).thenReturn(1);
        when(operationLogMapper.insert(any())).thenReturn(1);
        when(personMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(auditLogMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        var created = service.create(request, operator);

        assertThat(created.getInviteType()).isEqualTo(SiteAccessService.INVITE_TYPE_MEETING);
        assertThat(created.getStatus()).isEqualTo(SiteAccessService.STATUS_OPEN);
        ArgumentCaptor<SiteVisitInvitation> invitation = ArgumentCaptor.forClass(SiteVisitInvitation.class);
        verify(meetingCheckinQrProvisioner).provision(invitation.capture(), eq(operator));
        assertThat(invitation.getValue().getId()).isEqualTo(11L);
    }

    @Test
    void publicSubmissionBindsWechatIdentityWithoutCollectingIdCard() {
        SiteVisitInvitation invitation = pendingInvitation();
        stubPublicSubmission(invitation);
        when(invitationMapper.selectOne(any(Wrapper.class))).thenReturn(invitation);
        when(projectInfoMapper.selectById(10L)).thenReturn(project());
        when(personMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(personMapper.insert(any(SiteVisitPerson.class))).thenReturn(1);
        when(invitationMapper.updateById(invitation)).thenReturn(1);
        when(auditLogMapper.insert(any(SiteVisitAuditLog.class))).thenReturn(1);

        var result = service.submitPublic(validSubmission(), "visitor-session");

        assertThat(result.getStatus()).isEqualTo(SiteAccessService.STATUS_SUBMITTED);
        assertThat(result.getVisitorCompany()).isEqualTo("单位");
        assertThat(result.getContactName()).isEqualTo("外访联系人");
        assertThat(result.getVisitorCount()).isEqualTo(1);
        assertThat(result.getSubmittedTime()).isNotNull();
        assertThat(result.getServerTime()).isNotNull();
        assertThat(invitation.getVisitorCount()).isEqualTo(1);
        assertThat(invitation.getWechatAppId()).isEqualTo("wx-app");
        assertThat(invitation.getVisitorIdentityHash()).hasSize(64).doesNotContain("openid");
        verify(personalProfileService).saveOnSubmission(any(), any(), any());
        assertThat(invitation.getContactPhoneEncrypted()).startsWith("v1:").doesNotContain(TEST_PHONE);
        assertThat(crypto.decrypt(invitation.getContactPhoneEncrypted())).isEqualTo(TEST_PHONE);
        ArgumentCaptor<SiteVisitPerson> personCaptor = ArgumentCaptor.forClass(SiteVisitPerson.class);
        verify(personMapper).insert(personCaptor.capture());
        SiteVisitPerson saved = personCaptor.getValue();
        assertThat(saved.getPersonCompany()).isEqualTo("单位");
        assertThat(saved.getPersonName()).isEqualTo("外访联系人");
        assertThat(crypto.decrypt(saved.getPhoneEncrypted())).isEqualTo(TEST_PHONE);
        assertThat(saved.getIdCardEncrypted()).isNull();
        assertThat(saved.getIdCardHash()).isNull();
        ArgumentCaptor<SiteVisitAuditLog> auditCaptor = ArgumentCaptor.forClass(SiteVisitAuditLog.class);
        verify(auditLogMapper).insert(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getAfterSnapshotEncrypted())
                .startsWith("v1:")
                .doesNotContain(TEST_PHONE)
                .doesNotContain("idCard");
    }

    @Test
    void reusableProfileSourceIsBoundToTheSubmittedInvitationSnapshot() {
        SiteVisitInvitation invitation = pendingInvitation();
        stubPublicSubmission(invitation);
        when(invitationMapper.selectOne(any(Wrapper.class))).thenReturn(invitation);
        when(projectInfoMapper.selectById(10L)).thenReturn(project());
        when(personMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(personMapper.insert(any(SiteVisitPerson.class))).thenReturn(1);
        when(invitationMapper.updateById(invitation)).thenReturn(1);
        when(auditLogMapper.insert(any(SiteVisitAuditLog.class))).thenReturn(1);
        var context = new VisitorSessionService.VisitorSessionContext(
                invitation.getId(), invitation.getProjectId(), "wx-app", "identity", crypto.encrypt("openid"));
        when(visitorSessionService.require("visitor-session", invitation)).thenReturn(context);
        when(visitorProfileService.applyOnSubmission(
                any(), any(), any(), any(), any(), any(), any())).thenReturn(77L);
        PublicSiteVisitSubmitRequest request = validSubmission();
        request.setProfileAction("CREATE");
        request.setProfileRetentionAgreed(true);

        service.submitPublic(request, "visitor-session");

        assertThat(invitation.getSourceProfileId()).isEqualTo(77L);
        verify(visitorSessionService).require("visitor-session", invitation);
    }

    @Test
    void publicProjectProfileAllowsPendingAndSubmittedInvitationsBeforeVisitEnd() {
        SiteVisitInvitation invitation = pendingInvitation();
        PublicProjectProfileVO profile = new PublicProjectProfileVO();
        profile.setProjectName("公开项目");
        when(invitationMapper.selectOne(any(Wrapper.class))).thenReturn(invitation);
        when(projectProfileService.getPublicProfile(10L)).thenReturn(profile);

        assertThat(service.publicProjectProfile(TOKEN)).isSameAs(profile);

        invitation.setStatus(SiteAccessService.STATUS_SUBMITTED);
        assertThat(service.publicProjectProfile(TOKEN)).isSameAs(profile);
    }

    @Test
    void publicProjectProfileRejectsVoidedOrEndedInvitation() {
        SiteVisitInvitation invitation = pendingInvitation();
        when(invitationMapper.selectOne(any(Wrapper.class))).thenReturn(invitation);
        invitation.setStatus(SiteAccessService.STATUS_VOIDED);

        BusinessException voided = assertThrows(BusinessException.class,
                () -> service.publicProjectProfile(TOKEN));
        assertThat(voided.getCode()).isEqualTo(409);

        invitation.setStatus(SiteAccessService.STATUS_SUBMITTED);
        invitation.setVisitEndTime(LocalDateTime.now().minusSeconds(1));
        BusinessException ended = assertThrows(BusinessException.class,
                () -> service.publicProjectProfile(TOKEN));
        assertThat(ended.getCode()).isEqualTo(409);
        verify(projectProfileService, never()).getPublicProfile(any());
    }

    @Test
    void publicRouteImageUsesTheSameInvitationStateAndDepartureValidation() {
        SiteVisitInvitation invitation = pendingInvitation();
        when(invitationMapper.selectOne(any(Wrapper.class))).thenReturn(invitation);
        var content = new ProjectRouteImageService.PublicProjectRouteImageContent(
                new ByteArrayResource(new byte[]{1}), MediaType.IMAGE_JPEG, "jpg", 1L);
        when(projectRouteImageService.publicImage(10L)).thenReturn(content);

        assertThat(service.publicProjectRouteImage(TOKEN)).isSameAs(content);

        invitation.setStatus(SiteAccessService.STATUS_SUBMITTED);
        invitation.setVisitEndTime(LocalDateTime.now().minusSeconds(1));
        BusinessException ended = assertThrows(BusinessException.class,
                () -> service.publicProjectRouteImage(TOKEN));
        assertThat(ended.getCode()).isEqualTo(409);
        verify(projectRouteImageService, times(1)).publicImage(10L);
    }

    @Test
    void submittedInvitationResolvesAsGatePassUntilPlannedDeparture() {
        SiteVisitInvitation invitation = pendingInvitation();
        invitation.setStatus(SiteAccessService.STATUS_SUBMITTED);
        invitation.setVisitorCompany("单位");
        invitation.setContactName("外访联系人");
        invitation.setVisitorCount(2);
        invitation.setTravelMode(SiteAccessService.TRAVEL_DRIVING);
        invitation.setVehiclePlate("京A12345");
        invitation.setSubmittedTime(LocalDateTime.now().minusMinutes(5));
        when(invitationMapper.selectOne(any(Wrapper.class))).thenReturn(invitation);
        when(projectInfoMapper.selectById(10L)).thenReturn(project());
        when(projectRouteImageService.hasActiveImage(10L)).thenReturn(true);

        var pass = service.resolvePublic(TOKEN);

        assertThat(pass.getStatus()).isEqualTo(SiteAccessService.STATUS_SUBMITTED);
        assertThat(pass.getVisitorCompany()).isEqualTo("单位");
        assertThat(pass.getContactName()).isEqualTo("外访联系人");
        assertThat(pass.getVisitorCount()).isEqualTo(2);
        assertThat(pass.getTravelMode()).isEqualTo(SiteAccessService.TRAVEL_DRIVING);
        assertThat(pass.getVehiclePlate()).isEqualTo("京A12345");
        assertThat(pass.getSubmittedTime()).isEqualTo(invitation.getSubmittedTime());
        assertThat(pass.getServerTime()).isNotNull();
        assertThat(pass.getProjectLocation().getAddress()).isEqualTo("项目测试地址");
        assertThat(pass.getProjectLocation().getNavigable()).isTrue();
        assertThat(pass.getProjectLocation().getCoordinateType()).isEqualTo("GCJ02");
        assertThat(pass.getProjectLocation().getRouteImageAvailable()).isTrue();
        assertThat(pass.getProjectLocation().getLongitude()).isEqualByComparingTo("116.404000");
        assertThat(pass.getProjectLocation().getLatitude()).isEqualByComparingTo("39.915000");

        invitation.setVisitEndTime(LocalDateTime.now().minusSeconds(1));
        var expired = service.resolvePublic(TOKEN);
        assertThat(expired.getStatus()).isEqualTo(SiteAccessService.STATUS_EXPIRED);
        assertThat(expired.getProjectLocation()).isNull();
    }

    @Test
    void publicInvitationReturnsAddressWithoutNavigationWhenProjectHasNoCoordinates() {
        SiteVisitInvitation invitation = pendingInvitation();
        ProjectInfo project = project();
        project.setLongitude(null);
        project.setLatitude(null);
        when(invitationMapper.selectOne(any(Wrapper.class))).thenReturn(invitation);
        when(projectInfoMapper.selectById(10L)).thenReturn(project);

        var result = service.resolvePublic(TOKEN);

        assertThat(result.getProjectLocation().getAddress()).isEqualTo("项目测试地址");
        assertThat(result.getProjectLocation().getNavigable()).isFalse();
        assertThat(result.getProjectLocation().getCoordinateType()).isEqualTo("GCJ02");
        assertThat(result.getProjectLocation().getLongitude()).isNull();
        assertThat(result.getProjectLocation().getLatitude()).isNull();
    }

    @Test
    void publicInvitationConvertsWgs84CoordinatesAndHidesLocationForVoidedInvite() {
        SiteVisitInvitation invitation = pendingInvitation();
        ProjectInfo project = project();
        project.setLongitude(new BigDecimal("116.397128"));
        project.setLatitude(new BigDecimal("39.916527"));
        project.setCoordinateType("WGS84");
        when(invitationMapper.selectOne(any(Wrapper.class))).thenReturn(invitation);
        when(projectInfoMapper.selectById(10L)).thenReturn(project);

        var active = service.resolvePublic(TOKEN);
        assertThat(active.getProjectLocation().getLongitude()).isEqualByComparingTo("116.403372");
        assertThat(active.getProjectLocation().getLatitude()).isEqualByComparingTo("39.917931");

        invitation.setStatus(SiteAccessService.STATUS_VOIDED);
        assertThat(service.resolvePublic(TOKEN).getProjectLocation()).isNull();
    }

    @Test
    void submittedInvitationMiniCodeRemainsAvailableUntilPlannedDeparture() {
        SiteVisitInvitation invitation = pendingInvitation();
        invitation.setStatus(SiteAccessService.STATUS_SUBMITTED);
        when(invitationMapper.selectById(1L)).thenReturn(invitation);
        SysUser user = new SysUser();
        user.setId(7L);

        var miniCode = service.miniCode(1L, user);

        assertThat(miniCode.getInvitationId()).isEqualTo(1L);
        assertThat(miniCode.getHint()).contains("再次扫码").contains("门卫放行");
        verify(wechatPlatformClient).generateUnlimitedCode(
                "V:" + TOKEN, "pages/public/visitor-invite", "release");

        invitation.setVisitEndTime(LocalDateTime.now().minusSeconds(1));
        BusinessException ended = assertThrows(BusinessException.class,
                () -> service.miniCode(1L, user));
        assertThat(ended.getCode()).isEqualTo(409);
        assertThat(ended.getMessage()).contains("计划来访时段");
    }

    @Test
    void publicSubmissionAcceptsCompanionsWithoutIdCard() {
        SiteVisitInvitation invitation = pendingInvitation();
        stubPublicSubmission(invitation);
        when(invitationMapper.selectOne(any(Wrapper.class))).thenReturn(invitation);
        when(projectInfoMapper.selectById(10L)).thenReturn(project());
        when(personMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(personMapper.insert(any(SiteVisitPerson.class))).thenReturn(1);
        when(invitationMapper.updateById(invitation)).thenReturn(1);
        when(auditLogMapper.insert(any(SiteVisitAuditLog.class))).thenReturn(1);
        PublicSiteVisitSubmitRequest request = validSubmission();
        SiteVisitPersonRequest companion = new SiteVisitPersonRequest();
        companion.setPersonCompany("同行单位");
        companion.setPersonName("同行人员");
        companion.setPersonPhone(COMPANION_PHONE);
        request.setCompanions(List.of(companion));

        service.submitPublic(request, "visitor-session");

        ArgumentCaptor<SiteVisitPerson> people = ArgumentCaptor.forClass(SiteVisitPerson.class);
        verify(personMapper, org.mockito.Mockito.times(2)).insert(people.capture());
        assertThat(people.getAllValues()).extracting(SiteVisitPerson::getPersonName)
                .containsExactly("外访联系人", "同行人员");
        SiteVisitPerson savedCompanion = people.getAllValues().get(1);
        assertThat(savedCompanion.getPersonCompany()).isEqualTo("同行单位");
        assertThat(crypto.decrypt(savedCompanion.getPhoneEncrypted())).isEqualTo(COMPANION_PHONE);
        assertThat(people.getAllValues()).allSatisfy(person -> {
            assertThat(person.getIdCardEncrypted()).isNull();
            assertThat(person.getIdCardHash()).isNull();
        });
    }

    @Test
    void blankCompanionIsIgnoredAndPartialOptionalCompanionIsStored() {
        SiteVisitInvitation invitation = pendingInvitation();
        stubPublicSubmission(invitation);
        when(invitationMapper.selectOne(any(Wrapper.class))).thenReturn(invitation);
        when(projectInfoMapper.selectById(10L)).thenReturn(project());
        when(personMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(personMapper.insert(any(SiteVisitPerson.class))).thenReturn(1);
        when(invitationMapper.updateById(invitation)).thenReturn(1);
        when(auditLogMapper.insert(any(SiteVisitAuditLog.class))).thenReturn(1);
        SiteVisitPersonRequest blank = new SiteVisitPersonRequest();
        blank.setPersonCompany("  ");
        blank.setPersonName("");
        SiteVisitPersonRequest companyOnly = new SiteVisitPersonRequest();
        companyOnly.setPersonCompany("仅填写单位");
        PublicSiteVisitSubmitRequest request = validSubmission();
        request.setCompanions(List.of(blank, companyOnly));

        service.submitPublic(request, "visitor-session");

        assertThat(invitation.getVisitorCount()).isEqualTo(2);
        ArgumentCaptor<SiteVisitPerson> people = ArgumentCaptor.forClass(SiteVisitPerson.class);
        verify(personMapper, org.mockito.Mockito.times(2)).insert(people.capture());
        SiteVisitPerson savedCompanion = people.getAllValues().get(1);
        assertThat(savedCompanion.getPersonCompany()).isEqualTo("仅填写单位");
        assertThat(savedCompanion.getPersonName()).isNull();
        assertThat(savedCompanion.getPhoneEncrypted()).isNull();
    }

    @Test
    void optionalCompanionPhoneIsValidatedWhenProvided() {
        SiteVisitInvitation invitation = pendingInvitation();
        stubPublicSubmission(invitation);
        PublicSiteVisitSubmitRequest request = validSubmission();
        SiteVisitPersonRequest companion = new SiteVisitPersonRequest();
        companion.setPersonPhone("12345");
        request.setCompanions(List.of(companion));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.submitPublic(request, "visitor-session"));

        assertThat(exception.getMessage()).contains("同行人员手机号码格式不正确");
        verify(personMapper, never()).insert(any());
        verify(invitationMapper, never()).updateById(any());
    }

    @Test
    void repeatedSingleSubmissionReturnsOriginalResultOnlyToSameWechatWithoutSavingAgain() {
        SiteVisitInvitation invitation = pendingInvitation();
        invitation.setStatus(SiteAccessService.STATUS_SUBMITTED);
        invitation.setWechatAppId("wx-app");
        invitation.setVisitorIdentityHash(crypto.fingerprint("site-access:single-registration:v1", "wx-app:openid"));
        invitation.setContactName("首次提交的姓名");
        stubPublicSubmission(invitation);
        when(projectInfoMapper.selectById(10L)).thenReturn(project());

        var repeated = service.submitPublic(validSubmission(), "visitor-session");
        assertThat(repeated.getContactName()).isEqualTo("首次提交的姓名");
        assertThat(repeated.getStatus()).isEqualTo(SiteAccessService.STATUS_SUBMITTED);
        verify(personalProfileService, never()).saveOnSubmission(any(), any(), any());
        verify(invitationMapper, never()).updateById(any());
        verify(personMapper, never()).insert(any());
        verify(auditLogMapper, never()).insert(any());

        when(visitorSessionService.decryptOpenid(any())).thenReturn("another-openid");
        BusinessException denied = assertThrows(BusinessException.class,
                () -> service.submitPublic(validSubmission(), "visitor-session"));
        assertThat(denied.getCode()).isEqualTo(403);
        assertThat(denied.getMessage()).contains("其他微信");
    }

    @Test
    void submittedOrExpiredInvitationCannotBeSubmittedAgain() {
        SiteVisitInvitation invitation = pendingInvitation();
        invitation.setStatus(SiteAccessService.STATUS_SUBMITTED);
        stubPublicSubmission(invitation);

        BusinessException submitted = assertThrows(BusinessException.class,
                () -> service.submitPublic(validSubmission(), "visitor-session"));
        assertThat(submitted.getCode()).isEqualTo(409);
        assertThat(submitted.getMessage()).contains("已经提交");

        invitation.setStatus(SiteAccessService.STATUS_PENDING);
        invitation.setVisitEndTime(LocalDateTime.now().minusMinutes(1));
        BusinessException expired = assertThrows(BusinessException.class,
                () -> service.submitPublic(validSubmission(), "visitor-session"));
        assertThat(expired.getCode()).isEqualTo(409);
        assertThat(expired.getMessage()).contains("已过期");
        verify(personMapper, never()).insert(any());
    }

    @Test
    void invalidTokenAndDrivingWithoutPlateAreRejectedBeforeWriting() {
        PublicSiteVisitSubmitRequest invalidToken = validSubmission();
        invalidToken.setInviteToken("too-short");
        BusinessException tokenError = assertThrows(BusinessException.class,
                () -> service.submitPublic(invalidToken, "visitor-session"));
        assertThat(tokenError.getCode()).isEqualTo(404);
        verify(invitationMapper, never()).selectForUpdateByTokenHash(anyString());

        SiteVisitInvitation invitation = pendingInvitation();
        stubPublicSubmission(invitation);
        PublicSiteVisitSubmitRequest noPlate = validSubmission();
        noPlate.setTravelMode(SiteAccessService.TRAVEL_DRIVING);
        BusinessException plateError = assertThrows(BusinessException.class,
                () -> service.submitPublic(noPlate, "visitor-session"));
        assertThat(plateError.getMessage()).contains("必须填写车牌号");
        verify(personMapper, never()).insert(any());
        verify(invitationMapper, never()).updateById(any());
    }

    @Test
    void moreThanFiftyVisitorsAreRejectedBeforeWriting() {
        SiteVisitInvitation invitation = pendingInvitation();
        stubPublicSubmission(invitation);
        PublicSiteVisitSubmitRequest request = validSubmission();
        request.setCompanions(IntStream.rangeClosed(2, 51).mapToObj(sequence -> {
            SiteVisitPersonRequest companion = new SiteVisitPersonRequest();
            companion.setPersonName("同行人员" + sequence);
            return companion;
        }).toList());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.submitPublic(request, "visitor-session"));

        assertThat(exception.getMessage()).contains("最多登记50名人员");
        verify(personMapper, never()).insert(any());
        verify(invitationMapper, never()).updateById(any());
    }

    @Test
    void exportExpandsOnePersonPerRowAndNeutralizesFormulaPrefixes() throws Exception {
        SiteVisitInvitation invitation = pendingInvitation();
        invitation.setStatus(SiteAccessService.STATUS_SUBMITTED);
        invitation.setVisitorCompany("=WEBSERVICE(\"https://example.invalid\")");
        invitation.setContactName("外访联系人");
        invitation.setContactPhoneEncrypted(crypto.encrypt(TEST_PHONE));
        invitation.setVisitorCount(1);
        invitation.setTravelMode(SiteAccessService.TRAVEL_OTHER);
        invitation.setSubmittedTime(LocalDateTime.now());
        SiteVisitPerson person = new SiteVisitPerson();
        person.setId(3L);
        person.setInvitationId(invitation.getId());
        person.setPersonType(SiteAccessService.PERSON_CONTACT);
        person.setPersonName("+危险前缀");
        person.setIdCardEncrypted(crypto.encrypt(VALID_ID_CARD));
        person.setSortOrder(1);
        when(invitationMapper.selectList(any(Wrapper.class))).thenReturn(List.of(invitation));
        when(personMapper.selectList(any(Wrapper.class))).thenReturn(List.of(person));
        when(projectInfoMapper.selectById(10L)).thenReturn(project());
        when(auditLogMapper.insert(any(SiteVisitAuditLog.class))).thenReturn(1);
        when(operationLogMapper.insert(any())).thenReturn(1);
        SysUser user = new SysUser();
        user.setId(7L);
        user.setUsername("admin");

        SiteAccessService.ExportFile file = service.export(10L, null, null,
                LocalDate.now(), LocalDate.now(), user);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file.content()))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getLastRowNum()).isEqualTo(1);
            assertThat(sheet.getRow(1).getCell(6).getStringCellValue()).startsWith("'=");
            assertThat(sheet.getRow(1).getCell(8).getStringCellValue()).isEqualTo("'+危险前缀");
            assertThat(sheet.getRow(0).getCell(9).getStringCellValue()).isEqualTo("手机号码");
            assertThat(sheet.getRow(1).getCell(9).getStringCellValue()).isEqualTo(TEST_PHONE);
            assertThat(sheet.getRow(1).getLastCellNum()).isEqualTo((short) 16);
        }
    }

    private void stubPublicSubmission(SiteVisitInvitation invitation) {
        when(invitationMapper.selectForUpdateByTokenHash(anyString())).thenReturn(invitation);
        org.mockito.Mockito.lenient().when(invitationMapper.selectOne(any(Wrapper.class))).thenReturn(invitation);
        when(projectInfoMapper.selectByIdForUpdate(10L)).thenReturn(project());
        var context = new VisitorSessionService.VisitorSessionContext(
                invitation.getId(), invitation.getProjectId(), "wx-app", "profile-hash", crypto.encrypt("openid"));
        org.mockito.Mockito.lenient().when(visitorSessionService.require("visitor-session", invitation)).thenReturn(context);
        org.mockito.Mockito.lenient().when(visitorSessionService.decryptOpenid(any())).thenReturn("openid");
    }

    @Test
    void failedWechatSessionCannotSubmitOrRememberPersonalInformation() {
        SiteVisitInvitation invitation = pendingInvitation();
        stubPublicSubmission(invitation);
        when(visitorSessionService.require("visitor-session", invitation))
                .thenThrow(BusinessException.of(401, "请重新获取微信身份后提交"));
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.submitPublic(validSubmission(), "visitor-session"));
        assertThat(error.getCode()).isEqualTo(401);
        verify(personMapper, never()).insert(any());
        verify(invitationMapper, never()).updateById(any());
        verify(personalProfileService, never()).saveOnSubmission(any(), any(), any());
    }

    private SiteVisitInvitation pendingInvitation() {
        SiteVisitInvitation invitation = new SiteVisitInvitation();
        invitation.setId(1L);
        invitation.setProjectId(10L);
        invitation.setInviteNo("VIS-20260807-TEST0001");
        invitation.setTokenHash(crypto.digest(TOKEN));
        invitation.setTokenEncrypted(crypto.encrypt(TOKEN));
        invitation.setStatus(SiteAccessService.STATUS_PENDING);
        invitation.setVisitStartTime(LocalDateTime.now().plusHours(1));
        invitation.setVisitEndTime(LocalDateTime.now().plusHours(3));
        invitation.setPurpose("项目会议");
        invitation.setVisitLocation("项目会议室");
        invitation.setHostUserId(8L);
        invitation.setHostName("接待人");
        invitation.setHostPhoneEncrypted(crypto.encrypt(HOST_PHONE));
        invitation.setVisitorCount(0);
        invitation.setVersion(0);
        invitation.setDeleted(0);
        invitation.setCreateTime(LocalDateTime.now());
        invitation.setUpdateTime(LocalDateTime.now());
        return invitation;
    }

    private PublicSiteVisitSubmitRequest validSubmission() {
        PublicSiteVisitSubmitRequest request = new PublicSiteVisitSubmitRequest();
        request.setInviteToken(TOKEN);
        request.setVisitorCompany("单位");
        request.setContactName("外访联系人");
        request.setContactPhone(TEST_PHONE);
        request.setCompanions(List.of());
        request.setTravelMode(SiteAccessService.TRAVEL_OTHER);
        request.setPrivacyAgreed(true);
        return request;
    }

    private ProjectInfo project() {
        ProjectInfo project = new ProjectInfo();
        project.setId(10L);
        project.setProjectName("外访测试项目");
        project.setShortName("测试项目");
        project.setAddress("项目测试地址");
        project.setLongitude(new BigDecimal("116.41036949371029"));
        project.setLatitude(new BigDecimal("39.92133699351021"));
        project.setCoordinateType("BD09");
        project.setDeleted(0);
        return project;
    }

    private static String syntheticPhone(String prefix) {
        return prefix + "0".repeat(8);
    }

    private static String syntheticIdCard(int sequence) {
        String prefix = "99" + "0000" + "20000101" + String.format("%03d", sequence);
        int[] weights = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
        char[] checks = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};
        int sum = 0;
        for (int index = 0; index < prefix.length(); index++) {
            sum += (prefix.charAt(index) - '0') * weights[index];
        }
        return prefix + checks[sum % 11];
    }
}
