package com.example.siteplatform.seal.controller;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.common.Result;
import com.example.siteplatform.seal.dto.SealDefinitionCreateRequest;
import com.example.siteplatform.seal.dto.SealDefinitionRequest;
import com.example.siteplatform.seal.service.SealDefinitionService;
import com.example.siteplatform.seal.vo.SealDefinitionVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/system/seals")
public class SystemSealController {
    private final SealDefinitionService service;
    private final AuthService authService;

    public SystemSealController(SealDefinitionService service, AuthService authService) {
        this.service = service;
        this.authService = authService;
    }

    @GetMapping
    public Result<List<SealDefinitionVO>> list(@RequestParam Long projectId,
                                               @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(service.systemList(projectId, authService.getCurrentUser(token)));
    }

    @PostMapping
    public Result<SealDefinitionVO> create(@Valid @RequestBody SealDefinitionCreateRequest request,
                                           @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(service.create(request, authService.getCurrentUser(token)));
    }

    @PutMapping("/{id}")
    public Result<SealDefinitionVO> update(@PathVariable Long id,
                                           @Valid @RequestBody SealDefinitionRequest request,
                                           @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(service.update(id, request, authService.getCurrentUser(token)));
    }

    @GetMapping("/{id}/mini-code")
    public Result<Map<String, Object>> miniCode(@PathVariable Long id,
                                                @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser user = authService.getCurrentUser(token);
        var entry = service.entryCode(null, id, user);
        String image = service.miniCode(id, user);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sealId", id);
        result.put("scene", entry.getScene());
        result.put("dataUrl", image);
        result.put("imageBase64", image);
        result.put("codeType", image == null ? "DEVELOPMENT_SCENE" : "WECHAT_MINI_PROGRAM_CODE");
        return Result.success(result);
    }

    @PostMapping("/{id}/rotate-code")
    public Result<com.example.siteplatform.seal.vo.SealEntryVO> rotateCode(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(service.rotate(id, authService.getCurrentUser(token)));
    }

    @PutMapping("/{id}/status")
    public Result<com.example.siteplatform.seal.vo.SealEntryVO> qrStatus(
            @PathVariable Long id,
            @Valid @RequestBody com.example.siteplatform.seal.dto.SealEntryStatusRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(service.updateQrStatus(id, request.getEnabled(), authService.getCurrentUser(token)));
    }
}
