package com.example.siteplatform.file.service;

import com.example.siteplatform.file.entity.FileResource;
import com.example.siteplatform.file.mapper.FileResourceMapper;
import com.example.siteplatform.file.storage.FileStorageManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class QualityStagingFileCleanupServiceTest {
    private FileResourceMapper fileMapper;
    private FileStorageManager storageManager;
    private QualityStagingFileCleanupService service;

    @BeforeEach
    void setUp() {
        fileMapper = mock(FileResourceMapper.class);
        storageManager = mock(FileStorageManager.class);
        service = new QualityStagingFileCleanupService(fileMapper, storageManager);
    }

    @Test
    void deletesOnlyClaimedExpiredQualityStagingFilesAndRetriesPreviousClaims() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 7, 30, 10, 0);
        FileResource active = file(11L, "QUALITY_PENDING", null, cutoff.minusMinutes(1), 0);
        FileResource retry = file(
                12L,
                "QUALITY_REVIEW_PENDING",
                null,
                cutoff.minusHours(2),
                1);
        when(fileMapper.selectExpiredQualityStagingFiles(cutoff, 200))
                .thenReturn(List.of(active, retry));
        when(fileMapper.claimExpiredQualityStagingFile(11L, cutoff)).thenReturn(1);
        when(fileMapper.purgeClaimedQualityStagingFile(11L, cutoff)).thenReturn(1);
        when(fileMapper.purgeClaimedQualityStagingFile(12L, cutoff)).thenReturn(1);

        QualityStagingFileCleanupService.CleanupResult result =
                service.cleanupExpired(cutoff, 200);

        assertEquals(new QualityStagingFileCleanupService.CleanupResult(2, 2, 0, 0), result);
        verify(storageManager).delete(active);
        verify(storageManager).delete(retry);
        verify(fileMapper, never()).claimExpiredQualityStagingFile(12L, cutoff);
    }

    @Test
    void defensivelySkipsFinalBoundRecentAndInspectionFiles() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 7, 30, 10, 0);
        FileResource finalAttachment =
                file(21L, "QUALITY_ISSUE", null, cutoff.minusHours(2), 0);
        FileResource boundStaging =
                file(22L, "QUALITY_PENDING", 99L, cutoff.minusHours(2), 0);
        FileResource recentStaging =
                file(23L, "QUALITY_RECTIFICATION_PENDING", null, cutoff, 0);
        FileResource inspectionAttachment =
                file(24L, "INSPECTION_RECTIFICATION", null, cutoff.minusHours(2), 0);
        when(fileMapper.selectExpiredQualityStagingFiles(cutoff, 200))
                .thenReturn(List.of(
                        finalAttachment,
                        boundStaging,
                        recentStaging,
                        inspectionAttachment));

        QualityStagingFileCleanupService.CleanupResult result =
                service.cleanupExpired(cutoff, 200);

        assertEquals(new QualityStagingFileCleanupService.CleanupResult(4, 0, 0, 4), result);
        verifyNoInteractions(storageManager);
        verify(fileMapper, never()).claimExpiredQualityStagingFile(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any());
        verify(fileMapper, never()).purgeClaimedQualityStagingFile(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void continuesAfterSingleFileFailureAndLeavesClaimForRetry() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 7, 30, 10, 0);
        FileResource failed = file(
                31L,
                "QUALITY_RECTIFICATION_PENDING",
                null,
                cutoff.minusHours(2),
                0);
        FileResource succeeded =
                file(32L, "QUALITY_REVIEW_PENDING", null, cutoff.minusHours(2), 0);
        when(fileMapper.selectExpiredQualityStagingFiles(cutoff, 200))
                .thenReturn(List.of(failed, succeeded));
        when(fileMapper.claimExpiredQualityStagingFile(31L, cutoff)).thenReturn(1);
        when(fileMapper.claimExpiredQualityStagingFile(32L, cutoff)).thenReturn(1);
        doThrow(new RuntimeException("storage unavailable"))
                .when(storageManager).delete(failed);
        when(fileMapper.purgeClaimedQualityStagingFile(32L, cutoff)).thenReturn(1);

        QualityStagingFileCleanupService.CleanupResult result =
                service.cleanupExpired(cutoff, 200);

        assertEquals(new QualityStagingFileCleanupService.CleanupResult(2, 1, 1, 0), result);
        verify(fileMapper, never()).purgeClaimedQualityStagingFile(31L, cutoff);
        verify(fileMapper).purgeClaimedQualityStagingFile(32L, cutoff);
    }

    @Test
    void skipsFileWhenConcurrentBindingWinsTheClaim() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 7, 30, 10, 0);
        FileResource file = file(41L, "QUALITY_PENDING", null, cutoff.minusHours(2), 0);
        when(fileMapper.selectExpiredQualityStagingFiles(cutoff, 200))
                .thenReturn(List.of(file));
        when(fileMapper.claimExpiredQualityStagingFile(41L, cutoff)).thenReturn(0);

        QualityStagingFileCleanupService.CleanupResult result =
                service.cleanupExpired(cutoff, 200);

        assertEquals(new QualityStagingFileCleanupService.CleanupResult(1, 0, 0, 1), result);
        verifyNoInteractions(storageManager);
        verify(fileMapper, never()).purgeClaimedQualityStagingFile(41L, cutoff);
    }

    private FileResource file(Long id, String businessType, Long businessId,
                              LocalDateTime createTime, Integer deleted) {
        FileResource file = new FileResource();
        file.setId(id);
        file.setProjectId(2L);
        file.setBusinessType(businessType);
        file.setBusinessId(businessId);
        file.setCreateTime(createTime);
        file.setDeleted(deleted);
        file.setStorageProvider("local");
        file.setStorageKey("quality/" + id + ".jpg");
        return file;
    }
}
