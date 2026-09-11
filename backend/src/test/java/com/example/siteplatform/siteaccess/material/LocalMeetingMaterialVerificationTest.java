package com.example.siteplatform.siteaccess.material;

import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

/** Opt-in, current localhost:8080 / dianxinyun only. Keeps synthetic demo materials; cleans its own 1GB probe. */
@EnabledIfEnvironmentVariable(named="MEETING_MATERIAL_LOCAL_VERIFY",matches="LOCAL_DEVELOPMENT_ONLY")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={"spring.profiles.active=local","spring.main.banner-mode=off"})
class LocalMeetingMaterialVerificationTest {
    @Autowired AuthService auth;
    @Autowired SysUserMapper users;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired com.example.siteplatform.siteaccess.service.VisitorDataCryptoService crypto;
    final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    String token;
    long meetingId=Long.parseLong(System.getenv().getOrDefault("MEETING_MATERIAL_LOCAL_MEETING_ID","29"));
    final Path directory=Path.of("../logs/meeting-material-demo-files");
    @BeforeEach void setup() {
        assertThat(System.getenv().getOrDefault("DB_URL","jdbc:mysql://localhost:3306/dianxinyun")).matches("jdbc:mysql://(localhost|127\\.0\\.0\\.1):3306/dianxinyun(?:\\?.*)?");
        assertThat(jdbc.queryForObject("SELECT internal_remark FROM site_visit_invitation WHERE id=?",String.class,meetingId)).startsWith("[LOCAL_VISITOR_DEMO_");
        Long id=jdbc.queryForObject("SELECT MIN(u.id) FROM sys_user u JOIN sys_user_role ur ON ur.user_id=u.id JOIN sys_role r ON r.id=ur.role_id WHERE r.role_code='PLATFORM_ADMIN' AND u.deleted=0 AND u.status=1",Long.class);
        token=auth.issueToken(users.selectById(id));
    }
    @AfterEach void logout() { if(token!=null) auth.logoutSession(token); }
    String base() { return "/site-access/invitations/"+meetingId; }
    HttpResponse<byte[]> raw(String method,String path,byte[] body,String contentType,boolean loggedIn,Map<String,String> headers) throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:8080/api/v1"+path)).timeout(Duration.ofMinutes(20));
        if(loggedIn) request.header("Authorization","Bearer "+token);
        if(contentType!=null) request.header("Content-Type",contentType);
        headers.forEach(request::header);
        return http.send(request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofByteArray(body)).build(),HttpResponse.BodyHandlers.ofByteArray());
    }
    JsonNode call(String method,String path,Object body) throws Exception {
        var response=raw(method,path,body==null?null:json.writeValueAsBytes(body),"application/json",true,Map.of());
        assertThat(response.statusCode()).as(method+" "+path+" "+new String(response.body(),StandardCharsets.UTF_8)).isEqualTo(200);
        return json.readTree(response.body()).path("data");
    }
    Map<String,Object> metadata(Path file,String category,JsonNode row) throws Exception {
        var result=new LinkedHashMap<String,Object>();result.put("fileName",file.getFileName().toString());result.put("title",row==null?file.getFileName().toString():row.path("material").path("title").asText());
        result.put("totalSize",Files.size(file));result.put("sha256",MeetingMaterialUploadService.hash(Files.newInputStream(file)));result.put("category",category);result.put("description","合成测试资料，仅用于本地开发演示");
        if(row!=null) {result.put("materialId",row.path("material").path("id").asLong());result.put("expectedVersion",row.path("material").path("version").asInt());result.put("changeNote","开发验证：更新后默认内部可见");}
        return result;
    }
    void chunk(String id,int index,byte[] data) throws Exception {
        String boundary="meeting-verification-boundary";
        String sha=MeetingMaterialUploadService.hash(new java.io.ByteArrayInputStream(data));
        byte[] start=("--"+boundary+"\r\nContent-Disposition: form-data; name=\"sha256\"\r\n\r\n"+sha+"\r\n--"+boundary+"\r\nContent-Disposition: form-data; name=\"chunk\"; filename=\"chunk\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] end=("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] body=new byte[start.length+data.length+end.length];System.arraycopy(start,0,body,0,start.length);System.arraycopy(data,0,body,start.length,data.length);System.arraycopy(end,0,body,start.length+data.length,end.length);
        var response=raw("PUT",base()+"/material-uploads/"+id+"/chunks/"+index,body,"multipart/form-data; boundary="+boundary,true,Map.of());
        assertThat(response.statusCode()).as("chunk "+index).isEqualTo(200);
    }
    JsonNode upload(Path file,String category,JsonNode row) throws Exception {
        var metadata=metadata(file,category,row);String id=call("POST",base()+"/material-uploads",metadata).path("sessionId").asText();
        try(var input=Files.newInputStream(file)) { int index=0;byte[] data;while((data=input.readNBytes(MeetingMaterialUploadService.CHUNK_SIZE)).length>0)chunk(id,index++,data); }
        JsonNode result=call("POST",base()+"/material-uploads/"+id+"/complete",Map.of());
        assertThat(call("POST",base()+"/material-uploads/"+id+"/complete",Map.of()).path("material").path("id").asLong()).isEqualTo(result.path("material").path("id").asLong());
        return result;
    }
    JsonNode publication(JsonNode row,boolean publish) throws Exception {
        var body=new LinkedHashMap<String,Object>();body.put("expectedVersion",row.path("material").path("version").asInt());body.put("versionId",publish?row.path("currentVersion").path("id").asLong():null);
        return call("PUT","/site-access/materials/"+row.path("material").path("id").asLong()+"/publication",body);
    }
    String publicCode(JsonNode row) { return jdbc.queryForObject("SELECT public_code FROM site_meeting_material_version WHERE id=?",String.class,row.path("currentVersion").path("id").asLong()); }
    int publicStatus(String code) throws Exception { return raw("GET","/public/site-access/meeting/materials/content/"+code,null,null,false,Map.of("Range","bytes=0-7")).statusCode(); }
    @Test void uploadsPublicationHistoryRangeAndRealPreviewWorkOnCurrentDevelopmentSystem() throws Exception {
        // A single current-demo fixture set is repeatable and does not overwrite user uploaded materials.
        Assumptions.assumeTrue(jdbc.queryForObject("SELECT COUNT(*) FROM site_meeting_material WHERE invitation_id=? AND description='合成测试资料，仅用于本地开发演示'",Long.class,meetingId)==0,
                "演示资料已存在，本次跳过播种验证，避免覆盖用户修改");
        var rows=new ArrayList<JsonNode>();
        for(String file:List.of("会议通知.docx","会议议程.xlsx","会议课件.pptx","现场示意.png","内部会议纪要.txt","公开参会说明.txt","演示图纸.dxf","会议归档包.zip","现场视频.mp4","提示音.mp3")) {
            JsonNode row=upload(directory.resolve(file),file.contains("现场")||file.contains("提示")?"MEDIA":file.contains("通知")?"PUBLICITY":file.contains("纪要")?"MINUTES":"AGENDA",null);
            assertThat(row.path("material").path("publishedVersionId").isNull()).isTrue();
            assertThat(publicStatus(publicCode(row))).isEqualTo(404);
            if(!file.startsWith("内部")) row=publication(row,true);
            rows.add(row);
        }
        JsonNode row=rows.get(5);String oldCode=publicCode(row);assertThat(publicStatus(oldCode)).isEqualTo(206);
        JsonNode replacement=upload(directory.resolve("会议纪要修订版.txt"),"AGENDA",row);
        String newCode=publicCode(replacement);assertThat(publicStatus(newCode)).isEqualTo(404);assertThat(publicStatus(oldCode)).isEqualTo(206);
        replacement=publication(replacement,true);assertThat(publicStatus(oldCode)).isEqualTo(404);assertThat(publicStatus(newCode)).isEqualTo(206);
        replacement=publication(replacement,false);assertThat(publicStatus(newCode)).isEqualTo(404);publication(replacement,true);
        assertThat(call("GET","/site-access/materials/"+row.path("material").path("id").asLong()+"/versions",null).size()).isEqualTo(2);
        long versionId=rows.get(0).path("currentVersion").path("id").asLong();
        var response=raw("POST","/site-access/material-versions/"+versionId+"/read-session",null,null,true,Map.of());
        String cookie=response.headers().firstValue("set-cookie").orElseThrow().split(";",2)[0];
        assertThat(raw("GET","/site-access/material-versions/"+versionId+"/content",null,null,false,Map.of("Cookie",cookie,"Range","bytes=0-15")).statusCode()).isEqualTo(206);
        long fileId=jdbc.queryForObject("SELECT file_id FROM site_meeting_material_version WHERE id=?",Long.class,versionId);
        assertThat(raw("GET","/files/"+fileId+"/download",null,null,true,Map.of()).statusCode()).isEqualTo(403);
        long deadline=System.nanoTime()+Duration.ofMinutes(3).toNanos();
        while(System.nanoTime()<deadline && jdbc.queryForObject("SELECT COUNT(*) FROM site_meeting_material_preview WHERE invitation_id=? AND status IN ('QUEUED','PROCESSING')",Long.class,meetingId)>0) Thread.sleep(2000);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM site_meeting_material_preview WHERE invitation_id=? AND status<>'READY'",Long.class,meetingId)).isZero();
        for(JsonNode entry: rows) {
            long id=entry.path("currentVersion").path("id").asLong();String kind=entry.path("currentVersion").path("previewKind").asText();
            if(!kind.equals("UNSUPPORTED")) assertThat(raw("GET","/site-access/material-versions/"+id+"/content?preview=true",null,null,true,Map.of("Range","bytes=0-15")).statusCode()).isIn(200,206);
        }
    }
    @Test void regeneratedOfficePreviewsContainChineseAndRetireOldCopies() throws Exception {
        var entries=jdbc.queryForList("SELECT v.id, p.file_id FROM site_meeting_material_version v JOIN site_meeting_material_preview p ON p.version_id=v.id JOIN site_meeting_material m ON m.id=v.material_id WHERE v.invitation_id=? AND p.kind='OFFICE' AND m.description='合成测试资料，仅用于本地开发演示'",meetingId);
        assertThat(entries).hasSize(3);
        for(var entry:entries) {
            long id=((Number)entry.get("id")).longValue();Long oldFile=((Number)entry.get("file_id")).longValue();
            call("POST","/site-access/material-versions/"+id+"/preview-retry",Map.of());
            long deadline=System.nanoTime()+Duration.ofMinutes(2).toNanos();
            while(System.nanoTime()<deadline && !"READY".equals(call("GET","/site-access/material-versions/"+id,null).path("previewStatus").asText())) Thread.sleep(1000);
            var response=raw("GET","/site-access/material-versions/"+id+"/content?preview=true",null,null,true,Map.of());
            assertThat(response.statusCode()).isEqualTo(200);
            try(var pdf=org.apache.pdfbox.Loader.loadPDF(response.body())) { var stripper=new org.apache.pdfbox.text.PDFTextStripper();stripper.setSortByPosition(true);assertThat(stripper.getText(pdf).replaceAll("\\s+", "")).containsAnyOf("会议","质量","议题"); }
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM file_resource WHERE id=?",Long.class,oldFile)).isZero();
        }
    }
    @Test void endedMeetingMaterialsRemainPublicWithoutReopeningRegistration() throws Exception {
        meetingId=jdbc.queryForObject("SELECT MAX(id) FROM site_visit_invitation WHERE invite_type='MEETING' AND status='OPEN' AND visit_end_time<NOW() AND internal_remark LIKE '[LOCAL_VISITOR_DEMO_%'",Long.class);
        var row=publication(upload(directory.resolve("公开参会说明.txt"),"PUBLICITY",null),true);
        String invitationToken=crypto.decrypt(jdbc.queryForObject("SELECT token_encrypted FROM site_visit_invitation WHERE id=?",String.class,meetingId));
        var resolve=raw("POST","/public/site-access/meeting/materials/resolve",json.writeValueAsBytes(Map.of("inviteToken",invitationToken)),"application/json",false,Map.of());
        assertThat(resolve.statusCode()).isEqualTo(200);assertThat(json.readTree(resolve.body()).path("data").path("ended").asBoolean()).isTrue();
        assertThat(publicStatus(publicCode(row))).isEqualTo(206);
        assertThat(raw("POST","/public/site-access/meeting/session",json.writeValueAsBytes(Map.of("inviteToken",invitationToken,"wechatCode","development-expired-probe")),"application/json",false,Map.of()).statusCode()).isEqualTo(410);
    }
    @Test void failedConversionCanRetryAndOriginalRemainsDownloadable() throws Exception {
        Path file=directory.resolve("无效Office转换测试.docx");Long id=null;
        try {
            try(var zip=new java.util.zip.ZipOutputStream(Files.newOutputStream(file))) { zip.putNextEntry(new java.util.zip.ZipEntry("[Content_Types].xml"));zip.write("invalid XML".getBytes());zip.closeEntry();zip.putNextEntry(new java.util.zip.ZipEntry("word/document.xml"));zip.write("invalid XML".getBytes());zip.closeEntry(); }
            var row=upload(file,"OTHER",null);id=row.path("material").path("id").asLong();long versionId=row.path("currentVersion").path("id").asLong();
            for(int attempt=0;attempt<2;attempt++) {
                long deadline=System.nanoTime()+Duration.ofMinutes(2).toNanos();String status="";
                while(System.nanoTime()<deadline && !"FAILED".equals(status)) { status=call("GET","/site-access/material-versions/"+versionId,null).path("previewStatus").asText();Thread.sleep(1000); }
                assertThat(status).isEqualTo("FAILED");
                assertThat(raw("GET","/site-access/material-versions/"+versionId+"/content",null,null,true,Map.of()).body()).isEqualTo(Files.readAllBytes(file));
                if(attempt==0)call("POST","/site-access/material-versions/"+versionId+"/preview-retry",Map.of());
            }
        } finally {
            Files.deleteIfExists(file);
            if(id!=null) {var impact=call("POST","/system/deletions/preview",Map.of("targetType","MEETING_MATERIAL","targetId",id));call("POST","/system/deletions/execute",Map.of("targetType","MEETING_MATERIAL","targetId",id,"confirmationToken",impact.path("confirmationToken").asText(),"acknowledged",true));}
        }
    }
    @Test void concurrentVersionUpdatesHaveOneWinnerAndDeleteAllOriginalsAndPreviews() throws Exception {
        JsonNode original=upload(directory.resolve("会议通知.docx"),"OTHER",null);long materialId=original.path("material").path("id").asLong();
        try {
            var first=metadata(directory.resolve("会议通知.docx"),"OTHER",original);var second=new LinkedHashMap<>(first);second.put("changeNote","并发第二份更新");
            String a=call("POST",base()+"/material-uploads",first).path("sessionId").asText();String b=call("POST",base()+"/material-uploads",second).path("sessionId").asText();
            byte[] file=Files.readAllBytes(directory.resolve("会议通知.docx"));chunk(a,0,file);chunk(b,0,file);
            var executor=Executors.newFixedThreadPool(2);
            try {
                var left=executor.submit(()->raw("POST",base()+"/material-uploads/"+a+"/complete","{}".getBytes(),"application/json",true,Map.of()));
                var right=executor.submit(()->raw("POST",base()+"/material-uploads/"+b+"/complete","{}".getBytes(),"application/json",true,Map.of()));
                var codes=List.of(left.get(30,TimeUnit.SECONDS).statusCode(),right.get(30,TimeUnit.SECONDS).statusCode());assertThat(codes).containsExactlyInAnyOrder(200,409);
            } finally {executor.shutdownNow();}
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM site_meeting_material_version WHERE material_id=?",Long.class,materialId)).isEqualTo(2L);
            for(String session:List.of(a,b)) if(!call("GET",base()+"/material-uploads/"+session,null).path("completed").asBoolean()) call("DELETE",base()+"/material-uploads/"+session,null);
            long deadline=System.nanoTime()+Duration.ofMinutes(2).toNanos();
            while(System.nanoTime()<deadline && jdbc.queryForObject("SELECT COUNT(*) FROM site_meeting_material_preview p JOIN site_meeting_material_version v ON v.id=p.version_id WHERE v.material_id=? AND p.status='READY'",Long.class,materialId)<2) Thread.sleep(1000);
            var impact=call("POST","/system/deletions/preview",Map.of("targetType","MEETING_MATERIAL","targetId",materialId));assertThat(impact.path("fileCount").asLong()).isEqualTo(4);
        } finally {
            var paths=jdbc.queryForList("SELECT storage_key FROM file_resource WHERE business_type IN ('MEETING_MATERIAL','MEETING_MATERIAL_PREVIEW') AND business_id=?",String.class,materialId);
            var impact=call("POST","/system/deletions/preview",Map.of("targetType","MEETING_MATERIAL","targetId",materialId));
            call("POST","/system/deletions/execute",Map.of("targetType","MEETING_MATERIAL","targetId",materialId,"confirmationToken",impact.path("confirmationToken").asText(),"acknowledged",true));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM file_resource WHERE business_type IN ('MEETING_MATERIAL','MEETING_MATERIAL_PREVIEW') AND business_id=?",Long.class,materialId)).isZero();
            for(String path:paths) assertThat(Path.of("uploads").resolve(path)).doesNotExist();
        }
    }
    @Test void exportCurrentDemoCodeForDeveloperTool() throws Exception {
        var code=call("GET",base()+"/mini-code",null).path("imageContent").asText();
        assertThat(code).startsWith("data:image/");
        Path output=directory.resolve("会议预约小程序码.png");
        Files.write(output,Base64.getDecoder().decode(code.substring(code.indexOf(',')+1)));
        Files.setPosixFilePermissions(output,java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
    }
    @Test void oneGiBUploadResumesAndConcurrentCompletionStoresOneVersion() throws Exception {
        Path file=directory.resolve("1GB上传验证.txt");Long materialId=null;
        byte[] block=new byte[MeetingMaterialUploadService.CHUNK_SIZE];Arrays.fill(block,(byte)'A');
        try {
            try(var output=Files.newOutputStream(file)){ for(int i=0;i<128;i++)output.write(block); }
            var meta=metadata(file,"OTHER",null);String id=call("POST",base()+"/material-uploads",meta).path("sessionId").asText();
            chunk(id,127,block);chunk(id,0,block);
            assertThat(call("GET",base()+"/material-uploads/"+id,null).path("uploadedChunks").size()).isEqualTo(2);
            for(int i=1;i<127;i++)chunk(id,i,block);
            var pool=Executors.newFixedThreadPool(2);
            try {
                var a=pool.submit(()->raw("POST",base()+"/material-uploads/"+id+"/complete","{}".getBytes(),"application/json",true,Map.of()));
                var b=pool.submit(()->raw("POST",base()+"/material-uploads/"+id+"/complete","{}".getBytes(),"application/json",true,Map.of()));
                assertThat(List.of(a.get(5,TimeUnit.MINUTES).statusCode(),b.get(5,TimeUnit.MINUTES).statusCode())).contains(200).allMatch(s->s==200||s==409);
            } finally {pool.shutdownNow();}
            JsonNode result=call("POST",base()+"/material-uploads/"+id+"/complete",Map.of());materialId=result.path("material").path("id").asLong();
            assertThat(result.path("currentVersion").path("fileSize").asLong()).isEqualTo(1073741824L);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM site_meeting_material_version WHERE upload_key=?",Long.class,id)).isEqualTo(1L);
            long versionId=result.path("currentVersion").path("id").asLong();
            assertThat(raw("GET","/site-access/material-versions/"+versionId+"/content",null,null,true,Map.of("Range","bytes=1073741816-1073741823")).body()).hasSize(8);
        } finally {
            Files.deleteIfExists(file);
            if(materialId!=null) {
                var impact=call("POST","/system/deletions/preview",Map.of("targetType","MEETING_MATERIAL","targetId",materialId));
                assertThat(impact.path("fileCount").asLong()).isEqualTo(1);
                call("POST","/system/deletions/execute",Map.of("targetType","MEETING_MATERIAL","targetId",materialId,"confirmationToken",impact.path("confirmationToken").asText(),"acknowledged",true));
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM site_meeting_material WHERE id=?",Long.class,materialId)).isZero();
            }
        }
    }
}
