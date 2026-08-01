package com.example.siteplatform.file.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@ConditionalOnProperty(
        prefix = "file.cleanup.quality-staging",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class QualityStagingFileCleanupScheduler {
    private static final Logger log = LoggerFactory.getLogger(QualityStagingFileCleanupScheduler.class);

    private final QualityStagingFileCleanupService cleanupService;
    private final long ttlHours;
    private final int batchSize;

    public QualityStagingFileCleanupScheduler(
            QualityStagingFileCleanupService cleanupService,
            @Value("${file.cleanup.quality-staging.ttl-hours:24}") long ttlHours,
            @Value("${file.cleanup.quality-staging.batch-size:200}") int batchSize) {
        if (ttlHours <= 0) {
            throw new IllegalArgumentException("file.cleanup.quality-staging.ttl-hours must be positive");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("file.cleanup.quality-staging.batch-size must be positive");
        }
        this.cleanupService = cleanupService;
        this.ttlHours = ttlHours;
        this.batchSize = batchSize;
    }

    @Scheduled(
            initialDelayString = "${file.cleanup.quality-staging.initial-delay-ms:60000}",
            fixedDelayString = "${file.cleanup.quality-staging.fixed-delay-ms:3600000}"
    )
    public void cleanupExpiredFiles() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(ttlHours);
        QualityStagingFileCleanupService.CleanupResult result =
                cleanupService.cleanupExpired(cutoff, batchSize);
        if (result.scannedCount() > 0) {
            log.info("质量暂存附件清理完成: scanned={}, deleted={}, failed={}, skipped={}",
                    result.scannedCount(), result.deletedCount(),
                    result.failedCount(), result.skippedCount());
        }
    }
}
