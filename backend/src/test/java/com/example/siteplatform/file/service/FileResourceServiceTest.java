package com.example.siteplatform.file.service;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.file.entity.FileResource;
import com.example.siteplatform.file.mapper.FileResourceMapper;
import com.example.siteplatform.project.service.ProjectPermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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

        service.validateAndBind(user, 2L, List.of(11L),
                "QUALITY_PENDING", "QUALITY_ISSUE", 19L);

        assertEquals("QUALITY_ISSUE", file.getBusinessType());
        assertEquals(19L, file.getBusinessId());
        verify(fileMapper).updateById(file);
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
    void genericFileApiCannotAccessProjectDocumentVersion() {
        FileResource file = file(11L, 2L, 7L, "PROJECT_DOCUMENT", null);

        assertThrows(BusinessException.class, () -> service.checkRead(user(7L), file));
    }

    @Test
    void genericFileApiCannotModifyBoundWorkflowAttachment() {
        FileResource file = file(11L, 2L, 7L, "QUALITY_ISSUE", 19L);

        assertThrows(BusinessException.class, () -> service.checkWrite(user(7L), file));
    }

    @Test
    void qualityManagerCanMaintainUnboundQualityDocument() {
        SysUser manager = user(9L);
        FileResource file = file(11L, 2L, 7L, "QUALITY_DOCUMENT", null);
        when(permissionService.isPlatformAdmin(9L)).thenReturn(false);
        when(permissionService.canManageQuality(9L, 2L)).thenReturn(true);

        service.checkWrite(manager, file);

        verify(permissionService).checkProjectPermission(9L, 2L);
        verify(permissionService).canManageQuality(9L, 2L);
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
