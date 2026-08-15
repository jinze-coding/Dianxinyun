package com.example.siteplatform.file.service;

import com.example.siteplatform.file.entity.FileResource;
import com.example.siteplatform.file.mapper.FileResourceMapper;
import com.example.siteplatform.file.storage.FileStorageManager;
import com.example.siteplatform.project.service.ProjectProfileService;
import com.example.siteplatform.project.service.ProjectRouteImageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ProjectProfileStagingFileCleanupService {
    private static final Logger log = LoggerFactory.getLogger(ProjectProfileStagingFileCleanupService.class);
    private static final int MAX_BATCH_SIZE = 1000;

    private final FileResourceMapper fileMapper;
    private final FileStorageManager storageManager;

    public ProjectProfileStagingFileCleanupService(FileResourceMapper fileMapper,
                                                   FileStorageManager storageManager) {
        this.fileMapper = fileMapper;
        this.storageManager = storageManager;
    }

    public CleanupResult cleanupExpired(LocalDateTime cutoff, int batchSize) {
        if (cutoff == null) throw new IllegalArgumentException("cleanup cutoff cannot be null");
        int limit = Math.max(1, Math.min(batchSize, MAX_BATCH_SIZE));
        List<FileResource> candidates = fileMapper.selectExpiredProjectProfileStagingFiles(cutoff, limit);
        int deleted = 0;
        int failed = 0;
        int skipped = 0;
        for (FileResource file : candidates) {
            if (!eligible(file, cutoff)) {
                skipped++;
                continue;
            }
            try {
                if (!Integer.valueOf(1).equals(file.getDeleted())
                        && fileMapper.claimExpiredProjectProfileStagingFile(file.getId(), cutoff) != 1) {
                    skipped++;
                    continue;
                }
                storageManager.delete(file);
                if (fileMapper.purgeClaimedProjectProfileStagingFile(file.getId(), cutoff) != 1) {
                    throw new IllegalStateException("项目效果图暂存记录状态已变化");
                }
                deleted++;
            } catch (RuntimeException exception) {
                failed++;
                log.error("清理项目效果图暂存文件失败: fileId={}, projectId={}",
                        file.getId(), file.getProjectId(), exception);
            }
        }
        return new CleanupResult(candidates.size(), deleted, failed, skipped);
    }

    private boolean eligible(FileResource file, LocalDateTime cutoff) {
        return file != null && file.getId() != null && file.getBusinessId() == null
                && (ProjectProfileService.PENDING_IMAGE_TYPE.equals(file.getBusinessType())
                || ProjectRouteImageService.PENDING_IMAGE_TYPE.equals(file.getBusinessType()))
                && file.getCreateTime() != null && file.getCreateTime().isBefore(cutoff)
                && (file.getDeleted() == null || file.getDeleted() == 0 || file.getDeleted() == 1);
    }

    public record CleanupResult(int scannedCount, int deletedCount, int failedCount, int skippedCount) {
    }
}
