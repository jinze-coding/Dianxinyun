package com.example.siteplatform.siteaccess.service;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.service.WechatPlatformClient;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.common.RedisRateLimitService;
import com.example.siteplatform.log.mapper.OperationLogMapper;
import com.example.siteplatform.project.entity.ProjectInfo;
import com.example.siteplatform.project.mapper.ProjectInfoMapper;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.siteaccess.dto.MeetingCheckinLocationRequest;
import com.example.siteplatform.siteaccess.dto.PublicMeetingCheckinConfirmRequest;
import com.example.siteplatform.siteaccess.dto.PublicMeetingCheckinSessionRequest;
import com.example.siteplatform.siteaccess.dto.PublicMeetingCheckinWalkInRequest;
import com.example.siteplatform.siteaccess.dto.SiteMeetingAttendanceActionRequest;
import com.example.siteplatform.siteaccess.dto.SiteMeetingAttendeeUpdateRequest;
import com.example.siteplatform.siteaccess.entity.SiteMeetingAttendance;
import com.example.siteplatform.siteaccess.entity.SiteMeetingCheckinQr;
import com.example.siteplatform.siteaccess.entity.SiteMeetingVisitPerson;
import com.example.siteplatform.siteaccess.entity.SiteMeetingVisitRegistration;
import com.example.siteplatform.siteaccess.entity.SiteVisitInvitation;
import com.example.siteplatform.siteaccess.mapper.SiteMeetingAttendanceMapper;
import com.example.siteplatform.siteaccess.mapper.SiteMeetingCheckinQrMapper;
import com.example.siteplatform.siteaccess.mapper.SiteMeetingVisitAuditLogMapper;
import com.example.siteplatform.siteaccess.mapper.SiteMeetingVisitPersonMapper;
import com.example.siteplatform.siteaccess.mapper.SiteMeetingVisitRegistrationMapper;
import com.example.siteplatform.siteaccess.mapper.SiteVisitInvitationMapper;
import com.example.siteplatform.siteaccess.vo.PublicVisitorSessionVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.DuplicateKeyException;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeetingCheckinServiceTest {
    private static final String SCENE = "AbCdEfGhIjKlMnOpQrStUv";

    @Mock private SiteMeetingCheckinQrMapper qrMapper;
    @Mock private SiteMeetingAttendanceMapper attendanceMapper;
    @Mock private SiteMeetingVisitRegistrationMapper registrationMapper;
    @Mock private SiteMeetingVisitPersonMapper personMapper;
    @Mock private SiteMeetingVisitAuditLogMapper auditMapper;
    @Mock private SiteVisitInvitationMapper invitationMapper;
    @Mock private ProjectInfoMapper projectMapper;
    @Mock private ProjectPermissionService permissionService;
    @Mock private VisitorDataCryptoService cryptoService;
    @Mock private VisitorSessionService sessionService;
    @Mock private VisitorProfileService profileService;
    @Mock private RedisRateLimitService rateLimitService;
    @Mock private WechatPlatformClient wechatPlatformClient;
    @Mock private OperationLogMapper operationLogMapper;
    @Mock private MeetingCheckinQrProvisioner provisioner;
    @Mock private TransactionTemplate transactionTemplate;

    private MeetingCheckinService service;

    @BeforeEach
    void setUp() {
        service = new MeetingCheckinService(qrMapper, attendanceMapper, registrationMapper, personMapper,
                auditMapper, invitationMapper, projectMapper, permissionService, cryptoService, sessionService,
                profileService, rateLimitService, wechatPlatformClient, operationLogMapper, provisioner,
                new ObjectMapper().findAndRegisterModules(), transactionTemplate,
                "pages/public/meeting-check-in", "release");
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(new SimpleTransactionStatus());
        });
    }

    @Test
    void disabledVenueQrIsRejectedBeforeWechatCodeExchange() {
        SiteMeetingCheckinQr qr = qr(MeetingCheckinService.QR_DISABLED);
        when(cryptoService.digest(SCENE)).thenReturn("scene-digest");
        when(qrMapper.selectOne(any())).thenReturn(qr);
        when(invitationMapper.selectById(11L)).thenReturn(invitation());
        PublicMeetingCheckinSessionRequest request = new PublicMeetingCheckinSessionRequest();
        request.setSceneToken(SCENE);
        request.setWechatCode("wechat-code");

        assertThatThrownBy(() -> service.createPublicSession(request))
                .isInstanceOfSatisfying(BusinessException.class, error -> {
                    assertThat(error.getCode()).isEqualTo(410);
                    assertThat(error.getMessage()).contains("已停用");
                });
        verify(sessionService, never()).issueMeetingCheckin(anyString(), any(), any());
    }

    @Test
    void readingLegacyMeetingWithoutQrDoesNotCreateOneAsGetSideEffect() {
        when(invitationMapper.selectById(11L)).thenReturn(invitation());
        when(qrMapper.selectCurrent(11L)).thenReturn(null);
        SysUser viewer = new SysUser();
        viewer.setId(9L);

        assertThat(service.settings(11L, viewer)).isNull();

        verify(provisioner, never()).provision(any(), any());
    }

    @Test
    void meetingEndRejectsSessionEvenWhenQrWindowIsStale() {
        SiteMeetingCheckinQr qr = qr(MeetingCheckinService.QR_ENABLED);
        SiteVisitInvitation ended = invitation();
        ended.setVisitEndTime(LocalDateTime.now().minusMinutes(1));
        when(cryptoService.digest(SCENE)).thenReturn("scene-digest");
        when(qrMapper.selectOne(any())).thenReturn(qr);
        when(invitationMapper.selectById(11L)).thenReturn(ended);
        PublicMeetingCheckinSessionRequest request = new PublicMeetingCheckinSessionRequest();
        request.setSceneToken(SCENE);
        request.setWechatCode("wechat-code");

        assertThatThrownBy(() -> service.createPublicSession(request))
                .isInstanceOfSatisfying(BusinessException.class, error -> {
                    assertThat(error.getCode()).isEqualTo(410);
                    assertThat(error.getMessage()).contains("会议已经结束");
                });
        verify(sessionService, never()).issueMeetingCheckin(anyString(), any(), any());
    }

    @Test
    void publicCheckinCannotSubmitPersonFromAnotherRegistrationGroup() {
        prepareConfirmContext();
        SiteMeetingVisitPerson foreign = person(99L, 999L, "外组人员");
        when(personMapper.selectForUpdate(99L)).thenReturn(foreign);
        PublicMeetingCheckinConfirmRequest request = confirmRequest(99L, false);

        assertThatThrownBy(() -> service.confirmPublic(request, "visitor-session"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getCode()).isEqualTo(403));
        verify(attendanceMapper, never()).insert(any());
        verify(auditMapper, never()).insert(any());
    }

    @Test
    void repeatedCheckinReturnsExistingReceiptWithoutDuplicateAttendanceOrAudit() {
        prepareConfirmContext();
        SiteMeetingVisitPerson person = person(81L, 21L, "张三");
        SiteMeetingAttendance attendance = checkedAttendance(person.getId());
        when(personMapper.selectForUpdate(81L)).thenReturn(person);
        when(attendanceMapper.selectByPersonForUpdate(81L)).thenReturn(attendance);
        when(personMapper.selectList(any())).thenReturn(List.of(person));
        when(attendanceMapper.selectList(any())).thenReturn(List.of(attendance));

        var receipt = service.confirmPublic(confirmRequest(81L, false), "visitor-session");

        assertThat(receipt.getCheckedInCount()).isEqualTo(1);
        assertThat(receipt.getPendingCount()).isZero();
        verify(attendanceMapper, never()).insert(any());
        verify(attendanceMapper, never()).updateById(any());
        verify(auditMapper, never()).insert(any());
    }

    @Test
    void publicCheckinRequiresLegacyBlankNameAndCannotOverwriteExistingName() {
        prepareConfirmContext();
        SiteMeetingVisitPerson person = person(81L, 21L, "张三");
        when(personMapper.selectForUpdate(81L)).thenReturn(person);
        PublicMeetingCheckinConfirmRequest request = confirmRequest(81L, false);
        request.getAttendees().get(0).setCompletedName("李四");

        assertThatThrownBy(() -> service.confirmPublic(request, "visitor-session"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getMessage()).contains("已有姓名不能"));
        verify(attendanceMapper, never()).insert(any());
    }

    @Test
    void locationIsReducedToDistanceAccuracyAndProjectVersionBeforePersistence() {
        prepareConfirmContext();
        ProjectInfo located = project();
        located.setLongitude(new BigDecimal("120.123456"));
        located.setLatitude(new BigDecimal("30.123456"));
        located.setCoordinateType("GCJ02");
        located.setProfileVersion(7);
        when(projectMapper.selectById(7L)).thenReturn(located);
        SiteMeetingVisitPerson person = person(81L, 21L, "张三");
        when(personMapper.selectForUpdate(81L)).thenReturn(person);
        AtomicReference<SiteMeetingAttendance> inserted = new AtomicReference<>();
        when(attendanceMapper.insert(any())).thenAnswer(invocation -> {
            SiteMeetingAttendance value = invocation.getArgument(0);
            value.setId(301L);
            inserted.set(value);
            return 1;
        });
        when(personMapper.selectList(any())).thenReturn(List.of(person));
        when(attendanceMapper.selectList(any())).thenAnswer(invocation -> List.of(inserted.get()));
        when(auditMapper.insert(any())).thenReturn(1);
        when(cryptoService.encrypt(anyString())).thenReturn("encrypted-audit");
        PublicMeetingCheckinConfirmRequest request = confirmRequest(81L, true);

        var receipt = service.confirmPublic(request, "visitor-session");

        assertThat(receipt.getLocationResult()).isEqualTo(MeetingCheckinService.LOCATION_IN_RANGE);
        assertThat(inserted.get()).satisfies(value -> {
            assertThat(value.getDistanceMeters()).isZero();
            assertThat(value.getAccuracyMeters()).isEqualTo(18);
            assertThat(value.getReferenceProjectVersion()).isEqualTo(7);
            assertThat(value.getLocationResult()).isEqualTo(MeetingCheckinService.LOCATION_IN_RANGE);
        });
    }

    @Test
    void unavailableLocationDoesNotBlockCheckin() {
        prepareConfirmContext();
        SiteMeetingVisitPerson person = person(81L, 21L, "张三");
        when(personMapper.selectForUpdate(81L)).thenReturn(person);
        AtomicReference<SiteMeetingAttendance> inserted = new AtomicReference<>();
        when(attendanceMapper.insert(any())).thenAnswer(invocation -> {
            SiteMeetingAttendance value = invocation.getArgument(0);
            value.setId(301L);
            inserted.set(value);
            return 1;
        });
        when(personMapper.selectList(any())).thenReturn(List.of(person));
        when(attendanceMapper.selectList(any())).thenAnswer(invocation -> List.of(inserted.get()));
        when(auditMapper.insert(any())).thenReturn(1);
        when(cryptoService.encrypt(anyString())).thenReturn("encrypted-audit");

        var receipt = service.confirmPublic(confirmRequest(81L, false), "visitor-session");

        assertThat(receipt.getLocationResult()).isEqualTo(MeetingCheckinService.LOCATION_UNAVAILABLE);
        assertThat(inserted.get().getLocationResult()).isEqualTo(MeetingCheckinService.LOCATION_UNAVAILABLE);
        assertThat(inserted.get().getDistanceMeters()).isNull();
    }

    @Test
    void outOfRangeLocationIsRecordedButDoesNotBlockCheckin() {
        prepareConfirmContext();
        ProjectInfo located = project();
        located.setLongitude(new BigDecimal("121.123456"));
        located.setLatitude(new BigDecimal("31.123456"));
        located.setCoordinateType("GCJ02");
        when(projectMapper.selectById(7L)).thenReturn(located);
        SiteMeetingVisitPerson person = person(81L, 21L, "张三");
        when(personMapper.selectForUpdate(81L)).thenReturn(person);
        AtomicReference<SiteMeetingAttendance> inserted = new AtomicReference<>();
        when(attendanceMapper.insert(any())).thenAnswer(invocation -> {
            SiteMeetingAttendance value = invocation.getArgument(0);
            value.setId(301L);
            inserted.set(value);
            return 1;
        });
        when(personMapper.selectList(any())).thenReturn(List.of(person));
        when(attendanceMapper.selectList(any())).thenAnswer(invocation -> List.of(inserted.get()));
        when(auditMapper.insert(any())).thenReturn(1);
        when(cryptoService.encrypt(anyString())).thenReturn("encrypted-audit");

        var receipt = service.confirmPublic(confirmRequest(81L, true), "visitor-session");

        assertThat(receipt.getLocationResult()).isEqualTo(MeetingCheckinService.LOCATION_OUT_OF_RANGE);
        assertThat(inserted.get().getDistanceMeters()).isGreaterThan(300);
    }

    @Test
    void concurrentWalkInRetryReturnsRacedRegistrationWithoutCreatingDuplicatePeople() {
        VisitorSessionService.VisitorSessionContext context = new VisitorSessionService.VisitorSessionContext(
                null, 7L, "wx-app", "base-hash", "openid-encrypted",
                VisitorSessionService.SOURCE_MEETING_CHECKIN_QR, 31L);
        when(sessionService.require("visitor-session")).thenReturn(context);
        when(sessionService.requireMeetingCheckin("visitor-session", 31L, 7L)).thenReturn(context);
        when(sessionService.decryptOpenid(context)).thenReturn("openid-a");
        when(cryptoService.fingerprint(eq("site-access:meeting-checkin:v1"), anyString())).thenReturn("checkin-hash");
        when(cryptoService.fingerprint(eq("site-access:meeting-registration:v1"), anyString())).thenReturn("registration-hash");
        when(qrMapper.selectForUpdate(31L)).thenReturn(qr(MeetingCheckinService.QR_ENABLED));
        when(invitationMapper.selectForUpdate(11L)).thenReturn(invitation());
        when(projectMapper.selectByIdForUpdate(7L)).thenReturn(project());
        SiteMeetingVisitRegistration raced = registration();
        raced.setRegistrationSource(MeetingCheckinService.SOURCE_WALK_IN);
        when(registrationMapper.selectActiveForUpdate(11L, "wx-app", "registration-hash"))
                .thenReturn(null, raced);
        when(registrationMapper.insert(any())).thenThrow(new DuplicateKeyException("duplicate active identity"));
        SiteMeetingVisitPerson existingPerson = person(81L, 21L, "张三");
        when(personMapper.selectList(any())).thenReturn(List.of(existingPerson));
        when(attendanceMapper.selectList(any())).thenReturn(List.of());
        PublicMeetingCheckinWalkInRequest request = new PublicMeetingCheckinWalkInRequest();
        request.setVisitorCompany("测试单位");
        request.setContactName("张三");
        request.setContactPhone("13800138000");
        request.setTravelMode("OTHER");
        request.setPrivacyAgreed(true);
        MeetingCheckinLocationRequest location = new MeetingCheckinLocationRequest();
        location.setLocationAvailable(false);
        request.setLocation(location);

        var receipt = service.walkInPublic(request, "visitor-session");

        assertThat(receipt.getRegistrationNo()).isEqualTo("MVR-1");
        verify(personMapper, never()).insert(any());
        verify(auditMapper, never()).insert(any());
    }

    @Test
    void manualCheckinRejectsVoidedRegistrationBeforeAttendanceWrite() {
        SiteMeetingVisitPerson person = person(81L, 21L, "张三");
        SiteMeetingVisitRegistration registration = registration();
        registration.setStatus(MeetingVisitService.STATUS_VOIDED);
        when(personMapper.selectForUpdate(81L)).thenReturn(person);
        when(registrationMapper.selectForUpdate(21L)).thenReturn(registration);
        when(invitationMapper.selectForUpdate(11L)).thenReturn(invitation());
        SiteMeetingAttendanceActionRequest request = new SiteMeetingAttendanceActionRequest();
        request.setReason("现场核实补签");
        request.setVersion(0);
        SysUser user = new SysUser();
        user.setId(9L);
        user.setUsername("manager");

        assertThatThrownBy(() -> service.manualCheckIn(81L, request, user))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getCode()).isEqualTo(409));
        verify(attendanceMapper, never()).insert(any());
        verify(auditMapper, never()).insert(any());
    }

    @Test
    void correctingContactAlsoSynchronizesRegistrationHeader() {
        SiteMeetingVisitPerson person = person(81L, 21L, "旧姓名");
        person.setPersonCompany("旧单位");
        SiteMeetingVisitRegistration registration = registration();
        registration.setVisitorCompany("旧单位");
        registration.setContactName("旧姓名");
        registration.setVersion(2);
        when(personMapper.selectForUpdate(81L)).thenReturn(person);
        when(registrationMapper.selectForUpdate(21L)).thenReturn(registration);
        when(invitationMapper.selectForUpdate(11L)).thenReturn(invitation());
        when(personMapper.updateById(person)).thenReturn(1);
        when(registrationMapper.updateById(registration)).thenReturn(1);
        when(cryptoService.encrypt(anyString())).thenReturn("encrypted-value");
        when(auditMapper.insert(any())).thenReturn(1);
        when(operationLogMapper.insert(any())).thenReturn(1);
        Map<String, Object> updatedRow = attendeeRow();
        updatedRow.put("personName", "新姓名");
        updatedRow.put("personCompany", "新单位");
        when(attendanceMapper.selectAttendeeByPersonId(81L)).thenReturn(updatedRow);
        SiteMeetingAttendeeUpdateRequest request = new SiteMeetingAttendeeUpdateRequest();
        request.setPersonName("新姓名");
        request.setPersonCompany("新单位");
        request.setPersonPhone("13800138000");
        request.setReason("姓名信息录入有误");
        request.setVersion(0);
        SysUser user = new SysUser();
        user.setId(9L);
        user.setUsername("manager");

        service.updateAttendee(81L, request, user);

        assertThat(registration.getVisitorCompany()).isEqualTo("新单位");
        assertThat(registration.getContactName()).isEqualTo("新姓名");
        assertThat(registration.getContactPhoneEncrypted()).isEqualTo("encrypted-value");
        assertThat(registration.getVersion()).isEqualTo(3);
        verify(registrationMapper).updateById(registration);
    }

    @Test
    void attendanceExportHasTwoFixedSheetsAndNeutralizesFormulaText() throws Exception {
        SiteVisitInvitation invitation = invitation();
        ProjectInfo project = project();
        project.setProjectName("=FORMULA_PROJECT");
        when(invitationMapper.selectById(11L)).thenReturn(invitation);
        when(projectMapper.selectById(7L)).thenReturn(project);
        when(qrMapper.selectCurrent(11L)).thenReturn(qr(MeetingCheckinService.QR_ENABLED));
        when(attendanceMapper.selectSummary(11L)).thenReturn(Map.of(
                "reservedPersonCount", 1L, "reservedCheckedInCount", 1L,
                "walkInCheckedInCount", 0L, "totalCheckedInCount", 1L,
                "inRangeCount", 1L, "outOfRangeCount", 0L, "unavailableLocationCount", 0L));
        Map<String, Object> row = attendeeRow();
        row.put("personName", "=2+2");
        when(attendanceMapper.selectExportRows(11L)).thenReturn(List.of(row));
        when(cryptoService.decrypt("phone-encrypted")).thenReturn("13800138000");
        when(cryptoService.encrypt(anyString())).thenReturn("encrypted-audit");
        when(auditMapper.insert(any())).thenReturn(1);
        when(operationLogMapper.insert(any())).thenReturn(1);
        SysUser user = new SysUser();
        user.setId(9L);
        user.setUsername("manager");

        MeetingCheckinService.ExportFile file = service.export(7L, 11L, user);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file.content()))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(2);
            assertThat(workbook.getSheetName(0)).isEqualTo("签到汇总");
            assertThat(workbook.getSheetName(1)).isEqualTo("参会人员明细");
            assertThat(workbook.getSheetAt(1).getRow(1).getCell(7).getStringCellValue()).isEqualTo("'=2+2");
            assertThat(workbook.getSheetAt(0).getRow(0).getCell(1).getStringCellValue()).isEqualTo("'=FORMULA_PROJECT");
        }
    }

    private void prepareConfirmContext() {
        VisitorSessionService.VisitorSessionContext context = new VisitorSessionService.VisitorSessionContext(
                null, 7L, "wx-app", "base-hash", "openid-encrypted",
                VisitorSessionService.SOURCE_MEETING_CHECKIN_QR, 31L);
        when(sessionService.require("visitor-session")).thenReturn(context);
        when(sessionService.requireMeetingCheckin("visitor-session", 31L, 7L)).thenReturn(context);
        when(sessionService.decryptOpenid(context)).thenReturn("openid-a");
        when(cryptoService.fingerprint(eq("site-access:meeting-checkin:v1"), anyString())).thenReturn("checkin-hash");
        when(cryptoService.fingerprint(eq("site-access:meeting-registration:v1"), anyString())).thenReturn("registration-hash");
        when(qrMapper.selectForUpdate(31L)).thenReturn(qr(MeetingCheckinService.QR_ENABLED));
        when(invitationMapper.selectForUpdate(11L)).thenReturn(invitation());
        when(projectMapper.selectById(7L)).thenReturn(project());
        SiteMeetingVisitRegistration registration = registration();
        when(registrationMapper.selectActiveForUpdate(11L, "wx-app", "registration-hash")).thenReturn(registration);
    }

    private PublicMeetingCheckinConfirmRequest confirmRequest(Long personId, boolean locationAvailable) {
        PublicMeetingCheckinConfirmRequest.PersonSelection selection = new PublicMeetingCheckinConfirmRequest.PersonSelection();
        selection.setPersonId(personId);
        MeetingCheckinLocationRequest location = new MeetingCheckinLocationRequest();
        location.setLocationAvailable(locationAvailable);
        if (locationAvailable) {
            location.setLatitude(new BigDecimal("30.123456"));
            location.setLongitude(new BigDecimal("120.123456"));
            location.setAccuracyMeters(18);
        }
        PublicMeetingCheckinConfirmRequest request = new PublicMeetingCheckinConfirmRequest();
        request.setAttendees(List.of(selection));
        request.setLocation(location);
        return request;
    }

    private SiteVisitInvitation invitation() {
        SiteVisitInvitation value = new SiteVisitInvitation();
        value.setId(11L);
        value.setProjectId(7L);
        value.setInviteNo("VIS-MEETING-1");
        value.setInviteType(SiteAccessService.INVITE_TYPE_MEETING);
        value.setStatus(SiteAccessService.STATUS_OPEN);
        value.setPurpose("安全交底会");
        value.setVisitLocation("会议室");
        value.setHostName("接待人");
        value.setVisitStartTime(LocalDateTime.now().minusMinutes(30));
        value.setVisitEndTime(LocalDateTime.now().plusHours(2));
        return value;
    }

    private SiteMeetingCheckinQr qr(String status) {
        SiteMeetingCheckinQr value = new SiteMeetingCheckinQr();
        value.setId(31L);
        value.setInvitationId(11L);
        value.setProjectId(7L);
        value.setQrStatus(status);
        value.setQrVersion(1);
        value.setCheckinStartTime(LocalDateTime.now().minusHours(1));
        value.setCheckinEndTime(LocalDateTime.now().plusHours(1));
        value.setLocationRadiusMeters(300);
        value.setVersion(0);
        return value;
    }

    private ProjectInfo project() {
        ProjectInfo value = new ProjectInfo();
        value.setId(7L);
        value.setProjectName("测试项目");
        value.setShortName("测试");
        value.setProjectStatus("normal");
        value.setDeleted(0);
        return value;
    }

    private SiteMeetingVisitRegistration registration() {
        SiteMeetingVisitRegistration value = new SiteMeetingVisitRegistration();
        value.setId(21L);
        value.setInvitationId(11L);
        value.setProjectId(7L);
        value.setRegistrationNo("MVR-1");
        value.setRegistrationSource(MeetingCheckinService.SOURCE_INVITATION);
        value.setStatus(MeetingVisitService.STATUS_REGISTERED);
        return value;
    }

    private SiteMeetingVisitPerson person(Long id, Long registrationId, String name) {
        SiteMeetingVisitPerson value = new SiteMeetingVisitPerson();
        value.setId(id);
        value.setRegistrationId(registrationId);
        value.setProjectId(7L);
        value.setPersonType("CONTACT");
        value.setPersonName(name);
        value.setSortOrder(1);
        return value;
    }

    private SiteMeetingAttendance checkedAttendance(Long personId) {
        SiteMeetingAttendance value = new SiteMeetingAttendance();
        value.setId(301L);
        value.setInvitationId(11L);
        value.setRegistrationId(21L);
        value.setPersonId(personId);
        value.setProjectId(7L);
        value.setStatus(MeetingCheckinService.ATTENDANCE_CHECKED_IN);
        value.setCheckinMethod(MeetingCheckinService.METHOD_VENUE_QR);
        value.setCheckinTime(LocalDateTime.now().minusMinutes(2));
        value.setLocationResult(MeetingCheckinService.LOCATION_IN_RANGE);
        value.setDistanceMeters(10);
        value.setAccuracyMeters(20);
        value.setVersion(0);
        return value;
    }

    private Map<String, Object> attendeeRow() {
        Map<String, Object> row = new HashMap<>();
        row.put("personId", 81L);
        row.put("registrationId", 21L);
        row.put("registrationNo", "MVR-1");
        row.put("registrationSource", MeetingCheckinService.SOURCE_INVITATION);
        row.put("registrationStatus", MeetingVisitService.STATUS_REGISTERED);
        row.put("registeredTime", LocalDateTime.now().minusHours(1));
        row.put("travelMode", "OTHER");
        row.put("personType", "CONTACT");
        row.put("personCompany", "测试单位");
        row.put("personName", "张三");
        row.put("phoneEncrypted", "phone-encrypted");
        row.put("attendanceStatus", MeetingCheckinService.ATTENDANCE_CHECKED_IN);
        row.put("checkinMethod", MeetingCheckinService.METHOD_VENUE_QR);
        row.put("checkinTime", LocalDateTime.now());
        row.put("locationResult", MeetingCheckinService.LOCATION_IN_RANGE);
        row.put("distanceMeters", 20);
        row.put("accuracyMeters", 15);
        row.put("version", 0);
        return row;
    }
}
