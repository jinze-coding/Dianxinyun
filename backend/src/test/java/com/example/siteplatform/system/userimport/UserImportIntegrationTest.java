package com.example.siteplatform.system.userimport;

import com.example.siteplatform.system.userimport.mapper.UserImportBatchMapper;
import com.example.siteplatform.system.userimport.mapper.UserImportItemMapper;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.project.mapper.ProjectInfoMapper;
import com.example.siteplatform.system.mapper.SystemRoleMapper;
import com.fasterxml.jackson.databind.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

/** Real transaction/auth tests. Explicit isolated-database guard runs before Spring initialization. */
@EnabledIfEnvironmentVariable(named="USER_IMPORT_INTEGRATION", matches="ISOLATED_CLONE_ONLY")
@SpringBootTest(properties={"spring.profiles.active=local", "spring.main.banner-mode=off", "user-import.worker-enabled=false"})
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserImportIntegrationTest {
    static final Path ROOT;
    static { try { ROOT = Files.createTempDirectory("user-import-integration-"); } catch (IOException e) { throw new IllegalStateException(e); } }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        String database = System.getenv("USER_IMPORT_TEST_DATABASE");
        if (database == null || !database.matches("dianxinyun_import_verify_[0-9]{14}")) throw new IllegalStateException("Only an isolated import verification clone is allowed");
        registry.add("spring.datasource.url", () -> "jdbc:mysql://127.0.0.1:3306/" + database + "?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true");
        registry.add("spring.data.redis.port", () -> 6380); registry.add("spring.data.redis.database", () -> 15);
        registry.add("file.upload.path", ROOT::toString);
        byte[] key = new byte[32]; new java.security.SecureRandom().nextBytes(key);
        registry.add("user-import.credential-key", () -> Base64.getEncoder().encodeToString(key));
    }
    @Autowired UserImportService service; @Autowired UserImportItemMapper items; @Autowired UserImportBatchMapper batches;
    @Autowired SysUserMapper users; @Autowired ProjectInfoMapper projects; @Autowired SystemRoleMapper roles;
    @Autowired AuthService auth; @Autowired JdbcTemplate jdbc; @Autowired MockMvc mvc; @Autowired ObjectMapper json;
    @Autowired com.example.siteplatform.registration.service.RegistrationApplicationService registrationService;
    final String chosenPassword = "Batch7" + UUID.randomUUID();
    final String resetPassword = "Reset8" + UUID.randomUUID();
    SysUser admin, otherAdmin, ordinary; long a, b, role, managerRole;
    String adminToken, otherToken, ordinaryToken;
    final List<String> tokens = new ArrayList<>(); final AtomicInteger phones = new AtomicInteger(810000);
    @BeforeAll void setup() throws Exception {
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo(System.getenv("USER_IMPORT_TEST_DATABASE"));
        long adminId = jdbc.queryForObject("SELECT MIN(u.id) FROM sys_user u JOIN sys_user_role ur ON ur.user_id=u.id JOIN sys_role r ON r.id=ur.role_id WHERE u.deleted=0 AND u.status=1 AND r.role_code='PLATFORM_ADMIN'", Long.class);
        admin = users.selectById(adminId); adminToken = token(adminId);
        a = call("POST", "/projects", Map.of("projectName", "合成导入项目A"), adminToken).path("id").asLong();
        b = call("POST", "/projects", Map.of("projectName", "合成导入项目B"), adminToken).path("id").asLong();
        String code = "IMPORT_TEST_" + UUID.randomUUID().toString().substring(0, 12);
        jdbc.update("INSERT INTO sys_role(role_name,role_code,scope_type,enabled,deleted) VALUES(?,?,'PROJECT',1,0)", code, code);
        role = jdbc.queryForObject("SELECT id FROM sys_role WHERE role_code=?", Long.class, code);
        String managerCode = "IMPORT_MANAGER_" + UUID.randomUUID().toString().substring(0, 12);
        jdbc.update("INSERT INTO sys_role(role_name,role_code,scope_type,project_manager_role,enabled,deleted) VALUES(?,?,'PROJECT',1,1,0)", managerCode, managerCode);
        managerRole = jdbc.queryForObject("SELECT id FROM sys_role WHERE role_code=?", Long.class, managerCode);
        ordinary = createOrdinary(); otherAdmin = createOrdinary();
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) SELECT ?,id FROM sys_role WHERE role_code='PLATFORM_ADMIN' AND deleted=0", otherAdmin.getId());
        ordinaryToken = token(ordinary.getId()); otherToken = token(otherAdmin.getId());
    }
    @AfterAll void cleanup() { tokens.forEach(auth::logoutSession); }
    SysUser createOrdinary() {
        String name = "import-test-" + UUID.randomUUID().toString().substring(0, 16);
        jdbc.update("INSERT INTO sys_user(username,password,real_name,status,deleted) VALUES(?,?,?,1,0)", name, auth.hashPassword("Initial7" + UUID.randomUUID()), "合成测试人员");
        return users.selectById(jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?", Long.class, name));
    }
    String phone() { return String.format("199%08d", phones.incrementAndGet()); }
    String token(long id) { String token = auth.issueToken(users.selectById(id)); tokens.add(token); return token; }
    String[] row(String phone, long project) { return new String[]{"合成人员", phone, UserImportWorkbook.projectLabel(projects.selectById(project)), UserImportWorkbook.roleLabel(roles.selectById(role))}; }
    MockMultipartFile file(List<String[]> rows) throws Exception {
        try (XSSFWorkbook book = new XSSFWorkbook()) {
            var sheet = book.createSheet("用户名单"); var header = sheet.createRow(0); String[] headings = {"姓名", "手机号", "所属项目", "项目角色"};
            for (int i = 0; i < 4; i++) header.createCell(i).setCellValue(headings[i]);
            for (int i = 0; i < rows.size(); i++) { var r = sheet.createRow(i + 1); for (int c = 0; c < 4; c++) r.createCell(c).setCellValue(rows.get(i)[c]); }
            ByteArrayOutputStream out = new ByteArrayOutputStream(); book.write(out);
            return new MockMultipartFile("file", "users.xlsx", "application/octet-stream", out.toByteArray());
        }
    }
    long preview(List<String[]> rows) throws Exception { return ((Number)service.preview(file(rows), admin).get("id")).longValue(); }
    long importOne(String phone) throws Exception { long id = preview(Collections.singletonList(row(phone, a))); service.confirm(id, UUID.randomUUID().toString(), chosenPassword, admin); service.processNext(); assertThat(batches.selectById(id).getStatus()).isEqualTo("SUCCEEDED"); return id; }
    long userId(String phone) { return jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?", Long.class, phone); }
    String temporary(long batch) throws Exception {
        try (XSSFWorkbook book = new XSSFWorkbook(new ByteArrayInputStream(service.credentials(batch, admin)))) { return book.getSheetAt(0).getRow(1).getCell(2).getStringCellValue(); }
    }
    MvcResult raw(String method, String path, Object body, String token) throws Exception {
        var request = MockMvcRequestBuilders.request(org.springframework.http.HttpMethod.valueOf(method), java.net.URI.create("/api/v1" + path));
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body != null) request.contentType("application/json").content(json.writeValueAsBytes(body));
        return mvc.perform(request).andReturn();
    }
    JsonNode call(String method, String path, Object body, String token) throws Exception {
        var result = raw(method, path, body, token); assertThat(result.getResponse().getStatus()).as(method + " " + path).isEqualTo(200);
        return json.readTree(result.getResponse().getContentAsByteArray()).path("data");
    }
    long accounts(String phone) { return jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE username=?", Long.class, phone); }

    @Test @Order(1) void administratorOnlyAndMinimalUnchangedExistingAccount() throws Exception {
        assertThat(raw("GET", "/system/user-imports/template", null, ordinaryToken).getResponse().getStatus()).isEqualTo(403);
        assertThatThrownBy(() -> service.preview(file(Collections.singletonList(row(phone(), a))), ordinary)).isInstanceOf(BusinessException.class);
        String phone = phone(); jdbc.update("UPDATE sys_user SET phone=? WHERE id=?", phone, ordinary.getId());
        String before = users.selectById(ordinary.getId()).getPassword();
        long batch = preview(Collections.singletonList(row(phone, a)));
        assertThat(batches.selectById(batch).getSkippedCount()).isEqualTo(1);
        assertThatThrownBy(() -> service.confirm(batch, UUID.randomUUID().toString(), chosenPassword, admin)).hasMessageContaining("没有需要新增");
        assertThat(users.selectById(ordinary.getId()).getPassword()).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_project WHERE user_id=?", Long.class, ordinary.getId())).isZero();
    }
    @Test @Order(2) void errorRowsPreventWholeBatchAndPendingApplicationsAreNotApproved() throws Exception {
        String good = phone(), invalid = phone(); String[] bad = row(invalid, a); bad[0] = "";
        long batch = preview(List.of(row(good, a), bad));
        assertThat(batches.selectById(batch).getStatus()).isEqualTo("INVALID");
        assertThatThrownBy(() -> service.confirm(batch, UUID.randomUUID().toString(), chosenPassword, admin)).isInstanceOf(BusinessException.class);
        assertThat(accounts(good)).isZero(); assertThat(accounts(invalid)).isZero();
        String pending = phone();
        jdbc.update("INSERT INTO registration_application(username,phone,real_name,status,source_type,phone_verification_type,status_token_hash) VALUES(?,?,?,'PENDING','WEB','MANUAL_REVIEW',?)", pending, pending, "合成待审人员", UUID.randomUUID().toString());
        long pendingBatch = preview(Collections.singletonList(row(pending, a)));
        assertThat(batches.selectById(pendingBatch).getStatus()).isEqualTo("INVALID");
        assertThat(jdbc.queryForObject("SELECT status FROM registration_application WHERE username=?", String.class, pending)).isEqualTo("PENDING");
    }
    @Test @Order(3) void mergeProjectsDeduplicateRolesAndConfirmIdempotently() throws Exception {
        String phone = phone();
        String[] managerAssignment = row(phone, a); managerAssignment[3] = UserImportWorkbook.roleLabel(roles.selectById(managerRole));
        long batch = preview(List.of(row(phone, a), row(phone, a), row(phone, b), managerAssignment)); String key = UUID.randomUUID().toString();
        assertThat(items.forBatch(batch)).extracting(UserImportItem::getRowNumber).containsExactly(2, 3, 4, 5);
        service.confirm(batch, key, chosenPassword, admin); service.confirm(batch, key, chosenPassword, admin);
        assertThatThrownBy(() -> service.confirm(batch, UUID.randomUUID().toString(), chosenPassword, admin)).isInstanceOf(BusinessException.class);
        service.processNext(); service.processNext(); service.confirm(batch, key, chosenPassword, admin);
        assertThat(accounts(phone)).isEqualTo(1);
        long id = userId(phone);
        assertThat(items.forUser(id).getRowNumber()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_project WHERE user_id=?", Long.class, id)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_project_role WHERE user_id=?", Long.class, id)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_project_role WHERE user_id=? AND project_id=? AND role_id=?", Long.class, id, a, managerRole)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_role WHERE user_id=?", Long.class, id)).isZero();
        assertThat(temporary(batch)).isEqualTo(chosenPassword);
        assertThat(batches.selectById(batch).getTemporaryPasswordCipher()).isNull();
        assertThat(users.selectById(id).getPassword()).startsWith("$2a$12$");
        assertThatThrownBy(() -> service.credentials(batch, otherAdmin)).hasMessageContaining("原导入管理员");
    }
    @Test @Order(4) void initialLoginIsRestrictedAndChangeClearsHandoutAndInvalidatesOldSession() throws Exception {
        String phone = phone(); long batch = importOne(phone); String password = temporary(batch);
        String restricted = call("POST", "/auth/login", Map.of("username", phone, "password", password), null).path("token").asText(); tokens.add(restricted);
        JsonNode info = call("GET", "/auth/user-info", null, restricted);
        assertThat(info.path("initialPasswordSetupRequired").asBoolean()).isTrue(); assertThat(info.path("menus").size()).isZero(); assertThat(info.path("projectContexts").size()).isZero();
        assertThat(raw("GET", "/projects", null, restricted).getResponse().getStatus()).isEqualTo(403);
        assertThat(raw("GET", "/system/user-imports", null, restricted).getResponse().getStatus()).isEqualTo(403);
        assertThat(raw("POST", "/auth/wechat/mini/bind-login", Map.of("username",phone,"password",password,"code","unused-code"),null).getResponse().getStatus()).isEqualTo(403);
        assertThat(raw("POST", "/auth/initial-password", Map.of("newPassword",password),restricted).getResponse().getStatus()).isEqualTo(400);
        String fresh = call("POST", "/auth/initial-password", Map.of("newPassword", "Personal7" + UUID.randomUUID()), restricted).path("token").asText(); tokens.add(fresh);
        assertThat(raw("GET", "/auth/user-info", null, restricted).getResponse().getStatus()).isEqualTo(401);
        assertThat(call("GET", "/auth/user-info", null, fresh).path("initialPasswordSetupRequired").asBoolean()).isFalse();
        assertThat(items.forUser(userId(phone)).getCredentialCipher()).isNull(); assertThat(users.selectById(userId(phone)).getTemporaryPasswordExpiresAt()).isNull();
        assertThatThrownBy(() -> service.regenerate(userId(phone), resetPassword, admin)).hasMessageContaining("未完成首次改密");
    }
    @Test @Order(5) void expiryRegenerationAndHandoutOwnershipAreEnforced() throws Exception {
        String phone = phone(); long batch = importOne(phone); long id = userId(phone); String oldPassword = temporary(batch); String oldToken = token(id);
        jdbc.update("UPDATE system_user_import_item SET download_until=DATE_SUB(NOW(),INTERVAL 1 SECOND) WHERE batch_id=?",batch);
        assertThatThrownBy(() -> service.credentials(batch,admin)).isInstanceOf(BusinessException.class);
        service.purgeCredentials(); assertThat(items.forUser(id).getCredentialCipher()).isNull();
        jdbc.update("UPDATE sys_user SET temporary_password_expires_at=DATE_SUB(NOW(),INTERVAL 1 SECOND) WHERE id=?",id);
        assertThat(raw("POST","/auth/login",Map.of("username",phone,"password",oldPassword),null).getResponse().getStatus()).isEqualTo(403);
        assertThat(raw("POST","/auth/initial-password",Map.of("newPassword","Personal7"+UUID.randomUUID()),oldToken).getResponse().getStatus()).isEqualTo(403);
        service.regenerate(id, resetPassword, otherAdmin);
        assertThatThrownBy(() -> service.userCredential(id,admin)).isInstanceOf(BusinessException.class);
        assertThat(service.userCredential(id,otherAdmin)).isNotEmpty();
        assertThat(raw("GET","/auth/user-info",null,oldToken).getResponse().getStatus()).isEqualTo(401);
        assertThat(raw("POST","/auth/login",Map.of("username",phone,"password",oldPassword),null).getResponse().getStatus()).isEqualTo(401);
        assertThat(users.selectById(id).getTemporaryPasswordExpiresAt()).isAfter(java.time.LocalDateTime.now().plusDays(29));
    }
    @Test @Order(6) void concurrentBatchesDoNotCreateSameAccountAndLeaseCanRecover() throws Exception {
        String phone = phone(); long first = preview(Collections.singletonList(row(phone,a))), second = preview(Collections.singletonList(row(phone,b)));
        service.confirm(first,UUID.randomUUID().toString(), chosenPassword, admin); service.confirm(second,UUID.randomUUID().toString(), chosenPassword, admin);
        jdbc.update("UPDATE system_user_import_batch SET status='PROCESSING',lease_token='old-worker',lease_until=DATE_SUB(NOW(),INTERVAL 1 SECOND) WHERE id=?",first);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try { var one=pool.submit(service::processNext); var two=pool.submit(service::processNext); one.get(30,TimeUnit.SECONDS);two.get(30,TimeUnit.SECONDS); } finally { pool.shutdownNow(); }
        service.processNext(); assertThat(accounts(phone)).isEqualTo(1);
        assertThat(batches.selectById(first).getTemporaryPasswordCipher()).isNull(); assertThat(batches.selectById(second).getTemporaryPasswordCipher()).isNull();
        long successful = "SUCCEEDED".equals(batches.selectById(first).getStatus()) ? first : second; assertThat(temporary(successful)).isEqualTo(chosenPassword);
        assertThat(List.of(batches.selectById(first).getStatus(),batches.selectById(second).getStatus())).containsExactlyInAnyOrder("SUCCEEDED","CONFLICT");
    }
    @Test @Order(7) void rolesAreRevalidatedAndMidTransactionFailureRollsBackAllAccounts() throws Exception {
        String phone=phone();long batch=preview(Collections.singletonList(row(phone,a)));service.confirm(batch,UUID.randomUUID().toString(), chosenPassword, admin);
        jdbc.update("UPDATE sys_role SET enabled=0 WHERE id=?",role);
        try {service.processNext();assertThat(batches.selectById(batch).getStatus()).isEqualTo("CONFLICT");assertThat(accounts(phone)).isZero();}finally{jdbc.update("UPDATE sys_role SET enabled=1 WHERE id=?",role);}
        String good=phone(), bad=phone();long rollback=preview(List.of(row(good,a),row(bad,b)));service.confirm(rollback,UUID.randomUUID().toString(), chosenPassword, admin);
        jdbc.execute("CREATE TRIGGER import_verify_fail_assignment BEFORE INSERT ON sys_user_project_role FOR EACH ROW BEGIN IF NEW.project_id="+b+" THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Synthetic rollback test'; END IF; END");
        try {service.processNext();assertThat(batches.selectById(rollback).getStatus()).isEqualTo("FAILED");assertThat(accounts(good)).isZero();assertThat(accounts(bad)).isZero();assertThat(batches.selectById(rollback).getTemporaryPasswordCipher()).isNull();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_operation_log WHERE business_type='USER_IMPORT' AND business_id=? AND operation_type='COMPLETE_USER_IMPORT'",Long.class,rollback)).isZero();}
        finally {jdbc.execute("DROP TRIGGER import_verify_fail_assignment");}
    }
    @Test @Order(8) void conflictingNamesAndPlatformRolesAreErrorsWhileDisabledAccountsStayDisabled() throws Exception {
        String phone=phone();String[] one=row(phone,a),two=row(phone,b);two[0]="其他姓名";
        assertThat(batches.selectById(preview(List.of(one,two))).getStatus()).isEqualTo("INVALID");
        String[] platform=row(phone(),a);platform[3]="管理员 [R:"+roles.selectPlatformAdministratorForUpdate().getId()+"]";
        assertThat(batches.selectById(preview(Collections.singletonList(platform))).getStatus()).isEqualTo("INVALID");
        String occupied=phone();jdbc.update("UPDATE sys_user SET phone=?,status=0 WHERE id=?",occupied,ordinary.getId());
        long batch=preview(Collections.singletonList(row(occupied,a)));assertThat(batches.selectById(batch).getSkippedCount()).isEqualTo(1);
        assertThat(users.selectById(ordinary.getId()).getStatus()).isZero();
    }
    @Test @Order(9) void fiveHundredPersonBatchUsesChosenPasswordWithIndependentHashes() throws Exception {
        List<String[]> rows=new ArrayList<>();for(int i=0;i<500;i++)rows.add(row(phone(),a));
        long batch=preview(rows);String key=UUID.randomUUID().toString();service.confirm(batch,key, chosenPassword, admin);service.processNext();service.confirm(batch,key, chosenPassword, admin);
        assertThat(batches.selectById(batch).getStatus()).isEqualTo("SUCCEEDED");
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT user_id) FROM system_user_import_item WHERE batch_id=?",Long.class,batch)).isEqualTo(500);
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT u.password) FROM sys_user u JOIN system_user_import_item i ON i.user_id=u.id WHERE i.batch_id=?", Long.class, batch)).isEqualTo(500);
        try(XSSFWorkbook book=new XSSFWorkbook(new ByteArrayInputStream(service.credentials(batch,admin)))) {
            assertThat(book.getSheetAt(0).getLastRowNum()).isEqualTo(500);Set<String> unique=new HashSet<>();for(int i=1;i<=500;i++)unique.add(book.getSheetAt(0).getRow(i).getCell(2).getStringCellValue());assertThat(unique).containsExactly(chosenPassword);
        }
    }
    @Test @Order(11) void passwordValidationIdempotencyAndSensitiveMetadata() throws Exception {
        String phone = phone(); long batch = preview(Collections.singletonList(row(phone, a))); String key = UUID.randomUUID().toString();
        for (String invalid : List.of("", "Short1", "abcdefgh", "12345678", "A1" + "x".repeat(71), "A1" + "中".repeat(24))) {
            assertThat(raw("POST", "/system/user-imports/" + batch + "/confirm", Map.of("requestKey", key, "temporaryPassword", invalid), adminToken).getResponse().getStatus()).isEqualTo(400);
        }
        assertThat(raw("POST", "/system/user-imports/" + batch + "/confirm", Map.of("requestKey", key), adminToken).getResponse().getStatus()).isEqualTo(400);
        assertThat(batches.selectById(batch).getStatus()).isEqualTo("PREVIEW");
        assertThat(raw("POST", "/system/user-imports/" + batch + "/confirm", Map.of("requestKey", key, "temporaryPassword", chosenPassword), ordinaryToken).getResponse().getStatus()).isEqualTo(403);
        call("POST", "/system/user-imports/" + batch + "/confirm", Map.of("requestKey", key, "temporaryPassword", chosenPassword), adminToken);
        var queued = batches.selectById(batch);
        assertThat(queued.getTemporaryPasswordCipher()).isNotBlank().doesNotContain(chosenPassword);
        assertThat(queued.getConfirmationPasswordHash()).startsWith("$2a$12$");
        assertThat(queued.toString()).doesNotContain(chosenPassword, queued.getTemporaryPasswordCipher(), queued.getConfirmationPasswordHash());
        assertThat(json.writeValueAsString(queued)).doesNotContain("temporaryPasswordCipher", "confirmationPasswordHash");
        var request = new UserImportController.ConfirmRequest(key, chosenPassword);
        assertThat(request.toString()).doesNotContain(chosenPassword);
        assertThat(json.writeValueAsString(request)).doesNotContain(chosenPassword);
        assertThat(raw("POST", "/system/user-imports/" + batch + "/confirm", Map.of("requestKey", key, "temporaryPassword", resetPassword), adminToken).getResponse().getStatus()).isEqualTo(409);
        service.purgeCredentials(); assertThat(batches.selectById(batch).getTemporaryPasswordCipher()).isNotNull();
        service.processNext(); assertThat(temporary(batch)).isEqualTo(chosenPassword);
        assertThat(batches.selectById(batch).getTemporaryPasswordCipher()).isNull();
        assertThatThrownBy(() -> service.confirm(batch, key, resetPassword, admin)).hasMessageContaining("其他临时密码");
        long userId = userId(phone); String hash = users.selectById(userId).getPassword();
        assertThat(raw("POST", "/system/users/" + userId + "/temporary-password", Map.of(), adminToken).getResponse().getStatus()).isEqualTo(400);
        assertThat(raw("POST", "/system/users/" + userId + "/temporary-password", Map.of("temporaryPassword", chosenPassword), adminToken).getResponse().getStatus()).isEqualTo(400);
        assertThat(users.selectById(userId).getPassword()).isEqualTo(hash);
        call("POST", "/system/users/" + userId + "/temporary-password", Map.of("temporaryPassword", resetPassword), adminToken);
        try (XSSFWorkbook book = new XSSFWorkbook(new ByteArrayInputStream(service.userCredential(userId, admin)))) {
            assertThat(book.getSheetAt(0).getRow(1).getCell(2).getStringCellValue()).isEqualTo(resetPassword);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_operation_log WHERE operation_desc LIKE ?", Long.class, "%" + chosenPassword + "%")).isZero();
    }
    @Test @Order(10) void concurrentRegistrationApprovalKeepsItsOwnAccountAndMakesImportConflict() throws Exception {
        String phone = phone(); long batch = preview(Collections.singletonList(row(phone, a)));
        service.confirm(batch, UUID.randomUUID().toString(), chosenPassword, admin);
        jdbc.update("INSERT INTO registration_application(username,phone,real_name,password_hash,status,source_type,phone_verification_type,status_token_hash) VALUES(?,?,?,?,'PENDING','WEB','MANUAL_REVIEW',?)", phone, phone, "合成自主注册人员", auth.hashPassword("Personal7" + UUID.randomUUID()), UUID.randomUUID().toString());
        long application = jdbc.queryForObject("SELECT id FROM registration_application WHERE username=? AND status='PENDING'", Long.class, phone);
        var request = new com.example.siteplatform.registration.dto.RegistrationReviewRequest();
        var assignment = new com.example.siteplatform.registration.dto.RegistrationReviewRequest.ProjectAssignment();
        assignment.setProjectId(b); assignment.setRoleIds(List.of(role));
        request.setProjectAssignments(List.of(assignment)); request.setReviewComment("合成并发验收");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            var approval = pool.submit(() -> registrationService.approve(application, request, admin));
            var importing = pool.submit(service::processNext);
            approval.get(30, TimeUnit.SECONDS); importing.get(30, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
        assertThat(accounts(phone)).isEqualTo(1);
        assertThat(batches.selectById(batch).getStatus()).isEqualTo("CONFLICT");
        assertThat(users.selectById(userId(phone)).getMustChangePassword()).isZero();
        assertThat(jdbc.queryForList("SELECT project_id FROM sys_user_project WHERE user_id=?", Long.class, userId(phone))).containsExactly(b);
        assertThat(items.forBatch(batch)).allMatch(item -> item.getUserId() == null);
    }
}
