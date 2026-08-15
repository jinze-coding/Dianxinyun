package com.example.siteplatform.file.service;

import com.example.siteplatform.file.entity.FileResource;
import com.example.siteplatform.file.mapper.FileResourceMapper;
import com.example.siteplatform.file.storage.FileStorageManager;
import com.example.siteplatform.project.service.ProjectProfileService;
import com.example.siteplatform.project.service.ProjectRouteImageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectProfileStagingFileCleanupServiceTest {
    @Mock private FileResourceMapper fileMapper;
    @Mock private FileStorageManager storageManager;

    @Test
    void expiredUnboundProjectImageIsClaimedThenPhysicallyRemoved() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        FileResource file = new FileResource();
        file.setId(7L);
        file.setProjectId(3L);
        file.setBusinessType(ProjectProfileService.PENDING_IMAGE_TYPE);
        file.setCreateTime(cutoff.minusMinutes(1));
        file.setDeleted(0);
        when(fileMapper.selectExpiredProjectProfileStagingFiles(cutoff, 20)).thenReturn(List.of(file));
        when(fileMapper.claimExpiredProjectProfileStagingFile(7L, cutoff)).thenReturn(1);
        when(fileMapper.purgeClaimedProjectProfileStagingFile(7L, cutoff)).thenReturn(1);

        var result = new ProjectProfileStagingFileCleanupService(fileMapper, storageManager)
                .cleanupExpired(cutoff, 20);

        assertEquals(1, result.deletedCount());
        verify(storageManager).delete(file);
    }

    @Test
    void expiredUnboundProjectRouteImageUsesTheSameClaimedCleanupFlow() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        FileResource file = new FileResource();
        file.setId(8L);
        file.setProjectId(3L);
        file.setBusinessType(ProjectRouteImageService.PENDING_IMAGE_TYPE);
        file.setCreateTime(cutoff.minusMinutes(1));
        file.setDeleted(0);
        when(fileMapper.selectExpiredProjectProfileStagingFiles(cutoff, 20)).thenReturn(List.of(file));
        when(fileMapper.claimExpiredProjectProfileStagingFile(8L, cutoff)).thenReturn(1);
        when(fileMapper.purgeClaimedProjectProfileStagingFile(8L, cutoff)).thenReturn(1);

        var result = new ProjectProfileStagingFileCleanupService(fileMapper, storageManager)
                .cleanupExpired(cutoff, 20);

        assertEquals(1, result.deletedCount());
        verify(storageManager).delete(file);
    }
}
