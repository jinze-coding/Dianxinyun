package com.example.siteplatform.siteaccess.material;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.file.storage.PathMultipartFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;

@Service
public class MeetingMaterialUploadService {
    public static final int CHUNK_SIZE=8*1024*1024;
    private static final Duration TTL=Duration.ofHours(24);
    private final MeetingMaterialService service;
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final Path root;
    public MeetingMaterialUploadService(MeetingMaterialService service,StringRedisTemplate redis,ObjectMapper json,
            @Value("${file.upload.path:./uploads}") String path) {
        this.service=service; this.redis=redis; this.json=json; this.root=Paths.get(path).toAbsolutePath().normalize().resolve(".meeting-uploads");
    }
    public Map<String,Object> initialize(Long meetingId,MeetingMaterialRequests.Upload request,SysUser user) {
        service.validateUpload(meetingId,request,user);
        String id=UUID.randomUUID().toString().replace("-","")+UUID.randomUUID().toString().replace("-","");
        Session s=new Session(meetingId,user.getId(),request,System.currentTimeMillis()+TTL.toMillis());
        try {
            Files.createDirectories(directory(id)); space(request.totalSize());
            redis.opsForValue().set(key(id),json.writeValueAsString(s),TTL);
            return view(id,s);
        } catch(IOException e) { cleanup(directory(id)); throw new BusinessException("上传空间不可用"); }
    }
    public Map<String,Object> status(Long meetingId,String id,SysUser user) { return view(id,require(meetingId,id,user)); }
    public Map<String,Object> chunk(Long meetingId,String id,int index,String expectedSha,MultipartFile part,SysUser user) {
        Session s=require(meetingId,id,user); int count=count(s);
        long size=index==count-1 ? s.request.totalSize()-(long)index*CHUNK_SIZE : CHUNK_SIZE;
        if(index<0 || index>=count || part==null || part.getSize()!=size) throw new BusinessException("分片大小或序号不正确");
        if(expectedSha==null || !expectedSha.matches("[a-fA-F0-9]{64}")) throw new BusinessException("分片校验值不正确");
        String owner=lock(id);
        try {
            require(meetingId,id,user);
            if(service.completed(id)!=null) return view(id,s);
            String actual=hash(part.getInputStream());
            if(!actual.equalsIgnoreCase(expectedSha)) throw BusinessException.of(409,"分片内容校验失败");
            Path destination=directory(id).resolve(index+".part");
            if(Files.isRegularFile(destination) && actual.equals(hash(Files.newInputStream(destination)))) return view(id,s);
            Path pending=directory(id).resolve(index+".pending");
            try(InputStream in=part.getInputStream()) { Files.copy(in,pending,StandardCopyOption.REPLACE_EXISTING); }
            Files.move(pending,destination,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
            return view(id,s);
        } catch(IOException e) { throw new BusinessException("分片写入失败，请重试"); }
        finally { unlock(id,owner); }
    }
    public MeetingMaterialService.MaterialView complete(Long meetingId,String id,SysUser user) {
        Session s=require(meetingId,id,user); String owner=lock(id);
        try {
            var done=service.completed(id);
            if(done!=null) return service.view(service.requireMaterial(done.getMaterialId(),user,true,false));
            service.validateUpload(meetingId,s.request,user); space(s.request.totalSize()*2);
            Path assembled=directory(id).resolve("assembled");
            try(OutputStream out=Files.newOutputStream(assembled)) {
                for(int i=0;i<count(s);i++) {
                    Path part=directory(id).resolve(i+".part");
                    if(!Files.isRegularFile(part)) throw BusinessException.of(409,"尚有分片未上传完成");
                    Files.copy(part,out);
                }
            }
            if(Files.size(assembled)!=s.request.totalSize() || !hash(Files.newInputStream(assembled)).equalsIgnoreCase(s.request.sha256()))
                throw BusinessException.of(409,"完整文件校验失败，请重新上传");
            var result=service.complete(meetingId,s.request,new PathMultipartFile("file",s.request.fileName(),"application/octet-stream",assembled),id,user);
            cleanup(directory(id)); return result;
        } catch(IOException e) { throw new BusinessException("文件合并失败，请重试"); }
        finally { unlock(id,owner); }
    }
    public void cancel(Long meetingId,String id,SysUser user) {
        require(meetingId,id,user); String owner=lock(id);
        try {
            if(service.completed(id)!=null) throw BusinessException.of(409,"已完成的文件请在会议资料中撤下");
            redis.delete(key(id)); cleanup(directory(id));
        } finally { unlock(id,owner); }
    }
    private Session require(Long meetingId,String id,SysUser user) {
        directory(id); String raw=redis.opsForValue().get(key(id));
        if(raw==null) throw BusinessException.of(410,"上传已过期，请重新选择文件");
        Session s;
        try { s=json.readValue(raw,Session.class); } catch(IOException e) { throw BusinessException.of(410,"上传会话已失效"); }
        if(!Objects.equals(s.userId,user.getId()) || !Objects.equals(s.meetingId,meetingId)) throw BusinessException.forbidden("不能操作其他人员或会议的上传");
        if (s.expiresAt < System.currentTimeMillis()) throw BusinessException.of(410,"上传已过期，请重新选择文件");
        service.meeting(meetingId,user,true,false); return s;
    }
    private Map<String,Object> view(String id,Session s) {
        List<Integer> uploaded=new ArrayList<>();
        for(int i=0;i<count(s);i++) if(Files.isRegularFile(directory(id).resolve(i+".part"))) uploaded.add(i);
        var completed=service.completed(id);
        return Map.of("sessionId",id,"chunkSize",CHUNK_SIZE,"uploadedChunks",uploaded,"expiresAt",s.expiresAt,
                "completed",completed!=null,"materialId",completed==null?0:completed.getMaterialId());
    }
    private int count(Session s) { return (int)((s.request.totalSize()+CHUNK_SIZE-1)/CHUNK_SIZE); }
    private Path directory(String id) {
        if(id==null || !id.matches("[a-f0-9]{64}")) throw new BusinessException("上传编号不正确");
        return root.resolve(id);
    }
    private String key(String id) { return "meeting:upload:"+id; }
    private String lock(String id) {
        String owner=UUID.randomUUID().toString();
        if(!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key(id)+":lock",owner,Duration.ofMinutes(20))))
            throw BusinessException.of(409,"文件正在处理，请稍后重试");
        return owner;
    }
    private void unlock(String id,String owner) {
        redis.execute(new DefaultRedisScript<>("if redis.call('get',KEYS[1])==ARGV[1] then return redis.call('del',KEYS[1]) else return 0 end",Long.class),List.of(key(id)+":lock"),owner);
    }
    private void space(long bytes) throws IOException {
        if(Files.getFileStore(root).getUsableSpace()<bytes+100L*1024*1024) throw BusinessException.of(507,"文件存储空间不足");
    }
    public static String hash(InputStream stream) throws IOException {
        try(stream) {
            MessageDigest digest=MessageDigest.getInstance("SHA-256"); byte[] buffer=new byte[65536]; int read;
            while((read=stream.read(buffer))!=-1) digest.update(buffer,0,read);
            return HexFormat.of().formatHex(digest.digest());
        } catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    @Scheduled(fixedDelay=3600000,initialDelay=60000)
    public void cleanExpired() {
        if(!Files.isDirectory(root)) return;
        try(var paths=Files.list(root)) {
            paths.filter(Files::isDirectory).filter(p->p.getFileName().toString().matches("[a-f0-9]{64}")).forEach(p->{
                String id=p.getFileName().toString();
                if(!Boolean.TRUE.equals(redis.hasKey(key(id))) && !Boolean.TRUE.equals(redis.hasKey(key(id)+":lock"))) cleanup(p);
            });
        } catch(IOException ignored) { /* A subsequent cleanup run retries inaccessible staging directories. */ }
    }
    static void cleanup(Path directory) {
        if(!Files.exists(directory)) return;
        try(var entries=Files.walk(directory)) { entries.sorted(Comparator.reverseOrder()).forEach(p->{try {Files.deleteIfExists(p);}catch(IOException ignored){}}); }
        catch(IOException ignored) { /* Retain for cleanup retry. */ }
    }
    public record Session(Long meetingId,Long userId,MeetingMaterialRequests.Upload request,long expiresAt) {}
}
