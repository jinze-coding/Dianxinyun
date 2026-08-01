package com.example.siteplatform.file.service;

import com.example.siteplatform.file.entity.FileResource;
import com.example.siteplatform.file.mapper.FileResourceMapper;
import com.example.siteplatform.file.storage.FileStorageManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Permanently removes expired, unbound quality workflow uploads.
 *
 * <p>An active row is first claimed with a conditional logical delete. This
 * prevents a concurrent workflow submission from binding the same upload. If
 * physical deletion fails, the claimed row remains available to the next run
 * through the cleanup query's explicit deleted=1 recovery branch.</p>
 */
@Service
public class QualityStagingFileCleanupService {
    private static final Logger log = LoggerFactory.getLogger(QualityStagingFileCleanupService.class);
    private static final int MAX_BATCH_SIZE = 1000;
    private static final Set<String> QUALITY_STAGING_TYPES = Set.of(
            "QUALITY_PENDING",
            "QUALITY_RECTIFICATION_PENDING",
            "QUALITY_REVIEW_PENDING"
    );

    private final FileResourceMapper fileMapper;
    private final FileStorageManager storageManager;

    public QualityStagingFileCleanupService(FileResourceMapper fileMapper,
                                            FileStorageManager storageManager) {
        this.fileMapper = fileMapper;
        this.storageManager = storageManager;
    }

    public CleanupResult cleanupExpired(LocalDateTime cutoff, int batchSize) {
        if (cutoff == null) throw new IllegalArgumentException("cleanup cutoff cannot be null");
        int limit = Math.max(1, Math.min(batchSize, MAX_BATCH_SIZE));
        List<FileResource> candidates = fileMapper.selectExpiredQualityStagingFiles(cutoff, limit);
        if (candidates == null || candidates.isEmpty()) {
            return new CleanupResult(0, 0, 0, 0);
        }

        int deletedCount = 0;
        int failedCount = 0;
        int skippedCount = 0;
        for (FileResource file : candidates) {
            if (!isEligible(file, cutoff)) {
                skippedCount++;
                log.warn("忽略不符合条件的质量暂存附件清理候选: fileId={}, projectId={}, businessType={}",
                        file == null ? null : file.getId(),
                        file == null ? null : file.getProjectId(),
                        file == null ? null : file.getBusinessType());
                continue;
            }
            try {
                if (!Integer.valueOf(1).equals(file.getDeleted())) {
                    int claimed = fileMapper.claimExpiredQualityStagingFile(file.getId(), cutoff);
                    if (claimed != 1) {
                        skippedCount++;
                        continue;
                    }
                }

                storageManager.delete(file);
                int purged = fileMapper.purgeClaimedQualityStagingFile(file.getId(), cutoff);
                if (purged != 1) {
                    throw new IllegalStateException("暂存附件记录删除条件已变化");
                }
                deletedCount++;
            } catch (RuntimeException exception) {
                failedCount++;
                log.error("清理质量暂存附件失败，后续文件继续处理: fileId={}, projectId={}, businessType={}",
                        file.getId(), file.getProjectId(), file.getBusinessType(), exception);
            }
        }
        return new CleanupResult(candidates.size(), deletedCount, failedCount, skippedCount);
    }

    private boolean isEligible(FileResource file, LocalDateTime cutoff) {
        if (file == null
                || file.getId() == null
                || file.getBusinessId() != null
                || !QUALITY_STAGING_TYPES.contains(file.getBusinessType())
                || file.getCreateTime() == null
                || !file.getCreateTime().isBefore(cutoff)) {
            return false;
        }
        return file.getDeleted() == null || file.getDeleted() == 0 || file.getDeleted() == 1;
    }

    public record CleanupResult(int scannedCount, int deletedCount, int failedCount, int skippedCount) {
    }
}
