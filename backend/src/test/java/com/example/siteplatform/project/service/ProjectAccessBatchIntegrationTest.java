package com.example.siteplatform.project.service;

import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.system.constant.BusinessModuleCodes;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/** Only synthetic accounts in an explicitly named empty test database. */
@EnabledIfEnvironmentVariable(named="PROJECT_ACCESS_BATCH_INTEGRATION", matches="ISOLATED_EMPTY_ONLY")
@SpringBootTest(properties={"spring.profiles.active=local", "spring.main.banner-mode=off", "logging.level.root=ERROR"})
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(ProjectAccessBatchIntegrationTest.SyntheticAdministrator.class)
class ProjectAccessBatchIntegrationTest {
    private static final String ADMIN_NAME="access-admin-"+UUID.randomUUID();
    private static final String TEST_HASH=new BCryptPasswordEncoder().encode(UUID.randomUUID().toString());

    @TestConfiguration
    static class SyntheticAdministrator {
        @Bean @Order(Ordered.HIGHEST_PRECEDENCE)
        ApplicationRunner createSyntheticAdministrator(JdbcTemplate jdbc) {
            return args -> {
                String database=jdbc.queryForObject("SELECT DATABASE()",String.class);
                if(database==null || !database.matches("dianxinyun_access_verify_[0-9]{14}")
                        || !database.equals(System.getenv("PROJECT_ACCESS_BATCH_TEST_DATABASE"))) {
                    throw new IllegalStateException("Only the explicitly selected test database can receive fixtures");
                }
                jdbc.update("INSERT INTO sys_user(username,password,real_name,status,deleted) VALUES(?,?,?,1,0)",ADMIN_NAME,TEST_HASH,"合成测试管理员");
                long id=jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?",Long.class,ADMIN_NAME);
                jdbc.update("INSERT INTO sys_user_role(user_id,role_id) SELECT ?,id FROM sys_role WHERE role_code='PLATFORM_ADMIN' AND deleted=0",id);
            };
        }
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        String database=System.getenv("PROJECT_ACCESS_BATCH_TEST_DATABASE");
        if(database==null || !database.matches("dianxinyun_access_verify_[0-9]{14}")) throw new IllegalStateException("An isolated empty database is required");
        registry.add("spring.datasource.url",()->"jdbc:mysql://127.0.0.1:3306/"+database+"?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true");
        registry.add("spring.data.redis.port",()->6380); registry.add("spring.data.redis.database",()->14);
        registry.add("file.upload.path",()->System.getProperty("java.io.tmpdir")+"/dxy-access-batch-test-files");
    }
    @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; @Autowired ObjectMapper json;
    @Autowired AuthService auth; @Autowired SysUserMapper users;
    long adminId, source, target, first, second, roleA, roleB, roleC;
    String admin;
    final List<String> sessions=new ArrayList<>();

    @BeforeAll void administrator() {
        assertThat(jdbc.queryForObject("SELECT DATABASE()",String.class)).isEqualTo(System.getenv("PROJECT_ACCESS_BATCH_TEST_DATABASE"));
        adminId=jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?",Long.class,ADMIN_NAME);
        admin=token(adminId);
    }
    @BeforeEach void fixture() {
        source=createProject();target=createProject();first=createUser();second=createUser();
        roleA=createRole();roleB=createRole();roleC=createRole();
        member(first,source,"ACTIVE",roleA);member(second,source,"ACTIVE",roleA);
    }
    @AfterAll void sessions() { sessions.forEach(auth::logoutSession); }

    @Test void bulkAdditionPreservesExistingRolesSealAssignmentsAndRevokesOldSessions() throws Exception {
        long config=sealConfiguration(first);
        String oldSession=token(first);
        var preview=call("POST","/system/users/project-role-assignments/batch/preview",change("ADD_ROLES",List.of(first,second)),admin);
        assertThat(preview.path("responsibilityCount").asInt()).isZero();
        call("POST","/system/users/project-role-assignments/batch/confirm",confirmation(preview,false),admin);
        assertThat(grants(first,source)).containsExactlyInAnyOrder(roleA,roleB);
        assertThat(grants(second,source)).containsExactlyInAnyOrder(roleA,roleB);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM workflow_approval_config_user WHERE config_id=? AND user_id=?",Long.class,config,first)).isEqualTo(1);
        assertThat(status("GET","/auth/user-info",null,oldSession)).isEqualTo(401);
    }

    @Test void migrationRequiresConsentThenMovesWholeBatchAndRetainsTargetRoles() throws Exception {
        sealConfiguration(first);member(first,target,"ACTIVE",roleC);
        var preview=preview("MOVE_PROJECT",List.of(first,second));
        assertThat(preview.path("responsibilityCount").asInt()).isEqualTo(1);
        assertThat(status("POST","/system/users/project-role-assignments/batch/confirm",confirmation(preview,false),admin)).isEqualTo(409);
        assertThat(grants(first,source)).containsExactly(roleA);
        call("POST","/system/users/project-role-assignments/batch/confirm",confirmation(preview,true),admin);
        assertThat(grants(first,source)).isEmpty();assertThat(grants(second,source)).isEmpty();
        assertThat(grants(first,target)).containsExactlyInAnyOrder(roleA,roleC);assertThat(grants(second,target)).containsExactly(roleA);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM workflow_approval_config_user WHERE project_id=? AND user_id=?",Long.class,source,first)).isZero();
        assertThat(status("POST","/system/users/project-role-assignments/batch/confirm",confirmation(preview,true),admin)).isEqualTo(409);
    }

    @Test void auditFailureRollsBackMembershipRolesAndResponsibilityRelease() throws Exception {
        long config=sealConfiguration(first);
        var preview=preview("MOVE_PROJECT",List.of(first,second));
        jdbc.execute("CREATE TRIGGER access_batch_test_audit_failure BEFORE INSERT ON sys_operation_log FOR EACH ROW BEGIN IF NEW.operation_type='BATCH_PROJECT_ACCESS' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Synthetic audit failure'; END IF; END");
        try {
            var failed=raw("POST","/system/users/project-role-assignments/batch/confirm",confirmation(preview,true),admin);
            assertThat(failed.getResponse().getStatus()).isEqualTo(500);
            assertThat(failed.getResolvedException()).hasStackTraceContaining("Synthetic audit failure");
        }
        finally { jdbc.execute("DROP TRIGGER access_batch_test_audit_failure"); }
        assertThat(grants(first,source)).containsExactly(roleA);assertThat(grants(second,source)).containsExactly(roleA);
        assertThat(grants(first,target)).isEmpty();assertThat(grants(second,target)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_project WHERE project_id=?",Long.class,target)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM workflow_approval_config_user WHERE config_id=?",Long.class,config)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT config_version FROM workflow_approval_config WHERE id=?",Integer.class,config)).isEqualTo(1);
    }

    @Test void changedStateAndLastRoleBlockWithoutPartiallyUpdatingOtherUsers() throws Exception {
        var remove=change("REMOVE_ROLES",List.of(first,second)); remove.put("roleIds",List.of(roleA));
        var invalid=call("POST","/system/users/project-role-assignments/batch/preview",remove,admin);
        assertThat(invalid.path("blockedUserCount").asInt()).isEqualTo(2);assertThat(invalid.path("confirmationToken").isNull()).isTrue();
        var pending=preview("ADD_ROLES",List.of(first,second));
        jdbc.update("UPDATE sys_user_project SET status='DISABLED' WHERE project_id=? AND user_id=?",source,second);
        assertThat(status("POST","/system/users/project-role-assignments/batch/confirm",confirmation(pending,false),admin)).isEqualTo(409);
        assertThat(grants(first,source)).containsExactly(roleA);
        assertThat(grants(second,source)).containsExactly(roleA);
    }

    @Test void copyPreservesPausedSourceAndTargetAccess() throws Exception {
        jdbc.update("UPDATE sys_user_project SET status='DISABLED' WHERE project_id=? AND user_id=?",source,first);
        member(second,target,"DISABLED",roleC);
        var preview=preview("COPY_PROJECT",List.of(first,second));
        call("POST","/system/users/project-role-assignments/batch/confirm",confirmation(preview,false),admin);
        for(long id:List.of(first,second)) assertThat(jdbc.queryForObject("SELECT status FROM sys_user_project WHERE project_id=? AND user_id=?",String.class,target,id)).isEqualTo("DISABLED");
        assertThat(grants(first,source)).containsExactly(roleA);assertThat(grants(second,target)).containsExactlyInAnyOrder(roleA,roleC);
    }

    @Test void losingQualityViewRequiresAcknowledgementAndReleasesOnlyQualityResponsibility() throws Exception {
        for(long role:List.of(roleA,roleB)) jdbc.update("INSERT INTO sys_role_business_module(role_id,module_code) VALUES(?,'QUALITY')",role);
        for(String code:List.of("quality.view","quality.rectify")) {
            jdbc.update("INSERT IGNORE INTO sys_permission(permission_code,permission_name,module_code,enabled,deleted) VALUES(?,?,'QUALITY',1,0)",code,code);
            jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) SELECT ?,id FROM sys_permission WHERE permission_code=? AND deleted=0",code.equals("quality.view")?roleA:roleB,code);
        }
        jdbc.update("INSERT INTO sys_user_project_role(user_id,project_id,role_id) VALUES(?,?,?)",first,source,roleB);
        String issue="TEST-"+UUID.randomUUID().toString().substring(0,24);
        jdbc.update("INSERT INTO quality_issue(project_id,record_date,issue_no,title,assignee_id,created_by_id) VALUES(?,CURDATE(),?,'合成待整改问题',?,?)",source,issue,first,adminId);
        long config=sealConfiguration(first);
        var input=change("REMOVE_ROLES",List.of(first));input.put("roleIds",List.of(roleA));
        var preview=call("POST","/system/users/project-role-assignments/batch/preview",input,admin);
        assertThat(preview.path("responsibilityCount").asInt()).isEqualTo(1);
        assertThat(status("POST","/system/users/project-role-assignments/batch/confirm",confirmation(preview,false),admin)).isEqualTo(409);
        call("POST","/system/users/project-role-assignments/batch/confirm",confirmation(preview,true),admin);
        assertThat(grants(first,source)).containsExactly(roleB);
        assertThat(jdbc.queryForObject("SELECT assignee_id FROM quality_issue WHERE issue_no=?",Long.class,issue)).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM workflow_approval_config_user WHERE config_id=? AND user_id=?",Long.class,config,first)).isEqualTo(1);
    }

    @Test void concurrentConfirmationCanOnlyCommitOnce() throws Exception {
        var preview=preview("ADD_ROLES",List.of(first,second));var body=confirmation(preview,false);
        var gate=new CountDownLatch(1);var executor=Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> attempt=()->{gate.await();return status("POST","/system/users/project-role-assignments/batch/confirm",body,admin);};
            var a=executor.submit(attempt);var b=executor.submit(attempt);gate.countDown();
            assertThat(List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
        } finally {executor.shutdownNow();}
        assertThat(grants(first,source)).containsExactlyInAnyOrder(roleA,roleB);
    }

    @Test void roleAndAccessFiltersUseTheSameProjectAndNonAdminCannotPreview() throws Exception {
        member(first,target,"ACTIVE",roleB);
        String base="/system/users?projectId="+source+"&roleId="+roleB;
        assertThat(call("GET",base,null,admin).path("total").asInt()).isZero();
        assertThat(call("GET","/system/users?projectId="+target+"&roleId="+roleB+"&accessStatus=ACTIVE",null,admin).path("total").asInt()).isEqualTo(1);
        assertThat(status("GET","/system/users?roleId="+roleB,null,admin)).isEqualTo(400);
        assertThat(status("POST","/system/users/project-role-assignments/batch/preview",change("ADD_ROLES",List.of(second)),token(first))).isEqualTo(403);
    }

    @Test void twoHundredPeopleAreSavedInOneBatch() throws Exception {
        List<Long> selected=new ArrayList<>();
        for(int i=0;i<200;i++){long id=createUser();member(id,source,"ACTIVE",roleA);selected.add(id);}
        var preview=preview("ADD_ROLES",selected);
        assertThat(preview.path("changedUserCount").asInt()).isEqualTo(200);
        var result=call("POST","/system/users/project-role-assignments/batch/confirm",confirmation(preview,false),admin);
        assertThat(result.path("changedUserCount").asInt()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_project_role WHERE project_id=? AND role_id=?",Long.class,source,roleB)).isEqualTo(200);
    }

    Map<String,Object> change(String op,List<Long> ids) {var body=new LinkedHashMap<String,Object>();body.put("operation",op);body.put("userIds",ids);body.put("roleIds",List.of(roleB));if(op.endsWith("PROJECT")){body.put("sourceProjectId",source);body.put("targetProjectId",target);body.put("roleSource","SOURCE");}else body.put("projectId",source);return body;}
    Map<String,Object> confirmation(JsonNode preview,boolean acknowledge){return Map.of("confirmationToken",preview.path("confirmationToken").asText(),"confirmResponsibilityRelease",acknowledge);}
    JsonNode preview(String op,List<Long> ids)throws Exception{return call("POST","/system/users/project-role-assignments/batch/preview",change(op,ids),admin);}
    List<Long> grants(long user,long project){return jdbc.queryForList("SELECT role_id FROM sys_user_project_role WHERE user_id=? AND project_id=? ORDER BY role_id",Long.class,user,project);}
    void member(long user,long project,String status,long role){jdbc.update("INSERT INTO sys_user_project(user_id,project_id,status) VALUES(?,?,?)",user,project,status);jdbc.update("INSERT INTO sys_user_project_role(user_id,project_id,role_id) VALUES(?,?,?)",user,project,role);}
    long createUser(){String name="access-test-"+UUID.randomUUID();jdbc.update("INSERT INTO sys_user(username,password,real_name,status,deleted) VALUES(?,?,?,1,0)",name,TEST_HASH,"批量授权测试");return jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?",Long.class,name);}
    long createProject(){String name="批量授权合成项目-"+UUID.randomUUID();jdbc.update("INSERT INTO project_info(project_name,deleted) VALUES(?,0)",name);long id=jdbc.queryForObject("SELECT id FROM project_info WHERE project_name=?",Long.class,name);for(String module:BusinessModuleCodes.ALL)jdbc.update("INSERT INTO project_business_module(project_id,module_code,enabled,version,update_time) VALUES(?,?,1,1,NOW(6))",id,module);return id;}
    long createRole(){String name="ACCESS_TEST_"+UUID.randomUUID().toString().substring(0,16);jdbc.update("INSERT INTO sys_role(role_name,role_code,scope_type,enabled,deleted) VALUES(?,?,'PROJECT',1,0)",name,name);return jdbc.queryForObject("SELECT id FROM sys_role WHERE role_code=?",Long.class,name);}
    long sealConfiguration(long user){jdbc.update("INSERT INTO workflow_approval_config(business_code,project_id,seal_id,created_by,updated_by) VALUES('SEAL_APPLICATION',?,?,?,?)",source,source,adminId,adminId);long id=jdbc.queryForObject("SELECT id FROM workflow_approval_config WHERE project_id=?",Long.class,source);jdbc.update("INSERT INTO workflow_approval_config_user(config_id,project_id,user_id,assignment_type) VALUES(?,?,?,'APPROVER')",id,source,user);return id;}
    String token(long id){String token=auth.issueToken(users.selectById(id));sessions.add(token);return token;}
    MvcResult raw(String method,String path,Object body,String token)throws Exception{var builder=request(org.springframework.http.HttpMethod.valueOf(method),java.net.URI.create("/api/v1"+path));builder.header("Authorization","Bearer "+token);if(body!=null)builder.contentType("application/json").content(json.writeValueAsBytes(body));return mvc.perform(builder).andReturn();}
    int status(String method,String path,Object body,String token)throws Exception{return raw(method,path,body,token).getResponse().getStatus();}
    JsonNode call(String method,String path,Object body,String token)throws Exception{var result=raw(method,path,body,token);assertThat(result.getResponse().getStatus()).as(method+" "+path+" "+result.getResponse().getContentAsString()).isEqualTo(200);return json.readTree(result.getResponse().getContentAsByteArray()).path("data");}
}
