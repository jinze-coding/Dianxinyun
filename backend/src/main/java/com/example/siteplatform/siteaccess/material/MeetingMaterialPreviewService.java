package com.example.siteplatform.siteaccess.material;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.file.mapper.FileResourceMapper;
import com.example.siteplatform.file.entity.FileResource;
import com.example.siteplatform.file.security.FileUploadPolicy;
import com.example.siteplatform.file.storage.*;
import com.example.siteplatform.siteaccess.mapper.SiteVisitInvitationMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class MeetingMaterialPreviewService {
    private final MeetingMaterialPreviewMapper previews;
    private final MeetingMaterialVersionMapper versions;
    private final SiteVisitInvitationMapper invitations;
    private final FileResourceMapper files;
    private final FileStorageManager storage;
    private final MeetingMaterialService service;
    private final TransactionTemplate transaction;
    private final TransactionTemplate cleanupTransaction;
    private final Path root;
    private final String converter;
    public MeetingMaterialPreviewService(MeetingMaterialPreviewMapper previews,MeetingMaterialVersionMapper versions,
            SiteVisitInvitationMapper invitations,FileResourceMapper files,FileStorageManager storage,MeetingMaterialService service,
            PlatformTransactionManager manager,@Value("${file.upload.path:./uploads}") String path,
            @Value("${site-access.material-converter:../scripts/meeting-material-convert.sh}") String converter) {
        this.previews=previews; this.versions=versions; this.invitations=invitations; this.files=files; this.storage=storage;
        this.service=service; this.transaction=new TransactionTemplate(manager);
        this.cleanupTransaction=new TransactionTemplate(manager);
        this.cleanupTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.root=Paths.get(path).toAbsolutePath().normalize().resolve(".meeting-previews"); this.converter=converter;
    }
    @Transactional
    public void retry(Long versionId,SysUser user) {
        var v=service.requireVersion(versionId,user); var m=service.requireMaterial(v.getMaterialId(),user,true,true);
        var p=service.preview(versionId); if(p==null) throw BusinessException.notFound("预览任务不存在");
        p=previews.lock(p.getId());
        if(!Set.of("OFFICE","HEIF","VIDEO","AUDIO").contains(p.getKind()) || !Set.of("FAILED","READY").contains(p.getStatus()))
            throw BusinessException.of(409,"预览正在生成或此格式无需转换");
        if(p.getFileId()!=null) retirePreviewFile(files.selectById(p.getFileId()));
        MeetingMaterialService.one(previews.update(null,new LambdaUpdateWrapper<MeetingMaterialPreview>().eq(MeetingMaterialPreview::getId,p.getId())
                .set(MeetingMaterialPreview::getStatus,"QUEUED").set(MeetingMaterialPreview::getAttempts,0)
                .set(MeetingMaterialPreview::getFileId,null)
                .set(MeetingMaterialPreview::getFailureMessage,null).set(MeetingMaterialPreview::getLeaseUntil,null).set(MeetingMaterialPreview::getWorkerId,null)));
        service.audit(m,user,"MATERIAL_PREVIEW_RETRY","重新生成资料预览",Map.of("versionId",versionId));
    }
    private void retirePreviewFile(FileResource file) {
        if(file==null) return;
        MeetingMaterialService.one(files.update(null,new LambdaUpdateWrapper<FileResource>().eq(FileResource::getId,file.getId())
                .set(FileResource::getDeleted,1).set(FileResource::getStatus,"PENDING_DELETE")));
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                try {
                    storage.delete(file);
                    cleanupTransaction.executeWithoutResult(tx->MeetingMaterialService.one(files.purgeById(file.getId())));
                } catch(RuntimeException e) {
                    cleanupTransaction.executeWithoutResult(tx->files.markPhysicalDeleteFailed(file.getId()));
                }
            }
        });
    }
    @Scheduled(fixedDelay=3000,initialDelay=20000,scheduler="meetingPreviewScheduler")
    public void runNext() {
        var candidates=previews.selectList(new LambdaQueryWrapper<MeetingMaterialPreview>()
                .and(q->q.eq(MeetingMaterialPreview::getStatus,"QUEUED").or(r->r.eq(MeetingMaterialPreview::getStatus,"PROCESSING").lt(MeetingMaterialPreview::getLeaseUntil,LocalDateTime.now())))
                .orderByAsc(MeetingMaterialPreview::getId).last("LIMIT 1"));
        if(candidates.isEmpty()) return;
        MeetingMaterialPreview task=transaction.execute(tx->{
            var p=previews.lock(candidates.get(0).getId());
            if(p==null || !("QUEUED".equals(p.getStatus()) || ("PROCESSING".equals(p.getStatus()) && p.getLeaseUntil().isBefore(LocalDateTime.now())))) return null;
            if(p.getAttempts()>=3) { p.setStatus("FAILED"); p.setFailureMessage("转换多次中断，可重新生成预览"); previews.updateById(p); return null; }
            p.setStatus("PROCESSING"); p.setWorkerId(UUID.randomUUID().toString()); p.setLeaseUntil(LocalDateTime.now().plusMinutes(35));
            p.setAttempts(p.getAttempts()+1); p.setUpdateTime(LocalDateTime.now()); MeetingMaterialService.one(previews.updateById(p)); return p;
        });
        if(task==null) return;
        Path work=root.resolve(task.getWorkerId());
        Process process=null;
        try {
            var v=versions.selectById(task.getVersionId()); if(v==null) return;
            var original=files.selectById(v.getFileId()); if(original==null) throw new IOException("missing file");
            Files.createDirectories(work);
            if(Files.getFileStore(work).getUsableSpace()<original.getFileSize()+2L*FileUploadPolicy.MAX_MEETING_BYTES)
                throw new IOException("insufficient preview workspace");
            Path input=work.resolve("input."+original.getFileExtension());
            try(InputStream stream=storage.load(original).getInputStream()) { Files.copy(stream,input,StandardCopyOption.REPLACE_EXISTING); }
            process=new ProcessBuilder("bash",converter,work.toString(),task.getKind(),original.getFileExtension())
                    .redirectErrorStream(true).redirectOutput(work.resolve("conversion.log").toFile()).start();
            if(!process.waitFor(30,TimeUnit.MINUTES)) {
                process.destroy(); if(!process.waitFor(10,TimeUnit.SECONDS)) {process.descendants().forEach(ProcessHandle::destroyForcibly); process.destroyForcibly();}
                throw new IOException("conversion timeout");
            }
            String ext=switch(task.getKind()) {case "OFFICE"->"pdf";case "HEIF"->"jpg";case "VIDEO"->"mp4";default->"mp3";};
            Path output=work.resolve("output."+ext);
            if(process.exitValue()!=0 || !Files.isRegularFile(output) || Files.size(output)==0) throw new IOException("conversion failed");
            FileUploadPolicy.validateMeetingMaterial(new PathMultipartFile("file","预览."+ext,"application/octet-stream",output));
            transaction.executeWithoutResult(tx->{
                if(invitations.selectForUpdate(v.getInvitationId())==null || versions.selectById(v.getId())==null) return;
                var current=previews.lock(task.getId());
                if(current==null || !Objects.equals(current.getWorkerId(),task.getWorkerId()) || !"PROCESSING".equals(current.getStatus())) return;
                var file=service.store(new PathMultipartFile("file","预览."+ext,"application/octet-stream",output),v.getProjectId(),v.getMaterialId(),"MEETING_MATERIAL_PREVIEW",v.getUploaderId());
                MeetingMaterialService.one(previews.update(null,new LambdaUpdateWrapper<MeetingMaterialPreview>().eq(MeetingMaterialPreview::getId,current.getId())
                        .set(MeetingMaterialPreview::getStatus,"READY").set(MeetingMaterialPreview::getFileId,file.getId())
                        .set(MeetingMaterialPreview::getFailureMessage,null).set(MeetingMaterialPreview::getLeaseUntil,null).set(MeetingMaterialPreview::getUpdateTime,LocalDateTime.now())));
            });
        } catch(Exception e) {
            if(e instanceof InterruptedException) Thread.currentThread().interrupt();
            previews.update(null,new LambdaUpdateWrapper<MeetingMaterialPreview>().eq(MeetingMaterialPreview::getId,task.getId()).eq(MeetingMaterialPreview::getWorkerId,task.getWorkerId())
                    .set(MeetingMaterialPreview::getStatus,"FAILED").set(MeetingMaterialPreview::getFailureMessage,"预览生成失败，请重试或下载原文件")
                    .set(MeetingMaterialPreview::getLeaseUntil,null).set(MeetingMaterialPreview::getUpdateTime,LocalDateTime.now()));
        } finally {
            if(process!=null && process.isAlive()) {
                var descendants=process.descendants().toList();
                process.destroy();
                descendants.forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
            }
            MeetingMaterialUploadService.cleanup(work);
        }
    }

    @Scheduled(fixedDelay=3600000,initialDelay=120000)
    public void cleanAbandonedWorkDirectories() {
        if (!Files.isDirectory(root)) return;
        long oldest=System.currentTimeMillis()-3600000;
        try(var directories=Files.list(root)) {
            directories.filter(Files::isDirectory).filter(p->p.getFileName().toString().matches("[a-f0-9-]{36}")).forEach(p->{
                try {
                    if(Files.getLastModifiedTime(p).toMillis()>=oldest) return;
                    Long active=previews.selectCount(new LambdaQueryWrapper<MeetingMaterialPreview>()
                            .eq(MeetingMaterialPreview::getWorkerId,p.getFileName().toString())
                            .eq(MeetingMaterialPreview::getStatus,"PROCESSING").gt(MeetingMaterialPreview::getLeaseUntil,LocalDateTime.now()));
                    if(active==0) MeetingMaterialUploadService.cleanup(p);
                } catch(IOException ignored) { /* Retry on the next cleanup cycle. */ }
            });
        } catch(IOException ignored) { /* Retry on the next cleanup cycle. */ }
    }
}
