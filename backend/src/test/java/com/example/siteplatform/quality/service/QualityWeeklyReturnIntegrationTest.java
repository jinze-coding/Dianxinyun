package com.example.siteplatform.quality.service;

import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.system.constant.BusinessModuleCodes;
import com.example.siteplatform.system.dto.AdministrativeDeletionExecuteRequest;
import com.example.siteplatform.system.dto.AdministrativeDeletionPreviewRequest;
import com.example.siteplatform.system.service.AdministrativeDeletionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.*;

import java.nio.file.*;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/** Real transactions, files and competing requests; synthetic data in a guarded empty database only. */
@EnabledIfEnvironmentVariable(named="QUALITY_RETURN_INTEGRATION", matches="ISOLATED_EMPTY_ONLY")
@SpringBootTest(properties={"spring.profiles.active=local", "spring.main.banner-mode=off", "logging.level.root=ERROR"})
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(QualityWeeklyReturnIntegrationTest.Administrator.class)
class QualityWeeklyReturnIntegrationTest {
    static final String NAME="qr-"+UUID.randomUUID();
    static final String HASH=new BCryptPasswordEncoder().encode(UUID.randomUUID().toString());
    static final Path FILES=Path.of(System.getProperty("java.io.tmpdir"),NAME);
    static final byte[] PNG=Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j9xkAAAAASUVORK5CYII=");
    @TestConfiguration static class Administrator {
        @Bean @Order(Ordered.HIGHEST_PRECEDENCE) ApplicationRunner syntheticAdmin(JdbcTemplate jdbc) {
            return args -> {
                String database=jdbc.queryForObject("SELECT DATABASE()",String.class);
                if(database==null || !database.matches("dianxinyun_quality_return_[0-9]{14}") || !database.equals(System.getenv("QUALITY_RETURN_TEST_DATABASE"))) throw new IllegalStateException("Isolated test database required");
                jdbc.update("INSERT INTO sys_user(username,password,real_name,status,deleted) VALUES(?,?,?,1,0)",NAME,HASH,"回退合成管理员");
                long id=jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?",Long.class,NAME);
                jdbc.update("INSERT INTO sys_user_role(user_id,role_id) SELECT ?,id FROM sys_role WHERE role_code='PLATFORM_ADMIN' AND deleted=0",id);
            };
        }
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        String database=System.getenv("QUALITY_RETURN_TEST_DATABASE");
        if(database==null || !database.matches("dianxinyun_quality_return_[0-9]{14}")) throw new IllegalStateException("An isolated test database is required");
        registry.add("spring.datasource.url",()->"jdbc:mysql://127.0.0.1:3306/"+database+"?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true");
        registry.add("spring.data.redis.port",()->6380); registry.add("spring.data.redis.database",()->14);
        registry.add("file.upload.path",()->FILES.toString());
    }
    @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; @Autowired ObjectMapper json;
    @Autowired AuthService auth; @Autowired SysUserMapper users;
    @Autowired AdministrativeDeletionService deletion;
    long adminId,project,weekly,issue,photo,overview; String token;

    @BeforeAll void admin() throws Exception {
        assertThat(jdbc.queryForObject("SELECT DATABASE()",String.class)).isEqualTo(System.getenv("QUALITY_RETURN_TEST_DATABASE"));
        Files.createDirectories(FILES);
        adminId=jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?",Long.class,NAME);
        token=auth.issueToken(users.selectById(adminId));
    }
    @AfterAll void logout() { if(token!=null) auth.logoutSession(token); }
    @BeforeEach void fixture() throws Exception {
        String name="回退合成项目-"+UUID.randomUUID();
        jdbc.update("INSERT INTO project_info(project_name,deleted) VALUES(?,0)",name);
        project=jdbc.queryForObject("SELECT id FROM project_info WHERE project_name=?",Long.class,name);
        for(String module:BusinessModuleCodes.ALL) jdbc.update("INSERT INTO project_business_module(project_id,module_code,enabled,version,update_time) VALUES(?,?,1,1,NOW(6))",project,module);
        LocalDate today=LocalDate.now(ZoneId.of("Asia/Shanghai"));
        jdbc.update("INSERT INTO quality_weekly_inspection(project_id,inspection_no,week_start,inspection_date,status,conclusion,submitted_issue_count,submitted_by_id,submitted_by_name,submitted_time,created_by_id,created_by_name,last_edited_by_id,last_edited_by_name,version) VALUES(?,?,?,?,'SUBMITTED','原结论',1,?,'合成管理员',NOW(),?,'合成管理员',?,'合成管理员',1)",project,"QA-"+UUID.randomUUID(),today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),today,adminId,adminId,adminId);
        weekly=jdbc.queryForObject("SELECT id FROM quality_weekly_inspection WHERE project_id=?",Long.class,project);
        jdbc.update("INSERT INTO quality_issue(project_id,weekly_inspection_id,inspection_item_order,record_date,issue_no,title,description,location,severity,status,assignee_id,assignee_name,deadline,created_by_id,created_by_name,version,deleted) VALUES(?,?,1,?,?,'原问题','原说明','原位置','NORMAL','PENDING',?,'合成管理员',?,?,'合成管理员',0,0)",project,weekly,today,"QA-"+UUID.randomUUID(),adminId,today.plusDays(3),adminId);
        issue=jdbc.queryForObject("SELECT id FROM quality_issue WHERE weekly_inspection_id=?",Long.class,weekly);
        photo=file("QUALITY_ISSUE",issue); overview=file("QUALITY_WEEKLY_INSPECTION",weekly);
        jdbc.update("INSERT INTO quality_issue_log(issue_id,project_id,action_type,to_status,operator_id,operator_name,photo_file_ids) VALUES(?,?,'CREATE','PENDING',?,'合成管理员',?)",issue,project,adminId,String.valueOf(photo));
    }

    @Test void returnEditAndResubmitKeepsOriginalFilesAndOnlyCreatesNewActiveTodos() throws Exception {
        var draft=ok("POST",path(),body(1));
        assertThat(draft.path("status").asText()).isEqualTo("DRAFT");
        assertThat(draft.path("draftItems").size()).isEqualTo(1);
        assertThat(draft.path("draftItems").get(0).path("beforePhotoFileIds").get(0).asLong()).isEqualTo(photo);
        assertThat(draft.path("overviewPhotoFileIds").get(0).asLong()).isEqualTo(overview);
        assertThat(activeIssues()).isZero();
        assertThat(ok("GET","/quality/issues/todos?projectId="+project,null).size()).isZero();
        assertThat(raw("GET","/quality/issues/"+issue,null).getResponse().getStatus()).isEqualTo(404);
        assertThat(raw("POST",path(),body(1)).getResponse().getStatus()).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM quality_issue_log WHERE issue_id=?",Integer.class,issue)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT status FROM quality_issue WHERE id=?",String.class,issue)).isEqualTo("WITHDRAWN");
        Map<String,Object> item=json.convertValue(draft.path("draftItems").get(0),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});
        item.remove("id");
        item.put("title","修正后的问题");
        var saved=ok("PUT","/quality/weekly-inspections/"+weekly+"/draft",Map.of(
                "expectedVersion",2,"inspectionDate",draft.path("inspectionDate").asText(),
                "conclusion","补充后的结论","overviewPhotoFileIds",List.of(overview),"items",List.of(item)));
        assertThat(saved.path("version").asInt()).isEqualTo(3);
        var submitted=ok("POST","/quality/weekly-inspections/"+weekly+"/submit",Map.of("expectedVersion",3));
        assertThat(submitted.path("status").asText()).isEqualTo("SUBMITTED");
        assertThat(activeIssues()).isEqualTo(1);
        assertThat(submitted.path("issues").get(0).path("title").asText()).isEqualTo("修正后的问题");
        assertThat(submitted.path("issues").get(0).path("id").asLong()).isNotEqualTo(issue);
        assertThat(ok("GET","/quality/issues/todos?projectId="+project,null).size()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM file_resource WHERE project_id=? AND deleted=0",Integer.class,project)).isEqualTo(2);
        assertThat(Files.readAllBytes(FILES.resolve(jdbc.queryForObject("SELECT storage_key FROM file_resource WHERE id=?",String.class,photo)))).isEqualTo(PNG);
    }

    @Test void auditFailureRollsBackDraftIssueAndAttachmentOwnershipTogether() throws Exception {
        jdbc.execute("CREATE TRIGGER quality_return_audit_failure BEFORE INSERT ON sys_operation_log FOR EACH ROW BEGIN IF NEW.operation_type='QUALITY_WEEKLY_RETURN' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Synthetic return audit failure'; END IF; END");
        try { assertThat(raw("POST",path(),body(1)).getResponse().getStatus()).isEqualTo(500); }
        finally { jdbc.execute("DROP TRIGGER quality_return_audit_failure"); }
        assertThat(activeIssues()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM quality_weekly_inspection WHERE id=?",String.class,weekly)).isEqualTo("SUBMITTED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM quality_weekly_inspection_draft_item WHERE inspection_id=?",Integer.class,weekly)).isZero();
        assertThat(jdbc.queryForObject("SELECT business_type FROM file_resource WHERE id=?",String.class,photo)).isEqualTo("QUALITY_ISSUE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM quality_issue_log WHERE issue_id=?",Integer.class,issue)).isEqualTo(1);
    }

    @Test void rectifiedThenRejectedIssueCannotBeReturnedEvenThoughPendingAgain() throws Exception {
        long evidence=file("QUALITY_RECTIFICATION_PENDING",null);
        ok("POST","/quality/issues/"+issue+"/rectify",Map.of("description","已进行整改","photoFileIds",List.of(evidence)));
        ok("POST","/quality/issues/"+issue+"/review",Map.of("passed",false,"comment","需要补充整改","photoFileIds",List.of()));
        assertThat(raw("POST",path(),body(1)).getResponse().getStatus()).isEqualTo(409);
        assertThat(activeIssues()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM quality_weekly_inspection_draft_item WHERE inspection_id=?",Integer.class,weekly)).isZero();
    }

    @Test void nonAdministratorAndClosedModuleCannotBypassTheEndpoint() throws Exception {
        String name="qr-user-"+UUID.randomUUID();
        jdbc.update("INSERT INTO sys_user(username,password,real_name,status,deleted) VALUES(?,?,'合成普通用户',1,0)",name,HASH);
        long user=jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?",Long.class,name);
        String ordinary=auth.issueToken(users.selectById(user));
        try { assertThat(raw("POST",path(),body(1),ordinary).getResponse().getStatus()).isEqualTo(403); }
        finally { auth.logoutSession(ordinary); }
        jdbc.update("UPDATE project_business_module SET enabled=0,version=version+1 WHERE project_id=? AND module_code='QUALITY'",project);
        assertThat(raw("POST",path(),body(1)).getResponse().getStatus()).isEqualTo(403);
        assertThat(activeIssues()).isEqualTo(1);
    }

    @Test void competingRectificationAndReturnCannotBothCommit() throws Exception {
        long evidence=file("QUALITY_RECTIFICATION_PENDING",null);
        var start=new CountDownLatch(1); var pool=Executors.newFixedThreadPool(2);
        try {
            Future<Integer> returned=pool.submit(()->{start.await();return raw("POST",path(),body(1)).getResponse().getStatus();});
            Future<Integer> rectified=pool.submit(()->{start.await();return raw("POST","/quality/issues/"+issue+"/rectify",Map.of("description","并发整改","photoFileIds",List.of(evidence))).getResponse().getStatus();});
            start.countDown();
            List<Integer> states=List.of(returned.get(30,TimeUnit.SECONDS),rectified.get(30,TimeUnit.SECONDS));
            assertThat(states.stream().filter(s->s==200).count()).isEqualTo(1);
            assertThat(states).allMatch(s->s==200||s==409||s==404);
            String state=jdbc.queryForObject("SELECT status FROM quality_weekly_inspection WHERE id=?",String.class,weekly);
            assertThat(activeIssues()).isEqualTo("DRAFT".equals(state)?0:1);
        } finally {pool.shutdownNow();}
    }

    @Test void permanentlyDeletedOnlyIssueCanReopenAndAcceptANewIssueWithoutRestoringDeletedFiles() throws Exception {
        deleteIssue(issue);
        assertThat(Files.exists(FILES.resolve(deletedPhotoKey))).isFalse();
        var draft=ok("POST",path(),body(1));
        assertThat(draft.path("draftItems").size()).isZero();
        assertThat(draft.path("conclusion").asText()).isEqualTo("原结论");
        assertThat(draft.path("overviewPhotoFileIds").get(0).asLong()).isEqualTo(overview);
        assertThat(returnAudit()).contains("原提交1个问题，恢复0个现存问题，1个已删除问题不恢复");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM quality_issue WHERE id=?",Integer.class,issue)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_operation_log WHERE business_type='QUALITY_ISSUE' AND business_id=? AND operation_type='ADMIN_FORCE_DELETE'",Integer.class,issue)).isEqualTo(1);
        long replacement=file("QUALITY_WEEKLY_ITEM_PENDING",null);
        var saved=ok("PUT","/quality/weekly-inspections/"+weekly+"/draft",Map.of(
                "expectedVersion",2,"inspectionDate",draft.path("inspectionDate").asText(),
                "conclusion","继续本周巡检","overviewPhotoFileIds",List.of(overview),"items",List.of(Map.of(
                    "itemKey","new-after-deletion","title","新发现的问题","description","新说明",
                    "severity","NORMAL","assigneeId",adminId,"deadline",LocalDate.now().plusDays(3).toString(),
                    "beforePhotoFileIds",List.of(replacement)))));
        var submitted=ok("POST","/quality/weekly-inspections/"+weekly+"/submit",Map.of("expectedVersion",saved.path("version").asInt()));
        assertThat(submitted.path("issues").size()).isEqualTo(1);
        assertThat(submitted.path("issues").get(0).path("id").asLong()).isNotEqualTo(issue);
        assertThat(Files.exists(FILES.resolve(deletedPhotoKey))).isFalse();
        assertThat(Files.readAllBytes(FILES.resolve(jdbc.queryForObject("SELECT storage_key FROM file_resource WHERE id=?",String.class,overview)))).isEqualTo(PNG);
        assertThat(raw("POST",path(),body(1)).getResponse().getStatus()).isEqualTo(409);
    }

    @Test void partialDeletionRestoresOnlySurvivingIssueAndItsPhoto() throws Exception {
        long removed=extraIssue();
        deleteIssue(removed);
        var draft=ok("POST",path(),body(1));
        assertThat(draft.path("draftItems").size()).isEqualTo(1);
        assertThat(draft.path("draftItems").get(0).path("beforePhotoFileIds").get(0).asLong()).isEqualTo(photo);
        assertThat(returnAudit()).contains("原提交2个问题，恢复1个现存问题，1个已删除问题不恢复");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM quality_issue WHERE id=?",Integer.class,removed)).isZero();
    }

    @Test void partialDeletionStillRejectsAProcessedSurvivingIssue() throws Exception {
        deleteIssue(extraIssue());
        long evidence=file("QUALITY_RECTIFICATION_PENDING",null);
        ok("POST","/quality/issues/"+issue+"/rectify",Map.of("description","已整改","photoFileIds",List.of(evidence)));
        assertThat(raw("POST",path(),body(1)).getResponse().getStatus()).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT status FROM quality_weekly_inspection WHERE id=?",String.class,weekly)).isEqualTo("SUBMITTED");
    }

    @Test void allDeletedReturnStillRollsBackWhenAuditFails() throws Exception {
        deleteIssue(issue);
        jdbc.execute("CREATE TRIGGER quality_return_audit_failure BEFORE INSERT ON sys_operation_log FOR EACH ROW BEGIN IF NEW.operation_type='QUALITY_WEEKLY_RETURN' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Synthetic return audit failure'; END IF; END");
        try { assertThat(raw("POST",path(),body(1)).getResponse().getStatus()).isEqualTo(500); }
        finally { jdbc.execute("DROP TRIGGER quality_return_audit_failure"); }
        assertThat(jdbc.queryForObject("SELECT status FROM quality_weekly_inspection WHERE id=?",String.class,weekly)).isEqualTo("SUBMITTED");
        assertThat(jdbc.queryForObject("SELECT business_type FROM file_resource WHERE id=?",String.class,overview)).isEqualTo("QUALITY_WEEKLY_INSPECTION");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM quality_issue WHERE id=?",Integer.class,issue)).isZero();
    }

    String deletedPhotoKey;
    void deleteIssue(long id) {
        if(id==issue) deletedPhotoKey=jdbc.queryForObject("SELECT storage_key FROM file_resource WHERE id=?",String.class,photo);
        var preview=new AdministrativeDeletionPreviewRequest(); preview.setTargetType("QUALITY_ISSUE"); preview.setTargetId(id);
        var operator=users.selectById(adminId);
        var impact=deletion.preview(preview,operator);
        var confirm=new AdministrativeDeletionExecuteRequest(); confirm.setTargetType("QUALITY_ISSUE"); confirm.setTargetId(id);
        confirm.setAcknowledged(true); confirm.setConfirmationToken(impact.getConfirmationToken());
        deletion.execute(confirm,operator);
    }
    long extraIssue() {
        String no="QA-"+UUID.randomUUID();
        jdbc.update("INSERT INTO quality_issue(project_id,weekly_inspection_id,inspection_item_order,record_date,issue_no,title,severity,status,assignee_id,deadline,created_by_id,version,deleted) SELECT project_id,weekly_inspection_id,2,record_date,?,'将删除的问题',severity,status,assignee_id,deadline,created_by_id,0,0 FROM quality_issue WHERE id=?",no,issue);
        jdbc.update("UPDATE quality_weekly_inspection SET submitted_issue_count=2 WHERE id=?",weekly);
        return jdbc.queryForObject("SELECT id FROM quality_issue WHERE issue_no=?",Long.class,no);
    }
    String returnAudit(){return jdbc.queryForObject("SELECT operation_desc FROM sys_operation_log WHERE business_type='QUALITY_WEEKLY_INSPECTION' AND business_id=? AND operation_type='QUALITY_WEEKLY_RETURN'",String.class,weekly);}
    String path(){return "/quality/weekly-inspections/"+weekly+"/return-to-draft";}
    Map<String,Object> body(int version){return Map.of("expectedVersion",version,"reason","误点提交，需要继续整理");}
    int activeIssues(){return jdbc.queryForObject("SELECT COUNT(*) FROM quality_issue WHERE weekly_inspection_id=? AND deleted=0",Integer.class,weekly);}
    long file(String type,Long business)throws Exception{String name=UUID.randomUUID()+".png";Files.write(FILES.resolve(name),PNG);jdbc.update("INSERT INTO file_resource(project_id,file_name,file_path,storage_provider,storage_key,original_file_name,mime_type,file_extension,file_size,business_type,business_id,uploader_id,status,deleted) VALUES(?,?,?,'local',?,?,'image/png','png',?,?,?,?,'UPLOADED',0)",project,name,name,name,name,PNG.length,type,business,adminId);return jdbc.queryForObject("SELECT id FROM file_resource WHERE storage_key=?",Long.class,name);}
    MvcResult raw(String method,String path,Object body)throws Exception{return raw(method,path,body,token);}
    MvcResult raw(String method,String path,Object body,String session)throws Exception{var builder=request(org.springframework.http.HttpMethod.valueOf(method),java.net.URI.create("/api/v1"+path));builder.header("Authorization","Bearer "+session);if(body!=null)builder.contentType("application/json").content(json.writeValueAsBytes(body));return mvc.perform(builder).andReturn();}
    JsonNode ok(String method,String path,Object body)throws Exception{var result=raw(method,path,body);assertThat(result.getResponse().getStatus()).as(method+" "+path+" "+result.getResponse().getContentAsString()).isEqualTo(200);return json.readTree(result.getResponse().getContentAsByteArray()).path("data");}
}
