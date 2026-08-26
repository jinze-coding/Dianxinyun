package com.example.siteplatform.file.service;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.file.entity.FileResource;
import com.example.siteplatform.file.mapper.FileResourceMapper;
import com.example.siteplatform.file.storage.FileStorageManager;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.system.constant.SystemPermissionCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QualityWeeklyFileServiceTest {
    private FileResourceMapper fileMapper;
    private FileStorageManager storageManager;
    private ProjectPermissionService permissionService;
    private QualityWeeklyFileService service;

    @BeforeEach
    void setUp() {
        fileMapper = mock(FileResourceMapper.class);
        storageManager = mock(FileStorageManager.class);
        permissionService = mock(ProjectPermissionService.class);
        service = new QualityWeeklyFileService(fileMapper, storageManager, permissionService);
    }

    @Test
    void collaboratorCanRetainBoundPhotoAndAddOwnPendingPhoto() {
        FileResource retained = file(11L, 8L, "QUALITY_WEEKLY_DRAFT_ITEM", 42L);
        FileResource added = file(12L, 9L, "QUALITY_WEEKLY_ITEM_PENDING", null);
        when(fileMapper.selectWeeklyDraftFilesForUpdate(2L, "QUALITY_WEEKLY_DRAFT_ITEM", 42L))
                .thenReturn(List.of(retained));
        when(fileMapper.selectByIdsForUpdate(List.of(11L, 12L))).thenReturn(List.of(retained, added));
        when(fileMapper.bindWeeklyPendingFile(12L, 2L, "QUALITY_WEEKLY_ITEM_PENDING",
                "QUALITY_WEEKLY_DRAFT_ITEM", 42L, 9L)).thenReturn(1);

        List<Long> result = service.syncDraftFiles(user(9L), 2L, List.of(11L, 12L),
                "QUALITY_WEEKLY_ITEM_PENDING", "QUALITY_WEEKLY_DRAFT_ITEM", 42L);

        assertEquals(List.of(11L, 12L), result);
        verify(permissionService).requireSystemPermission(9L, 2L, SystemPermissionCodes.QUALITY_MANAGE);
        verify(fileMapper).bindWeeklyPendingFile(12L, 2L, "QUALITY_WEEKLY_ITEM_PENDING",
                "QUALITY_WEEKLY_DRAFT_ITEM", 42L, 9L);
        verify(storageManager, never()).delete(retained);
    }

    @Test
    void cannotAddAnotherUsersUnboundUploadToSharedDraft() {
        FileResource pending = file(12L, 8L, "QUALITY_WEEKLY_ITEM_PENDING", null);
        when(fileMapper.selectWeeklyDraftFilesForUpdate(2L, "QUALITY_WEEKLY_DRAFT_ITEM", 42L))
                .thenReturn(List.of());
        when(fileMapper.selectByIdsForUpdate(List.of(12L))).thenReturn(List.of(pending));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.syncDraftFiles(user(9L), 2L, List.of(12L),
                        "QUALITY_WEEKLY_ITEM_PENDING", "QUALITY_WEEKLY_DRAFT_ITEM", 42L));

        assertEquals(403, error.getCode());
        verify(fileMapper, never()).bindWeeklyPendingFile(
                12L, 2L, "QUALITY_WEEKLY_ITEM_PENDING", "QUALITY_WEEKLY_DRAFT_ITEM", 42L, 9L);
    }

    @Test
    void removedDraftPhotoIsStagedThenPhysicallyPurgedAfterCommitBoundary() {
        FileResource removed = file(11L, 8L, "QUALITY_WEEKLY_DRAFT", 7L);
        when(fileMapper.selectWeeklyDraftFilesForUpdate(2L, "QUALITY_WEEKLY_DRAFT", 7L))
                .thenReturn(List.of(removed));
        when(fileMapper.stageWeeklyDraftFileForDelete(11L, 2L, "QUALITY_WEEKLY_DRAFT", 7L))
                .thenReturn(1);
        when(fileMapper.purgeById(11L)).thenReturn(1);

        service.syncDraftFiles(user(9L), 2L, List.of(),
                "QUALITY_WEEKLY_PENDING", "QUALITY_WEEKLY_DRAFT", 7L);

        verify(fileMapper).stageWeeklyDraftFileForDelete(11L, 2L, "QUALITY_WEEKLY_DRAFT", 7L);
        verify(storageManager).delete(removed);
        verify(fileMapper).purgeById(11L);
    }

    @Test
    void transferMovesOnlyTheMatchingDraftOwnerToFinalIssue() {
        FileResource first = file(11L, 8L, "QUALITY_WEEKLY_DRAFT_ITEM", 42L);
        FileResource second = file(12L, 9L, "QUALITY_WEEKLY_DRAFT_ITEM", 42L);
        when(fileMapper.selectWeeklyDraftFilesForUpdate(2L, "QUALITY_WEEKLY_DRAFT_ITEM", 42L))
                .thenReturn(List.of(first, second));
        when(fileMapper.transferWeeklyDraftFile(11L, 2L, "QUALITY_WEEKLY_DRAFT_ITEM",
                42L, "QUALITY_ISSUE", 101L)).thenReturn(1);
        when(fileMapper.transferWeeklyDraftFile(12L, 2L, "QUALITY_WEEKLY_DRAFT_ITEM",
                42L, "QUALITY_ISSUE", 101L)).thenReturn(1);

        assertEquals(List.of(11L, 12L), service.transferDraftFiles(
                2L, "QUALITY_WEEKLY_DRAFT_ITEM", 42L, "QUALITY_ISSUE", 101L));
    }

    @Test
    void rejectsOverviewDraftBeingTransferredToIssue() {
        assertThrows(BusinessException.class, () -> service.transferDraftFiles(
                2L, "QUALITY_WEEKLY_DRAFT", 7L, "QUALITY_ISSUE", 101L));
        verify(fileMapper, never()).selectWeeklyDraftFilesForUpdate(2L, "QUALITY_WEEKLY_DRAFT", 7L);
    }

    private SysUser user(Long id) {
        SysUser user = new SysUser();
        user.setId(id);
        return user;
    }

    private FileResource file(Long id, Long uploaderId, String type, Long businessId) {
        FileResource file = new FileResource();
        file.setId(id);
        file.setProjectId(2L);
        file.setUploaderId(uploaderId);
        file.setBusinessType(type);
        file.setBusinessId(businessId);
        file.setStatus("UPLOADED");
        file.setStorageProvider("local");
        file.setStorageKey("quality-weekly/" + id + ".jpg");
        return file;
    }
}
