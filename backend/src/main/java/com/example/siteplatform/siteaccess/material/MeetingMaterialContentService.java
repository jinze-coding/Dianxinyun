package com.example.siteplatform.siteaccess.material;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.file.entity.FileResource;
import com.example.siteplatform.file.security.FileUploadPolicy;
import com.example.siteplatform.file.storage.FileStorageManager;
import com.example.siteplatform.siteaccess.service.VisitorDataCryptoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.AbstractResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MeetingMaterialContentService {
    private final MeetingMaterialService service;
    private final AuthService auth;
    private final StringRedisTemplate redis;
    private final VisitorDataCryptoService crypto;
    private final ObjectMapper json;
    private final FileStorageManager storage;
    public void issue(Long versionId,String authorization,HttpServletRequest request,HttpServletResponse response) {
        service.requireVersion(versionId,auth.getCurrentUser(authorization));
        String value=UUID.randomUUID().toString().replace("-","")+UUID.randomUUID().toString().replace("-","");
        // The short-lived grant cannot outlive a revoked/expired original login: every content request revalidates it.
        try { redis.opsForValue().set("meeting:read:"+crypto.digest(value),json.writeValueAsString(new Grant(versionId,crypto.encrypt(authorization))),Duration.ofMinutes(15)); }
        catch(com.fasterxml.jackson.core.JsonProcessingException e) { throw new BusinessException("文件读取会话创建失败"); }
        response.addHeader(HttpHeaders.SET_COOKIE,ResponseCookie.from("meeting_read",value).httpOnly(true).secure(request.isSecure())
                .sameSite("Strict").path(path(versionId)).maxAge(Duration.ofMinutes(15)).build().toString());
        response.setHeader(HttpHeaders.CACHE_CONTROL,"no-store");
    }
    public SysUser reader(Long versionId,HttpServletRequest request) {
        String header=request.getHeader("Authorization");
        if(header!=null && !header.isBlank()) return auth.getCurrentUser(header);
        Cookie[] cookies=request.getCookies();
        if(cookies!=null) for(Cookie cookie:cookies) {
            if(!"meeting_read".equals(cookie.getName()) || !cookie.getValue().matches("[a-f0-9]{64}")) continue;
            String raw=redis.opsForValue().get("meeting:read:"+crypto.digest(cookie.getValue()));
            if(raw==null) continue;
            try {
                Grant grant=json.readValue(raw,Grant.class);
                if(Objects.equals(grant.versionId(),versionId)) return auth.getCurrentUser(crypto.decrypt(grant.encryptedAuthorization()));
            } catch(com.fasterxml.jackson.core.JsonProcessingException e) { throw BusinessException.of(401,"文件读取会话已失效"); }
        }
        throw BusinessException.of(401,"文件读取会话已过期，请重新打开预览");
    }
    public ResponseEntity<Resource> response(MeetingMaterialVersion version,boolean preview) {
        FileResource f=service.contentFile(version,preview);
        String ext=f.getFileExtension();
        MediaType type=switch(ext) {
            case "mp4", "m4v" -> MediaType.parseMediaType("video/mp4");
            case "mp3" -> MediaType.parseMediaType("audio/mpeg");
            case "m4a" -> MediaType.parseMediaType("audio/mp4");
            default -> FileUploadPolicy.responseMediaType(f.getFileName());
        };
        return ResponseEntity.ok().contentType(type).contentLength(f.getFileSize())
                .header(HttpHeaders.CONTENT_DISPOSITION,(preview?ContentDisposition.inline():ContentDisposition.attachment())
                        .filename(f.getOriginalFileName(),StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL,"private, no-store")
                .header("X-Content-Type-Options","nosniff")
                .header("Referrer-Policy","no-referrer")
                .header(HttpHeaders.ACCEPT_RANGES,"bytes")
                // Spring skips Range handling for InputStreamResource (used by MinIO).
                // Reopen each stream with a known length so both storage providers support regions.
                .body(new AbstractResource() {
                    @Override public String getDescription() { return "meeting material content"; }
                    @Override public String getFilename() { return f.getOriginalFileName(); }
                    @Override public long contentLength() { return f.getFileSize(); }
                    @Override public java.io.InputStream getInputStream() throws java.io.IOException { return storage.load(f).getInputStream(); }
                });
    }
    public static String path(Long id) { return "/api/v1/site-access/material-versions/"+id+"/content"; }
    public record Grant(Long versionId,String encryptedAuthorization) {}
}
