package com.example.siteplatform.siteaccess.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.annotation.DbType;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.auth.service.WechatPlatformClient;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.common.RedisRateLimitService;
import com.example.siteplatform.log.mapper.OperationLogMapper;
import com.example.siteplatform.project.mapper.ProjectInfoMapper;
import com.example.siteplatform.project.mapper.SysUserProjectMapper;
import com.example.siteplatform.project.service.*;
import com.example.siteplatform.siteaccess.dto.*;
import com.example.siteplatform.siteaccess.entity.*;
import com.example.siteplatform.siteaccess.mapper.*;
import com.example.siteplatform.siteaccess.vo.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Runs only against a newly initialized, explicitly named verification database. No production connection. */
@EnabledIfEnvironmentVariable(named="VISITOR_REUSE_TEST_DATABASE", matches="dianxinyun_visitor_empty_[0-9]+")
class VisitorReuseDatabaseTest {
    SqlSessionTemplate sql;
    JdbcTemplate jdbc;
    DataSourceTransactionManager transactions;
    VisitorDataCryptoService crypto;
    VisitorSessionService sessions;
    GuardVisitService guards;
    MeetingVisitService meetings;
    SiteAccessService singles;
    VisitorPersonalProfileService personal;
    GuardVisitorMatchingService matching;
    GuardMeetingChoiceService choices;
    MeetingCheckinService attendance;
    ProjectPermissionService permissions;
    Long projectId, qrId;
    final Map<String, VisitorSessionService.VisitorSessionContext> contexts = new ConcurrentHashMap<>();
    final Map<String, String> redisValues = new ConcurrentHashMap<>();
    final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @BeforeEach void setup() throws Exception {
        String database = System.getenv("VISITOR_REUSE_TEST_DATABASE");
        assertThat(database).matches("dianxinyun_visitor_empty_[0-9]+");
        var ds = new DriverManagerDataSource("jdbc:mysql://127.0.0.1:3306/"+database+"?serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true", "root", "");
        jdbc = new JdbcTemplate(ds);
        transactions = new DataSourceTransactionManager(ds);
        var config = new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true);
        config.setLogImpl(org.apache.ibatis.logging.nologging.NoLoggingImpl.class);
        var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(ds); factory.setConfiguration(config);
        var pagination = new MybatisPlusInterceptor(); pagination.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        factory.setPlugins(pagination);
        sql = new SqlSessionTemplate(Objects.requireNonNull(factory.getObject()));
        var env = new MockEnvironment(); env.setActiveProfiles("test"); crypto = new VisitorDataCryptoService("", env);
        var project = new com.example.siteplatform.project.entity.ProjectInfo(); project.setProjectName("访客隔离验证-"+UUID.randomUUID()); project.setProjectStatus("normal"); project.setDeleted(0); project.setProfileVersion(0);
        mapper(ProjectInfoMapper.class).insert(project); projectId=project.getId();
        var qr = new SiteGuardVisitQr(); qr.setProjectId(projectId); qr.setQrStatus("ENABLED"); qr.setQrVersion(1); qr.setSceneTokenHash(crypto.digest(UUID.randomUUID().toString())); qr.setSceneTokenEncrypted(crypto.encrypt("scene-only-for-test")); qr.setCreatedById(1L); qr.setCreatedByName("隔离测试"); qr.setUpdatedById(1L); qr.setUpdatedByName("隔离测试"); qr.setVersion(0); qr.setDeleted(0);
        mapper(SiteGuardVisitQrMapper.class).insert(qr); qrId=qr.getId();
        sessions = mock(VisitorSessionService.class);
        when(sessions.require(anyString())).thenAnswer(call -> { var ctx=contexts.get(call.getArgument(0)); if(ctx==null)throw BusinessException.of(401,"失效会话"); return ctx; });
        when(sessions.requireGuard(anyString(),anyLong(),anyLong())).thenAnswer(call -> {
            var ctx=sessions.require(call.getArgument(0));
            if(!"GUARD_QR".equals(ctx.effectiveSourceType()) || !Objects.equals(ctx.effectiveSourceId(),call.getArgument(1)) || !Objects.equals(ctx.projectId(),call.getArgument(2))) throw BusinessException.of(403,"不匹配的会话"); return ctx;
        });
        when(sessions.require(anyString(),any(SiteVisitInvitation.class))).thenAnswer(call -> { var ctx=sessions.require(call.getArgument(0)); var invite=(SiteVisitInvitation)call.getArgument(1); if(!"INVITATION".equals(ctx.effectiveSourceType()) || !Objects.equals(invite.getId(),ctx.effectiveSourceId()) || !Objects.equals(invite.getProjectId(),ctx.projectId()))throw BusinessException.of(403,"不匹配的会话"); return ctx; });
        when(sessions.requireMeeting(anyString(),anyLong(),anyLong())).thenAnswer(call -> {var ctx=sessions.require(call.getArgument(0)); if(!"MEETING_INVITATION".equals(ctx.effectiveSourceType())||!Objects.equals(ctx.effectiveSourceId(),call.getArgument(1))||!Objects.equals(ctx.projectId(),call.getArgument(2)))throw BusinessException.of(403,"不匹配的会话"); return ctx;});
        when(sessions.decryptOpenid(any())).thenAnswer(call -> crypto.decrypt(((VisitorSessionService.VisitorSessionContext)call.getArgument(0)).openidEncrypted()));
        var named=mock(VisitorProfileService.class);
        personal=transactional(new VisitorPersonalProfileService(mapper(SiteVisitorPersonalProfileMapper.class),mapper(ProjectInfoMapper.class),sessions,crypto,json));
        matching=new GuardVisitorMatchingService(mapper(GuardVisitorMatchMapper.class),sessions,crypto);
        var redis=mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") ValueOperations<String,String> values=mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        doAnswer(call -> { redisValues.put(call.getArgument(0),call.getArgument(1)); return null; }).when(values).set(anyString(),anyString(),any(Duration.class));
        when(values.get(anyString())).thenAnswer(call -> redisValues.get(call.getArgument(0)));
        choices=new GuardMeetingChoiceService(mapper(SiteVisitInvitationMapper.class),mapper(SiteMeetingVisitRegistrationMapper.class),sessions,crypto,redis,json);
        permissions=mock(ProjectPermissionService.class);
        var profile=mock(ProjectProfileService.class); var route=mock(ProjectRouteImageService.class); var wechat=mock(WechatPlatformClient.class); var limiter=mock(RedisRateLimitService.class); var logs=mock(OperationLogMapper.class);
        singles=transactional(new SiteAccessService(mapper(SiteVisitInvitationMapper.class),mapper(SiteVisitPersonMapper.class),mapper(SiteVisitAuditLogMapper.class),mapper(SiteMeetingVisitRegistrationMapper.class),mapper(ProjectInfoMapper.class),mock(SysUserMapper.class),mock(SysUserProjectMapper.class),permissions,profile,route,crypto,wechat,sessions,named,personal,mock(MeetingCheckinQrProvisioner.class),logs,json,"pages/public/visitor-invite","pages/public/meeting-invite","develop"));
        meetings=transactional(new MeetingVisitService(mapper(SiteMeetingVisitRegistrationMapper.class),mapper(SiteMeetingVisitPersonMapper.class),mapper(SiteMeetingVisitAuditLogMapper.class),mapper(SiteMeetingAttendanceMapper.class),mapper(SiteVisitInvitationMapper.class),mapper(ProjectInfoMapper.class),permissions,route,crypto,sessions,named,personal,singles,limiter,logs,json,new TransactionTemplate(transactions)));
        guards=transactional(new GuardVisitService(mapper(SiteGuardVisitQrMapper.class),mapper(SiteGuardVisitRegistrationMapper.class),mapper(SiteGuardVisitPersonMapper.class),mapper(SiteGuardVisitAuditLogMapper.class),mapper(ProjectInfoMapper.class),permissions,profile,crypto,sessions,named,personal,matching,choices,meetings,mapper(SiteGuardMeetingRegistrationMapper.class),wechat,limiter,logs,json,"pages/public/guard-visitor-register","develop"));
        attendance=transactional(new MeetingCheckinService(mapper(SiteMeetingCheckinQrMapper.class),mapper(SiteMeetingAttendanceMapper.class),mapper(SiteMeetingVisitRegistrationMapper.class),mapper(SiteMeetingVisitPersonMapper.class),mapper(SiteMeetingVisitAuditLogMapper.class),mapper(SiteVisitInvitationMapper.class),mapper(ProjectInfoMapper.class),permissions,crypto,sessions,named,personal,limiter,wechat,logs,mock(MeetingCheckinQrProvisioner.class),json,new TransactionTemplate(transactions),"pages/public/meeting-check-in","develop"));
    }
    private <T>T mapper(Class<T> type) { var config=sql.getConfiguration(); if(!config.hasMapper(type))config.addMapper(type); return sql.getMapper(type); }
    @SuppressWarnings("unchecked") private <T>T transactional(T service) { var proxy=new ProxyFactory(service); proxy.setProxyTargetClass(true); proxy.addAdvice(new TransactionInterceptor(transactions,new AnnotationTransactionAttributeSource())); return (T)proxy.getProxy(); }
    private String session(String owner,String source,Long sourceId) { var token=UUID.randomUUID().toString(); contexts.put(token,new VisitorSessionService.VisitorSessionContext(source.equals("INVITATION")?sourceId:null,projectId,"wx-verify",crypto.fingerprint(owner),crypto.encrypt(owner),source,sourceId)); return token; }
    private SiteVisitInvitation invitation(String type, LocalDateTime start, LocalDateTime end) {
        var value=new SiteVisitInvitation(); value.setProjectId(projectId); value.setInviteNo("TEST-"+UUID.randomUUID().toString().substring(0,25)); var token=UUID.randomUUID().toString().replace("-",""); value.setTokenHash(crypto.digest(token));value.setTokenEncrypted(crypto.encrypt(token)); value.setInviteType(type); value.setStatus(type.equals("MEETING")?"OPEN":"PENDING"); value.setVisitStartTime(start);value.setVisitEndTime(end);value.setPurpose("隔离测试会议");value.setVisitLocation("测试会场");value.setHostUserId(1L);value.setHostName("测试接待");value.setCreatedById(1L);value.setCreatedByName("隔离验证");value.setVisitorCount(0);value.setVersion(0);value.setDeleted(0); mapper(SiteVisitInvitationMapper.class).insert(value); return value;
    }
    private PublicGuardVisitSubmitRequest submit(List<String> tokens) {var request=new PublicGuardVisitSubmitRequest(); request.setVisitorCompany("来访单位");request.setContactName("张三");request.setContactPhone("13800000000");request.setTravelMode("OTHER");request.setPrivacyAgreed(true);request.setRememberInfo(true);request.setMeetingChoiceTokens(tokens); var companion=new SiteVisitPersonRequest();companion.setPersonName("同行甲");request.setCompanions(List.of(companion)); return request;}
    private long count(String table) { return Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE project_id=?",Long.class,projectId)); }
    private List<String> meetingTokens(String token) {return guards.publicMeetings(token).stream().map(PublicGuardMeetingChoiceVO::choiceToken).toList();}

    @Test void multiMeetingSubmissionIsAtomicWhenOneMeetingChanges() {
        var now=LocalDateTime.now(); invitation("MEETING",now.minusMinutes(10),now.plusHours(1)); var changed=invitation("MEETING",now.plusHours(1),now.plusHours(2));
        var token=session("owner","GUARD_QR",qrId); var tokens=meetingTokens(token);
        jdbc.update("UPDATE site_visit_invitation SET version=version+1 WHERE id=?",changed.getId());
        assertThatThrownBy(() -> guards.submitPublic(submit(tokens),token)).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getCode()).isEqualTo(409));
        for(var table:List.of("site_guard_visit_registration","site_guard_visit_person","site_meeting_visit_registration","site_meeting_visit_person","site_guard_meeting_registration","site_visitor_personal_profile","site_visitor_personal_profile_audit","site_meeting_visit_audit_log","site_guard_visit_audit_log"))assertThat(count(table)).as(table).isZero();
    }
    @Test void concurrentRetryCreatesOneGatePassAndOneGroupPerMeetingWithoutAttendance() throws Exception {
        var now=LocalDateTime.now(); invitation("MEETING",now.minusMinutes(10),now.plusHours(1));invitation("MEETING",now.plusHours(2),now.plusHours(3));
        var token=session("owner","GUARD_QR",qrId);var request=submit(meetingTokens(token));
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            var first=pool.submit(()->{start.await();return guards.submitPublic(request,token);});var second=pool.submit(()->{start.await();return guards.submitPublic(request,token);});start.countDown();
            assertThat(first.get(20,TimeUnit.SECONDS).getRegistrationNo()).isEqualTo(second.get(20,TimeUnit.SECONDS).getRegistrationNo());
        } finally {pool.shutdownNow();}
        assertThat(count("site_guard_visit_registration")).isEqualTo(1);assertThat(count("site_meeting_visit_registration")).isEqualTo(2);assertThat(count("site_guard_meeting_registration")).isEqualTo(2);assertThat(count("site_meeting_visit_person")).isEqualTo(4);assertThat(count("site_meeting_attendance")).isZero();
        var state=guards.refreshPublicState(token);assertThat(state.getPageState()).isEqualTo("MATCHED");assertThat(state.getMatchedPasses()).hasSize(1);assertThat(state.getMatchedPasses().get(0).getVisitorCount()).isEqualTo(2);
        assertThat(personal.read(contexts.get(token)).getContactName()).isEqualTo("张三");
        assertThat(guards.publicMeetings(token)).allMatch(PublicGuardMeetingChoiceVO::registered);
    }
    @Test void singleInvitationIdentityMatchesOnlyInItsTimeWindowAndLegacyOptOutStillSaves() {
        var now=LocalDateTime.now().withNano(0);var invite=invitation("SINGLE",now.plusHours(1),now.plusHours(3));var singleToken=session("owner","INVITATION",invite.getId());
        var request=new PublicSiteVisitSubmitRequest();request.setInviteToken(crypto.decrypt(invite.getTokenEncrypted()));request.setVisitorCompany("预约单位");request.setContactName("王五");request.setContactPhone("13900000000");request.setTravelMode("DRIVING");request.setVehiclePlate("沪A12345");request.setPrivacyAgreed(true);request.setRememberInfo(true);
        singles.submitPublic(request,singleToken);
        var guardToken=session("owner","GUARD_QR",qrId);var ctx=contexts.get(guardToken);
        assertThat(personal.read(ctx).getContactName()).isEqualTo("王五");
        assertThat(matching.find(ctx,now)).isEmpty();assertThat(matching.find(ctx,invite.getVisitStartTime())).hasSize(1);assertThat(matching.find(ctx,invite.getVisitEndTime())).isEmpty();
        var other=contexts.get(session("other","GUARD_QR",qrId));assertThat(matching.find(other,invite.getVisitStartTime())).isEmpty();assertThat(personal.read(other).isAvailable()).isFalse();
        jdbc.update("UPDATE site_visit_invitation SET status='VOIDED' WHERE id=?",invite.getId());assertThat(matching.find(ctx,invite.getVisitStartTime())).isEmpty();
        var form=submit(List.of());form.setRememberInfo(false);guards.submitPublic(form,guardToken);
        assertThat(personal.read(ctx).isAvailable()).isTrue();assertThat(jdbc.queryForObject("SELECT contact_phone_encrypted FROM site_visitor_personal_profile WHERE project_id=?",String.class,projectId)).startsWith("v1:");
    }
    @Test void opaqueMeetingChoicesCannotCrossSessionProjectOrQrLifecycle() {
        var now=LocalDateTime.now();invitation("MEETING",now,now.plusHours(1));var token=session("owner","GUARD_QR",qrId);var options=meetingTokens(token);
        var second=session("other","GUARD_QR",qrId);
        assertThatThrownBy(()->guards.submitPublic(submit(options),second)).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getCode()).isEqualTo(409));
        jdbc.update("UPDATE site_guard_visit_qr SET qr_status='ROTATED' WHERE id=?",qrId);
        assertThatThrownBy(()->guards.refreshPublicState(token)).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getCode()).isEqualTo(410));
        assertThatThrownBy(()->guards.publicMeetings(token)).isInstanceOf(BusinessException.class);
        assertThat(count("site_guard_visit_registration")).isZero();
    }
    @Test void attendanceScreenPaginatesSanitizedNamesAndReflectsRevokeAndCorrection() throws Exception {
        var now=LocalDateTime.now();var invite=invitation("MEETING",now.minusMinutes(5),now.plusHours(2));var token=session("owner","GUARD_QR",qrId);var request=submit(meetingTokens(token));
        request.setCompanions(java.util.stream.IntStream.range(0,25).mapToObj(i->{var p=new SiteVisitPersonRequest();p.setPersonName("参会人员"+i);return p;}).toList());guards.submitPublic(request,token);
        List<Long> people=jdbc.queryForList("SELECT id FROM site_meeting_visit_person WHERE project_id=? ORDER BY id",Long.class,projectId);
        for(int i=0;i<people.size();i++) jdbc.update("INSERT INTO site_meeting_attendance (invitation_id,registration_id,person_id,project_id,status,checkin_method,checkin_time,location_result,version,deleted) SELECT r.invitation_id,p.registration_id,p.id,p.project_id,'CHECKED_IN','MANUAL',?,'MANUAL',0,0 FROM site_meeting_visit_person p JOIN site_meeting_visit_registration r ON r.id=p.registration_id WHERE p.id=?",now.plusSeconds(i),people.get(i));
        var user=new SysUser();user.setId(1L);var page=attendance.screen(invite.getId(),1,user);
        assertThat(page.getTotalCheckedInCount()).isEqualTo(26);assertThat(page.getRecords()).hasSize(24);assertThat(page.getReservedPendingCount()).isZero();assertThat(page.isCanShowQr()).isFalse();assertThat(page.getRecords().get(0).getPersonId()).isEqualTo(people.get(25));
        var serialized=json.writeValueAsString(page);assertThat(serialized).doesNotContain("contactPhone","vehiclePlate","locationResult","phoneEncrypted");
        assertThat(attendance.screen(invite.getId(),2,user).getRecords()).hasSize(2);
        jdbc.update("UPDATE site_meeting_attendance SET status='REVOKED' WHERE person_id IN (?,?)",people.get(25),people.get(24));jdbc.update("UPDATE site_meeting_visit_person SET person_name='已更正姓名' WHERE id=?",people.get(23));
        page=attendance.screen(invite.getId(),2,user);assertThat(page.getPageNo()).isEqualTo(1);assertThat(page.getTotalCheckedInCount()).isEqualTo(24);assertThat(page.getReservedPendingCount()).isEqualTo(2);assertThat(page.getRecords().get(0).getPersonName()).isEqualTo("已更正姓名");
        doThrow(BusinessException.of(403,"权限已撤销")).when(permissions).requireSystemPermission(eq(1L),eq(projectId),eq("site_access.view"));
        assertThatThrownBy(()->attendance.screen(invite.getId(),1,user)).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getCode()).isEqualTo(403));
    }
}
