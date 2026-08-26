package com.example.siteplatform.file.service;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.file.entity.FileResource;
import com.example.siteplatform.file.mapper.FileResourceMapper;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.system.constant.SystemPermissionCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileResourceServiceTest {
    private FileResourceMapper fileMapper;
    private ProjectPermissionService permissionService;
    private FileResourceService service;

    @BeforeEach
    void setUp() {
        fileMapper = mock(FileResourceMapper.class);
        permissionService = mock(ProjectPermissionService.class);
        service = new FileResourceService(fileMapper, permissionService);
    }

    @Test
    void bindsOnlyCurrentUsersPendingAttachment() {
        SysUser user = user(7L);
        FileResource file = file(11L, 2L, 7L, "QUALITY_PENDING", null);
        when(fileMapper.selectList(any())).thenReturn(List.of(file));
        when(fileMapper.updateById(file)).thenReturn(1);

        service.validateAndBind(user, 2L, List.of(11L),
                "QUALITY_PENDING", "QUALITY_ISSUE", 19L);

        assertEquals("QUALITY_ISSUE", file.getBusinessType());
        assertEquals(19L, file.getBusinessId());
        verify(fileMapper).updateById(file);
    }

    @Test
    void bindsFirstInspectionPhotoAfterRecordIsCreated() {
        SysUser user = user(7L);
        FileResource file = file(12L, 2L, 7L, "inspection_record", null);
        when(fileMapper.selectList(any())).thenReturn(List.of(file));
        when(fileMapper.updateById(file)).thenReturn(1);

        service.validateAndBind(user, 2L, List.of(12L),
                "inspection_record", "inspection_record", 31L);

        assertEquals("inspection_record", file.getBusinessType());
        assertEquals(31L, file.getBusinessId());
        verify(fileMapper).updateById(file);
    }

    @Test
    void rejectsAttachmentClaimedByCleanupDuringBinding() {
        FileResource file = file(11L, 2L, 7L, "QUALITY_PENDING", null);
        when(fileMapper.selectList(any())).thenReturn(List.of(file));
        when(fileMapper.updateById(file)).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.validateAndBind(user(7L), 2L, List.of(11L),
                        "QUALITY_PENDING", "QUALITY_ISSUE", 19L));

        assertEquals("附件状态已变化，请重新上传后提交", exception.getMessage());
    }

    @Test
    void rejectsAttachmentUploadedByAnotherUser() {
        FileResource file = file(11L, 2L, 8L, "QUALITY_PENDING", null);
        when(fileMapper.selectList(any())).thenReturn(List.of(file));

        assertThrows(BusinessException.class, () -> service.validateAndBind(user(7L), 2L, List.of(11L),
                "QUALITY_PENDING", "QUALITY_ISSUE", 19L));
        verify(fileMapper, never()).updateById(any());
    }

    @Test
    void rejectsAlreadyBoundAttachment() {
        FileResource file = file(11L, 2L, 7L, "QUALITY_PENDING", 18L);
        when(fileMapper.selectList(any())).thenReturn(List.of(file));

        assertThrows(BusinessException.class, () -> service.validateAndBind(user(7L), 2L, List.of(11L),
                "QUALITY_PENDING", "QUALITY_ISSUE", 19L));
        verify(fileMapper, never()).updateById(any());
    }

    @Test
    void rejectsAttachmentFromWrongStagingType() {
        FileResource file = file(11L, 2L, 7L, "QUALITY_REVIEW_PENDING", null);
        when(fileMapper.selectList(any())).thenReturn(List.of(file));

        assertThrows(BusinessException.class, () -> service.validateAndBind(user(7L), 2L, List.of(11L),
                "QUALITY_PENDING", "QUALITY_ISSUE", 19L));
        verify(fileMapper, never()).updateById(any());
    }

    @Test
    void rejectsMismatchedQualityBindingPairBeforeLoadingFiles() {
        assertThrows(BusinessException.class, () -> service.validateAndBind(user(7L), 2L, List.of(11L),
                "QUALITY_REVIEW_PENDING", "QUALITY_ISSUE", 19L));

        verify(fileMapper, never()).selectList(any());
    }

    @Test
    void genericFileApiCannotAccessProjectDocumentVersion() {
        FileResource file = file(11L, 2L, 7L, "PROJECT_DOCUMENT", null);

        assertThrows(BusinessException.class, () -> service.checkRead(user(7L), file));
    }

    @Test
    void genericFileApiCannotReadSealEvidenceEvenWhenUserHasProjectAccess() {
        FileResource file = file(11L, 2L, 7L, "SEAL_SOURCE", 42L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.checkRead(user(7L), file));

        assertEquals(403, exception.getCode());
        verify(permissionService, never()).checkProjectPermission(7L, 2L);
    }

    @Test
    void genericUploadCannotForgeSealEvidenceBusinessType() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.authorizeUpload(user(7L), 2L, " seal_stamped_result ", 42L));

        assertEquals(403, exception.getCode());
        verify(permissionService).checkProjectPermission(7L, 2L);
    }

    @Test
    void genericFileApiCannotModifyBoundWorkflowAttachment() {
        FileResource file = file(11L, 2L, 7L, "QUALITY_ISSUE", 19L);

        assertThrows(BusinessException.class, () -> service.checkWrite(user(7L), file));
    }

    @Test
    void genericFileApiCannotModifyFinalQualityAttachmentEvenWithoutBusinessId() {
        FileResource file = file(11L, 2L, 7L, "QUALITY_REVIEW", null);

        assertThrows(BusinessException.class, () -> service.checkWrite(user(7L), file));
    }

    @Test
    void qualityManagerCanMaintainUnboundQualityDocument() {
        SysUser manager = user(9L);
        FileResource file = file(11L, 2L, 7L, "QUALITY_DOCUMENT", null);

        service.checkWrite(manager, file);

        verify(permissionService).checkProjectPermission(9L, 2L);
        verify(permissionService).requireSystemPermission(9L, 2L, SystemPermissionCodes.QUALITY_VIEW);
        verify(permissionService).requireSystemPermission(9L, 2L, SystemPermissionCodes.QUALITY_MANAGE);
    }

    @Test
    void qualityDocumentUploaderStillNeedsManagePermission() {
        FileResource file = file(11L, 2L, 9L, "QUALITY_DOCUMENT", null);
        doThrow(BusinessException.forbidden("denied"))
                .when(permissionService)
                .requireSystemPermission(9L, 2L, SystemPermissionCodes.QUALITY_MANAGE);

        assertThrows(BusinessException.class, () -> service.checkWrite(user(9L), file));
    }

    @Test
    void stagedQualityAttachmentCanOnlyBeManagedByItsUploader() {
        FileResource file = file(11L, 2L, 8L, "QUALITY_RECTIFICATION_PENDING", null);

        assertThrows(BusinessException.class, () -> service.checkWrite(user(9L), file));

        verify(permissionService).requireSystemPermission(9L, 2L, SystemPermissionCodes.QUALITY_RECTIFY);
    }

    @Test
    void authorizesQualityStagingUploadAgainstTargetProjectAndNormalizesType() {
        String businessType = service.authorizeUpload(
                user(9L), 2L, " quality_review_pending ", null);

        assertEquals("QUALITY_REVIEW_PENDING", businessType);
        verify(permissionService).checkProjectPermission(9L, 2L);
        verify(permissionService).requireSystemPermission(9L, 2L, SystemPermissionCodes.QUALITY_REVIEW);
    }

    @Test
    void authorizesWeeklyDraftStagingUploadsWithQualityManagePermission() {
        assertEquals("QUALITY_WEEKLY_PENDING", service.authorizeUpload(
                user(9L), 2L, " quality_weekly_pending ", null));
        assertEquals("QUALITY_WEEKLY_ITEM_PENDING", service.authorizeUpload(
                user(9L), 2L, "quality_weekly_item_pending", null));

        verify(permissionService, org.mockito.Mockito.times(2))
                .requireSystemPermission(9L, 2L, SystemPermissionCodes.QUALITY_MANAGE);
    }

    @Test
    void sharedWeeklyDraftAttachmentRequiresQualityManageToRead() {
        FileResource file = file(11L, 2L, 8L, "QUALITY_WEEKLY_DRAFT_ITEM", 42L);

        service.checkRead(user(9L), file);

        verify(permissionService).requireSystemPermission(9L, 2L, SystemPermissionCodes.QUALITY_VIEW);
        verify(permissionService).requireSystemPermission(9L, 2L, SystemPermissionCodes.QUALITY_MANAGE);
    }

    @Test
    void weeklyPendingAttachmentRequiresQualityManageEvenForUploader() {
        FileResource file = file(11L, 2L, 9L, "QUALITY_WEEKLY_ITEM_PENDING", null);
        doThrow(BusinessException.forbidden("denied"))
                .when(permissionService)
                .requireSystemPermission(9L, 2L, SystemPermissionCodes.QUALITY_MANAGE);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.checkRead(user(9L), file));

        assertEquals(403, exception.getCode());
        verify(permissionService).requireSystemPermission(9L, 2L, SystemPermissionCodes.QUALITY_VIEW);
        verify(permissionService).requireSystemPermission(9L, 2L, SystemPermissionCodes.QUALITY_MANAGE);
    }

    @Test
    void weeklyPendingAttachmentIsPrivateUntilSavedIntoSharedDraft() {
        FileResource file = file(11L, 2L, 8L, "QUALITY_WEEKLY_PENDING", null);
        when(permissionService.isPlatformAdmin(9L)).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.checkRead(user(9L), file));

        assertEquals(403, exception.getCode());
        verify(permissionService, never()).checkProjectPermission(9L, 2L);
    }

    @Test
    void directUploadCannotCreateFinalQualityAttachment() {
        assertThrows(BusinessException.class, () -> service.authorizeUpload(
                user(9L), 2L, "QUALITY_ISSUE", null));
    }

    @Test
    void qualityUploadCannotInjectBusinessId() {
        assertThrows(BusinessException.class, () -> service.authorizeUpload(
                user(9L), 2L, "QUALITY_PENDING", 19L));
    }

    @Test
    void listHidesQualityFileWhenTargetProjectLacksQualityView() {
        FileResource file = file(11L, 2L, 7L, "QUALITY_DOCUMENT", null);
        doThrow(BusinessException.forbidden("denied"))
                .when(permissionService)
                .requireSystemPermission(9L, 2L, SystemPermissionCodes.QUALITY_VIEW);

        assertFalse(service.canReadInList(user(9L), file));
    }

    @Test
    void nonAdminCannotReadAnotherUsersPendingProjectProfileImage() {
        FileResource file = file(11L, 2L, 8L, "PROJECT_PROFILE_IMAGE_PENDING", null);
        when(permissionService.isPlatformAdmin(9L)).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.checkRead(user(9L), file));

        assertEquals(403, exception.getCode());
        verify(permissionService, never()).checkProjectPermission(9L, 2L);
    }

    @Test
    void onlyPlatformAdminCanOpenProjectProfileImageUploadChannel() {
        when(permissionService.isPlatformAdmin(9L)).thenReturn(false);
        assertThrows(BusinessException.class, () -> service.authorizeUpload(
                user(9L), 2L, "PROJECT_PROFILE_IMAGE_PENDING", null));

        when(permissionService.isPlatformAdmin(9L)).thenReturn(true);
        assertEquals("PROJECT_PROFILE_IMAGE_PENDING", service.authorizeUpload(
                user(9L), 2L, "PROJECT_PROFILE_IMAGE_PENDING", null));
    }

    @Test
    void onlyPlatformAdminCanUploadAndCancelPendingRouteImage() {
        when(permissionService.canManageProject(9L, 2L)).thenReturn(true);
        when(permissionService.isPlatformAdmin(9L)).thenReturn(false);

        BusinessException denied = assertThrows(BusinessException.class, () -> service.authorizeUpload(
                user(9L), 2L, " project_route_image_pending ", null));
        assertEquals(403, denied.getCode());

        FileResource file = file(11L, 2L, 9L, "PROJECT_ROUTE_IMAGE_PENDING", null);
        assertThrows(BusinessException.class, () -> service.checkTemporaryDelete(user(9L), file));

        when(permissionService.isPlatformAdmin(9L)).thenReturn(true);
        assertEquals("PROJECT_ROUTE_IMAGE_PENDING", service.authorizeUpload(
                user(9L), 2L, " project_route_image_pending ", null));
        service.checkTemporaryDelete(user(9L), file);
    }

    @Test
    void routeImageFinalTypeCannotBeUploadedOrModifiedThroughGenericFileApi() {
        assertThrows(BusinessException.class, () -> service.authorizeUpload(
                user(9L), 2L, "PROJECT_ROUTE_IMAGE", null));

        FileResource file = file(11L, 2L, 9L, "PROJECT_ROUTE_IMAGE", 2L);
        assertThrows(BusinessException.class, () -> service.checkWrite(user(9L), file));
    }

    @Test
    void projectManagerCannotOpenRouteImageUploadChannel() {
        when(permissionService.canManageProject(9L, 2L)).thenReturn(true);
        when(permissionService.isPlatformAdmin(9L)).thenReturn(false);

        BusinessException error = assertThrows(BusinessException.class, () -> service.authorizeUpload(
                user(9L), 2L, "PROJECT_ROUTE_IMAGE_PENDING", null));

        assertEquals(403, error.getCode());
    }

    private SysUser user(Long id) {
        SysUser user = new SysUser();
        user.setId(id);
        return user;
    }

    private FileResource file(Long id, Long projectId, Long uploaderId, String businessType, Long businessId) {
        FileResource file = new FileResource();
        file.setId(id);
        file.setProjectId(projectId);
        file.setUploaderId(uploaderId);
        file.setBusinessType(businessType);
        file.setBusinessId(businessId);
        return file;
    }
}
