package com.example.siteplatform.safetycommittee;

import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.*;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.io.*;
import java.time.Duration;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Destructive fixtures are restricted to an explicitly created isolated clone, never the working database. */
@EnabledIfEnvironmentVariable(named="COMMITTEE_INTEGRATION",matches="ISOLATED_CLONE_ONLY")
@SpringBootTest(properties={"spring.profiles.active=local","spring.main.banner-mode=off"})
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CommitteeIntegrationTest {
    static final Path ROOT=createRoot();
    static Path createRoot(){try{return Files.createTempDirectory("committee-integration-");}catch(IOException e){throw new IllegalStateException(e);}}
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){
        String name=System.getenv("COMMITTEE_TEST_DATABASE");
        if(name==null||!name.matches("dianxinyun_committee_verify_[0-9]{14}"))throw new IllegalStateException("Isolated clone required before Spring startup");
        r.add("spring.datasource.url",()->"jdbc:mysql://127.0.0.1:3306/"+name+"?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true");
        r.add("spring.data.redis.port",()->6380);r.add("spring.data.redis.database",()->15);
        r.add("file.upload.path",()->ROOT.toString());
    }
    @Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;@Autowired ObjectMapper json;@Autowired AuthService auth;@Autowired SysUserMapper users;
    long project,otherProject,authorId,readerId,role;String adminToken,authorToken,readerToken;
    final List<String> tokens=new ArrayList<>();
    @BeforeAll void setup(){
        assertThat(jdbc.queryForObject("SELECT DATABASE()",String.class)).isEqualTo(System.getenv("COMMITTEE_TEST_DATABASE"));
        project=jdbc.queryForObject("SELECT MIN(id) FROM project_info WHERE deleted=0",Long.class);
        otherProject=jdbc.queryForObject("SELECT MAX(id) FROM project_info WHERE deleted=0",Long.class);assertThat(project).isNotEqualTo(otherProject);
        long admin=jdbc.queryForObject("SELECT MIN(u.id) FROM sys_user u JOIN sys_user_role ur ON ur.user_id=u.id JOIN sys_role r ON r.id=ur.role_id WHERE r.role_code='PLATFORM_ADMIN' AND u.deleted=0 AND u.status=1",Long.class);
        adminToken=token(admin);authorId=createUser("检查人");readerId=createUser("只读用户");
        role=createRole("author",List.of("view","submit","edit_own"));long readRole=createRole("reader",List.of("view"));
        member(authorId,role);member(readerId,readRole);authorToken=token(authorId);readerToken=token(readerId);
    }
    @AfterAll void cleanup(){tokens.forEach(auth::logoutSession);}
    String token(long id){String t=auth.issueToken(users.selectById(id));tokens.add(t);return t;}
    long createUser(String label){String name="committee-test-"+UUID.randomUUID().toString().substring(0,16);
        jdbc.update("INSERT INTO sys_user(username,password,real_name,status,deleted) VALUES(?,?,?,1,0)",name,new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(UUID.randomUUID().toString()),label);
        return jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?",Long.class,name);
    }
    long createRole(String label,List<String> codes){String code="COMMITTEE_TEST_"+UUID.randomUUID().toString().substring(0,16);jdbc.update("INSERT INTO sys_role(role_name,role_code,scope_type,enabled,deleted) VALUES(?,?,'PROJECT',1,0)",label+code,code);
        long id=jdbc.queryForObject("SELECT id FROM sys_role WHERE role_code=?",Long.class,code);
        jdbc.update("INSERT INTO sys_role_business_module(role_id,module_code) VALUES(?,'SAFETY_COMMITTEE')",id);
        jdbc.update("INSERT INTO sys_role_menu(role_id,menu_id) SELECT ?,id FROM sys_menu WHERE menu_code IN ('WEB_SAFETY_COMMITTEE','MINI_SAFETY_COMMITTEE','SAFETY_COMMITTEE_RECORDS') AND deleted=0",id);
        for(String p:codes)jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) SELECT ?,id FROM sys_permission WHERE permission_code=? AND deleted=0",id,"safety_committee."+p);
        return id;
    }
    void member(long user,long role){jdbc.update("INSERT INTO sys_user_project(user_id,project_id,status) VALUES(?,?,'ACTIVE')",user,project);jdbc.update("INSERT INTO sys_user_project_role(user_id,project_id,role_id) VALUES(?,?,?)",user,project,role);}
    MvcResult raw(String method,String path,Object body,String token) throws Exception {
        var b=org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(org.springframework.http.HttpMethod.valueOf(method),java.net.URI.create(path));
        if(token!=null)b.header("Authorization","Bearer "+token);if(body!=null)b.contentType("application/json").content(json.writeValueAsBytes(body));return mvc.perform(b).andReturn();
    }
    JsonNode call(String method,String path,Object body,String token) throws Exception{var r=raw(method,path,body,token);assertThat(r.getResponse().getStatus()).as(method+" "+path+" "+r.getResponse().getContentAsString()).isEqualTo(200);return json.readTree(r.getResponse().getContentAsByteArray()).path("data");}
    String key(){return UUID.randomUUID().toString().replace("-","");}
    String base(){return "/api/v1/safety-committee";}
    Map<String,Object> createData(String category,String key,List<Long> ids){return new LinkedHashMap<>(Map.of("projectId",project,"category",category,"conclusion","","attachmentIds",ids,"requestKey",key));}
    int status(String method,String path,Object body,String token)throws Exception{return raw(method,path,body,token).getResponse().getStatus();}
    @Test void categoryOnlyIdempotencyIdentityConcurrencyAndAuthorization()throws Exception{
        assertThat(call("GET",base()+"/categories?projectId="+project,null,readerToken).size()).isEqualTo(11);
        var data=createData("其他",key(),List.of());data.put("inspectorId",readerId);data.put("inspectorName","伪造姓名");data.put("inspectedAt","1999-01-01T00:00:00");
        var record=call("POST",base()+"/records",data,authorToken);long id=record.path("id").asLong();
        assertThat(record.path("inspectorId").asLong()).isEqualTo(authorId);assertThat(record.path("inspectorName").asText()).isEqualTo("检查人");assertThat(record.path("inspectedAt").asText()).doesNotStartWith("1999");
        assertThat(call("POST",base()+"/records",data,authorToken).path("id").asLong()).isEqualTo(id);
        data.put("conclusion","changed request");assertThat(status("POST",base()+"/records",data,authorToken)).isEqualTo(409);
        assertThat(status("POST",base()+"/records",createData("其他",key(),List.of()),readerToken)).isEqualTo(403);
        var edit=Map.of("category","基坑工程","conclusion","修改留痕","attachmentIds",List.of(),"expectedVersion",1);
        assertThat(status("PUT",base()+"/records/"+id,edit,adminToken)).isEqualTo(403);
        ExecutorService pool=Executors.newFixedThreadPool(2);try{var a=pool.submit(()->status("PUT",base()+"/records/"+id,edit,authorToken));var b=pool.submit(()->status("PUT",base()+"/records/"+id,edit,authorToken));assertThat(List.of(a.get(),b.get())).containsExactlyInAnyOrder(200,409);}finally{pool.shutdownNow();}
        var detail=call("GET",base()+"/records/"+id,null,readerToken);assertThat(detail.path("inspectedAt")).isEqualTo(record.path("inspectedAt"));assertThat(detail.path("logs").size()).isEqualTo(2);
        assertThat(call("GET",base()+"/records?projectId="+project+"&category="+java.net.URLEncoder.encode("基坑工程",StandardCharsets.UTF_8),null,readerToken).path("records").toString()).contains("修改留痕");
        assertThat(status("GET",base()+"/records?projectId="+otherProject,null,readerToken)).isEqualTo(403);
        assertThat(status("GET",base()+"/records?projectId="+project,null,null)).isEqualTo(401);
        var user=call("GET","/api/v1/auth/user-info",null,authorToken);assertThat(user.toString()).contains("MINI_SAFETY_COMMITTEE","WEB_SAFETY_COMMITTEE","safety_committee.edit_own");
        String badKey=key();assertThat(status("POST",base()+"/records",createData("其他",badKey,List.of(Long.MAX_VALUE)),authorToken)).isEqualTo(403);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM safety_committee_record WHERE request_key=?",Long.class,badKey)).isZero();
        var ids=java.util.stream.LongStream.rangeClosed(1,31).boxed().toList();assertThat(status("POST",base()+"/records",createData("其他",key(),ids),authorToken)).isEqualTo(400);
    }
    JsonNode initialize(Path file,String draft,Long target)throws Exception{
        var metadata=new LinkedHashMap<String,Object>();metadata.put("projectId",project);metadata.put("draftKey",draft);metadata.put("targetRecordId",target);metadata.put("fileName",file.getFileName().toString());metadata.put("totalSize",Files.size(file));metadata.put("sha256",CommitteeUploadService.hash(Files.newInputStream(file)));
        return call("POST",base()+"/uploads",metadata,authorToken);
    }
    MvcResult chunk(String session,int index,byte[] data,String digest)throws Exception{
        return mvc.perform(multipart(base()+"/uploads/"+session+"/chunks/"+index).file(new MockMultipartFile("chunk","chunk","application/octet-stream",data)).param("projectId",String.valueOf(project)).param("sha256",digest).header("Authorization","Bearer "+authorToken).with(r->{r.setMethod("PUT");return r;})).andReturn();
    }
    JsonNode upload(Path file,String draft,Long target,boolean resume)throws Exception{
        String session=initialize(file,draft,target).path("sessionId").asText();
        try(var in=Files.newInputStream(file)){byte[] data;int index=0;while((data=in.readNBytes(CommitteeUploadService.CHUNK_SIZE)).length>0){
            if(index==0){assertThat(chunk(session,index,data,"0".repeat(64)).getResponse().getStatus()).isEqualTo(409);}
            assertThat(chunk(session,index,data,CommitteeUploadService.hash(new ByteArrayInputStream(data))).getResponse().getStatus()).isEqualTo(200);index++;
            if(resume&&index==31){var state=call("GET",base()+"/uploads/"+session+"?projectId="+project,null,authorToken);assertThat(state.path("uploadedChunks").size()).isEqualTo(31);assertThat(status("POST",base()+"/uploads/"+session+"/complete?projectId="+project,Map.of(),authorToken)).isEqualTo(409);}
        }}
        var result=call("POST",base()+"/uploads/"+session+"/complete?projectId="+project,Map.of(),authorToken);
        assertThat(call("POST",base()+"/uploads/"+session+"/complete?projectId="+project,Map.of(),authorToken).path("id")).isEqualTo(result.path("id"));return result;
    }
    @Test void mediaGrantHistoryDeletePreviewRollbackAndRevocation()throws Exception{
        Path image=ROOT.resolve("photo.png");javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",image.toFile());
        String draft=key();long file=upload(image,draft,null,false).path("id").asLong();
        assertThat(status("GET",base()+"/attachments/"+file,null,readerToken)).isEqualTo(403);
        var record=call("POST",base()+"/records",createData("消防管理",draft,List.of(file)),authorToken);long id=record.path("id").asLong();
        var granted=call("POST",base()+"/attachments/"+file+"/read-session?nativePlayback=true",Map.of(),readerToken);String url=granted.path("contentPath").asText();assertThat(url).doesNotContain(readerToken);
        assertThat(mvc.perform(get(url).header("Range","bytes=0-7")).andReturn().getResponse().getStatus()).isEqualTo(206);
        var cookie=raw("POST",base()+"/attachments/"+file+"/read-session",Map.of(),readerToken).getResponse().getHeader("Set-Cookie");assertThat(cookie).contains("HttpOnly","SameSite=Strict");String code=cookie.split(";",2)[0].split("=",2)[1];
        assertThat(mvc.perform(get(base()+"/attachments/"+file+"/content").cookie(new jakarta.servlet.http.Cookie("committee_read",code)).header("Range","bytes=0-7")).andReturn().getResponse().getStatus()).isEqualTo(206);
        long resource=jdbc.queryForObject("SELECT file_id FROM safety_committee_attachment WHERE id=?",Long.class,file);assertThat(status("GET","/api/v1/files/"+resource+"/download",null,adminToken)).isEqualTo(403);
        var before=call("POST","/api/v1/system/deletions/preview",Map.of("targetType","COMMITTEE_INSPECTION","targetId",id),adminToken);
        var edit=Map.of("category","其他","conclusion","移除附件并保留历史","attachmentIds",List.of(),"expectedVersion",1);call("PUT",base()+"/records/"+id,edit,authorToken);
        assertThat(call("GET",base()+"/records/"+id,null,readerToken).path("attachments").get(0).path("status").asText()).isEqualTo("HISTORICAL");
        assertThat(status("POST","/api/v1/system/deletions/execute",Map.of("targetType","COMMITTEE_INSPECTION","targetId",id,"confirmationToken",before.path("confirmationToken").asText(),"acknowledged",true),adminToken)).isEqualTo(409);
        jdbc.update("DELETE FROM sys_role_business_module WHERE role_id=?",role);
        assertThat(status("GET",base()+"/records?projectId="+project,null,authorToken)).isEqualTo(403);
        jdbc.update("INSERT INTO sys_role_business_module(role_id,module_code) VALUES(?,'SAFETY_COMMITTEE')",role);
        jdbc.update("UPDATE sys_user_project SET status='DISABLED' WHERE user_id=? AND project_id=?",readerId,project);
        assertThat(mvc.perform(get(url).header("Range","bytes=0-7")).andReturn().getResponse().getStatus()).isEqualTo(403);
        jdbc.update("UPDATE sys_user_project SET status='ACTIVE' WHERE user_id=? AND project_id=?",readerId,project);
        auth.logoutSession(readerToken);assertThat(mvc.perform(get(url).header("Range","bytes=0-7")).andReturn().getResponse().getStatus()).isEqualTo(401);readerToken=token(readerId);
        // Simulate a retired conversion copy whose current pointer has already changed.
        jdbc.update("INSERT INTO file_resource(project_id,business_id,business_type,file_name,original_file_name,file_size,file_path,storage_key,storage_provider,status,deleted) VALUES(?,?,'COMMITTEE_INSPECTION_PREVIEW','retired.pdf','retired.pdf',8,'committee/retired-missing.pdf','committee/retired-missing.pdf','LOCAL','PENDING_DELETE',1)",project,id);
        deleteRecord(id);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM file_resource WHERE business_id=? AND business_type='COMMITTEE_INSPECTION_PREVIEW'",Long.class,id)).isZero();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM file_resource WHERE id=?",Long.class,resource)).isZero();assertThat(status("GET",base()+"/attachments/"+file,null,authorToken)).isEqualTo(404);
    }
    void deleteRecord(long id)throws Exception{var impact=call("POST","/api/v1/system/deletions/preview",Map.of("targetType","COMMITTEE_INSPECTION","targetId",id),adminToken);call("POST","/api/v1/system/deletions/execute",Map.of("targetType","COMMITTEE_INSPECTION","targetId",id,"confirmationToken",impact.path("confirmationToken").asText(),"acknowledged",true),adminToken);}
    @Test void deletingInspectorPreservesRecordAndNameSnapshot()throws Exception{
        long inspector=createUser("待删除检查人");member(inspector,role);String inspectorToken=token(inspector);
        long id=call("POST",base()+"/records",createData("其他",key(),List.of()),inspectorToken).path("id").asLong();
        var impact=call("POST","/api/v1/system/deletions/preview",Map.of("targetType","USER","targetId",inspector),adminToken);
        assertThat(impact.path("items").toString()).contains("保留的本人安委会巡检");
        call("POST","/api/v1/system/deletions/execute",Map.of("targetType","USER","targetId",inspector,"confirmationToken",impact.path("confirmationToken").asText(),"acknowledged",true),adminToken);
        var retained=call("GET",base()+"/records/"+id,null,readerToken);
        assertThat(retained.path("inspectorName").asText()).isEqualTo("待删除检查人");assertThat(retained.path("logs").size()).isEqualTo(1);
        assertThat(status("GET",base()+"/records/"+id,null,inspectorToken)).isEqualTo(401);deleteRecord(id);
    }
    @Test void officeConversionAndFiveHundredMiBVideoResume()throws Exception{
        Path office=ROOT.resolve("巡检记录.docx");try(var document=new org.apache.poi.xwpf.usermodel.XWPFDocument();var out=Files.newOutputStream(office)){document.createParagraph().createRun().setText("安委会巡检合成测试");document.write(out);}
        String draft=key();long officeId=upload(office,draft,null,false).path("id").asLong();
        Path video=ROOT.resolve("现场录像.mp4");String ffmpeg=System.getenv().getOrDefault("COMMITTEE_TEST_FFMPEG","/opt/homebrew/bin/ffmpeg");
        Process process=new ProcessBuilder(ffmpeg,"-nostdin","-v","error","-f","lavfi","-i","color=c=blue:s=160x120:d=1","-c:v","libx264","-pix_fmt","yuv420p","-movflags","+faststart",video.toString()).redirectErrorStream(true).redirectOutput(ROOT.resolve("fixture.log").toFile()).start();assertThat(process.waitFor()).isZero();
        // A valid ISO BMFF free box pads the real one-second video to the exact size boundary without giant heap allocation.
        long limit=500L*1024*1024;try(var out=new RandomAccessFile(video.toFile(),"rw")){long padding=limit-out.length();out.seek(out.length());out.writeInt((int)padding);out.writeBytes("free");out.setLength(limit);}
        long videoId=upload(video,draft,null,true).path("id").asLong();long id=call("POST",base()+"/records",createData("施工安全管理",draft,List.of(officeId,videoId)),authorToken).path("id").asLong();
        try{for(long attachment:List.of(officeId,videoId)){long deadline=System.nanoTime()+Duration.ofMinutes(3).toNanos();String state="";while(System.nanoTime()<deadline){state=call("GET",base()+"/attachments/"+attachment,null,authorToken).path("previewStatus").asText();if(Set.of("READY","FAILED").contains(state))break;Thread.sleep(1000);}assertThat(state).isEqualTo("READY");
            var response=mvc.perform(get(base()+"/attachments/"+attachment+"/content").header("Authorization","Bearer "+readerToken).header("Range","bytes=0-31")).andReturn().getResponse();assertThat(response.getStatus()).isEqualTo(206);assertThat(response.getContentAsByteArray()).hasSize(32);
        }}finally{deleteRecord(id);}
        var over=Map.of("projectId",project,"draftKey",key(),"fileName","too-big.mp4","totalSize",limit+1,"sha256","0".repeat(64));assertThat(status("POST",base()+"/uploads",over,authorToken)).isEqualTo(400);
        var session=initialize(office,key(),null).path("sessionId").asText();assertThat(status("DELETE",base()+"/uploads/"+session+"?projectId="+project,null,authorToken)).isEqualTo(200);assertThat(status("GET",base()+"/uploads/"+session+"?projectId="+project,null,authorToken)).isEqualTo(410);
    }
}
