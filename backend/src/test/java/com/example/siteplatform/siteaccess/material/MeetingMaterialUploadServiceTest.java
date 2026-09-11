package com.example.siteplatform.siteaccess.material;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.file.security.FileUploadPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.file.Path;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class MeetingMaterialUploadServiceTest {
    @TempDir Path directory;
    MeetingMaterialService service=mock(MeetingMaterialService.class);
    StringRedisTemplate redis=mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked") ValueOperations<String,String> values=mock(ValueOperations.class);
    Map<String,String> cache=new HashMap<>(); SysUser user=new SysUser(); MeetingMaterialUploadService uploads;
    @BeforeEach void setup() {
        user.setId(7L); when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenAnswer(a->cache.get(a.getArgument(0)));
        doAnswer(a->{cache.put(a.getArgument(0),a.getArgument(1));return null;}).when(values).set(anyString(),anyString(),any(Duration.class));
        when(values.setIfAbsent(anyString(),anyString(),any(Duration.class))).thenAnswer(a->cache.putIfAbsent(a.getArgument(0),a.getArgument(1))==null);
        when(redis.execute(any(RedisScript.class),anyList(),any(Object[].class))).thenAnswer(a->{cache.remove(((List<?>)a.getArgument(1)).get(0));return 1L;});
        uploads=new MeetingMaterialUploadService(service,redis,new ObjectMapper(),directory.toString());
    }
    MeetingMaterialRequests.Upload request(byte[] bytes) throws Exception { return new MeetingMaterialRequests.Upload("demo.pdf",bytes.length,hash(bytes),"演示","OTHER","",null,null,""); }
    String hash(byte[] bytes) throws Exception { return MeetingMaterialUploadService.hash(new ByteArrayInputStream(bytes)); }
    @Test void resumeRejectsWrongDigestAndCompleteIsIdempotent() throws Exception {
        byte[] bytes="%PDF-1.7 demo".getBytes(); String id=(String)uploads.initialize(19L,request(bytes),user).get("sessionId");
        var chunk=new MockMultipartFile("chunk",bytes);
        assertThatThrownBy(()->uploads.chunk(19L,id,0,"0".repeat(64),chunk,user)).isInstanceOf(BusinessException.class);
        uploads.chunk(19L,id,0,hash(bytes),chunk,user);
        assertThat(uploads.status(19L,id,user).get("uploadedChunks")).isEqualTo(List.of(0));
        uploads.chunk(19L,id,0,hash(bytes),chunk,user);
        uploads.complete(19L,id,user); verify(service).complete(eq(19L),any(),any(),eq(id),eq(user));
        var completed=new MeetingMaterialVersion(); completed.setMaterialId(1L); when(service.completed(id)).thenReturn(completed);
        uploads.complete(19L,id,user); verify(service,times(1)).complete(anyLong(),any(),any(),anyString(),any());
    }
    @Test void incompleteChunksAndCrossMeetingOwnerCannotComplete() throws Exception {
        String id=(String)uploads.initialize(19L,request("%PDF-1.7".getBytes()),user).get("sessionId");
        assertThatThrownBy(()->uploads.complete(19L,id,user)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->uploads.status(20L,id,user)).isInstanceOf(BusinessException.class);
        var other=new SysUser();other.setId(8L);assertThatThrownBy(()->uploads.status(19L,id,other)).isInstanceOf(BusinessException.class);
        verify(service,never()).complete(anyLong(),any(),any(),anyString(),any());
    }
    @Test void sizeLimitIsMeetingSpecificAndFakeMediaIsRejected() {
        FileUploadPolicy.validateMeetingMetadata("large.mp4",1073741824L);
        assertThatThrownBy(()->FileUploadPolicy.validateMeetingMetadata("large.mp4",1073741825L)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->FileUploadPolicy.validateMeetingMaterial(new MockMultipartFile("file","fake.mp4","application/octet-stream","MZexecute".getBytes()))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->FileUploadPolicy.validateProjectDocument(new MockMultipartFile("file","video.mp4","application/octet-stream",new byte[32]))).isInstanceOf(BusinessException.class);
    }
    @Test void renamedZipCannotMasqueradeAsOffice() throws Exception {
        var output=new java.io.ByteArrayOutputStream();
        try(var zip=new java.util.zip.ZipOutputStream(output)) {zip.putNextEntry(new java.util.zip.ZipEntry("README.txt"));zip.write("sample".getBytes());zip.closeEntry();}
        var file=new org.springframework.mock.web.MockMultipartFile("file","forged.docx","application/octet-stream",output.toByteArray());
        assertThatThrownBy(()->com.example.siteplatform.file.security.FileUploadPolicy.validateMeetingMaterial(file)).isInstanceOf(BusinessException.class);
    }

}
