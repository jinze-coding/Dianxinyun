package com.example.siteplatform.siteaccess.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.example.siteplatform.project.entity.ProjectInfo;
import com.example.siteplatform.project.mapper.ProjectInfoMapper;
import com.example.siteplatform.siteaccess.entity.*;
import com.example.siteplatform.siteaccess.mapper.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Explicit local opt-in. Synthetic rows only; every test rolls back, including setup and audit rows. */
@EnabledIfEnvironmentVariable(named="VISITOR_IDENTITY_LOCAL_DATABASE", matches="dianxinyun")
class VisitorIdentityAutofillDatabaseTest {
    JdbcTemplate jdbc; SqlSessionTemplate sql; DataSourceTransactionManager transactions; TransactionStatus tx;
    VisitorPersonalProfileService personal; VisitorDataCryptoService crypto; VisitorSessionService sessions;
    long projectA, projectB; String app, openid; LocalDateTime now=LocalDateTime.now();
    @BeforeEach void setup() throws Exception {
        var ds=new DriverManagerDataSource("jdbc:mysql://127.0.0.1:3306/dianxinyun?serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true","root","");
        jdbc=new JdbcTemplate(ds); transactions=new DataSourceTransactionManager(ds);
        tx=transactions.getTransaction(new DefaultTransactionDefinition());
        var config=new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true); config.setLogImpl(org.apache.ibatis.logging.nologging.NoLoggingImpl.class);
        var factory=new MybatisSqlSessionFactoryBean(); factory.setDataSource(ds); factory.setConfiguration(config);
        sql=new SqlSessionTemplate(Objects.requireNonNull(factory.getObject()));
        var env=new MockEnvironment(); env.setActiveProfiles("test"); crypto=new VisitorDataCryptoService("",env);
        sessions=mock(VisitorSessionService.class);
        when(sessions.decryptOpenid(any())).thenAnswer(call->crypto.decrypt(((VisitorSessionService.VisitorSessionContext)call.getArgument(0)).openidEncrypted()));
        personal=new VisitorPersonalProfileService(mapper(SiteVisitorPersonalProfileMapper.class),mapper(ProjectInfoMapper.class),sessions,crypto,new ObjectMapper().findAndRegisterModules());
        projectA=project(); projectB=project(); app="autofill-test-"+UUID.randomUUID(); openid="synthetic-"+UUID.randomUUID();
    }
    @AfterEach void rollback() {
        if(tx!=null && !tx.isCompleted()) transactions.rollback(tx);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM project_info WHERE id IN (?,?)",Integer.class,projectA,projectB)).isZero();
    }
    private <T>T mapper(Class<T> type) {if(!sql.getConfiguration().hasMapper(type))sql.getConfiguration().addMapper(type);return sql.getMapper(type);}
    private long project(){var p=new ProjectInfo();p.setProjectName("AUTO_FILL_ROLLBACK_"+UUID.randomUUID());p.setProjectStatus("normal");p.setDeleted(0);p.setProfileVersion(0);mapper(ProjectInfoMapper.class).insert(p);return p.getId();}
    private VisitorSessionService.VisitorSessionContext context(long project,String application,String identity){return new VisitorSessionService.VisitorSessionContext(null,project,application,crypto.fingerprint(application+":"+identity),crypto.encrypt(identity),"GUARD_QR",1L);}
    private VisitorSessionService.VisitorSessionContext context(long project){return context(project,app,openid);}
    private String hash(String purpose){return VisitorIdentitySupport.hash(purpose,context(projectA),crypto,sessions);}
    private VisitorSubmissionNormalizer.Submission submission(String name){return VisitorSubmissionNormalizer.normalize("合成测试单位",name,"13800000000",List.of(),"DRIVING","沪ATEST1",null);}
    private SiteVisitInvitation invitation(String type,String status,String name,LocalDateTime submitted){
        var v=new SiteVisitInvitation(); v.setProjectId(projectA);v.setInviteNo("AF-"+UUID.randomUUID());v.setTokenHash(crypto.digest(UUID.randomUUID().toString()));v.setTokenEncrypted(crypto.encrypt("synthetic-scene"));v.setInviteType(type);v.setStatus(status);
        v.setVisitStartTime(now.minusDays(10));v.setVisitEndTime(now.minusDays(9));v.setPurpose("回滚测试");v.setVisitLocation("测试地点");v.setHostUserId(1L);v.setHostName("合成人员");v.setCreatedById(1L);v.setCreatedByName("测试");v.setVersion(0);v.setDeleted(0);
        if(name!=null){v.setWechatAppId(app);v.setVisitorIdentityHash(hash("single-registration"));v.setVisitorCompany("单次单位");v.setContactName(name);v.setContactPhoneEncrypted(crypto.encrypt("13800000000"));v.setTravelMode("OTHER");v.setSubmittedTime(submitted);}
        mapper(SiteVisitInvitationMapper.class).insert(v);return v;
    }
    private SiteMeetingVisitRegistration meeting(SiteVisitInvitation invite,String source,String name,LocalDateTime time){
        var v=new SiteMeetingVisitRegistration();v.setRegistrationNo("AF-"+UUID.randomUUID());v.setInvitationId(invite.getId());v.setProjectId(projectA);v.setWechatAppId(app);v.setVisitorIdentityHash(hash("meeting-registration"));v.setRegistrationSource(source);v.setStatus("REGISTERED");v.setVisitorCompany("会议单位");v.setContactName(name);v.setContactPhoneEncrypted(crypto.encrypt("13800000000"));v.setTravelMode("OTHER");v.setVisitorCount(1);v.setPrivacyAgreedTime(time);v.setRegisteredTime(time);v.setVersion(0);v.setDeleted(0);mapper(SiteMeetingVisitRegistrationMapper.class).insert(v);return v;
    }
    private SiteGuardVisitRegistration guard(String name,LocalDateTime time){
        var v=new SiteGuardVisitRegistration();v.setRegistrationNo("AF-"+UUID.randomUUID());v.setProjectId(projectA);v.setGuardQrId(0L);v.setWechatAppId(app);v.setVisitorIdentityHash(hash("guard-registration"));v.setStatus("REGISTERED");v.setVisitorCompany("门卫单位");v.setContactName(name);v.setContactPhoneEncrypted(crypto.encrypt("13800000000"));v.setTravelMode("OTHER");v.setVisitorCount(1);v.setPrivacyAgreedTime(time);v.setRegisteredTime(time);v.setValidUntil(time.plusHours(24));v.setVersion(0);v.setDeleted(0);mapper(SiteGuardVisitRegistrationMapper.class).insert(v);return v;
    }
    private void name(String expected){assertThat(personal.read(context(projectB)).getContactName()).isEqualTo(expected);}

    @Test void allHistoricalSourcesAreOrderedAcrossProjectsIncludingExpiredAndFormerOptOut(){
        assertThat(personal.read(context(projectB)).isAvailable()).isFalse();
        invitation("SINGLE","SUBMITTED","单次",now.minusDays(8));name("单次");
        var m=invitation("MEETING","OPEN",null,null);meeting(m,"INVITATION","会议",now.minusDays(7));name("会议");
        var walk=invitation("MEETING","OPEN",null,null);meeting(walk,"WALK_IN","补录",now.minusDays(6));name("补录");
        guard("门卫",now.minusDays(5));name("门卫");
        personal.saveOnSubmission(context(projectA),false,submission("最新本人"));name("最新本人");
        var data=personal.read(context(projectB));assertThat(data.getVehiclePlate()).isEqualTo("沪ATEST1");
        assertThat(jdbc.queryForObject("SELECT remember_enabled FROM site_visitor_personal_profile WHERE project_id=?",Boolean.class,projectA)).isTrue();
        assertThat(personal.read(context(projectB,app,"another-wechat")).isAvailable()).isFalse();
        assertThat(personal.read(context(projectB,"another-app",openid)).isAvailable()).isFalse();
    }
    @Test void voidDeletedUnboundAndManuallyRecordedHistoryNeverWins(){
        invitation("SINGLE","SUBMITTED","有效历史",now.minusDays(8));
        invitation("SINGLE","VOIDED","作废单次",now.minusDays(1));
        var deleted=invitation("SINGLE","SUBMITTED","删除单次",now.minusDays(1));jdbc.update("UPDATE site_visit_invitation SET deleted=1 WHERE id=?",deleted.getId());
        var unbound=invitation("SINGLE","SUBMITTED","未归属单次",now.minusDays(1));jdbc.update("UPDATE site_visit_invitation SET visitor_identity_hash=NULL WHERE id=?",unbound.getId());
        var voidMeeting=invitation("MEETING","VOIDED",null,null);meeting(voidMeeting,"INVITATION","作废会议",now.minusDays(1));
        var normalMeeting=invitation("MEETING","OPEN",null,null);var staff=meeting(normalMeeting,"WALK_IN","工作人员代录",now.minusDays(1));jdbc.update("UPDATE site_meeting_visit_registration SET wechat_app_id=NULL,visitor_identity_hash=NULL WHERE id=?",staff.getId());
        var g=guard("作废门卫",now.minusDays(1));jdbc.update("UPDATE site_guard_visit_registration SET status='VOIDED' WHERE id=?",g.getId());
        name("有效历史");
    }
    @Test void namedFallbackNeverOverridesSuccessfulHistory(){
        jdbc.update("INSERT INTO site_visitor_profile(profile_code,project_id,wechat_app_id,owner_openid_encrypted,owner_openid_hash,profile_name,visitor_company,contact_name,contact_phone_encrypted,travel_mode,privacy_agreed_time) VALUES (?,?,?,?,?,?,?,?,?,'OTHER',?)",
                "AF-"+UUID.randomUUID(),projectA,app,crypto.encrypt(openid),context(projectA).identityHash(),"旧命名","旧单位","命名本人",crypto.encrypt("13800000000"),now);
        name("命名本人");invitation("SINGLE","SUBMITTED","成功历史",now.minusDays(8));name("成功历史");
    }
    @Test void saveAndAuditRollBackTogetherAndSameSecondNewerSubmissionWins(){
        var point=tx.createSavepoint();personal.saveOnSubmission(context(projectA),null,submission("回滚信息"));name("回滚信息");
        tx.rollbackToSavepoint(point);sql.clearCache();assertThat(personal.read(context(projectB)).isAvailable()).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM site_visitor_personal_profile_audit WHERE project_id=?",Integer.class,projectA)).isZero();
        personal.saveOnSubmission(context(projectB),false,submission("先提交"));
        personal.saveOnSubmission(context(projectA),null,submission("后提交"));name("后提交");
    }
}
