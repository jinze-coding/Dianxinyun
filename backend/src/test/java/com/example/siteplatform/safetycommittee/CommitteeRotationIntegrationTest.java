package com.example.siteplatform.safetycommittee;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import java.nio.file.*;
import java.io.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Uses the existing explicitly gated clone harness and real sandboxed media conversion. */
@EnabledIfEnvironmentVariable(named="COMMITTEE_INTEGRATION",matches="ISOLATED_CLONE_ONLY")
class CommitteeRotationIntegrationTest extends CommitteeIntegrationTest {
    @Autowired CommitteePreviewService previews;
    JsonNode rotateMedia(long id,int angle,int version)throws Exception {
        return call("PUT",base()+"/attachments/"+id+"/rotation",Map.of("rotationDegrees",angle,"expectedVersion",version),authorToken);
    }
    void readyMedia(long id)throws Exception {
        long until=System.nanoTime()+Duration.ofSeconds(90).toNanos();
        while(System.nanoTime()<until){
            var state=call("GET",base()+"/attachments/"+id,null,authorToken);
            if("READY".equals(state.path("previewStatus").asText()))return;
            assertThat(state.path("previewStatus").asText()).isNotEqualTo("FAILED");
            previews.runNext();Thread.sleep(100);
        }
        throw new AssertionError("rotation preview not ready");
    }
    Path rotationPhoto(String name)throws Exception {
        Path path=ROOT.resolve(name+".png");var image=new java.awt.image.BufferedImage(80,48,java.awt.image.BufferedImage.TYPE_INT_RGB);
        var graphics=image.createGraphics();graphics.setColor(java.awt.Color.RED);graphics.fillRect(0,0,40,24);
        graphics.setColor(java.awt.Color.GREEN);graphics.fillRect(40,0,40,24);graphics.setColor(java.awt.Color.BLUE);graphics.fillRect(0,24,40,24);
        graphics.setColor(java.awt.Color.YELLOW);graphics.fillRect(40,24,40,24);graphics.dispose();javax.imageio.ImageIO.write(image,"png",path.toFile());return path;
    }
    @Test void rotationImagePersistsAcrossViewsKeepsOriginalAndInvalidatesOldGrants()throws Exception {
        String draft=key();Path photo=rotationPhoto("rotation-pixels");byte[] original=Files.readAllBytes(photo);
        long attachment=upload(photo,draft,null,false).path("id").asLong();
        assertThat(rotateMedia(attachment,90,1).path("rotationVersion").asInt()).isEqualTo(2);readyMedia(attachment);
        long id=call("POST",base()+"/records",createData("其他",draft,List.of(attachment)),authorToken).path("id").asLong();
        try {
            var grant=call("POST",base()+"/attachments/"+attachment+"/read-session?nativePlayback=true",Map.of(),readerToken).path("contentPath").asText();
            int[] colors={java.awt.Color.BLUE.getRGB(),java.awt.Color.YELLOW.getRGB(),java.awt.Color.GREEN.getRGB(),java.awt.Color.RED.getRGB()};
            int version=2;
            for(int index=0;index<4;index++) {
                int angle=(index+1)*90%360;if(index>0){rotateMedia(attachment,angle,version++);readyMedia(attachment);}
                var response=raw("GET",base()+"/attachments/"+attachment+"/content",null,readerToken).getResponse();
                assertThat(response.getStatus()).isEqualTo(200);var image=javax.imageio.ImageIO.read(new ByteArrayInputStream(response.getContentAsByteArray()));
                assertThat(image.getWidth()).isEqualTo(angle%180==0?80:48);assertThat(image.getHeight()).isEqualTo(angle%180==0?48:80);
                assertThat(image.getRGB(4,4)).isEqualTo(colors[index]);
                var reread=call("GET",base()+"/records/"+id,null,readerToken).path("attachments").get(0);
                assertThat(reread.path("rotationDegrees").asInt()).isEqualTo(angle);assertThat(reread.path("canRotate").asBoolean()).isFalse();
                assertThat(raw("GET",base()+"/attachments/"+attachment+"/content?preview=false",null,readerToken).getResponse().getContentAsByteArray()).isEqualTo(original);
            }
            assertThat(mvc.perform(get(grant).header("Range","bytes=0-7")).andReturn().getResponse().getStatus()).isEqualTo(409);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM safety_committee_log WHERE record_id=? AND action='ATTACHMENT_ROTATE'",Long.class,id)).isEqualTo(3);
            assertThat(jdbc.queryForObject("SELECT version FROM safety_committee_record WHERE id=?",Integer.class,id)).isEqualTo(1);
        } finally {deleteRecord(id);}
    }
    @Test void rotationPermissionsConflictRollbackAndHistoricalProtection()throws Exception {
        String draft=key();long attachment=upload(rotationPhoto("rotation-access"),draft,null,false).path("id").asLong();
        long id=call("POST",base()+"/records",createData("其他",draft,List.of(attachment)),authorToken).path("id").asLong();
        String path=base()+"/attachments/"+attachment+"/rotation";
        try {
            var command=Map.of("rotationDegrees",90,"expectedVersion",1);
            assertThat(status("PUT",path,command,readerToken)).isEqualTo(403);
            assertThat(status("PUT",path,Map.of("rotationDegrees",45,"expectedVersion",1),authorToken)).isEqualTo(400);
            var pool=Executors.newFixedThreadPool(2);try{
                var barrier=new CountDownLatch(1);var jobs=List.<Callable<Integer>>of(()->{barrier.await();return status("PUT",path,command,authorToken);},()->{barrier.await();return status("PUT",path,Map.of("rotationDegrees",270,"expectedVersion",1),authorToken);});
                var futures=jobs.stream().map(pool::submit).toList();barrier.countDown();assertThat(List.of(futures.get(0).get(),futures.get(1).get())).containsExactlyInAnyOrder(200,409);
            }finally{pool.shutdownNow();}
            readyMedia(attachment);
            var before=jdbc.queryForMap("SELECT rotation_degrees,rotation_version,preview_file_id,preview_status FROM safety_committee_attachment WHERE id=?",attachment);
            jdbc.execute("CREATE TRIGGER rotation_audit_fail BEFORE INSERT ON safety_committee_log FOR EACH ROW BEGIN IF NEW.action='ATTACHMENT_ROTATE' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='synthetic rotation audit rejection'; END IF; END");
            try{assertThat(status("PUT",path,Map.of("rotationDegrees",180,"expectedVersion",2),authorToken)).isEqualTo(500);}finally{jdbc.execute("DROP TRIGGER rotation_audit_fail");}
            assertThat(jdbc.queryForMap("SELECT rotation_degrees,rotation_version,preview_file_id,preview_status FROM safety_committee_attachment WHERE id=?",attachment)).isEqualTo(before);
            assertThat(jdbc.queryForObject("SELECT deleted FROM file_resource WHERE id=?",Integer.class,before.get("preview_file_id"))).isZero();
            jdbc.update("UPDATE project_business_module SET enabled=0 WHERE project_id=? AND module_code='SAFETY_COMMITTEE'",project);
            try{assertThat(status("PUT",path,Map.of("rotationDegrees",180,"expectedVersion",2),authorToken)).isEqualTo(403);}finally{jdbc.update("UPDATE project_business_module SET enabled=1 WHERE project_id=? AND module_code='SAFETY_COMMITTEE'",project);}
            call("PUT",base()+"/records/"+id,Map.of("category","其他","conclusion","","attachmentIds",List.of(),"expectedVersion",1),authorToken);
            assertThat(status("PUT",path,Map.of("rotationDegrees",180,"expectedVersion",2),authorToken)).isEqualTo(409);
        } finally {deleteRecord(id);}
    }
    @Test void rotationAdministratorCanCorrectSharedMediaWithoutEditingRecordOrPrivateDrafts()throws Exception {
        String draft=key();long attachment=upload(rotationPhoto("rotation-admin"),draft,null,false).path("id").asLong();
        String attachmentPath=base()+"/attachments/"+attachment;
        var command=Map.of("rotationDegrees",90,"expectedVersion",1);
        assertThat(status("GET",attachmentPath,null,adminToken)).isEqualTo(403);
        assertThat(status("PUT",attachmentPath+"/rotation",command,adminToken)).isEqualTo(403);
        long id=call("POST",base()+"/records",createData("其他",draft,List.of(attachment)),authorToken).path("id").asLong();
        try {
            var before=jdbc.queryForMap("SELECT * FROM safety_committee_record WHERE id=?",id);
            var detail=call("GET",base()+"/records/"+id,null,adminToken);
            assertThat(detail.path("canEdit").asBoolean()).isFalse();
            assertThat(detail.path("attachments").get(0).path("canRotate").asBoolean()).isTrue();
            assertThat(call("GET",attachmentPath,null,adminToken).path("canRotate").asBoolean()).isTrue();
            assertThat(call("PUT",attachmentPath+"/rotation",command,adminToken).path("rotationDegrees").asInt()).isEqualTo(90);
            readyMedia(attachment);
            assertThat(call("GET",attachmentPath,null,readerToken).path("rotationDegrees").asInt()).isEqualTo(90);
            assertThat(jdbc.queryForMap("SELECT * FROM safety_committee_record WHERE id=?",id)).isEqualTo(before);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM safety_committee_log WHERE record_id=? AND action='ATTACHMENT_ROTATE' AND operator_id<>?",Long.class,id,authorId)).isEqualTo(1);
            var edit=Map.of("category","其他","conclusion","禁止管理员修改正文","attachmentIds",List.of(attachment),"expectedVersion",1);
            assertThat(status("PUT",base()+"/records/"+id,edit,adminToken)).isEqualTo(403);
            jdbc.update("UPDATE project_business_module SET enabled=0 WHERE project_id=? AND module_code='SAFETY_COMMITTEE'",project);
            try{assertThat(status("PUT",attachmentPath+"/rotation",Map.of("rotationDegrees",180,"expectedVersion",2),adminToken)).isEqualTo(403);}
            finally{jdbc.update("UPDATE project_business_module SET enabled=1 WHERE project_id=? AND module_code='SAFETY_COMMITTEE'",project);}
            call("PUT",base()+"/records/"+id,Map.of("category","其他","conclusion","","attachmentIds",List.of(),"expectedVersion",1),authorToken);
            assertThat(call("GET",attachmentPath,null,adminToken).path("canRotate").asBoolean()).isFalse();
            assertThat(status("PUT",attachmentPath+"/rotation",Map.of("rotationDegrees",180,"expectedVersion",2),adminToken)).isEqualTo(409);
        }finally{deleteRecord(id);}
    }
    @Test void rotationVideoTransformsPixelsKeepsAudioAndDiscardsPendingDerivedFile()throws Exception {
        Path video=ROOT.resolve("rotation-video.mp4");
        var process=new ProcessBuilder("/opt/homebrew/bin/ffmpeg","-nostdin","-v","error","-f","lavfi","-i","color=red:s=160x96:d=1","-f","lavfi","-i","sine=frequency=440:duration=1","-c:v","libx264","-pix_fmt","yuv420p","-c:a","aac","-shortest","-y",video.toString()).redirectErrorStream(true).redirectOutput(ROOT.resolve("rotation-fixture.log").toFile()).start();assertThat(process.waitFor()).isZero();
        long attachment=upload(video,key(),null,false).path("id").asLong();rotateMedia(attachment,270,1);readyMedia(attachment);
        Path result=ROOT.resolve("rotated-result.mp4");Files.write(result,raw("GET",base()+"/attachments/"+attachment+"/content",null,authorToken).getResponse().getContentAsByteArray());
        var probe=new ProcessBuilder("/opt/homebrew/bin/ffprobe","-v","error","-show_streams","-of","json",result.toString()).start();var streams=json.readTree(probe.getInputStream()).path("streams");assertThat(probe.waitFor()).isZero();
        assertThat(streams.get(0).path("width").asInt()).isEqualTo(96);assertThat(streams.get(0).path("height").asInt()).isEqualTo(160);
        assertThat(streams.get(1).path("codec_type").asText()).isEqualTo("audio");
        long derived=jdbc.queryForObject("SELECT preview_file_id FROM safety_committee_attachment WHERE id=?",Long.class,attachment);
        call("DELETE",base()+"/attachments/"+attachment,null,authorToken);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM file_resource WHERE id=? AND deleted=0",Long.class,derived)).isZero();
    }
}
