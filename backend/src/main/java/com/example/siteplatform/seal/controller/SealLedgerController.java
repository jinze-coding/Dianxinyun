package com.example.siteplatform.seal.controller;

import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.seal.service.SealLedgerService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1")
public class SealLedgerController {
    private final SealLedgerService service;
    private final AuthService authService;

    public SealLedgerController(SealLedgerService service, AuthService authService) {
        this.service = service;
        this.authService = authService;
    }

    @GetMapping({"/seal/ledger/export", "/seal/applications/export"})
    public ResponseEntity<byte[]> export(
            @RequestParam Long projectId,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate anchorDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestHeader(value = "Authorization", required = false) String token,
            HttpServletRequest request) {
        SealLedgerService.LedgerExport export = service.export(projectId, period, anchorDate, startDate, endDate,
                keyword, status,
                authService.getCurrentUser(token), request);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(export.content().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(export.fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(export.content());
    }
}
