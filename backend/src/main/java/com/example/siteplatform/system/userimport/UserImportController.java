package com.example.siteplatform.system.userimport;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.common.PageResult;
import com.example.siteplatform.common.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/system")
@RequiredArgsConstructor
public class UserImportController {
    private final AuthService auth;
    private final UserImportService service;
    public record ConfirmRequest(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{16,80}") String requestKey,
            @NotBlank(message = "请填写临时密码") @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY) String temporaryPassword) {
        @Override public String toString() { return "ConfirmRequest[protected credential]"; }
    }
    public record TemporaryPasswordRequest(
            @NotBlank(message = "请填写临时密码") @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY) String temporaryPassword) {
        @Override public String toString() { return "TemporaryPasswordRequest[protected credential]"; }
    }
    private SysUser operator(String authorization) { SysUser user = auth.getCurrentUser(authorization); service.requireAdmin(user.getId()); return user; }
    @GetMapping("/user-imports/template")
    public ResponseEntity<byte[]> template(@RequestHeader("Authorization") String token) { return file(service.template(operator(token)), "用户批量导入模板.xlsx"); }
    @PostMapping(value="/user-imports/preview", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Map<String, Object>> preview(@RequestHeader("Authorization") String token, @RequestPart("file") MultipartFile file) { return Result.success(service.preview(file, operator(token))); }
    @PostMapping("/user-imports/{id}/confirm")
    public Result<Map<String, Object>> confirm(@RequestHeader("Authorization") String token, @PathVariable Long id, @Valid @RequestBody ConfirmRequest request) { return Result.success(service.confirm(id, request.requestKey(), request.temporaryPassword(), operator(token))); }
    @GetMapping({"/user-imports", "/user-imports/"})
    public Result<PageResult<Map<String, Object>>> list(@RequestHeader("Authorization") String token, @RequestParam(defaultValue="1") int pageNo, @RequestParam(defaultValue="20") int pageSize) { return Result.success(service.list(operator(token), pageNo, pageSize)); }
    @GetMapping("/user-imports/{id}")
    public Result<Map<String, Object>> detail(@RequestHeader("Authorization") String token, @PathVariable Long id, @RequestParam(defaultValue="true") boolean includeRows) { return Result.success(service.detail(id, includeRows, operator(token))); }
    @GetMapping("/user-imports/{id}/credentials")
    public ResponseEntity<byte[]> credentials(@RequestHeader("Authorization") String token, @PathVariable Long id) { return file(service.credentials(id, operator(token)), "账号发放表.xlsx"); }
    @PostMapping("/users/{id}/temporary-password")
    public Result<Void> regenerate(@RequestHeader("Authorization") String token, @PathVariable Long id, @Valid @RequestBody TemporaryPasswordRequest request) { service.regenerate(id, request.temporaryPassword(), operator(token)); return Result.success(); }
    @GetMapping("/users/{id}/temporary-password")
    public ResponseEntity<byte[]> userCredential(@RequestHeader("Authorization") String token, @PathVariable Long id) { return file(service.userCredential(id, operator(token)), "个人账号发放表.xlsx"); }
    private ResponseEntity<byte[]> file(byte[] bytes, String name) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Pragma", "no-cache").header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).body(bytes);
    }
}
