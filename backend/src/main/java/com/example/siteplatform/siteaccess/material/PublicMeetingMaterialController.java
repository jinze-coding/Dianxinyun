package com.example.siteplatform.siteaccess.material;

import com.example.siteplatform.common.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/public/site-access/meeting/materials")
@RequiredArgsConstructor
public class PublicMeetingMaterialController {
    private final MeetingMaterialService service;
    private final MeetingMaterialContentService content;
    @PostMapping("/resolve")
    public Result<?> resolve(@Valid @RequestBody MeetingMaterialRequests.Resolve request) { return Result.success(service.publicResolve(request.inviteToken())); }
    @GetMapping("/content/{code}")
    public ResponseEntity<Resource> read(@PathVariable String code,@RequestParam(defaultValue="false") boolean preview) {
        return content.response(service.publicVersion(code),preview);
    }
}
