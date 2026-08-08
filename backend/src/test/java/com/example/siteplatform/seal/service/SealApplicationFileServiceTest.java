package com.example.siteplatform.seal.service;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.document.service.ProjectDocumentService;
import com.example.siteplatform.file.mapper.FileResourceMapper;
import com.example.siteplatform.file.storage.FileStorageManager;
import com.example.siteplatform.seal.entity.SealApplication;
import com.example.siteplatform.seal.entity.SealApplicationFile;
import com.example.siteplatform.seal.mapper.SealApplicationFileMapper;
import com.example.siteplatform.seal.mapper.SealApplicationItemMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SealApplicationFileServiceTest {

    @Mock private SealApplicationService applicationService;
    @Mock private SealApplicationFileMapper relationMapper;
    @Mock private SealApplicationItemMapper itemMapper;
    @Mock private FileResourceMapper fileMapper;
    @Mock private FileStorageManager storageManager;
    @Mock private ProjectDocumentService documentService;

    private SealApplicationFileService service;

    @BeforeEach
    void setUp() {
        service = new SealApplicationFileService(applicationService, relationMapper, itemMapper, fileMapper,
                storageManager, documentService);
    }

    @Test
    void recordAclIsCheckedBeforeRelationMetadataOrPhysicalFileIsLoaded() {
        SealApplication application = application(42L, 7L, SealApplicationService.APPROVED);
        SysUser unrelatedUser = user(99L);
        when(applicationService.requireApplication(42L)).thenReturn(application);
        doThrow(BusinessException.forbidden("无权查看该用印申请"))
                .when(applicationService).requireReadable(application, unrelatedUser);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.content(42L, 501L, true, unrelatedUser, null));

        assertEquals(403, error.getCode());
        verify(relationMapper, never()).selectById(any());
        verify(fileMapper, never()).selectById(any());
        verify(storageManager, never()).load(any());
    }

    @Test
    void relationIdFromAnotherApplicationCannotBeUsedEvenByReadableParticipant() {
        SealApplication application = application(42L, 7L, SealApplicationService.APPROVED);
        SealApplicationFile foreignRelation = new SealApplicationFile();
        foreignRelation.setId(501L);
        foreignRelation.setApplicationId(43L);
        foreignRelation.setFileResourceId(900L);
        SysUser owner = user(7L);
        when(applicationService.requireApplication(42L)).thenReturn(application);
        when(relationMapper.selectById(501L)).thenReturn(foreignRelation);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.content(42L, 501L, false, owner, null));

        assertEquals(404, error.getCode());
        verify(fileMapper, never()).selectById(any());
        verify(storageManager, never()).load(any());
    }

    @Test
    void ccParticipantCannotUploadSourceFileToApplicantsDraft() {
        SealApplication application = application(42L, 7L, SealApplicationService.DRAFT);
        SysUser ccUser = user(8L);
        when(applicationService.requireApplicationForUpdate(42L)).thenReturn(application);
        MockMultipartFile file = new MockMultipartFile(
                "file", "施工方案.pdf", "application/pdf", "%PDF-1.7".getBytes());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.upload(42L, "SOURCE", null, file, ccUser, null));

        assertEquals(403, error.getCode());
        verify(storageManager, never()).store(any(), any());
        verify(fileMapper, never()).insert(any());
        verify(relationMapper, never()).insert(any());
    }

    @Test
    void sourceUploadLocksApplicationBeforeCheckingDraftStatus() {
        SealApplication submitted = application(42L, 7L, SealApplicationService.PENDING_APPROVAL);
        SysUser owner = user(7L);
        when(applicationService.requireApplicationForUpdate(42L)).thenReturn(submitted);
        MockMultipartFile file = new MockMultipartFile(
                "file", "施工方案.pdf", "application/pdf", "%PDF-1.7".getBytes());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.upload(42L, "SOURCE", null, file, owner, null));

        assertEquals(403, error.getCode());
        verify(applicationService).requireApplicationForUpdate(42L);
        verify(applicationService, never()).requireApplication(42L);
        verify(storageManager, never()).store(any(), any());
    }

    @Test
    void sourceDeleteLocksApplicationBeforeCheckingDraftStatus() {
        SealApplication submitted = application(42L, 7L, SealApplicationService.PENDING_APPROVAL);
        SealApplicationFile source = new SealApplicationFile();
        source.setId(501L);
        source.setApplicationId(42L);
        source.setFileResourceId(900L);
        source.setFileRole("SOURCE");
        SysUser owner = user(7L);
        when(applicationService.requireApplicationForUpdate(42L)).thenReturn(submitted);
        when(relationMapper.selectById(501L)).thenReturn(source);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.delete(42L, 501L, owner, null));

        assertEquals(403, error.getCode());
        verify(applicationService).requireApplicationForUpdate(42L);
        verify(applicationService, never()).requireApplication(42L);
        verify(fileMapper, never()).selectById(any());
    }

    private SealApplication application(Long id, Long applicantId, String status) {
        SealApplication application = new SealApplication();
        application.setId(id);
        application.setProjectId(9L);
        application.setApplicantId(applicantId);
        application.setStatus(status);
        return application;
    }

    private SysUser user(Long id) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername("user_" + id);
        return user;
    }
}
