package com.example.siteplatform.electricbox.controller;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.common.Result;
import com.example.siteplatform.electricbox.service.UnifiedElectricBoxScanService;
import com.example.siteplatform.electricbox.vo.UnifiedElectricBoxScanVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "统一巡检码", description = "电箱一码两用身份分流")
@RestController
@RequestMapping("/api/v1/scan/electric-boxes")
public class UnifiedElectricBoxScanController {

    private final UnifiedElectricBoxScanService scanService;
    private final AuthService authService;

    public UnifiedElectricBoxScanController(UnifiedElectricBoxScanService scanService, AuthService authService) {
        this.scanService = scanService;
        this.authService = authService;
    }

    @Operation(summary = "解析统一电箱巡检码")
    @GetMapping("/{sceneCode}")
    public Result<UnifiedElectricBoxScanVO> resolve(
            @PathVariable String sceneCode,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUserIfPresent(token);
        return Result.success(scanService.resolve(sceneCode, currentUser));
    }
}
