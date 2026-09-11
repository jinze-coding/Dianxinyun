package com.example.siteplatform.siteaccess.service;

import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.project.entity.ProjectInfo;
import com.example.siteplatform.project.mapper.ProjectInfoMapper;
import com.example.siteplatform.siteaccess.entity.SiteVisitorPersonalProfile;
import com.example.siteplatform.siteaccess.mapper.SiteVisitorPersonalProfileMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VisitorPersonalProfileServiceTest {
    @Mock SiteVisitorPersonalProfileMapper mapper;
    @Mock ProjectInfoMapper projects;
    @Mock VisitorSessionService sessions;
    VisitorDataCryptoService crypto;
    VisitorPersonalProfileService service;
    ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @BeforeEach void setup() {
        var env = new MockEnvironment(); env.setActiveProfiles("test");
        crypto = new VisitorDataCryptoService("", env);
        service = new VisitorPersonalProfileService(mapper, projects, sessions, crypto, json);
        lenient().when(sessions.decryptOpenid(any())).thenAnswer(call -> crypto.decrypt(((VisitorSessionService.VisitorSessionContext)call.getArgument(0)).openidEncrypted()));
    }
    private VisitorSessionService.VisitorSessionContext context(Long project, String app, String openid, String source) {
        return new VisitorSessionService.VisitorSessionContext(null, project, app, "legacy-owner", crypto.encrypt(openid), source, 11L);
    }
    private VisitorSubmissionNormalizer.Submission submission(String name) {
        return VisitorSubmissionNormalizer.normalize("测试单位", name, "13800000000", List.of(), "OTHER", null, null);
    }
    private void writable() {
        when(projects.selectByIdForUpdate(7L)).thenReturn(new ProjectInfo());
        lenient().when(mapper.insert(any(SiteVisitorPersonalProfile.class))).thenAnswer(call -> { ((SiteVisitorPersonalProfile)call.getArgument(0)).setId(1L); return 1; });
        lenient().when(mapper.insertAudit(anyLong(), anyLong(), anyString(), nullable(String.class), anyString())).thenReturn(1);
    }
    @Test void allFourSourcesSaveWithoutSeparateConsentAndKeepEncryptedProjectArchive() {
        writable();
        for (String source : List.of("INVITATION", "MEETING_INVITATION", "GUARD_QR", "MEETING_CHECKIN_QR")) {
            service.saveOnSubmission(context(7L,"wx-app","a",source), null, submission("张三"));
        }
        var rows = ArgumentCaptor.forClass(SiteVisitorPersonalProfile.class);
        verify(mapper, times(4)).insert(rows.capture());
        assertThat(rows.getAllValues()).extracting(SiteVisitorPersonalProfile::getOwnerIdentityHash).containsOnly(rows.getValue().getOwnerIdentityHash());
        assertThat(rows.getValue().getOwnerIdentityHash()).hasSize(64);
        assertThat(rows.getValue().getProjectId()).isEqualTo(7L);
        assertThat(rows.getValue().getRememberEnabled()).isTrue();
        assertThat(rows.getValue().getContactPhoneEncrypted()).startsWith("v1:").doesNotContain("13800000000");
        assertThat(crypto.decrypt(rows.getValue().getContactPhoneEncrypted())).isEqualTo("13800000000");
        var audit = ArgumentCaptor.forClass(String.class);
        verify(mapper,times(4)).insertAudit(eq(1L),eq(7L),eq("AUTO_SAVE"),isNull(),audit.capture());
        assertThat(audit.getValue()).startsWith("v1:").doesNotContain("张三");
    }
    @Test void projectIndependentLookupStillSeparatesAppAndWechatIdentityAndUsesPurposeHashes() {
        service.read(context(7L,"wx-app","a","INVITATION"));
        service.read(context(8L,"wx-app","a","GUARD_QR"));
        service.read(context(7L,"wx-other","a","MEETING_CHECKIN_QR"));
        service.read(context(7L,"wx-app","b","GUARD_QR"));
        var apps=ArgumentCaptor.forClass(String.class); var own=ArgumentCaptor.forClass(String.class);
        var single=ArgumentCaptor.forClass(String.class); var meeting=ArgumentCaptor.forClass(String.class); var guard=ArgumentCaptor.forClass(String.class);
        verify(mapper,times(4)).selectLatestPersonalInfo(apps.capture(),own.capture(),single.capture(),meeting.capture(),guard.capture());
        assertThat(apps.getAllValues()).containsExactly("wx-app","wx-app","wx-other","wx-app");
        assertThat(own.getAllValues().get(0)).isEqualTo(own.getAllValues().get(1));
        assertThat(own.getAllValues().get(2)).isNotEqualTo(own.getAllValues().get(0));
        assertThat(own.getAllValues().get(3)).isNotEqualTo(own.getAllValues().get(0));
        assertThat(List.of(own.getValue(),single.getValue(),meeting.getValue(),guard.getValue())).doesNotHaveDuplicates();
    }
    @Test void legacyFalseCannotDisableSavingOrEraseLatestFields() {
        writable();
        var row = new SiteVisitorPersonalProfile(); row.setId(1L); row.setProjectId(7L); row.setVersion(4); row.setRememberEnabled(false);
        when(mapper.selectOwnerForUpdate(any(), any(), any())).thenReturn(row);
        when(mapper.updateById(row)).thenReturn(1);
        service.saveOnSubmission(context(7L,"wx-app","a","GUARD_QR"), false, submission("李四"));
        assertThat(row.getContactName()).isEqualTo("李四");
        assertThat(row.getVersion()).isEqualTo(5);
        assertThat(row.getRememberEnabled()).isTrue();
        assertThat(crypto.decrypt(row.getContactPhoneEncrypted())).isEqualTo("13800000000");
    }
    @Test void historyWinsAndResponseDoesNotExposeSourceOrCompanions() throws Exception {
        var row=new SiteVisitorPersonalProfile(); row.setId(81L); row.setProjectId(99L); row.setWechatAppId("wx-app");
        row.setContactName("张三"); row.setContactPhoneEncrypted(crypto.encrypt("13800000000")); row.setRememberEnabled(false);
        when(mapper.selectLatestPersonalInfo(any(),any(),any(),any(),any())).thenReturn(row);
        var result=service.read(context(7L,"wx-app","a","GUARD_QR"));
        assertThat(result.isAvailable()).isTrue(); assertThat(result.getContactName()).isEqualTo("张三");
        assertThat(json.writeValueAsString(result)).doesNotContain("projectId","wechatAppId","ownerIdentityHash","companions","registration");
        verify(mapper,never()).selectLatestNamedPersonalInfo(any(),any());
    }
    @Test void namedProfilesOnlySupplementMissingHistoryAndUnknownWechatIsEmpty() {
        var row=new SiteVisitorPersonalProfile(); row.setContactName("张三"); row.setContactPhoneEncrypted(crypto.encrypt("13800000000"));
        when(mapper.selectLatestNamedPersonalInfo("wx-app","legacy-owner")).thenReturn(row);
        var data=service.read(context(7L,"wx-app","a","INVITATION"));
        assertThat(data.isAvailable()).isTrue(); assertThat(data.getContactName()).isEqualTo("张三");
        assertThat(service.read(context(7L,"wx-other","b","INVITATION")).isAvailable()).isFalse();
    }
    @Test void missingIdentityNeverReadsOrSavesData() {
        assertThatThrownBy(() -> service.read(null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.saveOnSubmission(null,null,submission("张三"))).isInstanceOf(BusinessException.class);
        verifyNoInteractions(mapper);
    }
    @Test void zeroRowWriteThrowsConflictBeforeAudit() {
        writable(); when(mapper.insert(any(SiteVisitorPersonalProfile.class))).thenReturn(0);
        assertThatThrownBy(() -> service.saveOnSubmission(context(7L,"wx-app","a","INVITATION"), true, submission("张三")))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo(409));
        verify(mapper,never()).insertAudit(any(),any(),any(),any(),any());
    }
    @Test void auditFailureFailsTheBusinessTransaction() {
        writable(); when(mapper.insertAudit(anyLong(),anyLong(),anyString(),nullable(String.class),anyString())).thenReturn(0);
        assertThatThrownBy(() -> service.saveOnSubmission(context(7L,"wx-app","a","INVITATION"),null,submission("张三")))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo(409));
    }
}
