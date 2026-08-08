package com.example.siteplatform.seal.controller;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.common.Result;
import com.example.siteplatform.seal.dto.SealEntryResolveRequest;
import com.example.siteplatform.seal.dto.SealEntryStatusRequest;
import com.example.siteplatform.seal.service.SealDefinitionService;
import com.example.siteplatform.seal.vo.SealDefinitionVO;
import com.example.siteplatform.seal.vo.SealEntryVO;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/seal")
public class SealEntryController {
    private final SealDefinitionService service;
    private final AuthService authService;

    public SealEntryController(SealDefinitionService service, AuthService authService) {
        this.service = service;
        this.authService = authService;
    }

    @GetMapping("/seals")
    public Result<List<SealDefinitionVO>> options(@RequestParam Long projectId,
                                                  @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(service.applicationOptions(projectId, authService.getCurrentUser(token)));
    }

    @PostMapping("/entry/resolve")
    public Result<SealEntryVO> resolve(@Valid @RequestBody SealEntryResolveRequest request,
                                       @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(service.resolve(request.getScene(), authService.getCurrentUser(token)));
    }

    @GetMapping("/entry-codes")
    public Result<SealEntryVO> entryCode(@RequestParam Long projectId,
                                         @RequestParam Long sealId,
                                         @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(service.entryCode(projectId, sealId, authService.getCurrentUser(token)));
    }

    @GetMapping("/entry-codes/{sealId}/mini-code")
    public Result<Map<String, Object>> miniCode(@PathVariable Long sealId,
                                                @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser user = authService.getCurrentUser(token);
        SealEntryVO entry = service.entryCode(null, sealId, user);
        String image = service.miniCode(sealId, user);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sealId", sealId);
        data.put("scene", entry.getScene());
        data.put("dataUrl", image);
        data.put("imageBase64", image);
        data.put("codeType", image == null ? "DEVELOPMENT_SCENE" : "WECHAT_MINI_PROGRAM_CODE");
        return Result.success(data);
    }

    @PostMapping("/entry-codes/{sealId}/rotate")
    public Result<SealEntryVO> rotate(@PathVariable Long sealId,
                                      @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(service.rotate(sealId, authService.getCurrentUser(token)));
    }

    @PutMapping("/entry-codes/{sealId}/status")
    public Result<SealEntryVO> status(@PathVariable Long sealId,
                                      @Valid @RequestBody SealEntryStatusRequest request,
                                      @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(service.updateQrStatus(sealId, request.getEnabled(), authService.getCurrentUser(token)));
    }
}
