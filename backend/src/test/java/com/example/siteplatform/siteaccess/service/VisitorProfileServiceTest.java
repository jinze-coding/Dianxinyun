package com.example.siteplatform.siteaccess.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.project.entity.ProjectInfo;
import com.example.siteplatform.project.mapper.ProjectInfoMapper;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.siteaccess.entity.SiteVisitorProfile;
import com.example.siteplatform.siteaccess.entity.SiteVisitorProfileAuditLog;
import com.example.siteplatform.siteaccess.entity.SiteVisitorProfilePerson;
import com.example.siteplatform.siteaccess.mapper.SiteVisitorProfileAuditLogMapper;
import com.example.siteplatform.siteaccess.mapper.SiteVisitorProfileMapper;
import com.example.siteplatform.siteaccess.mapper.SiteVisitorProfilePersonMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VisitorProfileServiceTest {
    @Mock private SiteVisitorProfileMapper profileMapper;
    @Mock private SiteVisitorProfilePersonMapper personMapper;
    @Mock private SiteVisitorProfileAuditLogMapper auditMapper;
    @Mock private ProjectInfoMapper projectInfoMapper;
    @Mock private ProjectPermissionService permissionService;
    @Mock private VisitorSessionService sessionService;

    private VisitorDataCryptoService crypto;
    private VisitorProfileService service;

    @BeforeEach
    void setUp() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("test");
        crypto = new VisitorDataCryptoService("", environment);
        service = new VisitorProfileService(profileMapper, personMapper, auditMapper,
                projectInfoMapper, permissionService, crypto, sessionService,
                new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void createEncryptsIdentityPhonePeopleAndAuditSnapshot() {
        var context = context();
        when(sessionService.decryptOpenid(context)).thenReturn("external-openid");
        when(projectInfoMapper.selectByIdForUpdate(10L)).thenReturn(project());
        when(profileMapper.selectActiveOwnerProfilesForUpdate(10L, "wx-app", "owner-fingerprint"))
                .thenReturn(List.of());
        doAnswer(invocation -> {
            SiteVisitorProfile profile = invocation.getArgument(0);
            profile.setId(91L);
            return 1;
        }).when(profileMapper).insert(any(SiteVisitorProfile.class));
        when(personMapper.insert(any(SiteVisitorProfilePerson.class))).thenReturn(1);
        when(personMapper.selectList(any(Wrapper.class))).thenAnswer(invocation -> List.of());
        when(auditMapper.insert(any(SiteVisitorProfileAuditLog.class))).thenReturn(1);

        Long id = service.applyOnSubmission(context, "CREATE", null, "项目会议资料", true, null,
                submission());

        assertThat(id).isEqualTo(91L);
        ArgumentCaptor<SiteVisitorProfile> profileCaptor = ArgumentCaptor.forClass(SiteVisitorProfile.class);
        verify(profileMapper).insert(profileCaptor.capture());
        SiteVisitorProfile profile = profileCaptor.getValue();
        assertThat(profile.getOwnerOpenidEncrypted()).startsWith("v1:").doesNotContain("external-openid");
        assertThat(profile.getOwnerOpenidHash()).isEqualTo(context.identityHash());
        assertThat(profile.getContactPhoneEncrypted()).startsWith("v1:").doesNotContain("13800000000");
        ArgumentCaptor<SiteVisitorProfilePerson> personCaptor = ArgumentCaptor.forClass(SiteVisitorProfilePerson.class);
        verify(personMapper).insert(personCaptor.capture());
        assertThat(personCaptor.getValue().getIdCardEncrypted()).startsWith("v1:").doesNotContain("990000200001010011");
        assertThat(personCaptor.getValue().getIdCardHash())
                .isEqualTo(crypto.idCardFingerprint("990000200001010011"))
                .isNotEqualTo(crypto.digest("990000200001010011"));
        ArgumentCaptor<SiteVisitorProfileAuditLog> auditCaptor = ArgumentCaptor.forClass(SiteVisitorProfileAuditLog.class);
        verify(auditMapper).insert(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getAfterSnapshotEncrypted()).startsWith("v1:")
                .doesNotContain("13800000000");
    }

    @Test
    void selectedProfileCannotCrossProjectOrWechatIdentity() {
        SiteVisitorProfile foreign = new SiteVisitorProfile();
        foreign.setId(9L);
        foreign.setProfileCode("VP_foreign");
        foreign.setProjectId(11L);
        foreign.setWechatAppId("wx-app");
        foreign.setOwnerOpenidHash("another-owner");
        foreign.setStatus(VisitorProfileService.STATUS_ACTIVE);
        when(profileMapper.selectForUpdateByCode("VP_foreign")).thenReturn(foreign);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.applyOnSubmission(context(), "NONE", "VP_foreign", null,
                        false, null, submission()));

        assertThat(error.getCode()).isEqualTo(404);
    }

    @Test
    void createLocksProjectBeforeCountingAndRejectsTwentyFirstActiveProfile() {
        when(projectInfoMapper.selectByIdForUpdate(10L)).thenReturn(project());
        when(profileMapper.selectActiveOwnerProfilesForUpdate(10L, "wx-app", "owner-fingerprint"))
                .thenReturn(java.util.stream.IntStream.range(0, 20)
                        .mapToObj(index -> new SiteVisitorProfile()).toList());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.applyOnSubmission(context(), "CREATE", null, "资料", true,
                        null, submission()));

        assertThat(error.getMessage()).contains("最多保存20份");
        var order = inOrder(projectInfoMapper, profileMapper);
        order.verify(projectInfoMapper).selectByIdForUpdate(10L);
        order.verify(profileMapper).selectActiveOwnerProfilesForUpdate(
                10L, "wx-app", "owner-fingerprint");
        verify(profileMapper, never()).insert(any());
    }

    @Test
    void updateRequiresExpectedVersionBeforeChangingProfile() {
        SiteVisitorProfile profile = ownedProfile();
        when(profileMapper.selectForUpdateByCode("VP_owned")).thenReturn(profile);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.applyOnSubmission(context(), "UPDATE", "VP_owned", "资料",
                        true, null, submission()));

        assertThat(error.getCode()).isEqualTo(400);
        assertThat(error.getMessage()).contains("必须提供当前版本号");
        verify(profileMapper, never()).updateById(any());
        verify(personMapper, never()).insert(any());
    }

    @Test
    void updateRejectsStaleExpectedVersionBeforeChangingProfile() {
        SiteVisitorProfile profile = ownedProfile();
        when(profileMapper.selectForUpdateByCode("VP_owned")).thenReturn(profile);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.applyOnSubmission(context(), "UPDATE", "VP_owned", "资料",
                        true, 2, submission()));

        assertThat(error.getCode()).isEqualTo(409);
        assertThat(error.getMessage()).contains("已更新");
        verify(profileMapper, never()).updateById(any());
        verify(personMapper, never()).insert(any());
    }

    private SiteVisitorProfile ownedProfile() {
        SiteVisitorProfile profile = new SiteVisitorProfile();
        profile.setId(9L);
        profile.setProfileCode("VP_owned");
        profile.setProjectId(10L);
        profile.setWechatAppId("wx-app");
        profile.setOwnerOpenidHash("owner-fingerprint");
        profile.setStatus(VisitorProfileService.STATUS_ACTIVE);
        profile.setVersion(3);
        return profile;
    }

    private ProjectInfo project() {
        ProjectInfo project = new ProjectInfo();
        project.setId(10L);
        project.setDeleted(0);
        return project;
    }

    private VisitorSessionService.VisitorSessionContext context() {
        return new VisitorSessionService.VisitorSessionContext(
                1L, 10L, "wx-app", "owner-fingerprint", crypto.encrypt("external-openid"));
    }

    private VisitorProfileService.SubmissionData submission() {
        return new VisitorProfileService.SubmissionData(
                "外访单位", "外访联系人", "13800000000", "OTHER", null,
                List.of(new VisitorProfileService.PersonData(
                        "CONTACT", "外访联系人", "990000200001010011")));
    }
}
