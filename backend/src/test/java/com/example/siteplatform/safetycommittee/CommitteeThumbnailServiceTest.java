package com.example.siteplatform.safetycommittee;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.file.entity.FileResource;
import com.example.siteplatform.file.storage.FileStorageManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class CommitteeThumbnailServiceTest {
 @TempDir Path root;
 CommitteeService service=mock(CommitteeService.class);AuthService auth=mock(AuthService.class);FileStorageManager storage=mock(FileStorageManager.class);
 CommitteeThumbnailService thumbnails;SysUser user=new SysUser();CommitteeAttachment attachment=new CommitteeAttachment();FileResource file=new FileResource();
 AuthService.ReadSessionSnapshot session=new AuthService.ReadSessionSnapshot(7L,"synthetic",1,System.currentTimeMillis()+600000);
 @BeforeEach void setup()throws Exception {
  user.setId(7L);attachment.setId(3L);attachment.setFileId(4L);attachment.setStatus("PENDING");attachment.setPreviewStatus("WAITING");attachment.setPreviewKind("IMAGE");
  file.setId(4L);file.setFileExtension("png");file.setFileSize(6L);
  when(auth.getCurrentUser("fixture")).thenReturn(user);when(auth.snapshotReadSession("fixture")).thenReturn(session);when(auth.validateReadSession(session)).thenReturn(user);
  when(service.requireAttachment(3L,user)).thenReturn(attachment);when(service.file(4L)).thenReturn(file);when(storage.load(file)).thenReturn(new ByteArrayResource(new byte[]{1,2,3}));
  Path converter=root.resolve("converter.sh");Files.writeString(converter,"#!/bin/bash\nprintf '\\377\\330test' > \"$1/output.jpg\"\nprintf '%s\\n' \"$2:$3\" >> \"$1/../../calls\"\n");
  thumbnails=new CommitteeThumbnailService(service,auth,storage,root.toString(),converter.toString());
 }
 @AfterEach void close(){thumbnails.close();}
 String completed(long id)throws Exception {
  long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(90);
  while(System.nanoTime()<deadline){String status=thumbnails.request(id,"fixture",false).status();if(Set.of("READY","FAILED").contains(status))return status;Thread.sleep(20);}
  throw new AssertionError("thumbnail did not finish");
 }
 @Test void pendingThumbnailDoesNotBindOrChangeOriginal()throws Exception {
  assertThat(completed(3)).isEqualTo("READY");assertThat(thumbnails.content(attachment)).startsWith((byte)255,(byte)216);
  assertThat(attachment.getRecordId()).isNull();assertThat(attachment.getPreviewStatus()).isEqualTo("WAITING");verify(service,never()).store(any(),any(),any(),any(),any());
 }
 @Test void concurrentAndCachedRequestsOnlyConvertOnce()throws Exception {
  var pool=Executors.newFixedThreadPool(8);try{var tasks=new ArrayList<Callable<Object>>();for(int i=0;i<30;i++)tasks.add(()->thumbnails.request(3L,"fixture",false));pool.invokeAll(tasks);}finally{pool.shutdownNow();}
  assertThat(completed(3)).isEqualTo("READY");for(int i=0;i<10;i++)thumbnails.request(3L,"fixture",false);assertThat(Files.readAllLines(root.resolve("calls"))).hasSize(1);
 }
 @Test void cachedThumbnailStillRequiresCurrentAttachmentAccess()throws Exception {
  assertThat(completed(3)).isEqualTo("READY");var content=new CommitteeContentService(service,auth,mock(StringRedisTemplate.class),new ObjectMapper(),storage,thumbnails);
  var request=new MockHttpServletRequest();request.addHeader("Authorization","fixture");var response=content.content(3L,true,true,request);
  assertThat(response.getHeaders().getContentType().toString()).isEqualTo("image/jpeg");assertThat(response.getHeaders().getCacheControl()).contains("no-store");
  assertThatThrownBy(()->content.content(3L,true,true,new MockHttpServletRequest())).isInstanceOf(BusinessException.class);
  when(service.requireAttachment(3L,user)).thenThrow(BusinessException.forbidden("project disabled"));
  assertThatThrownBy(()->content.content(3L,true,true,request)).isInstanceOf(BusinessException.class).hasMessageContaining("disabled");
  assertThatThrownBy(()->thumbnails.request(3L,"fixture",false)).isInstanceOf(BusinessException.class);
 }
 @Test void revokedSessionDuringConversionCannotPublish()throws Exception {
  when(auth.validateReadSession(session)).thenReturn(user).thenThrow(BusinessException.forbidden("session revoked"));
  assertThat(completed(3)).isEqualTo("FAILED");assertThatThrownBy(()->thumbnails.content(attachment)).isInstanceOf(BusinessException.class);
 }
 @Test void invalidImageCannotPublish()throws Exception {
  Files.writeString(root.resolve("converter.sh"),"#!/bin/bash\nprintf '<html>bad</html>' > \"$1/output.jpg\"\n");
  assertThat(completed(3)).isEqualTo("FAILED");assertThatThrownBy(()->thumbnails.content(attachment)).isInstanceOf(BusinessException.class);
 }
 @Test void rotationInvalidatesCachedThumbnailsAndRechecksVersion()throws Exception {
  attachment.setPreviewKind("IMAGE");attachment.setRotationVersion(1);attachment.setRotationDegrees(0);
  assertThat(completed(3)).isEqualTo("READY");attachment.setRotationVersion(2);attachment.setRotationDegrees(90);
  assertThatThrownBy(()->thumbnails.content(attachment)).isInstanceOf(BusinessException.class);
  assertThat(completed(3)).isEqualTo("READY");assertThat(Files.readAllLines(root.resolve("calls"))).hasSize(2);
 }
 @Test void existingOfficePdfIsReused()throws Exception {
  var pdf=new FileResource();pdf.setId(8L);pdf.setFileExtension("pdf");pdf.setFileSize(6L);attachment.setPreviewStatus("READY");attachment.setPreviewFileId(8L);attachment.setPreviewKind("OFFICE");
  when(service.file(8L)).thenReturn(pdf);when(storage.load(pdf)).thenReturn(new ByteArrayResource("%PDF-test".getBytes()));
  assertThat(completed(3)).isEqualTo("READY");assertThat(Files.readAllLines(root.resolve("calls"))).containsExactly("PDF_THUMBNAIL:pdf");
 }
 @Test @EnabledIfEnvironmentVariable(named="COMMITTEE_THUMBNAIL_FIXTURES",matches=".+")
 void realSandboxedConvertersGenerateReadableThumbnails()throws Exception {
  thumbnails.close();thumbnails=new CommitteeThumbnailService(service,auth,storage,root.toString(),Paths.get("../scripts/meeting-material-convert.sh").toAbsolutePath().normalize().toString());
  Path fixtures=Path.of(System.getenv("COMMITTEE_THUMBNAIL_FIXTURES"));
  try(var paths=Files.list(fixtures)){long id=100;for(Path path:paths.filter(Files::isRegularFile).sorted().toList()){
   var a=new CommitteeAttachment();a.setId(++id);a.setFileId(id);a.setStatus("PENDING");a.setPreviewStatus("WAITING");
   var f=new FileResource();f.setId(id);f.setFileSize(Files.size(path));f.setFileExtension(path.getFileName().toString().replaceAll("^.*\\.",""));
   a.setPreviewKind(CommitteeService.previewKind(f.getFileExtension()));
   when(service.requireAttachment(id,user)).thenReturn(a);when(service.file(id)).thenReturn(f);when(storage.load(f)).thenReturn(new FileSystemResource(path));
   assertThat(completed(id)).as(path.getFileName().toString()).isEqualTo("READY");
   var image=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(thumbnails.content(a)));assertThat(image).isNotNull();assertThat(Math.max(image.getWidth(),image.getHeight())).isLessThanOrEqualTo(640);
   int darkest=255,lightest=0;for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){int rgb=image.getRGB(x,y);int value=((rgb>>16&255)+(rgb>>8&255)+(rgb&255))/3;darkest=Math.min(darkest,value);lightest=Math.max(lightest,value);}
   assertThat(lightest-darkest).as("Fixture content must not render blank: %s",path.getFileName()).isGreaterThan(30);
   Files.write(fixtures.resolveSibling("thumbnail-"+path.getFileName()+".jpg"),thumbnails.content(a));
  }}
 }
}
