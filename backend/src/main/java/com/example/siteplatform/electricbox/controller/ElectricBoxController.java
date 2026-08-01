package com.example.siteplatform.electricbox.controller;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.common.Result;
import com.example.siteplatform.electricbox.dto.ElectricBoxLifecycleRequest;
import com.example.siteplatform.electricbox.dto.ElectricBoxPublicAccessRequest;
import com.example.siteplatform.electricbox.dto.ElectricBoxQrPrintLogRequest;
import com.example.siteplatform.electricbox.dto.ElectricBoxQrRebindRequest;
import com.example.siteplatform.electricbox.dto.ElectricBoxQrSvgRequest;
import com.example.siteplatform.electricbox.dto.ElectricBoxRequest;
import com.example.siteplatform.electricbox.dto.ElectricBoxScopeRequest;
import com.example.siteplatform.electricbox.service.ElectricBoxInspectionScopeService;
import com.example.siteplatform.electricbox.service.ElectricBoxService;
import com.example.siteplatform.electricbox.vo.ElectricBoxImportResultVO;
import com.example.siteplatform.electricbox.vo.ElectricBoxQrLogVO;
import com.example.siteplatform.electricbox.vo.ElectricBoxScopeVO;
import com.example.siteplatform.electricbox.vo.ElectricBoxVO;
import com.example.siteplatform.electricbox.vo.ElectricBoxUnifiedCodeVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Tag(name = "电箱台账", description = "现场电箱台账与二维码解析接口")
@RestController
@RequestMapping("/api/v1/electric-boxes")
public class ElectricBoxController {

    @Autowired
    private ElectricBoxService electricBoxService;

    @Autowired
    private ElectricBoxInspectionScopeService inspectionScopeService;

    @Autowired
    private AuthService authService;

    @Operation(summary = "获取电箱列表")
    @GetMapping
    public Result<List<ElectricBoxVO>> list(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        return Result.success(electricBoxService.list(projectId, status, keyword, currentUser));
    }

    @Operation(summary = "获取电箱详情")
    @GetMapping("/{id}")
    public Result<ElectricBoxVO> detail(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        return Result.success(electricBoxService.getById(id, currentUser));
    }

    @Operation(summary = "获取电箱当前日检范围")
    @GetMapping("/{id}/inspection-scope")
    public Result<ElectricBoxScopeVO> getInspectionScope(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        return Result.success(inspectionScopeService.getCurrent(id, currentUser));
    }

    @Operation(summary = "设置电箱日检范围")
    @PutMapping("/{id}/inspection-scope")
    public Result<ElectricBoxScopeVO> updateInspectionScope(
            @PathVariable Long id,
            @RequestBody ElectricBoxScopeRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        return Result.success(inspectionScopeService.update(id, request, currentUser));
    }

    @Operation(summary = "新增电箱")
    @PostMapping
    public Result<ElectricBoxVO> create(
            @RequestBody ElectricBoxRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        return Result.success(electricBoxService.create(request, currentUser));
    }

    @Operation(summary = "编辑电箱")
    @PutMapping("/{id}")
    public Result<ElectricBoxVO> update(
            @PathVariable Long id,
            @RequestBody ElectricBoxRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        return Result.success(electricBoxService.update(id, request, currentUser));
    }

    @Operation(summary = "停用电箱")
    @PostMapping("/{id}/disable")
    public Result<ElectricBoxVO> disable(
            @PathVariable Long id,
            @RequestBody(required = false) ElectricBoxLifecycleRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        return Result.success(electricBoxService.disable(id, request, currentUser));
    }

    @Operation(summary = "拆除电箱")
    @PostMapping("/{id}/remove")
    public Result<ElectricBoxVO> remove(
            @PathVariable Long id,
            @RequestBody(required = false) ElectricBoxLifecycleRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        return Result.success(electricBoxService.remove(id, request, currentUser));
    }

    @Operation(summary = "换绑内部二维码")
    @PostMapping("/{id}/qr/rebind")
    public Result<ElectricBoxVO> rebindQrCode(
            @PathVariable Long id,
            @RequestBody(required = false) ElectricBoxQrRebindRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        return Result.success(electricBoxService.rebindQrCode(id, request, currentUser));
    }

    @Operation(summary = "获取统一电箱巡检码")
    @GetMapping("/{id}/unified-code")
    public Result<ElectricBoxUnifiedCodeVO> getUnifiedCode(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        return Result.success(electricBoxService.getUnifiedCode(id, currentUser));
    }

    @Operation(summary = "更换统一电箱巡检码")
    @PostMapping("/{id}/unified-code/rotate")
    public Result<ElectricBoxUnifiedCodeVO> rotateUnifiedCode(
            @PathVariable Long id,
            @RequestBody(required = false) ElectricBoxQrRebindRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        return Result.success(electricBoxService.rotateUnifiedCode(id, request, currentUser));
    }

    @Operation(summary = "记录二维码打印或补打")
    @PostMapping("/{id}/qr/print-log")
    public Result<List<ElectricBoxQrLogVO>> recordQrPrintLog(
            @PathVariable Long id,
            @RequestBody(required = false) ElectricBoxQrPrintLogRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        return Result.success(electricBoxService.recordPrintLog(id, request, currentUser));
    }

    @Operation(summary = "获取二维码操作日志")
    @GetMapping("/{id}/qr-logs")
    public Result<List<ElectricBoxQrLogVO>> listQrLogs(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        return Result.success(electricBoxService.listQrLogs(id, currentUser));
    }

    @Operation(summary = "设置电箱公开扫码访问")
    @PostMapping("/{id}/public-access")
    public Result<ElectricBoxVO> setPublicAccess(
            @PathVariable Long id,
            @RequestBody ElectricBoxPublicAccessRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        boolean enabled = request != null && Boolean.TRUE.equals(request.getEnabled());
        return Result.success(electricBoxService.setPublicAccess(id, enabled, currentUser));
    }

    @Operation(summary = "解析电箱二维码")
    @GetMapping("/qr/{qrCode}")
    public Result<ElectricBoxVO> resolveQrCode(
            @PathVariable String qrCode,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        return Result.success(electricBoxService.resolveQrCode(qrCode, currentUser));
    }

    @Operation(summary = "下载电箱导入模板")
    @GetMapping("/import-template")
    public ResponseEntity<byte[]> downloadImportTemplate(
            @RequestHeader(value = "Authorization", required = false) String token) {
        authService.getCurrentUser(token);
        byte[] bytes = electricBoxService.buildImportTemplate();
        String filename = URLEncoder.encode("电箱台账导入模板.xlsx", StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .body(bytes);
    }

    @Operation(summary = "导入电箱台账")
    @PostMapping("/import")
    public Result<ElectricBoxImportResultVO> importBoxes(
            @RequestParam Long projectId,
            @RequestParam(defaultValue = "true") boolean dryRun,
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        return Result.success(electricBoxService.importBoxes(projectId, file, dryRun, currentUser));
    }

    @Operation(summary = "生成二维码 SVG")
    @PostMapping("/qr-svg")
    public Result<String> generateQrSvg(
            @RequestBody ElectricBoxQrSvgRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        authService.getCurrentUser(token);
        return Result.success(electricBoxService.generateQrSvg(request == null ? null : request.getPayload()));
    }
}
