package com.example.siteplatform.file.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@ConditionalOnProperty(prefix = "file.cleanup.project-profile-staging", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class ProjectProfileStagingFileCleanupScheduler {
    private static final Logger log = LoggerFactory.getLogger(ProjectProfileStagingFileCleanupScheduler.class);
    private final ProjectProfileStagingFileCleanupService cleanupService;
    private final long ttlHours;
    private final int batchSize;

    public ProjectProfileStagingFileCleanupScheduler(
            ProjectProfileStagingFileCleanupService cleanupService,
            @Value("${file.cleanup.project-profile-staging.ttl-hours:24}") long ttlHours,
            @Value("${file.cleanup.project-profile-staging.batch-size:200}") int batchSize) {
        if (ttlHours <= 0 || batchSize <= 0) throw new IllegalArgumentException("项目效果图清理配置必须为正数");
        this.cleanupService = cleanupService;
        this.ttlHours = ttlHours;
        this.batchSize = batchSize;
    }

    @Scheduled(initialDelayString = "${file.cleanup.project-profile-staging.initial-delay-ms:60000}",
            fixedDelayString = "${file.cleanup.project-profile-staging.fixed-delay-ms:3600000}")
    public void cleanupExpiredFiles() {
        ProjectProfileStagingFileCleanupService.CleanupResult result =
                cleanupService.cleanupExpired(LocalDateTime.now().minusHours(ttlHours), batchSize);
        if (result.scannedCount() > 0) {
            log.info("项目效果图暂存清理完成: scanned={}, deleted={}, failed={}, skipped={}",
                    result.scannedCount(), result.deletedCount(), result.failedCount(), result.skippedCount());
        }
    }
}
