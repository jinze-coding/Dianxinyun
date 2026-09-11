package com.example.siteplatform.siteaccess.material;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.common.*;
import com.example.siteplatform.file.entity.FileResource;
import com.example.siteplatform.file.storage.FileStorageManager;
import com.example.siteplatform.siteaccess.service.VisitorDataCryptoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.redis.core.*;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class MeetingMaterialContentTest {
    @TempDir Path directory;
    MeetingMaterialService service=mock(MeetingMaterialService.class);
    AuthService auth=mock(AuthService.class);
    FileStorageManager storage=mock(FileStorageManager.class);
    StringRedisTemplate redis=mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked") ValueOperations<String,String> values=mock(ValueOperations.class);
    Map<String,String> grants=new HashMap<>();
    SysUser user=new SysUser(); MeetingMaterialContentService content; MeetingMaterialVersion version=new MeetingMaterialVersion();
    @BeforeEach void setup() throws Exception {
        var env=new MockEnvironment();env.setActiveProfiles("test");
        content=new MeetingMaterialContentService(service,auth,redis,new VisitorDataCryptoService("",env),new ObjectMapper(),storage);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenAnswer(a->grants.get(a.getArgument(0)));
        doAnswer(a->{grants.put(a.getArgument(0),a.getArgument(1));return null;}).when(values).set(anyString(),anyString(),any(Duration.class));
        user.setId(7L);when(auth.getCurrentUser("Bearer valid-test-login")).thenReturn(user);
        version.setId(1L);when(service.requireVersion(1L,user)).thenReturn(version);
        var file=new FileResource();file.setFileName("video.mp4");file.setOriginalFileName("video.mp4");file.setFileExtension("mp4");file.setFileSize(10L);
        when(service.contentFile(eq(version),anyBoolean())).thenReturn(file);
        Path path=directory.resolve("video.mp4");Files.writeString(path,"0123456789");when(storage.load(file)).thenReturn(new FileSystemResource(path));
    }
    Cookie cookie() { var response=new MockHttpServletResponse();var request=new MockHttpServletRequest();request.setSecure(true);content.issue(1L,"Bearer valid-test-login",request,response);
        String header=response.getHeader("Set-Cookie");assertThat(header).contains("HttpOnly","Secure","SameSite=Strict","/api/v1/site-access/material-versions/1/content");
        return new Cookie("meeting_read",header.split(";",2)[0].split("=",2)[1]); }
    @Test void rangeUsesBoundCookieAndReturnsOnlyRequestedBytes() throws Exception {
        var controller=new MeetingMaterialController(auth,service,mock(MeetingMaterialUploadService.class),content,mock(MeetingMaterialPreviewService.class));
        var mvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get(MeetingMaterialContentService.path(1L)).cookie(cookie()).header("Range","bytes=3-6"))
                .andExpect(status().isPartialContent()).andExpect(header().string("Content-Range","bytes 3-6/10")).andExpect(content().string("3456"));
        mvc.perform(get(MeetingMaterialContentService.path(1L))).andExpect(status().isUnauthorized());
    }
    @Test void cookieCannotCrossVersionOrSurviveLoginRevocation() {
        var request=new MockHttpServletRequest();request.setCookies(cookie());
        assertThat(content.reader(1L,request)).isSameAs(user);
        assertThatThrownBy(()->content.reader(2L,request)).isInstanceOf(BusinessException.class);
        when(auth.getCurrentUser("Bearer valid-test-login")).thenThrow(BusinessException.of(401,"登录已撤销"));
        assertThatThrownBy(()->content.reader(1L,request)).isInstanceOf(BusinessException.class);
    }
    @Test void streamStorageSupportsSuffixRangeAndRejectsUnsatisfiableRange() throws Exception {
        when(storage.load(any())).thenAnswer(call->new org.springframework.core.io.InputStreamResource(new java.io.ByteArrayInputStream("0123456789".getBytes())));
        var controller=new MeetingMaterialController(auth,service,mock(MeetingMaterialUploadService.class),content,mock(MeetingMaterialPreviewService.class));
        var mvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get(MeetingMaterialContentService.path(1L)).cookie(cookie()).header("Range","bytes=-2"))
                .andExpect(status().isPartialContent()).andExpect(header().string("Content-Range","bytes 8-9/10")).andExpect(content().string("89"));
        mvc.perform(get(MeetingMaterialContentService.path(1L)).cookie(cookie()).header("Range","bytes=10-11"))
                .andExpect(status().isRequestedRangeNotSatisfiable());
    }
}
