package com.example.siteplatform.seal.controller;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.seal.service.SealPdfService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/seal/applications")
public class SealApplicationExportController {
    private final SealPdfService pdfService;
    private final AuthService authService;

    public SealApplicationExportController(SealPdfService pdfService, AuthService authService) {
        this.pdfService = pdfService;
        this.authService = authService;
    }

    @GetMapping({"/{id}/form.pdf", "/{id}/pdf"})
    public ResponseEntity<byte[]> formPdf(@PathVariable Long id,
                                          @RequestHeader(value = "Authorization", required = false) String token,
                                          HttpServletRequest request) {
        SysUser currentUser = authService.getCurrentUser(token);
        byte[] pdf = pdfService.generate(id, currentUser, request);
        String fileName = "用印申请单-" + id + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdf.length)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(fileName, StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(pdf);
    }
}
