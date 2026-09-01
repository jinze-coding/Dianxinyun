package com.example.siteplatform.document.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class DocumentUploadCleanupScheduler {
    private final DocumentUploadSessionService service;

    public DocumentUploadCleanupScheduler(DocumentUploadSessionService service) {
        this.service = service;
    }

    @Scheduled(initialDelayString = "${document.circulation.upload-cleanup-initial-delay-ms:60000}",
            fixedDelayString = "${document.circulation.upload-cleanup-fixed-delay-ms:3600000}")
    public void cleanup() {
        service.cleanupExpiredDirectories(LocalDateTime.now().minusHours(
                DocumentUploadSessionService.SESSION_TTL_HOURS), 200);
    }
}
