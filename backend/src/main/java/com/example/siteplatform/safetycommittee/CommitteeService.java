package com.example.siteplatform.safetycommittee;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.file.entity.FileResource;
import com.example.siteplatform.file.mapper.FileResourceMapper;
import com.example.siteplatform.file.security.FileUploadPolicy;
import com.example.siteplatform.file.storage.*;
import com.example.siteplatform.project.mapper.ProjectInfoMapper;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CommitteeService {
    public static final String VIEW = "safety_committee.view";
    public static final String SUBMIT = "safety_committee.submit";
    public static final String EDIT = "safety_committee.edit_own";
    public static final String PENDING = "COMMITTEE_INSPECTION_PENDING";
    public static final String ATTACHMENT = "COMMITTEE_INSPECTION_ATTACHMENT";
    public static final String PREVIEW = "COMMITTEE_INSPECTION_PREVIEW";
    public static final List<String> CATEGORIES = List.of("施工安全管理", "基坑工程", "模版工程及支持体系", "脚手架工程",
            "起重机械及吊装工程", "高处作业", "施工临时用电", "有限空间作业", "拆除工程", "消防管理", "其他");
    private final CommitteeRecordMapper records;
    private final CommitteeAttachmentMapper attachments;
    private final CommitteeLogMapper logs;
    private final ProjectPermissionService permissions;
    private final ProjectInfoMapper projects;
    private final FileResourceMapper files;
    private final FileStorageManager storage;
    private final ObjectMapper json;

    public static LocalDateTime now() { return LocalDateTime.now(ZoneId.of("Asia/Shanghai")); }
    public void access(Long projectId, SysUser user, String permission) {
        if (projectId == null || user == null) throw BusinessException.forbidden("无项目访问权限");
        if (projects.selectById(projectId) == null) throw BusinessException.notFound("项目不存在");
        permissions.requireSystemPermission(user.getId(), projectId, VIEW);
        if (!VIEW.equals(permission)) permissions.requireSystemPermission(user.getId(), projectId, permission);
    }
    public void lockProject(Long projectId) {
        if (projects.selectByIdForUpdate(projectId) == null) throw BusinessException.notFound("项目不存在");
    }
    public CommitteeRecord requireRecord(Long id, SysUser user, boolean edit, boolean lock) {
        var record = records.selectById(id);
        if (record == null) throw BusinessException.notFound("巡检记录不存在");
        if (lock) { lockProject(record.getProjectId()); record = records.lock(id); }
        if (record == null) throw BusinessException.of(409, "记录已变化，请刷新");
        access(record.getProjectId(), user, edit ? EDIT : VIEW);
        if (edit && !Objects.equals(record.getInspectorId(), user.getId()))
            throw BusinessException.forbidden("只能修改本人上报的巡检记录");
        return record;
    }
    public Map<String,Object> page(Long projectId, String category, long pageNo, SysUser user) {
        access(projectId, user, VIEW);
        if (category != null && !category.isBlank()) validateMetadata(category, null);
        var q = new LambdaQueryWrapper<CommitteeRecord>().eq(CommitteeRecord::getProjectId, projectId)
                .eq(category != null && !category.isBlank(), CommitteeRecord::getCategory, category)
                .orderByDesc(CommitteeRecord::getInspectedAt).orderByDesc(CommitteeRecord::getId);
        var page = records.selectPage(new Page<>(Math.max(1, pageNo), 20), q);
        var newest = records.selectList(new LambdaQueryWrapper<CommitteeRecord>().eq(CommitteeRecord::getProjectId, projectId)
                .eq(category != null && !category.isBlank(), CommitteeRecord::getCategory, category)
                .orderByDesc(CommitteeRecord::getInspectedAt).orderByDesc(CommitteeRecord::getId).last("LIMIT 1"));
        return Map.of("records", page.getRecords().stream().map(r -> view(r, user, false)).toList(),
                "total", page.getTotal(), "latestId", newest.isEmpty() ? 0L : newest.get(0).getId(), "serverTime", now());
    }
    public RecordView detail(Long id, SysUser user) { return view(requireRecord(id,user,false,false),user,true); }

    @Transactional
    public RecordView create(CommitteeRequests.Create request, SysUser user) {
        validateMetadata(request.category(), request.conclusion());
        var ids = normalizedIds(request.attachmentIds());
        lockProject(request.projectId()); access(request.projectId(), user, SUBMIT);
        String hash = hash(jsonValue(List.of(request.projectId(), request.category(), clean(request.conclusion()), ids)));
        var existing = records.selectOne(new LambdaQueryWrapper<CommitteeRecord>().eq(CommitteeRecord::getProjectId, request.projectId())
                .eq(CommitteeRecord::getInspectorId,user.getId()).eq(CommitteeRecord::getRequestKey,request.requestKey()));
        if (existing != null) {
            if (!hash.equals(existing.getRequestHash())) throw BusinessException.of(409,"同一提交请求的内容已变化，请核对后重新提交");
            return view(existing,user,true);
        }
        var record = new CommitteeRecord();
        record.setProjectId(request.projectId()); record.setInspectorId(user.getId()); record.setInspectorName(name(user));
        record.setInspectedAt(now()); record.setCategory(request.category()); record.setConclusion(clean(request.conclusion()));
        record.setRequestKey(request.requestKey()); record.setRequestHash(hash); record.setVersion(1);
        record.setCreateTime(record.getInspectedAt()); record.setUpdateTime(record.getInspectedAt());
        one(records.insert(record)); bind(record, ids, request.requestKey(), user, true);
        log(record,user,"CREATE",null,snapshot(record)); return view(record,user,true);
    }
    @Transactional
    public RecordView edit(Long id, CommitteeRequests.Edit request, SysUser user) {
        validateMetadata(request.category(),request.conclusion()); var ids = normalizedIds(request.attachmentIds());
        var record = requireRecord(id,user,true,true);
        if (!Objects.equals(request.expectedVersion(),record.getVersion())) throw BusinessException.of(409,"记录已被修改，请刷新后重试");
        String before = snapshot(record);
        record.setCategory(request.category()); record.setConclusion(clean(request.conclusion()));
        record.setVersion(record.getVersion()+1); record.setUpdateTime(now());
        one(records.updateById(record)); bind(record,ids,null,user,false);
        log(record,user,"EDIT",before,snapshot(record)); return view(record,user,true);
    }
    private void bind(CommitteeRecord record, List<Long> ids, String draftKey, SysUser user, boolean creating) {
        Set<Long> selected = new HashSet<>(ids);
        for (var old : attachments.selectList(new LambdaQueryWrapper<CommitteeAttachment>()
                .eq(CommitteeAttachment::getRecordId,record.getId()).eq(CommitteeAttachment::getStatus,"ACTIVE"))) {
            if (!selected.contains(old.getId())) one(attachments.update(null,new LambdaUpdateWrapper<CommitteeAttachment>()
                    .eq(CommitteeAttachment::getId,old.getId()).set(CommitteeAttachment::getStatus,"HISTORICAL")
                    .set(CommitteeAttachment::getUpdateTime,now())));
        }
        for (int i=0;i<ids.size();i++) {
            var a = attachments.lock(ids.get(i));
            if (a == null || !Objects.equals(a.getProjectId(),record.getProjectId()) || !Objects.equals(a.getUploaderId(),user.getId()))
                throw BusinessException.forbidden("附件不属于本人或当前项目");
            if (a.getRecordId() != null) {
                if (creating || !Objects.equals(a.getRecordId(),record.getId()) || !"ACTIVE".equals(a.getStatus()))
                    throw BusinessException.of(409,"附件已关联其他记录或已归档");
            } else {
                if (!"PENDING".equals(a.getStatus()) || a.getExpiresAt().isBefore(now())
                        || (creating ? a.getTargetRecordId()!=null || !Objects.equals(a.getDraftKey(),draftKey)
                        : !Objects.equals(a.getTargetRecordId(),record.getId())))
                    throw BusinessException.of(409,"暂存附件用途不符或已过期，请重新上传");
                var f = file(a.getFileId());
                if (!PENDING.equals(f.getBusinessType())) throw BusinessException.of(409,"附件绑定状态已变化");
                one(files.update(null,new LambdaUpdateWrapper<FileResource>().eq(FileResource::getId,f.getId())
                        .set(FileResource::getBusinessType,ATTACHMENT).set(FileResource::getBusinessId,record.getId())));
                a.setRecordId(record.getId()); a.setStatus("ACTIVE");
                if (conversion(a.getPreviewKind())) a.setPreviewStatus("QUEUED");
            }
            a.setSortOrder(i); a.setUpdateTime(now()); one(attachments.updateById(a));
        }
    }
    public void validateUpload(CommitteeRequests.Upload request, SysUser user) {
        FileUploadPolicy.validateCommitteeMetadata(request.fileName(),request.totalSize());
        if (request.targetRecordId() == null) access(request.projectId(),user,SUBMIT);
        else {
            var record = requireRecord(request.targetRecordId(),user,true,false);
            if (!Objects.equals(record.getProjectId(),request.projectId())) throw BusinessException.forbidden("上传项目不符");
        }
    }
    public CommitteeAttachment completed(String uploadKey) {
        return attachments.selectOne(new LambdaQueryWrapper<CommitteeAttachment>().eq(CommitteeAttachment::getUploadKey,uploadKey));
    }
    @Transactional
    public AttachmentView complete(CommitteeRequests.Upload request, MultipartFile file, String key, SysUser user) {
        lockProject(request.projectId()); validateUpload(request,user);
        var prior = completed(key); if (prior != null) return attachmentView(requireAttachment(prior.getId(),user));
        FileUploadPolicy.validateCommitteeAttachment(file);
        var f = store(file,request.projectId(),null,PENDING,user.getId());
        if (f.getFileSize() != request.totalSize() || !f.getSha256().equalsIgnoreCase(request.sha256()))
            throw BusinessException.of(409,"完整文件校验失败");
        var a = new CommitteeAttachment(); a.setProjectId(request.projectId()); a.setTargetRecordId(request.targetRecordId());
        a.setDraftKey(request.draftKey()); a.setUploaderId(user.getId()); a.setFileId(f.getId()); a.setUploadKey(key);
        a.setStatus("PENDING"); a.setSortOrder(0); a.setPreviewKind(previewKind(f.getFileExtension()));
        a.setPreviewStatus(conversion(a.getPreviewKind()) ? "WAITING" : "READY"); a.setAttempts(0);
        a.setExpiresAt(now().plusHours(24)); a.setCreateTime(now()); a.setUpdateTime(a.getCreateTime());
        one(attachments.insert(a)); return attachmentView(a);
    }
    public CommitteeAttachment requireAttachment(Long id, SysUser user) {
        var a = attachments.selectById(id); if (a == null) throw BusinessException.notFound("附件不存在");
        if (a.getRecordId() != null) requireRecord(a.getRecordId(),user,false,false);
        else {
            if (!Objects.equals(a.getUploaderId(),user.getId())) throw BusinessException.forbidden("无暂存附件权限");
            if (!"PENDING".equals(a.getStatus()) || a.getExpiresAt().isBefore(now())) throw BusinessException.of(410,"暂存附件已过期");
            if (a.getTargetRecordId()!=null) requireRecord(a.getTargetRecordId(),user,true,false);
            else access(a.getProjectId(),user,SUBMIT);
        }
        return a;
    }
    @Transactional
    public void discard(Long id, SysUser user) {
        var found = requireAttachment(id,user); lockProject(found.getProjectId());
        var a = attachments.lock(id);
        if (a == null || a.getRecordId()!=null || !"PENDING".equals(a.getStatus())) throw BusinessException.of(409,"只能清理本人暂存附件");
        retire(file(a.getFileId())); one(attachments.deleteById(id));
    }
    public FileResource store(MultipartFile file, Long projectId, Long businessId, String type, Long uploader) {
        String key = "committee/"+projectId+"/"+UUID.randomUUID()+"."+FileUploadPolicy.extensionOf(file.getOriginalFilename());
        var stored = storage.store(key,file);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status!=STATUS_COMMITTED) storage.deleteQuietly(stored.provider(),stored.storageKey());
            }
        });
        var f = new FileResource(); f.setProjectId(projectId); f.setBusinessId(businessId); f.setBusinessType(type);
        f.setFileName(stored.originalFileName()); f.setOriginalFileName(stored.originalFileName()); f.setFileExtension(stored.extension());
        f.setFileType("安委会巡检附件"); f.setFileSize(stored.size()); f.setSha256(stored.sha256());
        f.setStorageProvider(stored.provider()); f.setStorageKey(stored.storageKey()); f.setFilePath(stored.storageKey());
        f.setMimeType(stored.mimeType()); f.setUploaderId(uploader); f.setStatus("UPLOADED"); f.setDeleted(0);
        f.setCreateTime(now()); f.setUpdateTime(f.getCreateTime()); one(files.insert(f)); return f;
    }
    public void retire(FileResource f) {
        one(files.update(null,new LambdaUpdateWrapper<FileResource>().eq(FileResource::getId,f.getId())
                .set(FileResource::getDeleted,1).set(FileResource::getStatus,"PENDING_DELETE").set(FileResource::getUpdateTime,now())));
        // Existing post-commit file cleanup scheduler owns physical deletion and retries.
    }
    public FileResource file(Long id) { var f=files.selectById(id); if(f==null) throw BusinessException.notFound("文件不存在"); return f; }
    public FileResource content(CommitteeAttachment a, boolean preview) {
        if (!preview) return file(a.getFileId());
        if (!"READY".equals(a.getPreviewStatus())) throw BusinessException.of(409,"预览正在处理或失败，可下载原件");
        return file(a.getPreviewFileId()==null ? a.getFileId() : a.getPreviewFileId());
    }
    public AttachmentView attachmentView(CommitteeAttachment a) {
        var f=file(a.getFileId());
        return new AttachmentView(a.getId(),f.getOriginalFileName(),f.getFileSize(),f.getFileExtension(),a.getStatus(),
                a.getPreviewKind(),a.getPreviewStatus(),a.getFailureMessage());
    }
    private RecordView view(CommitteeRecord r, SysUser user, boolean detailed) {
        var list=attachments.selectList(new LambdaQueryWrapper<CommitteeAttachment>().eq(CommitteeAttachment::getRecordId,r.getId())
                .eq(!detailed,CommitteeAttachment::getStatus,"ACTIVE").orderByAsc(CommitteeAttachment::getSortOrder).orderByAsc(CommitteeAttachment::getId));
        var history=detailed ? logs.selectList(new LambdaQueryWrapper<CommitteeLog>().eq(CommitteeLog::getRecordId,r.getId()).orderByDesc(CommitteeLog::getId)) : List.<CommitteeLog>of();
        boolean canEdit=Objects.equals(r.getInspectorId(),user.getId()) && permissions.hasSystemPermission(user.getId(),r.getProjectId(),EDIT);
        return new RecordView(r.getId(),r.getProjectId(),r.getInspectorId(),r.getInspectorName(),r.getInspectedAt(),r.getCategory(),
                r.getConclusion(),r.getVersion(),r.getUpdateTime(),canEdit,permissions.isPlatformAdmin(user.getId()),
                list.stream().map(this::attachmentView).toList(),history);
    }
    private String snapshot(CommitteeRecord r) {
        return jsonValue(Map.of("category",r.getCategory(),"conclusion",r.getConclusion(),"version",r.getVersion(),
                "attachmentIds",attachments.selectList(new LambdaQueryWrapper<CommitteeAttachment>()
                        .eq(CommitteeAttachment::getRecordId,r.getId()).eq(CommitteeAttachment::getStatus,"ACTIVE")
                        .orderByAsc(CommitteeAttachment::getSortOrder)).stream().map(CommitteeAttachment::getId).toList()));
    }
    public void log(CommitteeRecord r,SysUser user,String action,String before,String after) {
        var l=new CommitteeLog(); l.setProjectId(r.getProjectId()); l.setRecordId(r.getId()); l.setOperatorId(user.getId());
        l.setOperatorName(name(user)); l.setAction(action); l.setBeforeJson(before); l.setAfterJson(after); l.setCreateTime(now()); one(logs.insert(l));
    }
    public static void validateMetadata(String category,String conclusion) {
        if (!CATEGORIES.contains(category)) throw new BusinessException("请选择有效的安全隐患分类");
        if (conclusion!=null && conclusion.length()>2000) throw new BusinessException("检查结论最多2000字");
    }
    public static List<Long> normalizedIds(List<Long> ids) {
        if (ids==null) return List.of();
        if (ids.size()>30 || ids.stream().anyMatch(id->id==null || id<=0) || new HashSet<>(ids).size()!=ids.size())
            throw new BusinessException("附件最多30个，且不能重复");
        return List.copyOf(ids);
    }
    public static String previewKind(String extension) {
        if (Set.of("heic","heif").contains(extension)) return "HEIF";
        if (Set.of("jpg","jpeg","png","gif","bmp","webp").contains(extension)) return "IMAGE";
        if ("pdf".equals(extension)) return "PDF";
        if (FileUploadPolicy.COMMITTEE_VIDEOS.contains(extension)) return "VIDEO";
        if (Set.of("txt","csv").contains(extension)) return "TEXT";
        return "OFFICE";
    }
    public static boolean conversion(String kind) { return Set.of("VIDEO","OFFICE","HEIF").contains(kind); }
    public static void one(int count) { if(count!=1) throw BusinessException.of(409,"写入状态已变化，请刷新后重试"); }
    private static String clean(String value) { return value==null ? "" : value.trim(); }
    private static String name(SysUser user) { return user.getRealName()==null ? user.getUsername() : user.getRealName(); }
    private String jsonValue(Object value) { try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);} }
    public static String hash(String value) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(Exception e){throw new IllegalStateException(e);}
    }
    public record AttachmentView(Long id,String fileName,Long fileSize,String extension,String status,String previewKind,String previewStatus,String failureMessage) {}
    public record RecordView(Long id,Long projectId,Long inspectorId,String inspectorName,LocalDateTime inspectedAt,String category,
            String conclusion,Integer version,LocalDateTime updatedAt,boolean canEdit,boolean canDelete,List<AttachmentView> attachments,List<CommitteeLog> logs) {}
}
