package com.example.siteplatform.system.correction;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.common.Result;
import com.example.siteplatform.file.security.FileUploadPolicy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/system/data-corrections")
@RequiredArgsConstructor
public class CorrectionController {
    private final AuthService auth;
    private final CorrectionAccess access;
    private final CorrectionService service;
    private final CorrectionAttachments files;
    private final CorrectionUploadService uploads;
    private SysUser user(HttpServletRequest request){var user=auth.getCurrentUser(request.getHeader("Authorization"));access.operator(user);return user;}
    @GetMapping("/catalog")
    public Result<?> catalog(HttpServletRequest req){return Result.success(service.catalog(user(req)));}
    @GetMapping("/records/{type}")
    public Result<?> page(@PathVariable String type,@RequestParam long projectId,@RequestParam(required=false) String keyword,
            @RequestParam(required=false) String status,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue="1") int pageNo,@RequestParam(defaultValue="20") int pageSize,HttpServletRequest req) {
        return Result.success(service.page(type,projectId,keyword,status,startDate,endDate,pageNo,pageSize,user(req)));
    }
    @GetMapping("/records/{type}/{id}")
    public Result<?> detail(@PathVariable String type,@PathVariable long id,HttpServletRequest req){return Result.success(service.detail(type,id,user(req)));}
    @GetMapping("/records/{type}/{id}/candidates")
    public Result<?> candidates(@PathVariable String type,@PathVariable long id,@RequestParam String field,HttpServletRequest req){return Result.success(service.candidates(type,id,field,user(req)));}
    @PostMapping("/records/{type}/{id}/preview")
    public Result<?> preview(@PathVariable String type,@PathVariable long id,@Valid @RequestBody CorrectionRequests.Preview body,HttpServletRequest req){return Result.success(service.preview(type,id,body,user(req)));}
    @PostMapping("/confirm")
    public Result<?> confirm(@Valid @RequestBody CorrectionRequests.Confirm body,HttpServletRequest req){return Result.success(service.confirm(body,user(req),req.getRemoteAddr()));}
    @GetMapping("/records/{type}/{id}/history")
    public Result<?> history(@PathVariable String type,@PathVariable long id,@RequestParam(defaultValue="1") int pageNo,HttpServletRequest req){return Result.success(service.history(type,id,pageNo,user(req)));}
    @GetMapping("/logs/{id}")
    public Result<?> log(@PathVariable long id,HttpServletRequest req){return Result.success(service.log(id,user(req)));}
    @PostMapping("/uploads")
    public Result<?> upload(@Valid @RequestBody CorrectionRequests.Upload body,HttpServletRequest req){return Result.success(uploads.initialize(body,user(req)));}
    @GetMapping("/uploads/{id}")
    public Result<?> uploadStatus(@PathVariable String id,@RequestParam Long projectId,HttpServletRequest req){return Result.success(uploads.status(projectId,id,user(req)));}
    @PutMapping(value="/uploads/{id}/chunks/{index}",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<?> chunk(@PathVariable String id,@PathVariable int index,@RequestParam Long projectId,@RequestParam String sha256,@RequestParam MultipartFile file,HttpServletRequest req){return Result.success(uploads.chunk(projectId,id,index,sha256,file,user(req)));}
    @PostMapping("/uploads/{id}/complete")
    public Result<?> complete(@PathVariable String id,@RequestParam Long projectId,HttpServletRequest req){return Result.success(uploads.complete(projectId,id,user(req)));}
    @DeleteMapping("/uploads/{id}")
    public Result<?> cancel(@PathVariable String id,@RequestParam Long projectId,HttpServletRequest req){uploads.cancel(projectId,id,user(req));return Result.success();}
    @GetMapping("/records/{type}/{id}/files/{fileId}")
    public ResponseEntity<Resource> file(@PathVariable String type,@PathVariable long id,@PathVariable long fileId,@RequestParam(required=false) Long correctionId,
            @RequestParam(defaultValue="AFTER") String phase,@RequestParam(defaultValue="false") boolean inline,HttpServletRequest req){
        var file=files.download(type,id,fileId,correctionId,phase,user(req));
        boolean safe=inline&&java.util.Set.of("jpg","jpeg","png","gif","webp","bmp","pdf","mp4","mov").contains(file.getFileExtension());
        ContentDisposition disposition=(safe?ContentDisposition.inline():ContentDisposition.attachment()).filename(file.getFileName(),StandardCharsets.UTF_8).build();
        MediaType media=safe?MediaType.parseMediaType(file.getMimeType()==null?"application/octet-stream":file.getMimeType()):MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").header("Content-Security-Policy","sandbox")
                .header(HttpHeaders.CONTENT_DISPOSITION,disposition.toString()).contentType(media).body(files.resource(file));
    }
}
