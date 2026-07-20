package com.example.siteplatform.document.service;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.document.entity.ProjectDocument;
import com.example.siteplatform.document.entity.ProjectDocumentVersion;
import com.example.siteplatform.document.mapper.DocumentFolderMapper;
import com.example.siteplatform.document.mapper.ProjectDocumentMapper;
import com.example.siteplatform.document.mapper.ProjectDocumentVersionMapper;
import com.example.siteplatform.document.vo.ProjectDocumentDetailVO;
import com.example.siteplatform.document.vo.ProjectDocumentSummaryVO;
import com.example.siteplatform.file.entity.FileResource;
import com.example.siteplatform.file.mapper.FileResourceMapper;
import com.example.siteplatform.file.storage.FileStorageManager;
import com.example.siteplatform.file.storage.StoredFile;
import com.example.siteplatform.log.mapper.OperationLogMapper;
import com.example.siteplatform.project.service.ProjectPermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectDocumentServiceTest {
    @Mock private ProjectDocumentMapper documentMapper;
    @Mock private ProjectDocumentVersionMapper versionMapper;
    @Mock private DocumentFolderMapper folderMapper;
    @Mock private FileResourceMapper fileMapper;
    @Mock private FileStorageManager storageManager;
    @Mock private ProjectPermissionService permissionService;
    @Mock private DocumentFolderService folderService;
    @Mock private OperationLogMapper operationLogMapper;
    @Mock private SysUserMapper userMapper;

    private ProjectDocumentService service;
    private SysUser user;
    private MockMultipartFile file;

    @BeforeEach
    void setUp() {
        service = spy(new ProjectDocumentService(documentMapper, versionMapper, folderMapper, fileMapper,
                storageManager, permissionService, folderService, operationLogMapper, userMapper));
        user = new SysUser();
        user.setId(7L);
        user.setUsername("builder");
        user.setRealName("施工员");
        file = new MockMultipartFile("file", "施工方案.pdf", "application/pdf", new byte[]{1, 2, 3});
        lenient().when(storageManager.store(any(), eq(file))).thenReturn(new StoredFile(
                "local", "documents/1/file.pdf", "施工方案.pdf", "application/pdf", "pdf", 3L, "abc"));
        lenient().doAnswer(invocation -> {
            ((FileResource) invocation.getArgument(0)).setId(100L);
            return 1;
        }).when(fileMapper).insert(any());
        lenient().doAnswer(invocation -> {
            ((ProjectDocumentVersion) invocation.getArgument(0)).setId(300L);
            return 1;
        }).when(versionMapper).insert(any());
        lenient().doReturn(new ProjectDocumentDetailVO()).when(service).detail(any(), eq(user));
    }

    @Test
    void initialUploadCreatesVersionOneAndStorageMetadata() {
        doAnswer(invocation -> {
            ((ProjectDocument) invocation.getArgument(0)).setId(200L);
            return 1;
        }).when(documentMapper).insert(any());
        service.create(1L, 0L, "DOC-001", "施工方案", "PROJECT_DATA", "备注", "首次上传",
                file, user, null);

        verify(permissionService).checkProjectPermission(7L, 1L);
        ArgumentCaptor<FileResource> fileCaptor = ArgumentCaptor.forClass(FileResource.class);
        verify(fileMapper).insert(fileCaptor.capture());
        assertEquals("local", fileCaptor.getValue().getStorageProvider());
        assertEquals("documents/1/file.pdf", fileCaptor.getValue().getStorageKey());
        assertEquals("abc", fileCaptor.getValue().getSha256());

        ArgumentCaptor<ProjectDocumentVersion> versionCaptor = ArgumentCaptor.forClass(ProjectDocumentVersion.class);
        verify(versionMapper).insert(versionCaptor.capture());
        assertEquals(1, versionCaptor.getValue().getVersionNo());
        assertEquals(100L, versionCaptor.getValue().getFileResourceId());
    }

    @Test
    void newVersionUsesLockedDocumentAndIncrementsVersionNumber() {
        ProjectDocument document = new ProjectDocument();
        document.setId(200L);
        document.setProjectId(1L);
        document.setTitle("施工方案");
        document.setCategory("PROJECT_DATA");
        document.setStatus(ProjectDocumentService.STATUS_ACTIVE);
        document.setCreatedBy(7L);
        when(documentMapper.selectById(200L)).thenReturn(document);
        when(documentMapper.selectForUpdate(200L)).thenReturn(document);
        when(versionMapper.selectMaxVersionNo(200L)).thenReturn(1);

        service.uploadVersion(200L, "修订施工顺序", file, user, null);

        ArgumentCaptor<ProjectDocumentVersion> versionCaptor = ArgumentCaptor.forClass(ProjectDocumentVersion.class);
        verify(versionMapper).insert(versionCaptor.capture());
        assertEquals(2, versionCaptor.getValue().getVersionNo());
        assertEquals("修订施工顺序", versionCaptor.getValue().getChangeNote());
    }

    @Test
    void summaryExposesProjectManagementCapabilityWhenLibraryIsEmpty() {
        when(permissionService.isPlatformAdmin(7L)).thenReturn(false);
        when(permissionService.canManageProject(7L, 1L)).thenReturn(true);
        when(documentMapper.selectCount(any())).thenReturn(0L);

        ProjectDocumentSummaryVO summary = service.summary(1L, user);

        verify(permissionService).checkProjectPermission(7L, 1L);
        assertEquals(Boolean.TRUE, summary.getCanManage());
        assertEquals(0L, summary.getTotal());
    }
}
