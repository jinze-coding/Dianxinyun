package com.example.siteplatform.siteaccess.material;

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
import com.example.siteplatform.siteaccess.entity.*;
import com.example.siteplatform.siteaccess.mapper.*;
import com.example.siteplatform.siteaccess.service.VisitorDataCryptoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MeetingMaterialService {
    public static final String FILE_TYPE = "MEETING_MATERIAL";
    public static final Set<String> CATEGORIES = Set.of("PUBLICITY", "AGENDA", "MINUTES", "MEDIA", "OTHER");
    private final MeetingMaterialMapper materials;
    private final MeetingMaterialVersionMapper versions;
    private final MeetingMaterialPreviewMapper previews;
    private final SiteVisitInvitationMapper invitations;
    private final ProjectInfoMapper projects;
    private final ProjectPermissionService permissions;
    private final FileResourceMapper files;
    private final FileStorageManager storage;
    private final SiteMeetingVisitAuditLogMapper audits;
    private final VisitorDataCryptoService crypto;
    private final ObjectMapper json;

    public SiteVisitInvitation meeting(Long id, SysUser user, boolean manage, boolean lock) {
        SiteVisitInvitation meeting = lock ? invitations.selectForUpdate(id) : invitations.selectById(id);
        if (meeting == null || !"MEETING".equals(meeting.getInviteType())) throw BusinessException.notFound("会议不存在");
        permissions.requireSystemPermission(user.getId(), meeting.getProjectId(), manage ? "site_access.manage" : "site_access.view");
        return meeting;
    }

    public Map<String,Object> list(Long invitationId, SysUser user, String keyword, String category, String status, long pageNo) {
        meeting(invitationId, user, false, false);
        if (keyword != null && keyword.length() > 200) throw new BusinessException("搜索文字过长");
        var query = new LambdaQueryWrapper<MeetingMaterial>().eq(MeetingMaterial::getInvitationId, invitationId)
                .eq(status != null && !status.isBlank(), MeetingMaterial::getStatus, status)
                .eq(category != null && !category.isBlank(), MeetingMaterial::getCategory, category)
                .like(keyword != null && !keyword.isBlank(), MeetingMaterial::getTitle, keyword)
                .orderByDesc(MeetingMaterial::getUpdateTime).orderByDesc(MeetingMaterial::getId);
        var page = materials.selectPage(new Page<>(Math.max(1, pageNo), 20), query);
        return Map.of("records", page.getRecords().stream().map(this::view).toList(), "total", page.getTotal());
    }

    public MeetingMaterial requireMaterial(Long id, SysUser user, boolean manage, boolean lock) {
        MeetingMaterial found = materials.selectById(id);
        if (found == null) throw BusinessException.notFound("会议资料不存在");
        meeting(found.getInvitationId(), user, manage, lock);
        if (lock) found = materials.lock(id);
        if (found == null) throw BusinessException.of(409, "资料已变化，请刷新");
        return found;
    }

    public List<VersionView> history(Long materialId, SysUser user) {
        requireMaterial(materialId, user, false, false);
        return versions.selectList(new LambdaQueryWrapper<MeetingMaterialVersion>().eq(MeetingMaterialVersion::getMaterialId, materialId)
                .orderByDesc(MeetingMaterialVersion::getVersionNo)).stream().map(this::versionView).toList();
    }

    public List<Map<String,Object>> activities(Long invitationId, SysUser user) {
        meeting(invitationId, user, false, false);
        return audits.selectList(new LambdaQueryWrapper<SiteMeetingVisitAuditLog>().eq(SiteMeetingVisitAuditLog::getInvitationId, invitationId)
                .orderByDesc(SiteMeetingVisitAuditLog::getId).last("LIMIT 200"))
                .stream().map(a -> Map.<String,Object>of("id",a.getId(), "actionType",a.getActionType(), "operatorName",Objects.toString(a.getOperatorName(),"访客"),
                        "createTime",a.getCreateTime(), "comment",Objects.toString(a.getComment(),""))).toList();
    }

    @Transactional
    public MaterialView edit(Long id, MeetingMaterialRequests.Edit request, SysUser user) {
        MeetingMaterial m = requireMaterial(id,user,true,true); expected(m,request.expectedVersion());
        metadata(request.title(),request.category(),request.description());
        m.setTitle(request.title().trim()); m.setCategory(request.category()); m.setDescription(clean(request.description()));
        update(m); audit(m,user,"MATERIAL_EDIT","修改资料信息",m); return view(m);
    }

    @Transactional
    public MaterialView publish(Long id, MeetingMaterialRequests.Publication request, SysUser user) {
        MeetingMaterial m = requireMaterial(id,user,true,true); expected(m,request.expectedVersion());
        if (request.versionId() != null) {
            if (!"ACTIVE".equals(m.getStatus()) || !Objects.equals(request.versionId(), m.getCurrentVersionId()))
                throw BusinessException.of(409,"只能发布当前有效资料的最新版本");
            SiteVisitInvitation invite = meeting(m.getInvitationId(),user,true,false);
            if ("VOIDED".equals(invite.getStatus())) throw BusinessException.of(409,"作废会议不能公开资料");
        }
        m.setPublishedVersionId(request.versionId()); update(m);
        audit(m,user,"MATERIAL_PUBLISH",request.versionId()==null ? "资料改为内部可见" : "发布资料最新版本",m);
        return view(m);
    }

    @Transactional
    public MaterialView state(Long id, MeetingMaterialRequests.State request, SysUser user) {
        MeetingMaterial m = requireMaterial(id,user,true,true); expected(m,request.expectedVersion());
        m.setStatus(request.withdrawn() ? "WITHDRAWN" : "ACTIVE"); m.setPublishedVersionId(null); update(m);
        audit(m,user,"MATERIAL_STATE", request.withdrawn() ? "撤下资料并保留历史" : "恢复内部资料",m); return view(m);
    }

    public void validateUpload(Long invitationId, MeetingMaterialRequests.Upload request, SysUser user) {
        meeting(invitationId,user,true,false); metadata(request.title(),request.category(),request.description());
        FileUploadPolicy.validateMeetingMetadata(request.fileName(),request.totalSize());
        if (request.materialId()!=null) {
            MeetingMaterial m=requireMaterial(request.materialId(),user,true,false);
            if (!Objects.equals(m.getInvitationId(),invitationId)) throw BusinessException.forbidden("资料不属于本次会议");
            expected(m,request.expectedVersion());
            if (!"ACTIVE".equals(m.getStatus())) throw BusinessException.of(409,"请先恢复已撤下资料");
        }
    }

    public MeetingMaterialVersion completed(String uploadKey) {
        return versions.selectOne(new LambdaQueryWrapper<MeetingMaterialVersion>().eq(MeetingMaterialVersion::getUploadKey,uploadKey));
    }

    @Transactional
    public MaterialView complete(Long invitationId, MeetingMaterialRequests.Upload request, MultipartFile file, String uploadKey, SysUser user) {
        SiteVisitInvitation invite=meeting(invitationId,user,true,true);
        MeetingMaterialVersion prior=completed(uploadKey);
        if(prior!=null) return view(requireMaterial(prior.getMaterialId(),user,true,false));
        validateUpload(invitationId,request,user); FileUploadPolicy.validateMeetingMaterial(file);
        MeetingMaterial m=request.materialId()==null ? new MeetingMaterial() : materials.lock(request.materialId());
        int versionNo=1;
        if (m.getId()==null) {
            m.setInvitationId(invitationId); m.setProjectId(invite.getProjectId()); m.setTitle(request.title().trim());
            m.setCategory(request.category()); m.setDescription(clean(request.description())); m.setStatus("ACTIVE");
            m.setVersion(0); m.setCreateTime(LocalDateTime.now()); m.setUpdateTime(m.getCreateTime()); one(materials.insert(m));
        } else {
            expected(m,request.expectedVersion()); versionNo=versions.selectById(m.getCurrentVersionId()).getVersionNo()+1;
        }
        FileResource resource=store(file,invite.getProjectId(),m.getId(),FILE_TYPE,user.getId());
        if (!resource.getSha256().equalsIgnoreCase(request.sha256()) || resource.getFileSize()!=request.totalSize())
            throw BusinessException.of(409,"完整文件校验失败，请重试上传");
        MeetingMaterialVersion v=new MeetingMaterialVersion();
        v.setMaterialId(m.getId()); v.setInvitationId(invitationId); v.setProjectId(invite.getProjectId());
        v.setVersionNo(versionNo); v.setFileId(resource.getId()); v.setPublicCode(UUID.randomUUID().toString().replace("-",""));
        v.setUploadKey(uploadKey); v.setUploaderId(user.getId()); v.setUploaderName(user.getRealName()==null ? "项目管理人员" : user.getRealName());
        v.setChangeNote(clean(request.changeNote())); v.setCreateTime(LocalDateTime.now()); one(versions.insert(v));
        m.setCurrentVersionId(v.getId()); update(m);
        MeetingMaterialPreview p=new MeetingMaterialPreview(); p.setVersionId(v.getId()); p.setInvitationId(invitationId);
        p.setKind(previewKind(resource.getFileExtension())); p.setStatus(requiresConversion(p.getKind()) ? "QUEUED" : "READY");
        p.setAttempts(0); p.setCreateTime(LocalDateTime.now()); p.setUpdateTime(p.getCreateTime()); one(previews.insert(p));
        audit(m,user,"MATERIAL_UPLOAD","上传资料《"+m.getTitle()+"》V"+versionNo,v);
        return view(m);
    }

    public FileResource store(MultipartFile file, Long projectId, Long businessId, String businessType, Long userId) {
        String key="meeting-materials/"+projectId+"/"+UUID.randomUUID()+"."+FileUploadPolicy.extensionOf(file.getOriginalFilename());
        StoredFile stored=storage.store(key,file);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) { if(status!=STATUS_COMMITTED) storage.deleteQuietly(stored.provider(),stored.storageKey()); }
        });
        FileResource f=new FileResource(); f.setProjectId(projectId); f.setBusinessId(businessId); f.setBusinessType(businessType);
        f.setFileName(stored.originalFileName()); f.setOriginalFileName(stored.originalFileName()); f.setFileExtension(stored.extension());
        f.setFileType("会议资料"); f.setFileSize(stored.size()); f.setSha256(stored.sha256()); f.setStorageProvider(stored.provider());
        f.setStorageKey(stored.storageKey()); f.setFilePath(stored.storageKey()); f.setMimeType(stored.mimeType()); f.setUploaderId(userId);
        f.setStatus("UPLOADED"); f.setDeleted(0); f.setCreateTime(LocalDateTime.now()); f.setUpdateTime(f.getCreateTime()); one(files.insert(f)); return f;
    }

    public MeetingMaterialVersion requireVersion(Long id, SysUser user) {
        MeetingMaterialVersion v=versions.selectById(id);
        if(v==null) throw BusinessException.notFound("资料版本不存在");
        requireMaterial(v.getMaterialId(),user,false,false); return v;
    }

    public Map<String,Object> publicResolve(String token) {
        if(token==null || !token.matches("[A-Za-z0-9_-]{16,128}")) throw new BusinessException("会议邀请码无效");
        SiteVisitInvitation invite=invitations.selectOne(new LambdaQueryWrapper<SiteVisitInvitation>().eq(SiteVisitInvitation::getTokenHash,crypto.digest(token)));
        publicMeeting(invite);
        var list=materials.selectList(new LambdaQueryWrapper<MeetingMaterial>().eq(MeetingMaterial::getInvitationId,invite.getId())
                .eq(MeetingMaterial::getStatus,"ACTIVE").isNotNull(MeetingMaterial::getPublishedVersionId).orderByDesc(MeetingMaterial::getUpdateTime));
        return Map.of("title",invite.getPurpose(),"ended",!invite.getVisitEndTime().isAfter(LocalDateTime.now()),
                "records",list.stream().map(m -> publicView(m, versions.selectById(m.getPublishedVersionId()))).toList());
    }

    public MeetingMaterialVersion publicVersion(String code) {
        if(code==null || !code.matches("[a-f0-9]{32}")) throw BusinessException.notFound("资料不存在或未公开");
        MeetingMaterialVersion v=versions.selectOne(new LambdaQueryWrapper<MeetingMaterialVersion>().eq(MeetingMaterialVersion::getPublicCode,code));
        if(v==null) throw BusinessException.notFound("资料不存在或未公开");
        MeetingMaterial m=materials.selectById(v.getMaterialId());
        if(m==null || !"ACTIVE".equals(m.getStatus()) || !Objects.equals(m.getPublishedVersionId(),v.getId()))
            throw BusinessException.notFound("资料已撤下或版本已更新");
        publicMeeting(invitations.selectById(v.getInvitationId())); return v;
    }

    private void publicMeeting(SiteVisitInvitation invite) {
        if(invite==null || !"MEETING".equals(invite.getInviteType()) || "VOIDED".equals(invite.getStatus()))
            throw BusinessException.of(410,"会议资料入口已关闭");
        var project=projects.selectById(invite.getProjectId());
        if(project==null || "stopped".equalsIgnoreCase(project.getProjectStatus())) throw BusinessException.of(410,"项目资料入口已关闭");
    }

    private Map<String,Object> publicView(MeetingMaterial m, MeetingMaterialVersion v) {
        VersionView view=versionView(v);
        return Map.of("title",m.getTitle(),"category",m.getCategory(),"description",m.getDescription(), "versionNo",v.getVersionNo(),
                "publicCode",v.getPublicCode(),"fileName",view.fileName(),"fileSize",view.fileSize(),"previewKind",view.previewKind(),
                "previewStatus",view.previewStatus(),"updatedAt",v.getCreateTime());
    }

    public FileResource contentFile(MeetingMaterialVersion v, boolean preview) {
        if(!preview) return file(v.getFileId());
        MeetingMaterialPreview p=preview(v.getId());
        if(p==null || !"READY".equals(p.getStatus())) throw BusinessException.of(409,"预览尚未就绪，请稍后重试或下载原文件");
        if("UNSUPPORTED".equals(p.getKind())) throw BusinessException.of(415,"此格式请下载后查看");
        return file(p.getFileId()==null ? v.getFileId() : p.getFileId());
    }
    private FileResource file(Long id) {
        FileResource f=files.selectById(id); if(f==null) throw BusinessException.notFound("文件不存在"); return f;
    }
    public MeetingMaterialPreview preview(Long versionId) { return previews.selectOne(new LambdaQueryWrapper<MeetingMaterialPreview>().eq(MeetingMaterialPreview::getVersionId,versionId)); }
    public VersionView versionView(MeetingMaterialVersion v) {
        FileResource f=file(v.getFileId()); MeetingMaterialPreview p=preview(v.getId());
        return new VersionView(v.getId(),v.getVersionNo(),f.getFileName(),f.getFileSize(),f.getFileExtension(),v.getUploaderName(),v.getChangeNote(),v.getCreateTime(),
                p==null ? "UNSUPPORTED" : p.getKind(),p==null ? "FAILED" : p.getStatus(),p==null ? "预览未建立" : p.getFailureMessage());
    }
    public MaterialView view(MeetingMaterial m) { return new MaterialView(m, m.getCurrentVersionId()==null ? null : versionView(versions.selectById(m.getCurrentVersionId()))); }
    private void update(MeetingMaterial m) {
        m.setVersion(m.getVersion()+1); m.setUpdateTime(LocalDateTime.now());
        // Null publication explicitly clears SQL rather than MyBatis' default NOT_NULL update strategy.
        one(materials.update(m,new LambdaUpdateWrapper<MeetingMaterial>().eq(MeetingMaterial::getId,m.getId())
                .set(MeetingMaterial::getPublishedVersionId,m.getPublishedVersionId())));
    }
    public static void expected(MeetingMaterial m,Integer version) { if(version==null || !version.equals(m.getVersion())) throw BusinessException.of(409,"资料已由其他人员修改，请刷新后重试"); }
    public static void metadata(String title,String category,String description) {
        if(title==null || title.isBlank() || title.trim().length()>200 || !CATEGORIES.contains(category==null?"":category)
                || (description!=null && description.length()>1000)) throw new BusinessException("资料名称、分类或说明不正确");
    }
    public static String previewKind(String ext) {
        if(Set.of("doc","docx","xls","xlsx","ppt","pptx","wps","et","dps","rtf","odt","ods","odp").contains(ext)) return "OFFICE";
        if("pdf".equals(ext)) return "PDF";
        if(Set.of("heic","heif").contains(ext)) return "HEIF";
        if(Set.of("jpg","jpeg","png","gif","bmp","webp").contains(ext)) return "IMAGE";
        if(Set.of("txt","md","csv").contains(ext)) return "TEXT";
        if(Set.of("mp4","mov","m4v","webm","mkv","avi").contains(ext)) return "VIDEO";
        if(Set.of("mp3","m4a","aac","wav","flac","ogg").contains(ext)) return "AUDIO";
        return "UNSUPPORTED";
    }
    public static boolean requiresConversion(String kind) { return Set.of("OFFICE","HEIF","VIDEO","AUDIO").contains(kind); }
    private String clean(String text) { return text==null ? "" : text.trim(); }
    public static void one(int rows) { if(rows!=1) throw BusinessException.of(409,"资料写入未生效，请刷新后重试"); }
    public void audit(MeetingMaterial m,SysUser user,String action,String comment,Object snapshot) {
        SiteMeetingVisitAuditLog a=new SiteMeetingVisitAuditLog(); a.setInvitationId(m.getInvitationId()); a.setProjectId(m.getProjectId());
        a.setActionType(action); a.setOperatorId(user.getId()); a.setOperatorName(user.getRealName()==null?"项目管理人员":user.getRealName());
        a.setComment(comment); a.setCreateTime(LocalDateTime.now());
        try { a.setAfterSnapshotEncrypted(crypto.encrypt(json.writeValueAsString(snapshot))); }
        catch(Exception e) { throw new BusinessException("资料审计生成失败"); }
        one(audits.insert(a));
    }
    public record MaterialView(MeetingMaterial material,VersionView currentVersion) {}
    public record VersionView(Long id,Integer versionNo,String fileName,Long fileSize,String extension,String uploaderName,String changeNote,
            LocalDateTime createTime,String previewKind,String previewStatus,String previewError) {}
}
