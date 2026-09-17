package com.example.siteplatform.system.correction;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.file.entity.FileResource;
import com.example.siteplatform.file.mapper.FileResourceMapper;
import com.example.siteplatform.file.security.FileUploadPolicy;
import com.example.siteplatform.file.storage.*;
import com.example.siteplatform.safetycommittee.CommitteeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.Resource;
import java.util.*;
import static com.example.siteplatform.system.correction.CorrectionRepository.*;

@Service
@RequiredArgsConstructor
public class CorrectionAttachments {
    private final CorrectionRepository repo;
    private final CorrectionCatalog catalog;
    private final CorrectionAccess access;
    private final FileResourceMapper mapper;
    private final FileStorageManager storage;
    public long projectId(CorrectionRequests.Upload request,SysUser user) {
        var type=catalog.require(request.targetType());return access.projectId(type,access.target(type,request.targetId(),user,false));
    }
    public CorrectionCatalog.Slot slot(CorrectionCatalog.Type type,String key) {
        return type.slots().stream().filter(s->s.key().equals(key)).findFirst().orElseThrow(()->new BusinessException("不支持的附件用途"));
    }
    public List<CorrectionCatalog.Slot> slots(CorrectionCatalog.Type type,Map<String,Object> row) {
        var slots=new ArrayList<>(type.slots());
        if(type.code().equals("EDGE_TASK"))slots.replaceAll(s->s.key().equals("photos")?new CorrectionCatalog.Slot(s.key(),s.label(),s.mode(),s.column(),s.businessType(),s.policy(),row.get("overall_photo_min") instanceof Number n?n.intValue():0,row.get("overall_photo_max") instanceof Number n?n.intValue():9):s);
        if(type.code().equals("EDGE_TASK"))for(var item:repo.rows("SELECT id,item_name,result,normal_photo_min,abnormal_photo_min,photo_max FROM general_inspection_task_item WHERE task_id=? ORDER BY id",row.get("id")))
            slots.add(new CorrectionCatalog.Slot("item-"+item.get("id"),"检查项目："+item.get("item_name"),"CHILD",string(item.get("id")),"EDGE_INSPECTION_TASK","IMAGE",((Number)item.get("ABNORMAL".equals(item.get("result"))?"abnormal_photo_min":"normal_photo_min")).intValue(),((Number)item.get("photo_max")).intValue()));
        if(type.code().equals("QUALITY_WEEKLY"))for(var item:repo.rows("SELECT id,title FROM quality_weekly_inspection_draft_item WHERE inspection_id=? ORDER BY id",row.get("id")))
            slots.add(new CorrectionCatalog.Slot("draft-"+item.get("id"),"问题照片："+item.get("title"),"DRAFT",string(item.get("id")),"QUALITY_WEEKLY_DRAFT_ITEM","IMAGE",1,20));
        return slots;
    }
    private CorrectionCatalog.Slot slot(CorrectionCatalog.Type type,Map<String,Object> row,String key) {
        return slots(type,row).stream().filter(s->s.key().equals(key)).findFirst().orElseThrow(()->new BusinessException("不支持的附件用途"));
    }
    public Map<String,List<Map<String,Object>>> current(CorrectionCatalog.Type type,Map<String,Object> row,boolean lock) {
        long target=id(row.get("id"));var groups=new LinkedHashMap<String,List<Map<String,Object>>>();
        for(var slot:slots(type,row)) {
            List<Long> ids=switch(slot.mode()) {
                case "CHILD" -> repo.ids(repo.one("SELECT photo_file_ids FROM general_inspection_task_item WHERE id=? AND task_id=?",slot.column(),target).get("photo_file_ids"));
                case "DRAFT" -> repo.rows("SELECT id FROM file_resource WHERE business_type='QUALITY_WEEKLY_DRAFT_ITEM' AND business_id=? AND deleted=0 AND status='UPLOADED' ORDER BY id",slot.column()).stream().map(r->id(r.get("id"))).toList();
                case "JSON" -> repo.ids(row.get(slot.column()));
                case "BUSINESS" -> repo.rows("SELECT id FROM file_resource WHERE business_type=? AND business_id=? AND deleted=0"+(type.code().equals("PROJECT")?" AND status='UPLOADED'":" AND status<>'ARCHIVED'")+" ORDER BY id"+(lock?" FOR UPDATE":""),business(type,slot,row),target).stream().map(r->id(r.get("id"))).toList();
                case "RESOURCE" -> List.of(target);
                case "DOCUMENT" -> row.get("current_version_id")==null?List.of():List.of(id(repo.one("SELECT file_resource_id FROM project_document_version WHERE id=?",row.get("current_version_id")).get("file_resource_id")));
                case "MEETING" -> row.get("current_version_id")==null?List.of():List.of(id(repo.one("SELECT file_id FROM site_meeting_material_version WHERE id=?",row.get("current_version_id")).get("file_id")));
                case "COMMITTEE" -> repo.rows("SELECT file_id FROM safety_committee_attachment WHERE record_id=? AND status='ACTIVE' ORDER BY sort_order,id"+(lock?" FOR UPDATE":""),target).stream().map(r->id(r.get("file_id"))).toList();
                case "SEAL" -> repo.rows("SELECT file_resource_id FROM seal_application_file WHERE application_id=? AND file_role=? AND deleted=0 ORDER BY id"+(lock?" FOR UPDATE":""),target,slot.column()).stream().map(r->id(r.get("file_resource_id"))).toList();
                default -> throw new IllegalStateException("Unknown attachment mode");
            };
            var values=new ArrayList<Map<String,Object>>();
            for(long fileId:ids) {if(lock)repo.one("SELECT id FROM file_resource WHERE id=? AND deleted=0 FOR UPDATE",fileId);values.add(info(file(fileId)));}
            groups.put(slot.key(),values);
        }
        return groups;
    }
    private String business(CorrectionCatalog.Type type,CorrectionCatalog.Slot slot,Map<String,Object> row) {
        if(type.code().equals("QUALITY_WEEKLY"))return "DRAFT".equals(row.get("status"))?"QUALITY_WEEKLY_DRAFT":"QUALITY_WEEKLY_INSPECTION";
        return slot.businessType();
    }
    public Map<String,List<Map<String,Object>>> validate(CorrectionCatalog.Type type,CorrectionService.State before,Map<String,List<Long>> selection,SysUser user,boolean lock) {
        if(selection==null)return before.attachments();
        var output=new LinkedHashMap<>(before.attachments());long target=id(before.row().get("id")),projectId=access.projectId(type,before.row());
        for(var entry:selection.entrySet()) {
            var slot=slot(type,before.row(),entry.getKey());List<Long> requested=entry.getValue();
            if(requested==null || requested.size()>slot.max() || requested.size()<slot.min() || new HashSet<>(requested).size()!=requested.size())throw new BusinessException(slot.label()+"数量应为"+slot.min()+"至"+slot.max()+"，且不能重复");
            var existing=before.attachments().get(slot.key()).stream().map(r->id(r.get("id"))).toList();
            var views=new ArrayList<Map<String,Object>>();
            for(Long fileId:requested) {
                if(fileId==null||fileId<=0)throw new BusinessException("附件编号不正确");
                var f=file(fileId);
                if(!Objects.equals(f.getProjectId(),projectId))throw BusinessException.forbidden("附件不属于当前项目");
                if(lock)repo.one("SELECT id FROM file_resource WHERE id=? AND deleted=0 FOR UPDATE",fileId);
                if(!existing.contains(fileId)) {
                    if(repo.count("SELECT COUNT(*) FROM sys_data_correction_attachment WHERE file_id=? AND target_type=? AND target_id=? AND slot_key=? AND operator_id=? AND phase='PENDING' AND expires_at>?",fileId,type.code(),target,slot.key(),user.getId(),now())!=1
                            || !"DATA_CORRECTION_PENDING".equals(f.getBusinessType()))throw BusinessException.forbidden("只能使用本次由本人上传且尚未绑定的纠错附件");
                }
                views.add(info(f));
            }
            output.put(slot.key(),views);
        }
        if(type.code().equals("QUALITY_WEEKLY") && "SUBMITTED".equals(before.row().get("status")) && ((Number)before.row().getOrDefault("submitted_issue_count",0)).intValue()==0 && output.get("photos").isEmpty())throw new BusinessException("无问题周检必须保留现场照片");
        if(Set.of("ELECTRIC_RECTIFICATION","EDGE_RECTIFICATION","QUALITY_ISSUE").contains(type.code())) {
            String key=type.code().equals("QUALITY_ISSUE")?"after":"photos";
            if(!before.attachments().get(key).isEmpty()&&output.get(key).isEmpty())throw new BusinessException("已提交的整改凭据不能全部移除");
        }
        return output;
    }
    public void apply(CorrectionCatalog.Type type,CorrectionService.State before,Map<String,List<Long>> selection,SysUser user,String reason,Map<String,Object> updates) {
        if(selection==null)return;
        for(var entry:selection.entrySet()) {
            var slot=slot(type,before.row(),entry.getKey());long target=id(before.row().get("id")),projectId=access.projectId(type,before.row());
            var original=before.attachments().get(slot.key()).stream().map(r->id(r.get("id"))).toList();var selected=entry.getValue();
            if(original.equals(selected))continue;
            List<Long> removed=original.stream().filter(i->!selected.contains(i)).toList();List<Long> added=selected.stream().filter(i->!original.contains(i)).toList();
            switch(slot.mode()) {
                case "CHILD" -> repo.update("general_inspection_task_item",Long.parseLong(slot.column()),Map.of("photo_file_ids",selected.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")),"update_time",now()));
                case "DRAFT" -> {for(long fileId:removed)repo.update("file_resource",fileId,Map.of("status","ARCHIVED","update_time",now()));}
                case "JSON" -> updates.put(slot.column(),selected.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")));
                case "BUSINESS" -> {
                    // Historical references are detached from the current view, not destroyed.
                    for(long fileId:removed)repo.update("file_resource",fileId,Map.of("status","ARCHIVED","update_time",now()));
                }
                case "COMMITTEE" -> {
                    for(long fileId:removed)repo.changed(repo.jdbc().update("UPDATE safety_committee_attachment SET status='HISTORICAL',update_time=? WHERE record_id=? AND file_id=? AND status='ACTIVE'",now(),target,fileId));
                    for(long fileId:added) {
                        var f=file(fileId);String kind=CommitteeService.previewKind(f.getFileExtension());
                        repo.insert("INSERT INTO safety_committee_attachment(project_id,record_id,target_record_id,draft_key,uploader_id,file_id,upload_key,status,sort_order,preview_kind,preview_status,rotation_degrees,rotation_version,attempts,expires_at,create_time,update_time) VALUES(?,?,?,'correction',?,?,?,'ACTIVE',?,?,?,0,1,0,?,?,?)",
                                projectId,target,target,f.getUploaderId(),fileId,"correction-"+UUID.randomUUID(),selected.indexOf(fileId),kind,CommitteeService.conversion(kind)?"QUEUED":"READY",now().plusHours(24),now(),now());
                    }
                    for(int i=0;i<selected.size();i++)repo.changed(repo.jdbc().update("UPDATE safety_committee_attachment SET sort_order=? WHERE record_id=? AND file_id=? AND status='ACTIVE'",i,target,selected.get(i)));
                }
                case "SEAL" -> {
                    for(long fileId:removed)repo.changed(repo.jdbc().update("UPDATE seal_application_file SET deleted=1,update_time=? WHERE application_id=? AND file_resource_id=? AND file_role=? AND deleted=0",now(),target,fileId,slot.column()));
                    for(long fileId:added)repo.insert("INSERT INTO seal_application_file(application_id,project_id,file_resource_id,file_role,uploader_id,uploader_name,deleted,create_time,update_time) VALUES(?,?,?,?,?,?,0,?,?)",target,projectId,fileId,slot.column(),user.getId(),name(user),now(),now());
                }
                case "DOCUMENT" -> {
                    var oldVersion=repo.one("SELECT * FROM project_document_version WHERE id=? FOR UPDATE",before.row().get("current_version_id"));
                    long version=repo.count("SELECT COALESCE(MAX(version_no),0) FROM project_document_version WHERE document_id=?",target)+1;
                    long versionId=repo.insert("INSERT INTO project_document_version(document_id,version_no,file_resource_id,change_note,external_revision,version_status,created_by,created_by_name,create_time) VALUES(?,?,?,?,?,'CURRENT',?,?,?)",target,version,selected.get(0),reason,oldVersion.get("external_revision"),user.getId(),name(user),now());
                    repo.update("project_document_version",id(oldVersion.get("id")),Map.of("version_status","SUPERSEDED","superseded_by_version_id",versionId));updates.put("current_version_id",versionId);
                }
                case "MEETING" -> {
                    long version=repo.count("SELECT COALESCE(MAX(version_no),0) FROM site_meeting_material_version WHERE material_id=?",target)+1;
                    long versionId=repo.insert("INSERT INTO site_meeting_material_version(material_id,invitation_id,project_id,version_no,file_id,public_code,upload_key,uploader_id,uploader_name,change_note,create_time) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                            target,before.row().get("invitation_id"),projectId,version,selected.get(0),UUID.randomUUID().toString().replace("-",""),"correction-"+UUID.randomUUID(),user.getId(),name(user),reason,now());
                    var f=file(selected.get(0));String kind=CommitteeService.previewKind(f.getFileExtension());if(Set.of("mp3","m4a","aac","wav","flac","ogg").contains(f.getFileExtension()))kind="AUDIO";
                    repo.insert("INSERT INTO site_meeting_material_preview(version_id,invitation_id,kind,status,attempts,create_time,update_time) VALUES(?,?,?,?,0,?,?)",versionId,before.row().get("invitation_id"),kind,CommitteeService.conversion(kind)?"QUEUED":"READY",now(),now());updates.put("current_version_id",versionId);
                    // published_version_id deliberately remains the last explicitly published version.
                }
                case "RESOURCE" -> {
                    // A new physical object is installed; the journal takes an independent immutable copy of the old one first.
                    archiveResourceBeforeReplacement(type,before,slot,user);
                    var oldResource=file(target);
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){storage.deleteQuietly(oldResource.getStorageProvider(),oldResource.getStorageKey());}});
                    var f=file(selected.get(0));
                    if(!updates.containsKey("file_name"))updates.put("file_name",f.getFileName());
                    updates.put("storage_provider",f.getStorageProvider());updates.put("storage_key",f.getStorageKey());updates.put("file_path",f.getFilePath());
                    updates.put("file_size",f.getFileSize());updates.put("sha256",f.getSha256());updates.put("mime_type",f.getMimeType());updates.put("file_extension",f.getFileExtension());updates.put("original_file_name",f.getOriginalFileName());
                }
                default -> throw new IllegalStateException("Unknown attachment adapter");
            }
            for(long fileId:added) {
                repo.update("file_resource",fileId,Map.of("business_type",slot.mode().equals("RESOURCE")?"DATA_CORRECTION_RETIRED":business(type,slot,before.row()),"business_id",slot.mode().equals("DRAFT")?Long.parseLong(slot.column()):target,"update_time",now()));
                repo.changed(repo.jdbc().update("UPDATE sys_data_correction_attachment SET expires_at=NULL,phase='AFTER' WHERE file_id=? AND phase='PENDING' AND operator_id=?",fileId,user.getId()));
            }
        }
    }
    public void metadata(CorrectionRequests.Upload request,SysUser user) {
        var type=catalog.require(request.targetType());access.target(type,request.targetId(),user,false);var slot=slot(type,access.target(type,request.targetId(),user,false),request.slotKey());
        String name=FileUploadPolicy.safeOriginalFileName(request.fileName());long size=request.totalSize();
        switch(slot.policy()) {
            case "COMMITTEE" -> FileUploadPolicy.validateCommitteeMetadata(name,size);
            case "MEETING" -> FileUploadPolicy.validateMeetingMetadata(name,size);
            default -> {
                long max=Set.of("IMAGE","PROFILE").contains(slot.policy())?FileUploadPolicy.MAX_IMAGE_BYTES:
                        type.code().equals("DOCUMENT")&&!"GENERAL".equals(access.target(type,request.targetId(),user,false).get("document_type"))?FileUploadPolicy.MAX_CIRCULATION_DOCUMENT_BYTES:FileUploadPolicy.MAX_DOCUMENT_BYTES;
                if(size<=0||size>max)throw BusinessException.of(413,"文件超过该业务允许的大小");
            }
        }
    }
    @Transactional
    public Map<String,Object> complete(CorrectionRequests.Upload request,MultipartFile upload,String key,SysUser user) {
        metadata(request,user);var type=catalog.require(request.targetType());var row=access.target(type,request.targetId(),user,true);var slot=slot(type,access.target(type,request.targetId(),user,false),request.slotKey());
        var completed=repo.rows("SELECT file_id FROM sys_data_correction_attachment WHERE upload_key=? AND operator_id=?",key,user.getId());if(!completed.isEmpty())return info(file(id(completed.get(0).get("file_id"))));
        switch(slot.policy()) {
            case "COMMITTEE" -> FileUploadPolicy.validateCommitteeAttachment(upload);
            case "MEETING" -> FileUploadPolicy.validateMeetingMaterial(upload);
            case "IMAGE" -> FileUploadPolicy.validateBusinessUpload(upload,"QUALITY_ISSUE");
            case "PROFILE" -> FileUploadPolicy.validateBusinessUpload(upload,"PROJECT_PROFILE_IMAGE_PENDING");
            default -> {if(type.code().equals("DOCUMENT")&&!"GENERAL".equals(row.get("document_type")))FileUploadPolicy.validateCirculationDocument(upload);else FileUploadPolicy.validateProjectDocument(upload);}
        }
        StoredFile stored=storage.store("corrections/"+UUID.randomUUID()+"."+FileUploadPolicy.extensionOf(request.fileName()),upload);
        rollback(stored);if(stored.size()!=request.totalSize()||!stored.sha256().equalsIgnoreCase(request.sha256()))throw new BusinessException("文件完整校验失败");
        FileResource f=persist(stored,access.projectId(type,row),request.targetId(),"DATA_CORRECTION_PENDING",user.getId());
        repo.insert("INSERT INTO sys_data_correction_attachment(project_id,target_type,target_id,slot_key,file_id,original_file_id,phase,operator_id,upload_key,file_name,file_sha256,expires_at,create_time) VALUES(?,?,?,?,?,?,'PENDING',?,?,?,?,?,?)",
                f.getProjectId(),type.code(),request.targetId(),slot.key(),f.getId(),f.getId(),user.getId(),key,f.getFileName(),f.getSha256(),now().plusHours(24),now());return info(f);
    }
    public Map<String,Object> completed(String key,SysUser user) {
        var rows=repo.rows("SELECT file_id FROM sys_data_correction_attachment WHERE upload_key=? AND operator_id=?",key,user.getId());return rows.isEmpty()?null:info(file(id(rows.get(0).get("file_id"))));
    }
    public void journal(long logId,CorrectionCatalog.Type type,CorrectionService.State before,CorrectionService.State after,SysUser user) {
        for(String phase:List.of("BEFORE","AFTER")) {
            var state=phase.equals("BEFORE")?before:after;
            for(var entry:state.attachments().entrySet())for(var item:entry.getValue()) {
                long source=id(item.get("id"));String sha=string(item.get("sha256"));
                long archived=archive(type,access.projectId(type,state.row()),id(state.row().get("id")),source,sha,user);
                repo.insert("INSERT INTO sys_data_correction_attachment(correction_id,project_id,target_type,target_id,slot_key,file_id,original_file_id,phase,operator_id,file_name,file_sha256,create_time) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                        logId,access.projectId(type,state.row()),type.code(),id(state.row().get("id")),entry.getKey(),archived,source,phase,user.getId(),item.get("name"),sha,now());
            }
        }
    }
    private void archiveResourceBeforeReplacement(CorrectionCatalog.Type type,CorrectionService.State before,CorrectionCatalog.Slot slot,SysUser user) {
        for(var f:before.attachments().get(slot.key()))archive(type,access.projectId(type,before.row()),id(before.row().get("id")),id(f.get("id")),string(f.get("sha256")),user);
    }
    private long archive(CorrectionCatalog.Type type,long project,long target,long source,String hash,SysUser user) {
        var existing=repo.rows("SELECT a.file_id FROM sys_data_correction_attachment a JOIN file_resource f ON f.id=a.file_id AND f.deleted=0 WHERE a.target_type=? AND a.target_id=? AND a.original_file_id=? AND a.file_sha256=? AND f.business_type='DATA_CORRECTION_HISTORY' ORDER BY a.id LIMIT 1",type.code(),target,source,hash);
        if(!existing.isEmpty())return id(existing.get(0).get("file_id"));
        var original=file(source);if(!hash.isEmpty()&&!Objects.equals(hash,original.getSha256()))throw BusinessException.of(409,"附件内容已变化，请重新核对");
        StoredFile stored=storage.copy(original,"correction-history/"+UUID.randomUUID()+"."+original.getFileExtension());rollback(stored);
        FileResource copy=persist(stored,project,target,"DATA_CORRECTION_HISTORY",original.getUploaderId());
        repo.insert("INSERT INTO sys_data_correction_attachment(project_id,target_type,target_id,slot_key,file_id,original_file_id,phase,operator_id,file_name,file_sha256,create_time) VALUES(?,?,?,'archive',?,?,'BEFORE',?,?,?,?)",project,type.code(),target,copy.getId(),source,user.getId(),copy.getFileName(),hash,now());
        return copy.getId();
    }
    public FileResource download(String code,long target,long fileId,Long correctionId,String phase,SysUser user) {
        var type=catalog.require(code);access.operator(user);
        if(correctionId!=null) {
            var record=repo.one("SELECT project_id FROM sys_data_correction_log WHERE id=? AND target_type=? AND target_id=?",correctionId,code,target);access.project(id(record.get("project_id")),type,user);
            if(!Set.of("BEFORE","AFTER").contains(phase))throw new BusinessException("请选择历史附件版本");
            var ref=repo.one("SELECT file_id FROM sys_data_correction_attachment WHERE correction_id=? AND original_file_id=? AND phase=? ORDER BY id LIMIT 1",correctionId,fileId,phase);return file(id(ref.get("file_id")));
        }
        var row=access.target(type,target,user,false);
        boolean current=current(type,row,false).values().stream().flatMap(Collection::stream).anyMatch(f->id(f.get("id"))==fileId);
        if(!current && repo.count("SELECT COUNT(*) FROM sys_data_correction_attachment WHERE target_type=? AND target_id=? AND file_id=? AND operator_id=? AND phase='PENDING' AND expires_at>?",code,target,fileId,user.getId(),now())!=1)throw BusinessException.forbidden("无该附件的读取资格");
        return file(fileId);
    }
    public Resource resource(FileResource file){return storage.load(file);}
    private FileResource file(long id){
        // Correction adapters update metadata through JDBC; avoid a stale MyBatis session cache after replacement.
        var rows=repo.jdbc().query("SELECT * FROM file_resource WHERE id=? AND deleted=0",new org.springframework.jdbc.core.BeanPropertyRowMapper<>(FileResource.class),id);
        if(rows.isEmpty())throw BusinessException.notFound("附件已失效");return rows.get(0);
    }
    public static Map<String,Object> info(FileResource f) {var m=new LinkedHashMap<String,Object>();m.put("id",f.getId());m.put("name",f.getFileName());m.put("size",f.getFileSize());m.put("extension",f.getFileExtension());m.put("sha256",f.getSha256());return m;}
    private FileResource persist(StoredFile stored,long project,long target,String business,Long userId) {
        var f=new FileResource();f.setProjectId(project);f.setBusinessId(target);f.setBusinessType(business);f.setUploaderId(userId);f.setFileName(stored.originalFileName());f.setOriginalFileName(stored.originalFileName());f.setFileType("纠错附件");
        f.setFileSize(stored.size());f.setSha256(stored.sha256());f.setStorageProvider(stored.provider());f.setStorageKey(stored.storageKey());f.setFilePath(stored.storageKey());f.setMimeType(stored.mimeType());f.setFileExtension(stored.extension());f.setStatus("UPLOADED");f.setDeleted(0);f.setCreateTime(now());f.setUpdateTime(now());repo.changed(mapper.insert(f));return f;
    }
    private void rollback(StoredFile stored){TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCompletion(int status){if(status!=STATUS_COMMITTED)storage.deleteQuietly(stored.provider(),stored.storageKey());}});}
    private static String name(SysUser user){return user.getRealName()==null?user.getUsername():user.getRealName();}
}
