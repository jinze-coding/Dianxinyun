package com.example.siteplatform.siteaccess.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.project.entity.ProjectInfo;
import com.example.siteplatform.project.mapper.ProjectInfoMapper;
import com.example.siteplatform.siteaccess.entity.SiteVisitorPersonalProfile;
import com.example.siteplatform.siteaccess.mapper.SiteVisitorPersonalProfileMapper;
import com.example.siteplatform.siteaccess.vo.SiteVisitorProfileVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
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
    @Mock VisitorProfileService named;
    @Mock VisitorSessionService sessions;
    VisitorDataCryptoService crypto;
    VisitorPersonalProfileService service;

    @BeforeEach void setup() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "personal"), SiteVisitorPersonalProfile.class);
        var env = new MockEnvironment(); env.setActiveProfiles("test");
        crypto = new VisitorDataCryptoService("", env);
        service = new VisitorPersonalProfileService(mapper, projects, named, sessions, crypto, new ObjectMapper().findAndRegisterModules());
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
    @Test void noConsentDoesNotCreatePersonalData() {
        when(projects.selectByIdForUpdate(7L)).thenReturn(new ProjectInfo());
        service.saveOnSubmission(context(7L,"wx-app","a","INVITATION"), null, submission("张三"));
        verify(mapper, never()).insert(any(SiteVisitorPersonalProfile.class));
        verify(mapper, never()).insertAudit(any(), any(), any(), any(), any());
    }
    @Test void threeSourcesUseSameOwnerButProjectsAndAppsStaySeparate() {
        writable();
        for (String source : List.of("INVITATION", "MEETING_INVITATION", "GUARD_QR")) {
            service.saveOnSubmission(context(7L,"wx-app","a",source), true, submission("张三"));
        }
        var rows = ArgumentCaptor.forClass(SiteVisitorPersonalProfile.class);
        verify(mapper, times(3)).insert(rows.capture());
        assertThat(rows.getAllValues()).extracting(SiteVisitorPersonalProfile::getOwnerIdentityHash).containsOnly(rows.getValue().getOwnerIdentityHash());
        assertThat(rows.getValue().getOwnerIdentityHash()).hasSize(64).doesNotContain("openid");
        assertThat(rows.getValue().getContactPhoneEncrypted()).startsWith("v1:").doesNotContain("13800000000");
        assertThat(crypto.decrypt(rows.getValue().getContactPhoneEncrypted())).isEqualTo("13800000000");
        var owner = rows.getValue().getOwnerIdentityHash();
        service.read(context(8L,"wx-app","a","GUARD_QR"));
        service.read(context(7L,"wx-other","a","GUARD_QR"));
        service.read(context(7L,"wx-app","b","GUARD_QR"));
        var queries = ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        verify(mapper,times(3)).selectOne(queries.capture());
        for (var query : queries.getAllValues()) query.getSqlSegment();
        var first = (com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<?>)queries.getAllValues().get(0);
        assertThat(first.getParamNameValuePairs().values()).contains(8L, "wx-app", owner);
        for (int i=1;i<3;i++) {
            var query = (com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<?>)queries.getAllValues().get(i);
            assertThat(query.getParamNameValuePairs().values()).doesNotContain(owner);
        }
    }
    @Test void successfulUpdateUsesLatestValuesAndOptOutSuppressesLegacyFallback() {
        writable();
        var row = new SiteVisitorPersonalProfile(); row.setId(1L); row.setProjectId(7L); row.setVersion(4); row.setRememberEnabled(true);
        when(mapper.selectOwnerForUpdate(any(), any(), any())).thenReturn(row);
        when(mapper.updateById(row)).thenReturn(1);
        var ctx = context(7L,"wx-app","a","GUARD_QR");
        service.saveOnSubmission(ctx, null, submission("李四"));
        assertThat(row.getContactName()).isEqualTo("李四");
        assertThat(row.getVersion()).isEqualTo(5);
        service.saveOnSubmission(ctx, false, submission("赵六"));
        assertThat(row.getRememberEnabled()).isFalse();
        assertThat(row.getContactName()).isNull();
        assertThat(row.getContactPhoneEncrypted()).isNull();
        when(mapper.selectOne(any())).thenReturn(row);
        var read = service.read(ctx);
        assertThat(read.isRememberInfo()).isFalse(); assertThat(read.isAvailable()).isFalse();
        verify(named,never()).publicList(any());
    }
    @Test void firstPrefillUsesNamedProfileOnlyWithoutAssumingNewConsent() {
        var profile = new SiteVisitorProfileVO(); profile.setProfileCode("existing-code"); profile.setContactName("张三"); profile.setVisitorCompany("已有单位");
        when(named.publicList(any())).thenReturn(List.of(profile)); when(named.publicDetail(any(),eq("existing-code"))).thenReturn(profile);
        var data = service.read(context(7L,"wx-app","a","INVITATION"));
        assertThat(data.isAvailable()).isTrue(); assertThat(data.isRememberInfo()).isFalse();
        assertThat(data.getContactName()).isEqualTo("张三");
    }
    @Test void zeroRowWriteThrowsConflictBeforeAudit() {
        writable(); when(mapper.insert(any(SiteVisitorPersonalProfile.class))).thenReturn(0);
        assertThatThrownBy(() -> service.saveOnSubmission(context(7L,"wx-app","a","INVITATION"), true, submission("张三")))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo(409));
        verify(mapper,never()).insertAudit(any(),any(),any(),any(),any());
    }
}
