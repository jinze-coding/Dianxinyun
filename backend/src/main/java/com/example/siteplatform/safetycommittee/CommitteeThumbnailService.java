package com.example.siteplatform.safetycommittee;

import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.file.storage.FileStorageManager;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Disposable, bounded thumbnails. No business file, record, or original preview is changed. */
@Service
public class CommitteeThumbnailService {
    private static final long TTL = Duration.ofMinutes(15).toMillis();
    private static final int MAX_ENTRIES = 128;
    private final CommitteeService service;
    private final AuthService auth;
    private final FileStorageManager storage;
    private final String converter;
    private final Path root;
    private final Map<Long,Job> jobs = new LinkedHashMap<>();
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(64),r->{Thread t=new Thread(r,"committee-thumbnail");t.setDaemon(true);return t;});

    public CommitteeThumbnailService(CommitteeService service, AuthService auth, FileStorageManager storage,
            @Value("${file.upload.path:./uploads}") String path,
            @Value("${safety-committee.converter:${site-access.material-converter:../scripts/meeting-material-convert.sh}}") String converter) {
        this.service=service;this.auth=auth;this.storage=storage;this.converter=converter;
        root=Paths.get(path).toAbsolutePath().normalize().resolve(".committee-thumbnails");
    }

    public synchronized State request(Long id,String authorization,boolean retry) {
        var user=auth.getCurrentUser(authorization);
        var attachment=service.requireAttachment(id,user);
        evict();
        Job existing=jobs.get(id);
        if(existing!=null && Objects.equals(existing.fileId,attachment.getFileId()) && existing.rotationVersion==CommitteeService.rotationVersion(attachment)
                && !(retry && "FAILED".equals(existing.status) && System.currentTimeMillis()-existing.createdAt>5000))
            return state(existing);
        if(jobs.size()>=MAX_ENTRIES) {
            var oldest=jobs.entrySet().stream().filter(e->e.getValue().bytes!=null||"FAILED".equals(e.getValue().status)).findFirst();
            if(oldest.isEmpty()) throw BusinessException.of(429,"缩略图队列繁忙，请稍后重试");
            jobs.remove(oldest.get().getKey());
        }
        Job job=new Job(id,attachment.getFileId(),CommitteeService.rotation(attachment),CommitteeService.rotationVersion(attachment),auth.snapshotReadSession(authorization));
        jobs.put(id,job);
        try {executor.execute(()->generate(job));}
        catch(RejectedExecutionException e){jobs.remove(id,job);throw BusinessException.of(429,"缩略图队列繁忙，请稍后重试");}
        return state(job);
    }

    /** Caller must authenticate the original attachment on every read, including cache hits. */
    public synchronized byte[] content(CommitteeAttachment attachment) {
        evict();
        Job job=jobs.get(attachment.getId());
        if(job==null || !Objects.equals(job.fileId,attachment.getFileId()) || job.rotationVersion!=CommitteeService.rotationVersion(attachment) || job.bytes==null)
            throw BusinessException.of(409,"缩略图尚未就绪，请重新加载");
        return job.bytes;
    }
    private State state(Job job){return new State(job.status,"FAILED".equals(job.status)?"缩略图生成失败，点击重试":"");}
    private synchronized void evict(){jobs.entrySet().removeIf(e->System.currentTimeMillis()-e.getValue().createdAt>TTL);}
    private CommitteeAttachment authorize(Job job) {
        var attachment=service.requireAttachment(job.id,auth.validateReadSession(job.session));
        if(!Objects.equals(attachment.getFileId(),job.fileId) || job.rotationVersion!=CommitteeService.rotationVersion(attachment)) throw BusinessException.of(409,"附件已变化");
        return attachment;
    }
    private void generate(Job job) {
        Path work=root.resolve(UUID.randomUUID().toString());
        try {
            synchronized(this){if(jobs.get(job.id)!=job)return;job.status="PROCESSING";}
            var attachment=authorize(job);
            var source=service.file(!CommitteeService.rotatable(attachment) && "READY".equals(attachment.getPreviewStatus()) && attachment.getPreviewFileId()!=null
                    ? attachment.getPreviewFileId() : attachment.getFileId());
            String extension=source.getFileExtension();
            if(extension==null || !extension.matches("[a-z0-9]+")) throw new IOException("invalid extension");
            Files.createDirectories(work);
            if(Files.getFileStore(work).getUsableSpace()<source.getFileSize()+256L*1024*1024) throw new IOException("insufficient workspace");
            Path input=work.resolve("input."+extension);
            try(InputStream stream=storage.load(source).getInputStream()){Files.copy(stream,input);}
            long deadline=System.nanoTime()+TimeUnit.MINUTES.toNanos(3);
            String kind=CommitteeService.previewKind(extension);
            if(Set.of("OFFICE","TEXT").contains(kind)) {
                convert(work,"OFFICE",extension,job,deadline);
                Files.move(work.resolve("output.pdf"),work.resolve("input.pdf"),StandardCopyOption.REPLACE_EXISTING);
                extension="pdf";kind="PDF";
            }
            convert(work,"PDF".equals(kind)?"PDF_THUMBNAIL":"THUMBNAIL",extension,job,deadline);
            Path output=work.resolve("output.jpg");
            if(!Files.isRegularFile(output) || Files.size(output)<4 || Files.size(output)>512*1024)
                throw new IOException("invalid thumbnail");
            byte[] bytes=Files.readAllBytes(output);
            if((bytes[0]&255)!=255 || (bytes[1]&255)!=216)throw new IOException("invalid jpeg");
            authorize(job);
            synchronized(this){if(jobs.get(job.id)==job){job.bytes=bytes;job.status="READY";}}
        } catch(Exception e) {
            if(e instanceof InterruptedException)Thread.currentThread().interrupt();
            synchronized(this){if(jobs.get(job.id)==job){job.bytes=null;job.status="FAILED";}}
        } finally {CommitteeUploadService.cleanup(work);}
    }
    private void convert(Path work,String kind,String extension,Job job,long deadline)throws Exception {
        Process process=new ProcessBuilder("bash",converter,work.toString(),kind,extension,Integer.toString(job.rotation))
                .redirectErrorStream(true).redirectOutput(work.resolve("conversion.log").toFile()).start();
        try {
            while(!process.waitFor(2,TimeUnit.SECONDS)) {
                authorize(job);
                if(System.nanoTime()>deadline)throw new IOException("thumbnail timeout");
            }
            if(process.exitValue()!=0)throw new IOException("thumbnail conversion failed");
        } finally {
            if(process.isAlive()){process.destroy();if(!process.waitFor(2,TimeUnit.SECONDS)){process.descendants().forEach(ProcessHandle::destroyForcibly);process.destroyForcibly();}}
        }
    }
    @Scheduled(fixedDelay=60000,initialDelay=30000)
    public void cleanup(){
        evict();
        if(!Files.isDirectory(root))return;
        try(var paths=Files.list(root)){
            paths.filter(Files::isDirectory).filter(p->p.getFileName().toString().matches("[a-f0-9-]{36}"))
                    .filter(p->{try{return Files.getLastModifiedTime(p).toMillis()<System.currentTimeMillis()-TTL;}catch(IOException e){return false;}})
                    .forEach(CommitteeUploadService::cleanup);
        }catch(IOException ignored){/* Disposable crash leftovers are retried next minute. */}
    }
    @PreDestroy public void close(){executor.shutdownNow();synchronized(this){jobs.clear();}}
    public record State(String status,String message){}
    private static class Job {
        final Long id,fileId;final int rotation,rotationVersion;final AuthService.ReadSessionSnapshot session;final long createdAt=System.currentTimeMillis();
        String status="QUEUED";byte[] bytes;
        Job(Long id,Long fileId,int rotation,int rotationVersion,AuthService.ReadSessionSnapshot session){this.id=id;this.fileId=fileId;this.rotation=rotation;this.rotationVersion=rotationVersion;this.session=session;}
    }
}
