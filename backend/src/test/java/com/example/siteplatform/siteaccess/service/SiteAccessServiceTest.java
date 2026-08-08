package com.example.siteplatform.siteaccess.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.auth.service.WechatPlatformClient;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.log.mapper.OperationLogMapper;
import com.example.siteplatform.project.entity.ProjectInfo;
import com.example.siteplatform.project.mapper.ProjectInfoMapper;
import com.example.siteplatform.project.mapper.SysUserProjectMapper;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.siteaccess.dto.PublicSiteVisitSubmitRequest;
import com.example.siteplatform.siteaccess.dto.SiteVisitPersonRequest;
import com.example.siteplatform.siteaccess.entity.SiteVisitAuditLog;
import com.example.siteplatform.siteaccess.entity.SiteVisitInvitation;
import com.example.siteplatform.siteaccess.entity.SiteVisitPerson;
import com.example.siteplatform.siteaccess.mapper.SiteVisitAuditLogMapper;
import com.example.siteplatform.siteaccess.mapper.SiteVisitInvitationMapper;
import com.example.siteplatform.siteaccess.mapper.SiteVisitPersonMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SiteAccessServiceTest {
    private static final String TOKEN = "abcdefghijklmnopqrstuv";
    private static final String TEST_PHONE = syntheticPhone("138");
    private static final String HOST_PHONE = syntheticPhone("139");
    private static final String VALID_ID_CARD = syntheticIdCard(1);

    @Mock private SiteVisitInvitationMapper invitationMapper;
    @Mock private SiteVisitPersonMapper personMapper;
    @Mock private SiteVisitAuditLogMapper auditLogMapper;
    @Mock private ProjectInfoMapper projectInfoMapper;
    @Mock private SysUserMapper userMapper;
    @Mock private SysUserProjectMapper userProjectMapper;
    @Mock private ProjectPermissionService projectPermissionService;
    @Mock private WechatPlatformClient wechatPlatformClient;
    @Mock private OperationLogMapper operationLogMapper;

    private VisitorDataCryptoService crypto;
    private SiteAccessService service;

    @BeforeEach
    void setUp() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("test");
        crypto = new VisitorDataCryptoService("", environment);
        service = new SiteAccessService(invitationMapper, personMapper, auditLogMapper,
                projectInfoMapper, userMapper, userProjectMapper, projectPermissionService,
                crypto, wechatPlatformClient, operationLogMapper,
                new ObjectMapper().findAndRegisterModules(), "pages/public/visitor-invite", "release");
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
    void publicSubmissionEncryptsCredentialsAndLocksInvitationAsSubmitted() {
        SiteVisitInvitation invitation = pendingInvitation();
        when(invitationMapper.selectForUpdateByTokenHash(anyString())).thenReturn(invitation);
        when(invitationMapper.selectOne(any(Wrapper.class))).thenReturn(invitation);
        when(projectInfoMapper.selectById(10L)).thenReturn(project());
        when(personMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(personMapper.insert(any(SiteVisitPerson.class))).thenReturn(1);
        when(invitationMapper.updateById(invitation)).thenReturn(1);
        when(auditLogMapper.insert(any(SiteVisitAuditLog.class))).thenReturn(1);

        var result = service.submitPublic(validSubmission());

        assertThat(result.getStatus()).isEqualTo(SiteAccessService.STATUS_SUBMITTED);
        assertThat(invitation.getVisitorCount()).isEqualTo(1);
        assertThat(invitation.getContactPhoneEncrypted()).startsWith("v1:").doesNotContain(TEST_PHONE);
        assertThat(crypto.decrypt(invitation.getContactPhoneEncrypted())).isEqualTo(TEST_PHONE);
        ArgumentCaptor<SiteVisitPerson> personCaptor = ArgumentCaptor.forClass(SiteVisitPerson.class);
        verify(personMapper).insert(personCaptor.capture());
        SiteVisitPerson saved = personCaptor.getValue();
        assertThat(saved.getIdCardEncrypted()).startsWith("v1:").doesNotContain(VALID_ID_CARD);
        assertThat(saved.getIdCardHash()).hasSize(64).doesNotContain(VALID_ID_CARD);
        assertThat(crypto.decrypt(saved.getIdCardEncrypted())).isEqualTo(VALID_ID_CARD);
        ArgumentCaptor<SiteVisitAuditLog> auditCaptor = ArgumentCaptor.forClass(SiteVisitAuditLog.class);
        verify(auditLogMapper).insert(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getAfterSnapshotEncrypted())
                .startsWith("v1:")
                .doesNotContain(TEST_PHONE)
                .doesNotContain(VALID_ID_CARD);
    }

    @Test
    void publicSubmissionRejectsDuplicatePeopleBeforeWriting() {
        SiteVisitInvitation invitation = pendingInvitation();
        when(invitationMapper.selectForUpdateByTokenHash(anyString())).thenReturn(invitation);
        PublicSiteVisitSubmitRequest request = validSubmission();
        SiteVisitPersonRequest companion = new SiteVisitPersonRequest();
        companion.setPersonName("同行人员");
        companion.setIdCard(VALID_ID_CARD);
        request.setCompanions(List.of(companion));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.submitPublic(request));

        assertThat(exception.getMessage()).contains("不能重复登记");
        verify(personMapper, never()).insert(any());
        verify(invitationMapper, never()).updateById(any());
        verify(auditLogMapper, never()).insert(any());
    }

    @Test
    void submittedOrExpiredInvitationCannotBeSubmittedAgain() {
        SiteVisitInvitation invitation = pendingInvitation();
        invitation.setStatus(SiteAccessService.STATUS_SUBMITTED);
        when(invitationMapper.selectForUpdateByTokenHash(anyString())).thenReturn(invitation);

        BusinessException submitted = assertThrows(BusinessException.class,
                () -> service.submitPublic(validSubmission()));
        assertThat(submitted.getCode()).isEqualTo(409);
        assertThat(submitted.getMessage()).contains("已经提交");

        invitation.setStatus(SiteAccessService.STATUS_PENDING);
        invitation.setVisitEndTime(LocalDateTime.now().minusMinutes(1));
        BusinessException expired = assertThrows(BusinessException.class,
                () -> service.submitPublic(validSubmission()));
        assertThat(expired.getCode()).isEqualTo(409);
        assertThat(expired.getMessage()).contains("已过期");
        verify(personMapper, never()).insert(any());
    }

    @Test
    void invalidTokenAndDrivingWithoutPlateAreRejectedBeforeWriting() {
        PublicSiteVisitSubmitRequest invalidToken = validSubmission();
        invalidToken.setInviteToken("too-short");
        BusinessException tokenError = assertThrows(BusinessException.class,
                () -> service.submitPublic(invalidToken));
        assertThat(tokenError.getCode()).isEqualTo(404);
        verify(invitationMapper, never()).selectForUpdateByTokenHash(anyString());

        SiteVisitInvitation invitation = pendingInvitation();
        when(invitationMapper.selectForUpdateByTokenHash(anyString())).thenReturn(invitation);
        PublicSiteVisitSubmitRequest noPlate = validSubmission();
        noPlate.setTravelMode(SiteAccessService.TRAVEL_DRIVING);
        BusinessException plateError = assertThrows(BusinessException.class,
                () -> service.submitPublic(noPlate));
        assertThat(plateError.getMessage()).contains("必须填写车牌号");
        verify(personMapper, never()).insert(any());
        verify(invitationMapper, never()).updateById(any());
    }

    @Test
    void moreThanFiftyVisitorsAreRejectedBeforeWriting() {
        SiteVisitInvitation invitation = pendingInvitation();
        when(invitationMapper.selectForUpdateByTokenHash(anyString())).thenReturn(invitation);
        PublicSiteVisitSubmitRequest request = validSubmission();
        request.setCompanions(IntStream.rangeClosed(2, 51).mapToObj(sequence -> {
            SiteVisitPersonRequest companion = new SiteVisitPersonRequest();
            companion.setPersonName("同行人员" + sequence);
            companion.setIdCard(syntheticIdCard(sequence));
            return companion;
        }).toList());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.submitPublic(request));

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
            assertThat(sheet.getRow(1).getCell(9).getStringCellValue()).isEqualTo(VALID_ID_CARD);
            assertThat(sheet.getRow(1).getCell(10).getStringCellValue()).isEqualTo(TEST_PHONE);
        }
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
        request.setVisitorCompany("外访单位");
        request.setContactName("外访联系人");
        request.setContactPhone(TEST_PHONE);
        request.setContactIdCard(VALID_ID_CARD);
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
