package com.example.siteplatform.safetycommittee;

import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.common.Result;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/safety-committee")
@RequiredArgsConstructor
public class CommitteeController {
    private final CommitteeService service;
    private final CommitteeUploadService uploads;
    private final CommitteeContentService content;
    private final CommitteePreviewService previews;
    private final AuthService auth;
    private final CommitteeThumbnailService thumbnails;

    @GetMapping("/categories") public Result<List<String>> categories(@RequestParam Long projectId,@RequestHeader(value="Authorization",required=false) String authorization) {
        service.access(projectId,auth.getCurrentUser(authorization),CommitteeService.VIEW);return Result.success(CommitteeService.CATEGORIES);
    }
    @GetMapping("/records") public Result<Map<String,Object>> page(@RequestParam Long projectId,@RequestParam(required=false) String category,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue="1") long pageNo,@RequestHeader(value="Authorization",required=false) String authorization) {
        return Result.success(service.page(projectId,category,startDate,endDate,pageNo,auth.getCurrentUser(authorization)));
    }
    @GetMapping("/records/{id}") public Result<CommitteeService.RecordView> detail(@PathVariable Long id,@RequestHeader(value="Authorization",required=false) String authorization) {
        return Result.success(service.detail(id,auth.getCurrentUser(authorization)));
    }
    @PostMapping("/records") public Result<CommitteeService.RecordView> create(@Valid @RequestBody CommitteeRequests.Create request,@RequestHeader(value="Authorization",required=false) String authorization) {
        return Result.success(service.create(request,auth.getCurrentUser(authorization)));
    }
    @PutMapping("/records/{id}") public Result<CommitteeService.RecordView> edit(@PathVariable Long id,@Valid @RequestBody CommitteeRequests.Edit request,@RequestHeader(value="Authorization",required=false) String authorization) {
        return Result.success(service.edit(id,request,auth.getCurrentUser(authorization)));
    }
    @PostMapping("/uploads") public Result<Map<String,Object>> upload(@Valid @RequestBody CommitteeRequests.Upload request,@RequestHeader(value="Authorization",required=false) String authorization) {
        return Result.success(uploads.initialize(request,auth.getCurrentUser(authorization)));
    }
    @GetMapping("/uploads/{id}") public Result<Map<String,Object>> uploadStatus(@PathVariable String id,@RequestParam Long projectId,@RequestHeader(value="Authorization",required=false) String authorization) {
        return Result.success(uploads.status(projectId,id,auth.getCurrentUser(authorization)));
    }
    @PutMapping("/uploads/{id}/chunks/{index}") public Result<Map<String,Object>> chunk(@PathVariable String id,@PathVariable int index,@RequestParam Long projectId,
            @RequestParam String sha256,@RequestParam("chunk") MultipartFile file,@RequestHeader(value="Authorization",required=false) String authorization) {
        return Result.success(uploads.chunk(projectId,id,index,sha256,file,auth.getCurrentUser(authorization)));
    }
    @PostMapping("/uploads/{id}/complete") public Result<CommitteeService.AttachmentView> complete(@PathVariable String id,@RequestParam Long projectId,@RequestHeader(value="Authorization",required=false) String authorization) {
        return Result.success(uploads.complete(projectId,id,auth.getCurrentUser(authorization)));
    }
    @DeleteMapping("/uploads/{id}") public Result<Void> cancel(@PathVariable String id,@RequestParam Long projectId,@RequestHeader(value="Authorization",required=false) String authorization) {
        uploads.cancel(projectId,id,auth.getCurrentUser(authorization));return Result.success();
    }
    @GetMapping("/attachments/{id}") public Result<CommitteeService.AttachmentView> attachment(@PathVariable Long id,@RequestHeader(value="Authorization",required=false) String authorization) {
        var user=auth.getCurrentUser(authorization);
        return Result.success(service.attachmentView(service.requireAttachment(id,user),user));
    }
    @PutMapping("/attachments/{id}/rotation") public Result<CommitteeService.AttachmentView> rotate(@PathVariable Long id,
            @Valid @RequestBody CommitteeRequests.Rotation request,@RequestHeader(value="Authorization",required=false) String authorization) {
        return Result.success(service.rotate(id,request,auth.getCurrentUser(authorization)));
    }
    @DeleteMapping("/attachments/{id}") public Result<Void> discard(@PathVariable Long id,@RequestHeader(value="Authorization",required=false) String authorization) {
        service.discard(id,auth.getCurrentUser(authorization));return Result.success();
    }
    @PostMapping("/attachments/{id}/preview-retry") public Result<Void> retry(@PathVariable Long id,@RequestHeader(value="Authorization",required=false) String authorization) {
        previews.retry(id,auth.getCurrentUser(authorization));return Result.success();
    }
    @PostMapping("/attachments/{id}/read-session") public Result<Map<String,Object>> readSession(@PathVariable Long id,@RequestParam(defaultValue="false") boolean nativePlayback,
            @RequestHeader(value="Authorization",required=false) String authorization,HttpServletRequest request,HttpServletResponse response) {
        return Result.success(content.issue(id,authorization,nativePlayback,request,response));
    }
    @PostMapping("/attachments/{id}/thumbnail") public Result<CommitteeThumbnailService.State> thumbnail(@PathVariable Long id,
            @RequestParam(defaultValue="false") boolean retry,@RequestHeader(value="Authorization",required=false) String authorization) {
        return Result.success(thumbnails.request(id,authorization,retry));
    }
    @RequestMapping(value="/attachments/{id}/content",method={RequestMethod.GET,RequestMethod.HEAD})
    public ResponseEntity<Resource> file(@PathVariable Long id,@RequestParam(defaultValue="true") boolean preview,
            @RequestParam(defaultValue="false") boolean thumbnail,HttpServletRequest request) {
        return content.content(id,preview,thumbnail,request);
    }
    @RequestMapping(value="/media/{code}",method={RequestMethod.GET,RequestMethod.HEAD})
    public ResponseEntity<Resource> media(@PathVariable String code,@RequestParam(defaultValue="true") boolean preview,@RequestParam(defaultValue="false") boolean thumbnail) {
        return content.nativeContent(code,preview,thumbnail);
    }
}
