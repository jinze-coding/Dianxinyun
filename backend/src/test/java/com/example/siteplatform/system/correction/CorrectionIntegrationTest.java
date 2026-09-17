package com.example.siteplatform.system.correction;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.system.constant.BusinessModuleCodes;
import com.example.siteplatform.safetycommittee.CommitteeService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import java.nio.file.*;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static com.example.siteplatform.system.correction.CorrectionRepository.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@EnabledIfEnvironmentVariable(named="CORRECTION_INTEGRATION", matches="ISOLATED_EMPTY_ONLY")
@SpringBootTest(properties={"spring.profiles.active=local", "spring.main.banner-mode=off", "logging.level.root=ERROR"})
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(CorrectionIntegrationTest.Administrator.class)
class CorrectionIntegrationTest {
    static final String NAME="correction-"+UUID.randomUUID();
    static final Path FILES=Path.of(System.getProperty("java.io.tmpdir"),NAME);
    static final byte[] PNG=Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j9xkAAAAASUVORK5CYII=");
    @TestConfiguration static class Administrator {
        @Bean @Order(Ordered.HIGHEST_PRECEDENCE) ApplicationRunner syntheticCorrectionAdmin(JdbcTemplate jdbc) {
            return args -> {
                if(!Objects.equals(jdbc.queryForObject("SELECT DATABASE()",String.class),System.getenv("CORRECTION_TEST_DATABASE")))throw new IllegalStateException("Isolated database required");
                jdbc.update("INSERT INTO sys_user(username,password,real_name,status,deleted) VALUES(?,?,?,1,0)",NAME,new BCryptPasswordEncoder().encode(UUID.randomUUID().toString()),"纠错合成管理员");
                long id=jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?",Long.class,NAME);
                jdbc.update("INSERT INTO sys_user_role(user_id,role_id) SELECT ?,id FROM sys_role WHERE role_code='PLATFORM_ADMIN' AND deleted=0",id);
            };
        }
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        String database=System.getenv("CORRECTION_TEST_DATABASE");
        if(database==null||!database.matches("dianxinyun_correction_[0-9]{14}"))throw new IllegalStateException("Isolated database required");
        registry.add("spring.datasource.url",()->"jdbc:mysql://127.0.0.1:3306/"+database+"?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true");
        registry.add("spring.data.redis.port",()->6380);registry.add("spring.data.redis.database",()->14);registry.add("file.upload.path",()->FILES.toString());
    }
    @Autowired CorrectionService service; @Autowired CorrectionRepository repo; @Autowired CorrectionCatalog catalog;
    @Autowired CorrectionAccess access; @Autowired CorrectionAttachments files; @Autowired SysUserMapper users; @Autowired AuthService auth; @Autowired MockMvc mvc;
    @Autowired com.example.siteplatform.siteaccess.service.VisitorDataCryptoService crypto;
    @Autowired com.example.siteplatform.system.service.AdministrativeDeletionService deletions;
    @Autowired CorrectionDeletionSupport deletionFiles;
    SysUser admin; long project;
    @BeforeAll void setup() throws Exception {Files.createDirectories(FILES);admin=users.selectById(repo.count("SELECT id FROM sys_user WHERE username=?",NAME));}
    @BeforeEach void project() {
        project=repo.insert("INSERT INTO project_info(project_name,deleted) VALUES(?,0)","合成纠错项目-"+UUID.randomUUID());
        for(String module:BusinessModuleCodes.ALL)repo.jdbc().update("INSERT INTO project_business_module(project_id,module_code,enabled,version,update_time) VALUES(?,?,1,1,NOW(6))",project,module);
    }
    long fixture(CorrectionCatalog.Type type) throws Exception {
        if(type.table().equals("project_info")) {repo.update("project_info",project,Map.of("longitude",121.4,"latitude",31.2,"address","合成地址","coordinate_type","GCJ02"));return project;}
        var values=new LinkedHashMap<String,Object>();
        for(var c:repo.rows("SELECT COLUMN_NAME,DATA_TYPE,COLUMN_DEFAULT,IS_NULLABLE,EXTRA,CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? ORDER BY ORDINAL_POSITION",type.table())) {
            String column=string(c.get("column_name"));if(column.equals("id")||c.get("column_default")!=null||!"NO".equals(c.get("is_nullable"))||string(c.get("extra")).contains("GENERATED"))continue;
            String kind=string(c.get("data_type"));
            Object value=kind.contains("int")?1:kind.equals("date")?now().toLocalDate():kind.contains("datetime")?now():"合成"+UUID.randomUUID().toString().substring(0,8);
            if(column.endsWith("_encrypted"))value=crypto.encrypt("synthetic");
            values.put(column,value);
        }
        values.put("project_id",project);
        if(type.code().equals("ELECTRIC_INSPECTION")) {values.put("electric_box_id",fixture(catalog.require("ELECTRIC_BOX")));values.put("template_code","ELECTRIC_BOX_DAILY");values.put("source","ELECTRICIAN_DAILY");}
        if(Set.of("MEETING_MATERIAL","MEETING_REGISTRATION").contains(type.code()))values.put("invitation_id",fixture(catalog.require("MEETING")));
        if(type.code().equals("DOCUMENT_DISTRIBUTION"))values.put("incoming_batch_id",fixture(catalog.require("DOCUMENT_INCOMING")));
        if(type.code().equals("ELECTRIC_RECTIFICATION")) {long daily=fixture(catalog.require("ELECTRIC_INSPECTION"));values.put("inspection_record_id",daily);values.put("electric_box_id",repo.one("SELECT electric_box_id FROM inspection_record WHERE id=?",daily).get("electric_box_id"));}
        if(Set.of("EDGE_POINT","EDGE_TASK").contains(type.code())) {values.put("point_type_code","FLOOR_BALCONY_EAVE_EDGE");values.put("point_type_name","楼层、阳台及挑檐边");}
        if(type.code().equals("EDGE_TASK"))values.put("point_id",fixture(catalog.require("EDGE_POINT")));
        if(type.code().equals("EDGE_RECTIFICATION"))values.put("task_id",fixture(catalog.require("EDGE_TASK")));

        for(var f:type.fields()) {
            if(f.encrypted())values.put(f.key(),crypto.encrypt("13800000000"));
            if(f.type().equals("DATE")) values.put(f.key(),now().toLocalDate());
            if(f.type().equals("DATETIME")) values.put(f.key(),now().minusDays(1));
            if(f.type().equals("USER")) {values.put(f.key(),admin.getId());values.put(f.nameColumn(),admin.getRealName());}
            if(f.type().equals("ENUM"))values.put(f.key(),f.options().get(0));
        }
        if(type.code().equals("SINGLE_VISIT")||type.code().equals("MEETING")) values.put("invite_type",type.code().equals("MEETING")?"MEETING":"SINGLE");
        if(type.code().equals("QUALITY_WEEKLY")) {values.put("week_start",now().toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));values.put("status","SUBMITTED");values.put("submitted_issue_count",1);}
        if(type.code().equals("COMMITTEE"))values.put("category",CommitteeService.CATEGORIES.get(0));
        if(type.code().equals("QUALITY_DOCUMENT")) {
            String name=UUID.randomUUID()+".png";Files.write(FILES.resolve(name),PNG);
            values.putAll(Map.of("business_type","QUALITY_DOCUMENT","file_name",name,"file_extension","png","storage_key",name,"file_path",name,"storage_provider","LOCAL","sha256",hash(PNG),"file_size",PNG.length,"uploader_id",admin.getId()));
        }
        return repo.insert("INSERT INTO "+type.table()+" ("+String.join(",",values.keySet())+") VALUES ("+String.join(",",Collections.nCopies(values.size(),"?"))+")",values.values().toArray());
    }
    static String hash(byte[] data)throws Exception{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(data));}
    CorrectionRequests.Preview request(String type,long id,Map<String,Object> changes)throws Exception {
        return new CorrectionRequests.Preview((String)service.detail(type,id,admin).get("expectedRevision"),"合成数据纠错测试",changes,null,null,true);
    }
    Map<String,Object> save(String type,long id,Map<String,Object> changes)throws Exception {
        var p=service.preview(type,id,request(type,id,changes),admin);
        return service.confirm(new CorrectionRequests.Confirm((String)p.get("confirmationToken"),UUID.randomUUID().toString()),admin,"127.0.0.1");
    }
    @TestFactory Collection<DynamicTest> everyCatalogTypeSavesContentAndPreservesSystemFields() {
        return catalog.all().stream().map(type->DynamicTest.dynamicTest(type.code(),()->{
            project();long id=fixture(type);var before=repo.one("SELECT * FROM "+type.table()+" WHERE id=?",id);
            var field=type.fields().stream().filter(f->Set.of("TEXT","TEXTAREA").contains(f.type())&&!f.key().equals("category")).findFirst().orElseThrow();
            save(type.code(),id,Map.of(field.key(),"已纠正"));
            var after=repo.one("SELECT * FROM "+type.table()+" WHERE id=?",id);
            assertThat(after.get(field.key())).isEqualTo("已纠正");
            for(String k:List.of("create_time","status","submitted_time","approval_time","review_time","registered_time")) assertThat(after.get(k)).as(k).isEqualTo(before.get(k));
            var detail=service.detail(type.code(),id,admin);assertThat(((Map<?,?>)detail.get("values")).get(field.key())).isEqualTo("已纠正");
            businessReadBack(type,id,after);
            assertThat(service.page(type.code(),project,null,null,null,null,1,20,admin).getTotal()).isPositive();
            assertThat(repo.count("SELECT COUNT(*) FROM sys_data_correction_log WHERE target_type=? AND target_id=?",type.code(),id)).isPositive();
        })).toList();
    }
    @Test void whitelistConflictReplayAndRollback()throws Exception {
        long id=fixture(catalog.require("COMMITTEE"));var req=request("COMMITTEE",id,Map.of("conclusion","更新结论"));
        assertThatThrownBy(()->service.preview("COMMITTEE",id,new CorrectionRequests.Preview(req.expectedRevision(),"test",Map.of("project_id",999),null,null,false),admin)).isInstanceOf(BusinessException.class);
        var preview=service.preview("COMMITTEE",id,req,admin);var confirm=new CorrectionRequests.Confirm(string(preview.get("confirmationToken")),UUID.randomUUID().toString());
        var result=service.confirm(confirm,admin,"test");assertThat(service.confirm(confirm,admin,"test").get("correctionId")).isEqualTo(result.get("correctionId"));
        assertThatThrownBy(()->service.preview("COMMITTEE",id,req,admin)).isInstanceOf(BusinessException.class);
        var rollbackRequest=request("COMMITTEE",id,Map.of("conclusion","回滚内容"));preview=service.preview("COMMITTEE",id,rollbackRequest,admin);
        var fail=new CorrectionRequests.Confirm(string(preview.get("confirmationToken")),UUID.randomUUID().toString());
        long logs=repo.count("SELECT COUNT(*) FROM sys_data_correction_log");
        repo.jdbc().execute("CREATE TRIGGER correction_audit_fail BEFORE INSERT ON sys_operation_log FOR EACH ROW BEGIN IF NEW.operation_type='DATA_CORRECTION' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Synthetic audit failure'; END IF; END");
        try {assertThatThrownBy(()->service.confirm(fail,admin,"test")).isInstanceOf(Exception.class);}finally {repo.jdbc().execute("DROP TRIGGER correction_audit_fail");}
        assertThat(repo.one("SELECT conclusion FROM safety_committee_record WHERE id=?",id).get("conclusion")).isEqualTo("更新结论");assertThat(repo.count("SELECT COUNT(*) FROM sys_data_correction_log")).isEqualTo(logs);
    }
    @Test void ordinaryAndManagerCannotAccessEvenWithPermissionAndClosedModuleBlocksAdmin()throws Exception {
        long id=repo.insert("INSERT INTO sys_user(username,password,real_name,status,deleted) VALUES(?,?,'合成普通用户',1,0)","user-"+UUID.randomUUID(),new BCryptPasswordEncoder().encode("Synthetic99"));
        for(String role:List.of("USER","PROJECT_ADMIN")) {
            repo.jdbc().update("INSERT IGNORE INTO sys_user_role(user_id,role_id) SELECT ?,id FROM sys_role WHERE role_code=?",id,role);
            String token=auth.issueToken(users.selectById(id));
            try {assertThat(mvc.perform(get("/api/v1/system/data-corrections/catalog").header("Authorization","Bearer "+token)).andReturn().getResponse().getStatus()).isEqualTo(403);}finally {auth.logoutSession(token);}
        }
        long record=fixture(catalog.require("COMMITTEE"));repo.jdbc().update("UPDATE project_business_module SET enabled=0 WHERE project_id=? AND module_code='SAFETY_COMMITTEE'",project);
        assertThatThrownBy(()->service.detail("COMMITTEE",record,admin)).isInstanceOf(BusinessException.class);
    }
    @Test void datesMoveAcrossPeriodsAndDuplicatesAreBlocked()throws Exception {
        long weekly=fixture(catalog.require("QUALITY_WEEKLY"));long issue=fixture(catalog.require("QUALITY_ISSUE"));
        repo.update("quality_issue",issue,Map.of("weekly_inspection_id",weekly,"status","CLOSED"));
        String day=now().toLocalDate().minusWeeks(2).toString();save("QUALITY_WEEKLY",weekly,Map.of("inspection_date",day));
        assertThat(repo.one("SELECT record_date,status FROM quality_issue WHERE id=?",issue)).containsEntry("record_date",day).containsEntry("status","CLOSED");
        long second=fixture(catalog.require("QUALITY_WEEKLY"));
        assertThatThrownBy(()->save("QUALITY_WEEKLY",second,Map.of("inspection_date",day))).isInstanceOf(BusinessException.class);
        long record=fixture(catalog.require("ELECTRIC_INSPECTION"));long box=id(repo.one("SELECT electric_box_id FROM inspection_record WHERE id=?",record).get("electric_box_id"));
        repo.update("electric_box",box,Map.of("create_time",now().minusMonths(2)));
        save("ELECTRIC_INSPECTION",record,Map.of("check_date",day));
        assertThat(repo.one("SELECT check_date FROM inspection_record WHERE id=?",record).get("check_date")).isEqualTo(day);
        long competing=fixture(catalog.require("ELECTRIC_INSPECTION"));repo.update("inspection_record",competing,Map.of("electric_box_id",box));
        assertThatThrownBy(()->save("ELECTRIC_INSPECTION",competing,Map.of("check_date",day))).isInstanceOf(BusinessException.class);
    }
    long anotherAdmin() {
        long user=repo.insert("INSERT INTO sys_user(username,password,real_name,status,deleted) VALUES(?,?,'合成第二管理员',1,0)","person-"+UUID.randomUUID(),new BCryptPasswordEncoder().encode(UUID.randomUUID().toString()));
        repo.jdbc().update("INSERT INTO sys_user_role(user_id,role_id) SELECT ?,id FROM sys_role WHERE role_code='PLATFORM_ADMIN'",user);return user;
    }
    @Test void ownershipUsesEligibleUserAndRechecksQualification()throws Exception {
        long record=fixture(catalog.require("COMMITTEE")), person=anotherAdmin();
        var old=repo.one("SELECT inspector_id,inspected_at,create_time FROM safety_committee_record WHERE id=?",record);
        var preview=service.preview("COMMITTEE",record,request("COMMITTEE",record,Map.of("inspector_id",person)),admin);
        var confirm=new CorrectionRequests.Confirm(string(preview.get("confirmationToken")),UUID.randomUUID().toString());
        repo.update("sys_user",person,Map.of("status",0));
        assertThatThrownBy(()->service.confirm(confirm,admin,"test")).isInstanceOf(BusinessException.class);
        repo.update("sys_user",person,Map.of("status",1));service.confirm(confirm,admin,"test");
        var corrected=repo.one("SELECT inspector_id,inspected_at,create_time FROM safety_committee_record WHERE id=?",record);
        assertThat(id(corrected.get("inspector_id"))).isEqualTo(person);assertThat(corrected.get("inspected_at")).isEqualTo(old.get("inspected_at"));assertThat(corrected.get("create_time")).isEqualTo(old.get("create_time"));
        assertThat(access.candidates(project,catalog.require("COMMITTEE"),catalog.require("COMMITTEE").fields().get(2),admin)).isNotEmpty();
    }
    @Test void attachmentReplacementIsImmutableScopedAndAudited()throws Exception {
        long record=fixture(catalog.require("COMMITTEE"));
        var upload=new CorrectionRequests.Upload("COMMITTEE",record,"files","test.png",(long)PNG.length,hash(PNG));
        var first=files.complete(upload,new org.springframework.mock.web.MockMultipartFile("file","test.png","image/png",PNG),UUID.randomUUID().toString(),admin);
        var detail=service.detail("COMMITTEE",record,admin);
        var req=new CorrectionRequests.Preview(string(detail.get("expectedRevision")),"合成附件测试",Map.of(),null,Map.of("files",List.of(id(first.get("id")))),false);
        var preview=service.preview("COMMITTEE",record,req,admin);
        var result=service.confirm(new CorrectionRequests.Confirm(string(preview.get("confirmationToken")),UUID.randomUUID().toString()),admin,"test");
        long log=id(result.get("correctionId"));
        var current=files.download("COMMITTEE",record,id(first.get("id")),null,"AFTER",admin);
        assertThat(files.resource(current).getContentAsByteArray()).isEqualTo(PNG);
        var history=files.download("COMMITTEE",record,id(first.get("id")),log,"AFTER",admin);assertThat(history.getId()).isNotEqualTo(current.getId());
        save("COMMITTEE",record,Map.of("conclusion","更新但保留原附件"));
        long other=fixture(catalog.require("COMMITTEE"));
        var bad=new CorrectionRequests.Preview(string(service.detail("COMMITTEE",other,admin).get("expectedRevision")),"测试禁止跨记录绑定",Map.of(),null,Map.of("files",List.of(current.getId())),false);
        assertThatThrownBy(()->service.preview("COMMITTEE",other,bad,admin)).isInstanceOf(BusinessException.class);
        var removal=new CorrectionRequests.Preview(string(service.detail("COMMITTEE",record,admin).get("expectedRevision")),"移除当前附件",Map.of(),null,Map.of("files",List.of()),false);
        preview=service.preview("COMMITTEE",record,removal,admin);service.confirm(new CorrectionRequests.Confirm(string(preview.get("confirmationToken")),UUID.randomUUID().toString()),admin,"test");
        assertThat(files.resource(files.download("COMMITTEE",record,current.getId(),log,"AFTER",admin)).getContentAsByteArray()).isEqualTo(PNG);
        assertThat(repo.one("SELECT status FROM safety_committee_attachment WHERE record_id=? AND file_id=?",record,current.getId()).get("status")).isEqualTo("HISTORICAL");
        var extras=deletionFiles.files("COMMITTEE_INSPECTION",record);
        assertThat(extras).extracting(com.example.siteplatform.file.entity.FileResource::getId).contains(history.getId());
        var deletePreview=new com.example.siteplatform.system.dto.AdministrativeDeletionPreviewRequest();
        deletePreview.setTargetType("COMMITTEE_INSPECTION");deletePreview.setTargetId(record);
        var impact=deletions.preview(deletePreview,admin);assertThat(impact.getFileCount()).isGreaterThanOrEqualTo(extras.size());
        var deletion=new com.example.siteplatform.system.dto.AdministrativeDeletionExecuteRequest();
        deletion.setTargetType("COMMITTEE_INSPECTION");deletion.setTargetId(record);deletion.setAcknowledged(true);
        deletion.setConfirmationToken(impact.getConfirmationToken());deletion.setConfirmationText(impact.getTargetName());
        deletions.execute(deletion,admin);
        assertThat(repo.count("SELECT COUNT(*) FROM sys_data_correction_log WHERE id=?",log)).isEqualTo(1);
        assertThatThrownBy(()->files.resource(history)).isInstanceOf(BusinessException.class);
        assertThat(repo.count("SELECT COUNT(*) FROM file_resource WHERE id=?",history.getId())).isZero();
    }
    @Test void documentMeetingAndQualityReplacementsKeepOldBytesAndPublishedVersion()throws Exception {
        var bitmap=new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB);
        bitmap.setRGB(0,0,0x0088ff);var output=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(bitmap,"png",output);byte[] replacement=output.toByteArray();
        for(String code:List.of("DOCUMENT","MEETING_MATERIAL","QUALITY_DOCUMENT")) {
            long record=fixture(catalog.require(code));long original=code.equals("QUALITY_DOCUMENT")?record:fixture(catalog.require("QUALITY_DOCUMENT"));
            long oldVersion=0;
            if(code.equals("DOCUMENT")) {
                oldVersion=repo.insert("INSERT INTO project_document_version(document_id,version_no,file_resource_id,version_status,created_by,created_by_name,create_time) VALUES(?,1,?,'CURRENT',?,?,?)",record,original,admin.getId(),admin.getRealName(),now());
                repo.update("project_document",record,Map.of("current_version_id",oldVersion,"document_type","GENERAL","status","ARCHIVED"));
                repo.update("file_resource",original,Map.of("business_type","PROJECT_DOCUMENT","business_id",record));
            } else if(code.equals("MEETING_MATERIAL")) {
                Object invitation=repo.one("SELECT invitation_id FROM site_meeting_material WHERE id=?",record).get("invitation_id");
                oldVersion=repo.insert("INSERT INTO site_meeting_material_version(material_id,invitation_id,project_id,version_no,file_id,public_code,upload_key,uploader_id,uploader_name,create_time) VALUES(?,?,?,1,?,?,?,?,?,?)",record,invitation,project,original,UUID.randomUUID().toString().replace("-",""),UUID.randomUUID().toString(),admin.getId(),admin.getRealName(),now());
                repo.update("site_meeting_material",record,Map.of("current_version_id",oldVersion,"published_version_id",oldVersion));
                repo.update("file_resource",original,Map.of("business_type","MEETING_MATERIAL","business_id",record));
            }
            var upload=new CorrectionRequests.Upload(code,record,"file","replacement.png",(long)replacement.length,hash(replacement));
            var added=files.complete(upload,new org.springframework.mock.web.MockMultipartFile("file","replacement.png","image/png",replacement),UUID.randomUUID().toString(),admin);
            var req=new CorrectionRequests.Preview(string(service.detail(code,record,admin).get("expectedRevision")),"替换附件并保留旧版本",Map.of(),null,Map.of("file",List.of(id(added.get("id")))),false);
            var preview=service.preview(code,record,req,admin);
            var saved=service.confirm(new CorrectionRequests.Confirm(string(preview.get("confirmationToken")),UUID.randomUUID().toString()),admin,"test");
            long log=id(saved.get("correctionId"));
            assertThat(files.resource(files.download(code,record,original,log,"BEFORE",admin)).getContentAsByteArray()).isEqualTo(PNG);
            long current=code.equals("QUALITY_DOCUMENT")?record:id(added.get("id"));
            assertThat(files.resource(files.download(code,record,current,log,"AFTER",admin)).getContentAsByteArray()).as(code+" replacement bytes").isEqualTo(replacement);
            if(code.equals("DOCUMENT")) {
                assertThat(repo.one("SELECT file_resource_id,version_status FROM project_document_version WHERE id=?",oldVersion)).containsEntry("file_resource_id",original).containsEntry("version_status","SUPERSEDED");
                assertThat(repo.one("SELECT current_version_id,status FROM project_document WHERE id=?",record)).containsEntry("status","ARCHIVED").doesNotContainEntry("current_version_id",oldVersion);
            } else if(code.equals("MEETING_MATERIAL")) assertThat(repo.one("SELECT current_version_id,published_version_id FROM site_meeting_material WHERE id=?",record)).containsEntry("published_version_id",oldVersion).doesNotContainEntry("current_version_id",oldVersion);
            else assertThat(repo.count("SELECT COUNT(*) FROM file_resource WHERE project_id=? AND business_type='QUALITY_DOCUMENT' AND id IN (?,?)",project,record,id(added.get("id")))).isEqualTo(1);
        }
    }
    @Test void edgeSheetOwnershipMovesAllExistingItemsAndInvalidatesTaskVersion()throws Exception {
        long first=fixture(catalog.require("EDGE_RECTIFICATION")),second=fixture(catalog.require("EDGE_RECTIFICATION")),person=anotherAdmin();
        long task=id(repo.one("SELECT task_id FROM general_inspection_rectification WHERE id=?",first).get("task_id"));
        repo.update("general_inspection_rectification",first,Map.of("status","PENDING"));
        repo.update("general_inspection_rectification",second,Map.of("task_id",task,"status","PENDING"));
        long version=((Number)repo.one("SELECT version FROM general_inspection_task WHERE id=?",task).get("version")).longValue();
        var preview=service.preview("EDGE_RECTIFICATION",first,request("EDGE_RECTIFICATION",first,Map.of("assignee_id",person)),admin);
        assertThat(repo.encode(preview.get("after"))).contains("同一临边整改单","合成第二管理员");
        var saved=service.confirm(new CorrectionRequests.Confirm(string(preview.get("confirmationToken")),UUID.randomUUID().toString()),admin,"test");
        assertThat(repo.count("SELECT COUNT(*) FROM general_inspection_rectification WHERE task_id=? AND assignee_id=? AND status='PENDING'",task,person)).isEqualTo(2);
        assertThat(id(repo.one("SELECT version FROM general_inspection_task WHERE id=?",task).get("version"))).isEqualTo(version+1);
        assertThat(repo.encode(service.log(id(saved.get("correctionId")),admin).get("after"))).contains("同一临边整改单","合成第二管理员");
    }
    @Test void competingPreviewsOnlyCommitOnceAndBusinessResponseHasMarker()throws Exception {
        long record=fixture(catalog.require("COMMITTEE"));var one=service.preview("COMMITTEE",record,request("COMMITTEE",record,Map.of("conclusion","并发一")),admin);var two=service.preview("COMMITTEE",record,request("COMMITTEE",record,Map.of("conclusion","并发二")),admin);
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        try {
            var futures=List.of(one,two).stream().map(p->pool.submit(()->{start.await();try {service.confirm(new CorrectionRequests.Confirm(string(p.get("confirmationToken")),UUID.randomUUID().toString()),admin,"test");return 200;}catch(BusinessException e){return e.getCode();}})).toList();
            start.countDown();assertThat(List.of(futures.get(0).get(10,TimeUnit.SECONDS),futures.get(1).get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
        }finally{pool.shutdownNow();}
        String token=auth.issueToken(admin);
        try {String body=mvc.perform(get("/api/v1/safety-committee/records/"+record).header("Authorization","Bearer "+token)).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);assertThat(body).contains("correctionNotice","管理员纠错");}finally{auth.logoutSession(token);}
    }

    void businessReadBack(CorrectionCatalog.Type type,long id,Map<String,Object> row)throws Exception {
        String path=switch(type.code()) {
            case "SINGLE_VISIT","MEETING"->"/site-access/invitations/"+id;
            case "MEETING_REGISTRATION"->"/site-access/meeting-registrations/"+id;
            case "GUARD_REGISTRATION"->"/site-access/guard/registrations/"+id;
            case "MEETING_MATERIAL"->"/site-access/invitations/"+row.get("invitation_id")+"/materials";
            case "DOCUMENT_FOLDER"->"/document-folders?projectId="+project;
            case "DOCUMENT"->"/project-documents/"+id;
            case "DOCUMENT_INCOMING"->"/document-incoming-batches/"+id;
            case "DOCUMENT_DISTRIBUTION"->"/document-distributions/"+id;
            case "SEAL_APPLICATION"->"/seal/applications/"+id;
            case "ELECTRIC_BOX"->"/electric-boxes/"+id;
            case "ELECTRIC_INSPECTION"->"/inspection/records/"+id;
            case "ELECTRIC_RECTIFICATION"->"/inspection/rectifications/"+id;
            case "EDGE_POINT"->"/edge-inspections/points?projectId="+project;
            case "EDGE_TASK"->"/edge-inspections/tasks/"+id;
            case "EDGE_RECTIFICATION"->"/edge-inspections/rectifications/"+row.get("task_id");
            case "QUALITY_WEEKLY"->"/quality/weekly-inspections/"+id;
            case "QUALITY_ISSUE"->"/quality/issues/"+id;
            case "QUALITY_DOCUMENT"->"/files/"+id;
            case "COMMITTEE"->"/safety-committee/records/"+id;
            case "PROJECT"->"/projects/"+id+"/profile";
            case "PROJECT_LOCATION"->"/projects/"+id+"/map-detail";
            default->throw new IllegalStateException();
        };
        String token=auth.issueToken(admin);
        try {
            var response=mvc.perform(get("/api/v1"+path).header("Authorization","Bearer "+token)).andReturn().getResponse();
            String body=response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
            assertThat(response.getStatus()).as(type.code()+" original business response: "+body).isEqualTo(200);
            assertThat(body).contains("已纠正");
            if(type.code().equals("QUALITY_DOCUMENT"))assertThat(body).contains("correctionNotice","管理员纠错");
        }finally{auth.logoutSession(token);}
    }

}
