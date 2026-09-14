package com.example.siteplatform.safetycommittee;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.file.security.FileUploadPolicy;
import com.example.siteplatform.file.storage.FileStorageManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CommitteeContentService {
    private final CommitteeService service;
    private final AuthService auth;
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final FileStorageManager storage;

    public Map<String,Object> issue(Long id,String authorization,boolean nativePlayback,HttpServletRequest request,HttpServletResponse response) {
        service.requireAttachment(id,auth.getCurrentUser(authorization));
        String value=UUID.randomUUID().toString().replace("-","")+UUID.randomUUID().toString().replace("-","");
        var session=auth.snapshotReadSession(authorization);
        long expires=Math.min(session.expiresAt(),System.currentTimeMillis()+Duration.ofMinutes(15).toMillis());
        long remaining=expires-System.currentTimeMillis();
        if(remaining<=0) throw BusinessException.of(401,"登录已过期，请重新登录");
        try { redis.opsForValue().set(key(value),json.writeValueAsString(new Grant(id,session,expires)),Duration.ofMillis(remaining)); }
        catch(com.fasterxml.jackson.core.JsonProcessingException e) { throw new BusinessException("附件读取会话创建失败"); }
        response.setHeader(HttpHeaders.CACHE_CONTROL,"no-store");
        if (nativePlayback) return Map.of("contentPath","/api/v1/safety-committee/media/"+value,"expiresAt",expires);
        response.addHeader(HttpHeaders.SET_COOKIE,ResponseCookie.from("committee_read",value).httpOnly(true).secure(request.isSecure())
                .sameSite("Strict").path(path(id)).maxAge(Duration.ofMillis(expires-System.currentTimeMillis())).build().toString());
        return Map.of("expiresAt",expires);
    }
    private Grant grant(String value) {
        if(value==null || !value.matches("[a-f0-9]{64}")) throw BusinessException.of(401,"附件读取凭证无效");
        String raw=redis.opsForValue().get(key(value));
        if(raw==null) throw BusinessException.of(401,"附件读取会话已过期，请重新打开");
        try {
            var grant=json.readValue(raw,Grant.class);
            if(grant.expiresAt()<=System.currentTimeMillis()) throw BusinessException.of(401,"附件读取会话已过期");
            return grant;
        } catch(com.fasterxml.jackson.core.JsonProcessingException e) { throw BusinessException.of(401,"附件读取凭证无效"); }
    }
    public ResponseEntity<Resource> nativeContent(String code,boolean preview) {
        var grant=grant(code); SysUser user=auth.validateReadSession(grant.session());
        return response(service.requireAttachment(grant.attachmentId(),user),preview);
    }
    public ResponseEntity<Resource> content(Long id,boolean preview,HttpServletRequest request) {
        SysUser user=null;
        if(request.getHeader("Authorization")!=null) user=auth.getCurrentUser(request.getHeader("Authorization"));
        else if(request.getCookies()!=null) for(Cookie cookie:request.getCookies()) {
            if(!"committee_read".equals(cookie.getName())) continue;
            var grant=grant(cookie.getValue());
            if(Objects.equals(grant.attachmentId(),id)) { user=auth.validateReadSession(grant.session()); break; }
        }
        if(user==null) throw BusinessException.of(401,"请重新打开附件预览");
        return response(service.requireAttachment(id,user),preview);
    }
    private ResponseEntity<Resource> response(CommitteeAttachment attachment,boolean preview) {
        var f=service.content(attachment,preview);
        MediaType type="mp4".equals(f.getFileExtension()) ? MediaType.parseMediaType("video/mp4") : FileUploadPolicy.responseMediaType(f.getFileName());
        return ResponseEntity.ok().contentType(type).contentLength(f.getFileSize())
                .header(HttpHeaders.CONTENT_DISPOSITION,(preview?ContentDisposition.inline():ContentDisposition.attachment())
                        .filename(f.getOriginalFileName(),StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL,"private, no-store").header("X-Content-Type-Options","nosniff")
                .header("Referrer-Policy","no-referrer").header(HttpHeaders.ACCEPT_RANGES,"bytes")
                .body(new AbstractResource() {
                    @Override public String getDescription(){return "committee inspection attachment";}
                    @Override public String getFilename(){return f.getOriginalFileName();}
                    @Override public long contentLength(){return f.getFileSize();}
                    @Override public java.io.InputStream getInputStream() throws java.io.IOException{return storage.load(f).getInputStream();}
                });
    }
    private static String key(String value){return "committee:read:"+CommitteeService.hash(value);}
    public static String path(Long id){return "/api/v1/safety-committee/attachments/"+id+"/content";}
    public record Grant(Long attachmentId,AuthService.ReadSessionSnapshot session,long expiresAt) {}
}
