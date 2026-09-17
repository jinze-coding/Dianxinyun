package com.example.siteplatform.project.service;

import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.notification.service.UserNotificationService;
import com.example.siteplatform.system.constant.BusinessModuleCodes;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.*;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="PROJECT_MODULE_INTEGRATION", matches="ISOLATED_CLONE_ONLY")
@SpringBootTest(properties={"spring.profiles.active=local", "spring.main.banner-mode=off"})
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProjectBusinessModuleIntegrationTest {
    static final Path ROOT;
    static { try { ROOT=Files.createTempDirectory("project-module-integration-"); } catch(Exception e) { throw new IllegalStateException(e); } }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        String database=System.getenv("PROJECT_MODULE_TEST_DATABASE");
        if (database==null || !database.matches("dianxinyun_modules_verify_[0-9]{14}")) throw new IllegalStateException("An isolated clone is required");
        registry.add("spring.datasource.url", () -> "jdbc:mysql://127.0.0.1:3306/"+database+"?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true");
        registry.add("spring.data.redis.port", () -> 6380); registry.add("spring.data.redis.database", () -> 15);
        registry.add("file.upload.path", ROOT::toString);
    }
    @Autowired com.example.siteplatform.project.mapper.ProjectInfoMapper projectInfoMapper;
    @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; @Autowired ObjectMapper json;
    @Autowired AuthService auth; @Autowired SysUserMapper users; @Autowired ProjectBusinessModuleService modules;
    @Autowired UserNotificationService notifications;
    @Autowired com.example.siteplatform.quality.mapper.QualityIssueExportJobMapper qualityJobs;
    @Autowired com.example.siteplatform.inspection.general.mapper.GeneralInspectionExportJobMapper edgeJobs;
    @Autowired com.example.siteplatform.seal.mapper.SealFormExportJobMapper sealJobs;
    @Autowired org.springframework.transaction.support.TransactionTemplate transactions;
    long projectA,projectB,adminId,memberId,outsiderId;
    String admin,member,outsider;
    final List<String> tokens=new ArrayList<>();

    @BeforeAll void setup() throws Exception {
        assertThat(jdbc.queryForObject("SELECT DATABASE()",String.class)).isEqualTo(System.getenv("PROJECT_MODULE_TEST_DATABASE"));
        adminId=jdbc.queryForObject("SELECT MIN(u.id) FROM sys_user u JOIN sys_user_role ur ON ur.user_id=u.id JOIN sys_role r ON r.id=ur.role_id WHERE u.deleted=0 AND u.status=1 AND r.role_code='PLATFORM_ADMIN'",Long.class);
        admin=token(adminId);
        projectA=call("POST","/projects",Map.of("projectName","模块隔离验证 A"),admin).path("id").asLong();
        projectB=call("POST","/projects",Map.of("projectName","模块隔离验证 B"),admin).path("id").asLong();
        memberId=createUser(); outsiderId=createUser();
        String code="MODULE_TEST_"+UUID.randomUUID().toString().substring(0,12);
        jdbc.update("INSERT INTO sys_role(role_name,role_code,scope_type,enabled,deleted) VALUES(?,?,'PROJECT',1,0)",code,code);
        long role=jdbc.queryForObject("SELECT id FROM sys_role WHERE role_code=?",Long.class,code);
        for(String module:BusinessModuleCodes.ALL) jdbc.update("INSERT INTO sys_role_business_module(role_id,module_code) VALUES(?,?)",role,module);
        jdbc.update("INSERT INTO sys_role_menu(role_id,menu_id) SELECT ?,id FROM sys_menu WHERE deleted=0 AND enabled=1",role);
        jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) SELECT ?,id FROM sys_permission WHERE deleted=0 AND enabled=1",role);
        for(long project:List.of(projectA,projectB)) {
            jdbc.update("INSERT INTO sys_user_project(user_id,project_id,status) VALUES(?,?,'ACTIVE')",memberId,project);
            jdbc.update("INSERT INTO sys_user_project_role(user_id,project_id,role_id) VALUES(?,?,?)",memberId,project,role);
        }
        member=token(memberId); outsider=token(outsiderId);
    }
    @BeforeEach void reset() { for(long id:List.of(projectA,projectB)) modules.update(id,BusinessModuleCodes.ALL,modules.state(id).moduleConfigVersion(),true,users.selectById(adminId)); }
    @AfterAll void cleanup() { for(long id:List.of(projectA,projectB)) if(id>0)set(id,BusinessModuleCodes.ALL); tokens.forEach(auth::logoutSession); }
    String token(long id) { String value=auth.issueToken(users.selectById(id)); tokens.add(value); return value; }
    long createUser() {
        String username="module-test-"+UUID.randomUUID();
        jdbc.update("INSERT INTO sys_user(username,password,real_name,status,deleted) VALUES(?,?,?,1,0)",username,new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(UUID.randomUUID().toString()),"项目模块验证用户");
        return jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?",Long.class,username);
    }
    ProjectBusinessModuleService.State set(long id,List<String> enabled) { return modules.update(id,enabled,modules.state(id).moduleConfigVersion(),users.selectById(adminId)); }
    MvcResult raw(String method,String path,Object body,String token) throws Exception {
        var request=org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(org.springframework.http.HttpMethod.valueOf(method),java.net.URI.create("/api/v1"+path));
        if(token!=null)request.header("Authorization","Bearer "+token);
        if(body!=null)request.contentType("application/json").content(json.writeValueAsBytes(body));
        return mvc.perform(request).andReturn();
    }
    JsonNode call(String method,String path,Object body,String token) throws Exception {
        var result=raw(method,path,body,token);
        assertThat(result.getResponse().getStatus()).as(method+" "+path+" "+result.getResponse().getContentAsString()).isEqualTo(200);
        return json.readTree(result.getResponse().getContentAsByteArray()).path("data");
    }
    int status(String method,String path,Object body,String token) throws Exception { return raw(method,path,body,token).getResponse().getStatus(); }
    JsonNode context(JsonNode user,long project) { for(var value:user.path("projectContexts"))if(value.path("projectId").asLong()==project)return value;throw new AssertionError("Missing project context"); }
    List<Long> grantCounts() { return List.of("sys_role_business_module","sys_role_menu","sys_role_permission").stream().map(table->jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Long.class)).toList(); }

    @Test void inboxDisplayIsIndependentPersistentAndCompatible() throws Exception {
        var before = modules.state(projectA);
        assertThat(before.inboxEntryVisible()).isTrue();
        var grants = grantCounts();
        var moduleRows = jdbc.queryForList("SELECT module_code,enabled,activated_at FROM project_business_module WHERE project_id=? ORDER BY module_code", projectA);
        var notificationsBefore = jdbc.queryForList("SELECT * FROM user_notification ORDER BY id");
        var hide = Map.of("moduleCodes", before.enabledBusinessModules(), "expectedVersion", before.moduleConfigVersion(), "inboxEntryVisible", false);
        assertThat(status("PUT", "/system/project-modules/"+projectA, hide, member)).isEqualTo(403);
        var hidden = call("PUT", "/system/project-modules/"+projectA, hide, admin);
        assertThat(hidden.path("inboxEntryVisible").asBoolean(true)).isFalse();
        assertThat(hidden.path("moduleConfigVersion").asLong()).isEqualTo(before.moduleConfigVersion()+1);
        for (String token : List.of(admin, member)) {
            var user = call("GET", "/auth/user-info", null, token);
            assertThat(context(user, projectA).path("inboxEntryVisible").asBoolean(true)).isFalse();
            assertThat(context(user, projectB).path("inboxEntryVisible").asBoolean()).isTrue();
            call("GET", "/me/work-summary?projectId="+projectA, null, token);
            call("GET", "/safety-committee/records?projectId="+projectA, null, token);
        }
        assertThat(status("PUT", "/system/project-modules/"+projectA, hide, admin)).isEqualTo(409);
        var legacy = call("PUT", "/system/project-modules/"+projectA, Map.of("moduleCodes",before.enabledBusinessModules(),"expectedVersion",before.moduleConfigVersion()+1), admin);
        assertThat(legacy.path("inboxEntryVisible").asBoolean(true)).isFalse();
        assertThat(legacy.path("moduleConfigVersion")).isEqualTo(hidden.path("moduleConfigVersion"));
        assertThat(grantCounts()).isEqualTo(grants);
        assertThat(jdbc.queryForList("SELECT module_code,enabled,activated_at FROM project_business_module WHERE project_id=? ORDER BY module_code", projectA)).isEqualTo(moduleRows);
        assertThat(jdbc.queryForList("SELECT * FROM user_notification ORDER BY id")).isEqualTo(notificationsBefore);
        var staleProject = new com.example.siteplatform.project.entity.ProjectInfo();
        staleProject.setId(projectA); staleProject.setInboxEntryVisible(true); staleProject.setDescription("Display setting is not a general project field");
        projectInfoMapper.updateById(staleProject);
        assertThat(modules.state(projectA).inboxEntryVisible()).isFalse();
    }

    @Test void inboxAuditFailureRollsBackDisplayAndSharedVersion() {
        var before = modules.state(projectA);
        jdbc.execute("CREATE TRIGGER inbox_test_audit_failure BEFORE INSERT ON sys_operation_log FOR EACH ROW BEGIN IF NEW.operation_type='UPDATE_PROJECT_MODULES' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Intentional isolated inbox audit failure'; END IF; END");
        try {
            assertThatThrownBy(() -> modules.update(projectA,before.enabledBusinessModules(),before.moduleConfigVersion(),false,users.selectById(adminId))).isInstanceOf(RuntimeException.class);
            assertThat(modules.state(projectA)).isEqualTo(before);
        } finally { jdbc.execute("DROP TRIGGER inbox_test_audit_failure"); }
    }

    @Test void newProjectsAndSharedRoleRespectIndependentProjectCaps() throws Exception {
        assertThat(modules.state(projectA).enabledBusinessModules()).containsExactlyElementsOf(BusinessModuleCodes.ALL);
        var grants=grantCounts();
        set(projectA,List.of("SITE_ACCESS","DOCUMENT","SAFETY_COMMITTEE"));
        for(String token:List.of(admin,member)) {
            var user=call("GET","/auth/user-info",null,token);
            var a=context(user,projectA); var b=context(user,projectB);
            assertThat(a.path("enabledBusinessModules").toString()).doesNotContain("\"QUALITY\"","\"INSPECTION\"");
            assertThat(a.path("menuCodes").toString()).doesNotContain("WEB_QUALITY","MINI_QUALITY","WEB_INSPECTION","MINI_INSPECTION");
            assertThat(a.path("permissionCodes").toString()).doesNotContain("quality.manage","inspection.submit");
            assertThat(b.path("menuCodes").toString()).contains("WEB_QUALITY","MINI_INSPECTION","WEB_SAFETY_COMMITTEE");
            assertThat(status("GET","/electric-boxes?projectId="+projectA,null,token)).isEqualTo(403);
            assertThat(status("GET","/quality/issues?projectId="+projectA,null,token)).isEqualTo(403);
            call("GET","/electric-boxes?projectId="+projectB,null,token);
            call("GET","/safety-committee/records?projectId="+projectA,null,token);
        }
        assertThat(grantCounts()).isEqualTo(grants);
    }

    @Test void projectDeletionPreviewsAndCleansConfigurationEvenWhenAllModulesAreOff() throws Exception {
        long project=call("POST","/projects",Map.of("projectName","模块删除验证"),admin).path("id").asLong();
        set(project,List.of());
        var impact=call("POST","/system/deletions/preview",Map.of("targetType","PROJECT","targetId",project),admin);
        var moduleItems=new ArrayList<JsonNode>();
        impact.path("items").forEach(item->{ if("projectModules".equals(item.path("code").asText())) moduleItems.add(item); });
        assertThat(moduleItems).hasSize(1);
        assertThat(moduleItems.get(0).path("count").asInt()).isEqualTo(5);
        var userImpact=call("POST","/system/deletions/preview",Map.of("targetType","USER","targetId",outsiderId),admin);
        assertThat(userImpact.path("items").toString()).doesNotContain("projectModules");
        call("POST","/system/deletions/execute",Map.of("targetType","PROJECT","targetId",project,
            "acknowledged",true,"confirmationToken",impact.path("confirmationToken").asText()),admin);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM project_business_module WHERE project_id=?",Long.class,project)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM project_info WHERE id=?",Long.class,project)).isZero();
    }

    @Test void configurationRequiresPlatformRoleAndRejectsConcurrentOverwrite() throws Exception {
        long version=modules.state(projectA).moduleConfigVersion();
        var request=Map.of("moduleCodes",List.of(),"expectedVersion",version);
        assertThat(status("GET","/system/project-modules",null,member)).isEqualTo(403);
        assertThat(status("PUT","/system/project-modules/"+projectA,request,member)).isEqualTo(403);
        assertThat(status("GET","/projects/"+projectA+"/business-modules",null,outsider)).isEqualTo(403);
        call("PUT","/system/project-modules/"+projectA,request,admin);
        assertThat(status("PUT","/system/project-modules/"+projectA,Map.of("moduleCodes",BusinessModuleCodes.ALL,"expectedVersion",version),admin)).isEqualTo(409);
        assertThat(modules.state(projectA).enabledBusinessModules()).isEmpty();
        for(var invalid:List.of(List.of("DOCUMENT","DOCUMENT"),List.of("OLD_SAFETY"))) {
            assertThat(status("PUT","/system/project-modules/"+projectA,Map.of("moduleCodes",invalid,"expectedVersion",version+1),admin)).isEqualTo(400);
        }
        call("GET","/projects/"+projectA+"/profile",null,member);
        call("GET","/system/project-modules",null,admin);
        call("GET","/me/work-summary?projectId="+projectA,null,member);
    }

    @Test void disablingPreservesRecordsAndBlocksOriginalLinksForAdminAndMember() throws Exception {
        var record=call("POST","/safety-committee/records",Map.of("projectId",projectA,"category","其他","conclusion","保留原记录","attachmentIds",List.of(),"requestKey",UUID.randomUUID().toString()),member);
        long id=record.path("id").asLong();
        var before=jdbc.queryForMap("SELECT * FROM safety_committee_record WHERE id=?",id);
        set(projectA,List.of());
        for(String token:List.of(admin,member)) {
            assertThat(status("GET","/safety-committee/records/"+id,null,token)).isEqualTo(403);
            assertThat(status("GET","/safety-committee/categories?projectId="+projectA,null,token)).isEqualTo(403);
            assertThat(status("GET","/project-documents?projectId="+projectA,null,token)).isEqualTo(403);
            assertThat(status("GET","/seal/applications?projectId="+projectA+"&scope=INITIATED",null,token)).isEqualTo(403);
            assertThat(status("GET","/site-access/invitations?projectId="+projectA,null,token)).isEqualTo(403);
        }
        assertThat(jdbc.queryForMap("SELECT * FROM safety_committee_record WHERE id=?",id)).isEqualTo(before);
        assertThat(status("POST","/system/deletions/preview",Map.of("targetType","COMMITTEE_INSPECTION","targetId",id),admin)).isEqualTo(403);
        set(projectA,BusinessModuleCodes.ALL);
        call("GET","/safety-committee/records/"+id,null,member);
        assertThat(jdbc.queryForMap("SELECT * FROM safety_committee_record WHERE id=?",id)).isEqualTo(before);
    }

    @Test void uploadsAndExistingReadGrantsStopThenResumeWithoutSessionRevocation() throws Exception {
        byte[] bytes=Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j3ioAAAAASUVORK5CYII=");
        String sha=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        var metadata=Map.of("projectId",projectA,"fileName","现场.png","totalSize",bytes.length,"sha256",sha,"draftKey",UUID.randomUUID().toString());
        var session=call("POST","/safety-committee/uploads",metadata,member);
        String id=session.path("sessionId").asText();
        set(projectA,List.of("DOCUMENT"));
        assertThat(status("GET","/safety-committee/uploads/"+id+"?projectId="+projectA,null,member)).isEqualTo(403);
        assertThat(status("POST","/safety-committee/uploads",metadata,member)).isEqualTo(403);
        set(projectA,BusinessModuleCodes.ALL);
        assertThat(call("GET","/safety-committee/uploads/"+id+"?projectId="+projectA,null,member).path("expiresAt")).isEqualTo(session.path("expiresAt"));
        var chunk=org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/safety-committee/uploads/"+id+"/chunks/0")
            .file(new org.springframework.mock.web.MockMultipartFile("chunk","chunk","application/octet-stream",bytes))
            .param("projectId",String.valueOf(projectA)).param("sha256",sha).header("Authorization","Bearer "+member).with(r->{r.setMethod("PUT");return r;});
        assertThat(mvc.perform(chunk).andReturn().getResponse().getStatus()).isEqualTo(200);
        long attachment=call("POST","/safety-committee/uploads/"+id+"/complete?projectId="+projectA,Map.of(),member).path("id").asLong();
        call("POST","/safety-committee/records",Map.of("projectId",projectA,"category","其他","attachmentIds",List.of(attachment),"requestKey",metadata.get("draftKey")),member);
        String media=call("POST","/safety-committee/attachments/"+attachment+"/read-session?nativePlayback=true",Map.of(),member).path("contentPath").asText();
        var cookieHeader=raw("POST","/safety-committee/attachments/"+attachment+"/read-session",Map.of(),member).getResponse().getHeader("Set-Cookie");
        var cookie=new jakarta.servlet.http.Cookie("committee_read",cookieHeader.split(";",2)[0].split("=",2)[1]);
        var get=org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(media).header("Range","bytes=0-7");
        assertThat(mvc.perform(get).andReturn().getResponse().getStatus()).isEqualTo(206);
        set(projectA,List.of());
        assertThat(mvc.perform(get).andReturn().getResponse().getStatus()).isEqualTo(403);
        assertThat(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/safety-committee/attachments/"+attachment+"/content").cookie(cookie).header("Range","bytes=0-7")).andReturn().getResponse().getStatus()).isEqualTo(403);
        call("GET","/safety-committee/categories?projectId="+projectB,null,member);
        set(projectA,BusinessModuleCodes.ALL);
        assertThat(mvc.perform(get).andReturn().getResponse().getStatus()).isEqualTo(206);
    }

    @Test void disabledExportQueuesPreserveWorkAndResumeInOriginalOrder() {
        set(projectA,List.of());
        jdbc.update("INSERT INTO quality_issue_export_job(project_id,requested_by_id,start_date,end_date,status,create_time) VALUES(?,?,CURDATE(),CURDATE(),'PENDING','2000-01-01')",projectA,memberId);
        jdbc.update("INSERT INTO general_inspection_export_job(project_id,requested_by_id,start_date,end_date,export_type,status,create_time) VALUES(?,?,CURDATE(),CURDATE(),'EDGE','PENDING','2000-01-01')",projectA,memberId);
        jdbc.update("INSERT INTO seal_form_export_job(project_id,requested_by_id,requested_by_name,request_key,request_hash,selection_mode,status,application_count,create_time) VALUES(?,?,'验证',?,?,'SELECTED','PENDING',0,'2000-01-01')",projectA,memberId,UUID.randomUUID().toString(),"0".repeat(64));
        var tables=List.of("quality_issue_export_job","general_inspection_export_job","seal_form_export_job");
        var before=tables.stream().map(t->jdbc.queryForMap("SELECT * FROM "+t+" WHERE project_id=? ORDER BY id DESC LIMIT 1",projectA)).toList();
        var q=qualityJobs.selectNextPendingForUpdate(); var e=edgeJobs.selectNextPendingEdgeForUpdate(); var f=sealJobs.nextForUpdate();
        assertThat(q==null || q.getProjectId()!=projectA).isTrue();
        assertThat(e==null || e.getProjectId()!=projectA).isTrue();
        assertThat(f==null || f.getProjectId()!=projectA).isTrue();
        transactions.executeWithoutResult(tx->{
            set(projectA,BusinessModuleCodes.ALL);
            assertThat(qualityJobs.selectNextPendingForUpdate().getProjectId()).isEqualTo(projectA);
            assertThat(edgeJobs.selectNextPendingEdgeForUpdate().getProjectId()).isEqualTo(projectA);
            assertThat(sealJobs.nextForUpdate().getProjectId()).isEqualTo(projectA);
            assertThat(tables.stream().map(t->jdbc.queryForMap("SELECT * FROM "+t+" WHERE project_id=? ORDER BY id DESC LIMIT 1",projectA)).toList()).isEqualTo(before);
            // Synthetic queue inputs have no snapshots; retire only these clone fixtures.
            for (String table:tables) jdbc.update("UPDATE "+table+" SET status='EXPIRED' WHERE project_id=? AND create_time='2000-01-01'",projectA);
        });
    }

    @Test void publicBoxAndUnifiedSceneCannotBypassProjectDisablement() throws Exception {
        String code=UUID.randomUUID().toString().replace("-","");
        jdbc.update("INSERT INTO electric_box(project_id,box_code,box_name,install_location,public_code,status,deleted) VALUES(?,?,?,'验证位置',?,'ACTIVE',0)",projectA,code,"模块验证电箱",code);
        set(projectA,List.of("DOCUMENT"));
        assertThat(status("GET","/public/electric-boxes/"+code+"/summary",null,null)).isEqualTo(403);
        assertThat(status("GET","/public/electric-boxes/"+code+"/monthly-records",null,null)).isEqualTo(403);
        assertThat(status("GET","/scan/electric-boxes/B:"+code,null,null)).isEqualTo(403);
        assertThat(status("GET","/scan/electric-boxes/B:"+code,null,admin)).isEqualTo(403);
        set(projectA,BusinessModuleCodes.ALL);
        call("GET","/public/electric-boxes/"+code+"/summary",null,null);
    }

    @Test void notificationPaginationCountsAndReadAllPreserveHiddenMessages() throws Exception {
        String key=UUID.randomUUID().toString();
        notifications.notify(memberId,projectA,"QUALITY_ISSUE",1L,"TEST","模块验证","",key+"a","QUALITY_ISSUE_DETAIL","{}");
        notifications.notify(memberId,projectB,"QUALITY_ISSUE",1L,"TEST","模块验证","",key+"b","QUALITY_ISSUE_DETAIL","{}");
        notifications.notify(memberId,projectA,"SEAL_APPLICATION",1L,"TEST","模块验证","",key+"c","SEAL_APPLICATION_DETAIL","{}");
        long hidden=jdbc.queryForObject("SELECT id FROM user_notification WHERE dedup_key=?",Long.class,key+"a");
        set(projectA,List.of("DOCUMENT"));
        assertThat(call("GET","/me/inbox?projectId="+projectA,null,member).path("total").asLong()).isEqualTo(1);
        assertThat(status("PUT","/me/inbox/"+hidden+"/read",null,member)).isEqualTo(403);
        call("PUT","/me/inbox/read-all?projectId="+projectA,null,member);
        assertThat(jdbc.queryForObject("SELECT is_read FROM user_notification WHERE id=?",Integer.class,hidden)).isZero();
        assertThat(jdbc.queryForObject("SELECT is_read FROM user_notification WHERE dedup_key=?",Integer.class,key+"b")).isZero();
        set(projectA,BusinessModuleCodes.ALL);
        assertThat(call("GET","/me/inbox?projectId="+projectA,null,member).path("total").asLong()).isEqualTo(2);
    }

    @Test void auditFailureRollsBackEntireConfigurationAndActivationBoundary() throws Exception {
        var before=modules.state(projectA);
        jdbc.execute("CREATE TRIGGER module_test_audit_failure BEFORE INSERT ON sys_operation_log FOR EACH ROW BEGIN IF NEW.operation_type='UPDATE_PROJECT_MODULES' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Intentional isolated audit failure'; END IF; END");
        try {
            assertThat(status("PUT","/system/project-modules/"+projectA,Map.of("moduleCodes",List.of(),"expectedVersion",before.moduleConfigVersion()),admin)).isEqualTo(500);
            assertThat(modules.state(projectA)).isEqualTo(before);
        } finally { jdbc.execute("DROP TRIGGER module_test_audit_failure"); }
        set(projectA,List.of());
        assertThat(modules.occurrenceEnabled(projectA,"QUALITY",java.time.LocalDateTime.now(ProjectBusinessModuleService.ZONE))).isFalse();
        set(projectA,BusinessModuleCodes.ALL);
        var activation=modules.activatedAt(projectA,"QUALITY");
        assertThat(activation).isNotNull();
        assertThat(modules.occurrenceEnabled(projectA,"QUALITY",activation.minusSeconds(1))).isFalse();
        assertThat(modules.occurrenceEnabled(projectA,"QUALITY",activation.plusSeconds(1))).isTrue();
    }
}
