package com.example.siteplatform.siteaccess.material;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.common.Result;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/site-access")
@RequiredArgsConstructor
public class MeetingMaterialController {
    private final AuthService auth;
    private final MeetingMaterialService materials;
    private final MeetingMaterialUploadService uploads;
    private final MeetingMaterialContentService content;
    private final MeetingMaterialPreviewService previews;
    private SysUser user(String token) { return auth.getCurrentUser(token); }
    @GetMapping("/invitations/{id}/materials")
    public Result<?> list(@PathVariable Long id,@RequestParam(defaultValue="") String keyword,@RequestParam(defaultValue="") String category,
            @RequestParam(defaultValue="ACTIVE") String status,@RequestParam(defaultValue="1") long pageNo,@RequestHeader("Authorization") String token) {
        return Result.success(materials.list(id,user(token),keyword,category,status,pageNo));
    }
    @GetMapping("/invitations/{id}/materials/activities")
    public Result<?> activities(@PathVariable Long id,@RequestHeader("Authorization") String token) { return Result.success(materials.activities(id,user(token))); }
    @PutMapping("/materials/{id}")
    public Result<?> edit(@PathVariable Long id,@Valid @RequestBody MeetingMaterialRequests.Edit request,@RequestHeader("Authorization") String token) { return Result.success(materials.edit(id,request,user(token))); }
    @PutMapping("/materials/{id}/publication")
    public Result<?> publish(@PathVariable Long id,@Valid @RequestBody MeetingMaterialRequests.Publication request,@RequestHeader("Authorization") String token) { return Result.success(materials.publish(id,request,user(token))); }
    @PutMapping("/materials/{id}/state")
    public Result<?> state(@PathVariable Long id,@Valid @RequestBody MeetingMaterialRequests.State request,@RequestHeader("Authorization") String token) { return Result.success(materials.state(id,request,user(token))); }
    @GetMapping("/materials/{id}/versions")
    public Result<?> history(@PathVariable Long id,@RequestHeader("Authorization") String token) { return Result.success(materials.history(id,user(token))); }
    @GetMapping("/material-versions/{id}")
    public Result<?> version(@PathVariable Long id,@RequestHeader("Authorization") String token) { return Result.success(materials.versionView(materials.requireVersion(id,user(token)))); }
    @PostMapping("/material-versions/{id}/preview-retry")
    public Result<?> retry(@PathVariable Long id,@RequestHeader("Authorization") String token) { previews.retry(id,user(token)); return Result.success(); }
    @PostMapping("/material-versions/{id}/read-session")
    public Result<?> readSession(@PathVariable Long id,@RequestHeader("Authorization") String token,HttpServletRequest request,HttpServletResponse response) {
        content.issue(id,token,request,response); return Result.success();
    }
    @GetMapping("/material-versions/{id}/content")
    public ResponseEntity<Resource> read(@PathVariable Long id,@RequestParam(defaultValue="false") boolean preview,HttpServletRequest request) {
        return content.response(materials.requireVersion(id,content.reader(id,request)),preview);
    }
    @PostMapping("/invitations/{id}/material-uploads")
    public Result<?> initialize(@PathVariable Long id,@Valid @RequestBody MeetingMaterialRequests.Upload request,@RequestHeader("Authorization") String token) {
        return Result.success(uploads.initialize(id,request,user(token)));
    }
    @GetMapping("/invitations/{id}/material-uploads/{sessionId}")
    public Result<?> status(@PathVariable Long id,@PathVariable String sessionId,@RequestHeader("Authorization") String token) { return Result.success(uploads.status(id,sessionId,user(token))); }
    @PutMapping("/invitations/{id}/material-uploads/{sessionId}/chunks/{index}")
    public Result<?> chunk(@PathVariable Long id,@PathVariable String sessionId,@PathVariable int index,@RequestParam String sha256,
            @RequestParam MultipartFile chunk,@RequestHeader("Authorization") String token) { return Result.success(uploads.chunk(id,sessionId,index,sha256,chunk,user(token))); }
    @PostMapping("/invitations/{id}/material-uploads/{sessionId}/complete")
    public Result<?> complete(@PathVariable Long id,@PathVariable String sessionId,@RequestHeader("Authorization") String token) { return Result.success(uploads.complete(id,sessionId,user(token))); }
    @DeleteMapping("/invitations/{id}/material-uploads/{sessionId}")
    public Result<?> cancel(@PathVariable Long id,@PathVariable String sessionId,@RequestHeader("Authorization") String token) { uploads.cancel(id,sessionId,user(token)); return Result.success(); }
}
