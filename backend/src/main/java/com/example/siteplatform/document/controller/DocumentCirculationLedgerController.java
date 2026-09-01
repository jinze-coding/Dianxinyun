package com.example.siteplatform.document.controller;

import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.document.service.DocumentCirculationLedgerExport;
import com.example.siteplatform.document.service.DocumentCirculationLedgerService;
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

@RestController
@RequestMapping("/api/v1/document-circulation-ledger")
public class DocumentCirculationLedgerController {
    private final DocumentCirculationLedgerService service;
    private final AuthService authService;

    public DocumentCirculationLedgerController(DocumentCirculationLedgerService service, AuthService authService) {
        this.service = service;
        this.authService = authService;
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam Long projectId,
                                         @RequestHeader("Authorization") String token) {
        DocumentCirculationLedgerExport result = service.export(projectId, authService.getCurrentUser(token));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(result.content().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(result.fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(result.content());
    }
}
