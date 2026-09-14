package com.example.siteplatform.safetycommittee;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.file.security.FileUploadPolicy;
import com.example.siteplatform.file.storage.*;
import com.example.siteplatform.project.service.ProjectPermissionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class CommitteePreviewService {
    private final CommitteeAttachmentMapper attachments;
    private final CommitteeRecordMapper records;
    private final CommitteeService service;
    private final ProjectPermissionService permissions;
    private final FileStorageManager storage;
    private final TransactionTemplate transaction;
    private final Path root;
    private final String converter;
    public CommitteePreviewService(CommitteeAttachmentMapper attachments,CommitteeRecordMapper records,CommitteeService service,
            ProjectPermissionService permissions,FileStorageManager storage,PlatformTransactionManager manager,
            @Value("${file.upload.path:./uploads}") String path,
            @Value("${safety-committee.converter:${site-access.material-converter:../scripts/meeting-material-convert.sh}}") String converter) {
        this.attachments=attachments;this.records=records;this.service=service;this.permissions=permissions;this.storage=storage;
        this.transaction=new TransactionTemplate(manager);this.root=Paths.get(path).toAbsolutePath().normalize().resolve(".committee-previews");this.converter=converter;
    }
    @Transactional
    public void retry(Long id,SysUser user) {
        var a=service.requireAttachment(id,user);
        if(a.getRecordId()==null) throw BusinessException.of(409,"请提交巡检记录后生成预览");
        if(!permissions.isPlatformAdmin(user.getId())) service.requireRecord(a.getRecordId(),user,true,false);
        service.lockProject(a.getProjectId()); a=attachments.lock(id);
        if(a==null || !CommitteeService.conversion(a.getPreviewKind()) || !Set.of("FAILED","READY").contains(a.getPreviewStatus()))
            throw BusinessException.of(409,"预览正在生成或无需转换");
        if(a.getPreviewFileId()!=null) service.retire(service.file(a.getPreviewFileId()));
        CommitteeService.one(attachments.update(null,new LambdaUpdateWrapper<CommitteeAttachment>().eq(CommitteeAttachment::getId,id)
                .set(CommitteeAttachment::getPreviewStatus,"QUEUED").set(CommitteeAttachment::getPreviewFileId,null)
                .set(CommitteeAttachment::getAttempts,0).set(CommitteeAttachment::getWorkerId,null)
                .set(CommitteeAttachment::getLeaseUntil,null).set(CommitteeAttachment::getFailureMessage,null).set(CommitteeAttachment::getUpdateTime,CommitteeService.now())));
        service.log(records.selectById(a.getRecordId()),user,"PREVIEW_RETRY",null,"重新生成附件 #"+id+" 预览");
    }
    @Scheduled(fixedDelay=3000,initialDelay=20000,scheduler="committeePreviewScheduler")
    public void runNext() {
        var list=attachments.selectList(new LambdaQueryWrapper<CommitteeAttachment>().isNotNull(CommitteeAttachment::getRecordId)
                .inSql(CommitteeAttachment::getProjectId, com.example.siteplatform.project.service.ProjectBusinessModuleService.enabledProjectSql("SAFETY_COMMITTEE"))
                .and(q->q.eq(CommitteeAttachment::getPreviewStatus,"QUEUED")
                        .or(r->r.eq(CommitteeAttachment::getPreviewStatus,"PROCESSING").lt(CommitteeAttachment::getLeaseUntil,CommitteeService.now())))
                .orderByAsc(CommitteeAttachment::getId).last("LIMIT 1"));
        if(list.isEmpty()) return;
        var candidate=list.get(0);
        CommitteeAttachment task=transaction.execute(tx->{
            service.lockProject(candidate.getProjectId()); var a=attachments.lock(candidate.getId());
            if(a==null || records.selectById(a.getRecordId())==null || !("QUEUED".equals(a.getPreviewStatus())
                    || ("PROCESSING".equals(a.getPreviewStatus()) && a.getLeaseUntil()!=null && a.getLeaseUntil().isBefore(CommitteeService.now())))) return null;
            if(permissions.isBusinessModuleDisabled(a.getProjectId(), "SAFETY_COMMITTEE")) return null;
            if(a.getAttempts()>=3) {
                a.setPreviewStatus("FAILED");a.setFailureMessage("转换多次中断，请重新生成预览");CommitteeService.one(attachments.updateById(a));return null;
            }
            a.setPreviewStatus("PROCESSING");a.setWorkerId(UUID.randomUUID().toString());a.setLeaseUntil(CommitteeService.now().plusMinutes(35));
            a.setAttempts(a.getAttempts()+1);a.setUpdateTime(CommitteeService.now());CommitteeService.one(attachments.updateById(a));return a;
        });
        if(task==null) return;
        Path work=root.resolve(task.getWorkerId()); Process process=null;
        try {
            var original=service.file(task.getFileId()); Files.createDirectories(work);
            if(Files.getFileStore(work).getUsableSpace()<original.getFileSize()+1024L*1024*1024) throw new IOException("insufficient workspace");
            Path input=work.resolve("input."+original.getFileExtension());
            try(InputStream in=storage.load(original).getInputStream()){Files.copy(in,input,StandardCopyOption.REPLACE_EXISTING);}
            process=new ProcessBuilder("bash",converter,work.toString(),task.getPreviewKind(),original.getFileExtension())
                    .redirectErrorStream(true).redirectOutput(work.resolve("conversion.log").toFile()).start();
            long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(30);
            while (!process.waitFor(2,TimeUnit.SECONDS)) {
                permissions.requireBusinessModule(task.getProjectId(), "SAFETY_COMMITTEE");
                if (System.nanoTime() > deadline) throw new IOException("converter timeout");
            }
            if(process.exitValue()!=0) throw new IOException("converter failed");
            String ext=switch(task.getPreviewKind()){case "OFFICE"->"pdf";case "VIDEO"->"mp4";default->"jpg";};
            Path output=work.resolve("output."+ext);
            if(!Files.isRegularFile(output) || Files.size(output)<=0 || Files.size(output)>1024L*1024*1024) throw new IOException("invalid preview");
            var file=new PathMultipartFile("file","预览."+ext,"application/octet-stream",output);
            FileUploadPolicy.validateMeetingMaterial(file);
            transaction.executeWithoutResult(tx->{
                service.lockProject(task.getProjectId()); var a=attachments.lock(task.getId());
                if(a==null || !Objects.equals(a.getWorkerId(),task.getWorkerId()) || !"PROCESSING".equals(a.getPreviewStatus())
                        || records.selectById(a.getRecordId())==null) return;
                permissions.requireBusinessModule(a.getProjectId(), "SAFETY_COMMITTEE");
                var preview=service.store(file,a.getProjectId(),a.getRecordId(),CommitteeService.PREVIEW,a.getUploaderId());
                if(a.getPreviewFileId()!=null) service.retire(service.file(a.getPreviewFileId()));
                CommitteeService.one(attachments.update(null,new LambdaUpdateWrapper<CommitteeAttachment>().eq(CommitteeAttachment::getId,a.getId())
                        .set(CommitteeAttachment::getPreviewFileId,preview.getId()).set(CommitteeAttachment::getPreviewStatus,"READY")
                        .set(CommitteeAttachment::getWorkerId,null).set(CommitteeAttachment::getLeaseUntil,null)
                        .set(CommitteeAttachment::getFailureMessage,null).set(CommitteeAttachment::getUpdateTime,CommitteeService.now())));
            });
        } catch(Exception e) {
            if(e instanceof InterruptedException) Thread.currentThread().interrupt();
            boolean paused = com.example.siteplatform.project.service.ProjectBusinessModuleService.isModulePause(e);
            transaction.executeWithoutResult(tx->attachments.update(null,new LambdaUpdateWrapper<CommitteeAttachment>()
                    .eq(CommitteeAttachment::getId,task.getId()).eq(CommitteeAttachment::getWorkerId,task.getWorkerId())
                    .set(CommitteeAttachment::getPreviewStatus,paused ? "QUEUED" : "FAILED").set(CommitteeAttachment::getAttempts,paused ? Math.max(0,task.getAttempts()-1) : task.getAttempts()).set(CommitteeAttachment::getFailureMessage,paused ? null : "预览转换未完成，可下载原文件或重新生成")
                    .set(CommitteeAttachment::getWorkerId,null).set(CommitteeAttachment::getLeaseUntil,null).set(CommitteeAttachment::getUpdateTime,CommitteeService.now())));
        } finally {
            if(process!=null && process.isAlive()){process.descendants().forEach(ProcessHandle::destroyForcibly);process.destroyForcibly();}
            CommitteeUploadService.cleanup(work);
        }
    }
    @Scheduled(fixedDelay=3600000,initialDelay=65000)
    public void cleanStaging() {
        var expired=attachments.selectList(new LambdaQueryWrapper<CommitteeAttachment>().eq(CommitteeAttachment::getStatus,"PENDING")
                .lt(CommitteeAttachment::getExpiresAt,CommitteeService.now()).orderByAsc(CommitteeAttachment::getId).last("LIMIT 100"));
        for(var candidate:expired) transaction.executeWithoutResult(tx->{
            service.lockProject(candidate.getProjectId());var a=attachments.lock(candidate.getId());
            if(a!=null && a.getRecordId()==null && "PENDING".equals(a.getStatus()) && a.getExpiresAt().isBefore(CommitteeService.now())) {
                service.retire(service.file(a.getFileId()));CommitteeService.one(attachments.deleteById(a.getId()));
            }
        });
        if(!Files.isDirectory(root))return;
        try(var directories=Files.list(root)) {
            directories.filter(Files::isDirectory).filter(p->p.getFileName().toString().matches("[a-f0-9-]{36}"))
                    .filter(p->{try{return Files.getLastModifiedTime(p).toMillis()<System.currentTimeMillis()-Duration.ofHours(24).toMillis();}catch(IOException e){return false;}})
                    .forEach(p->{
                        if(attachments.selectCount(new LambdaQueryWrapper<CommitteeAttachment>().eq(CommitteeAttachment::getWorkerId,p.getFileName().toString())
                                .gt(CommitteeAttachment::getLeaseUntil,CommitteeService.now()))==0)CommitteeUploadService.cleanup(p);
                    });
        } catch(IOException ignored){ /* Retry next cleanup cycle. */ }
    }
}
