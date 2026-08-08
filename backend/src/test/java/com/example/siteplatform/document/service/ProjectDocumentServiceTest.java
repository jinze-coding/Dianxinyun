package com.example.siteplatform.document.service;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.document.dto.ProjectDocumentClientActionRequest;
import com.example.siteplatform.document.entity.ProjectDocument;
import com.example.siteplatform.document.entity.ProjectDocumentVersion;
import com.example.siteplatform.document.mapper.DocumentFolderMapper;
import com.example.siteplatform.document.mapper.ProjectDocumentMapper;
import com.example.siteplatform.document.mapper.ProjectDocumentVersionMapper;
import com.example.siteplatform.document.vo.ProjectDocumentActivityVO;
import com.example.siteplatform.document.vo.ProjectDocumentDetailVO;
import com.example.siteplatform.document.vo.ProjectDocumentSummaryVO;
import com.example.siteplatform.file.entity.FileResource;
import com.example.siteplatform.file.mapper.FileResourceMapper;
import com.example.siteplatform.file.storage.FileStorageManager;
import com.example.siteplatform.file.storage.StoredFile;
import com.example.siteplatform.log.entity.OperationLog;
import com.example.siteplatform.log.mapper.OperationLogMapper;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.system.constant.SystemPermissionCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
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
    @Mock private JdbcTemplate jdbc;

    private ProjectDocumentService service;
    private SysUser user;
    private MockMultipartFile file;

    @BeforeEach
    void setUp() {
        service = spy(new ProjectDocumentService(documentMapper, versionMapper, folderMapper, fileMapper,
                storageManager, permissionService, folderService, operationLogMapper, userMapper, jdbc));
        lenient().when(jdbc.queryForObject(any(String.class), eq(Long.class), anyLong(), anyLong())).thenReturn(0L);
        user = new SysUser();
        user.setId(7L);
        user.setUsername("builder");
        user.setRealName("施工员");
        file = new MockMultipartFile(
                "file", "施工方案.pdf", "application/pdf", "%PDF-1.7".getBytes());
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
        lenient().when(documentMapper.updateById(any())).thenReturn(1);
        lenient().when(documentMapper.deleteById(anyLong())).thenReturn(1);
        lenient().when(documentMapper.restoreById(anyLong())).thenReturn(1);
        lenient().when(documentMapper.purgeById(anyLong())).thenReturn(1);
        lenient().when(versionMapper.deleteByDocumentId(anyLong())).thenReturn(1);
        lenient().doAnswer(invocation -> {
            OperationLog log = invocation.getArgument(0);
            if (log.getId() == null) log.setId(900L);
            return 1;
        }).when(operationLogMapper).insert(any());
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
    void createRejectsDatabaseLengthOverflowBeforeStoringFile() {
        BusinessException documentNoError = assertThrows(BusinessException.class,
                () -> service.create(1L, 0L, "N".repeat(101), "施工方案", "PROJECT_DATA",
                        null, null, file, user, null));
        BusinessException remarkError = assertThrows(BusinessException.class,
                () -> service.create(1L, 0L, null, "施工方案", "PROJECT_DATA",
                        "备".repeat(501), null, file, user, null));
        BusinessException changeNoteError = assertThrows(BusinessException.class,
                () -> service.create(1L, 0L, null, "施工方案", "PROJECT_DATA",
                        null, "说".repeat(501), file, user, null));

        assertTrue(documentNoError.getMessage().contains("资料编号不能超过100"));
        assertTrue(remarkError.getMessage().contains("资料备注不能超过500"));
        assertTrue(changeNoteError.getMessage().contains("版本说明不能超过500"));
        verify(storageManager, never()).store(any(), any());
    }

    @Test
    void createRollsBackWhenFileMetadataInsertDidNotTakeEffect() {
        doReturn(0).when(fileMapper).insert(any());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.create(1L, 0L, "DOC-001", "施工方案", "PROJECT_DATA",
                        null, null, file, user, null));

        assertEquals(409, error.getCode());
        verify(documentMapper, never()).insert(any());
    }

    @Test
    void updateReturnsConflictAndDoesNotWriteSuccessLogWhenRowChangedConcurrently() {
        ProjectDocument document = document(200L, 1L, 300L);
        document.setFolderId(0L);
        document.setStatus(ProjectDocumentService.STATUS_ACTIVE);
        document.setCreatedBy(7L);
        when(documentMapper.selectById(200L)).thenReturn(document);
        when(documentMapper.updateById(any())).thenReturn(0);
        com.example.siteplatform.document.dto.ProjectDocumentUpdateRequest update =
                new com.example.siteplatform.document.dto.ProjectDocumentUpdateRequest();
        update.setTitle("新方案");

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.update(200L, update, user, null));

        assertEquals(409, error.getCode());
        verify(operationLogMapper, never()).insert(any());
    }

    @Test
    void clientActionReturnsConflictWhenAuditLogWasNotPersisted() {
        ProjectDocument document = document(200L, 1L, 300L);
        ProjectDocumentVersion version = version(300L, 200L, 1);
        when(documentMapper.selectById(200L)).thenReturn(document);
        when(versionMapper.selectById(300L)).thenReturn(version);
        doReturn(0).when(operationLogMapper).insert(any());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.recordClientAction(
                        200L, clientAction("SHARE_WECHAT_FILE", null), user, null));

        assertEquals(409, error.getCode());
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

    @Test
    void clientActionRecordsSaveMenuForSelectedVersionAndReturnsActivity() {
        ProjectDocument document = document(200L, 1L, 300L);
        ProjectDocumentVersion version = version(301L, 200L, 2);
        when(documentMapper.selectById(200L)).thenReturn(document);
        when(versionMapper.selectById(301L)).thenReturn(version);
        doAnswer(invocation -> {
            ((OperationLog) invocation.getArgument(0)).setId(900L);
            return 1;
        }).when(operationLogMapper).insert(any());

        ProjectDocumentClientActionRequest request = clientAction("OPEN_SAVE_MENU", 301L);
        ProjectDocumentActivityVO activity = service.recordClientAction(200L, request, user, null);

        verify(permissionService).checkProjectPermission(7L, 1L);
        verify(permissionService).requireSystemPermission(7L, 1L, SystemPermissionCodes.DOCUMENT_VIEW);
        ArgumentCaptor<OperationLog> logCaptor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogMapper).insert(logCaptor.capture());
        assertEquals("DOCUMENT_SAVE_MENU", logCaptor.getValue().getOperationType());
        assertEquals("打开《施工方案》V2保存菜单", logCaptor.getValue().getOperationDesc());
        assertEquals(900L, activity.getId());
        assertEquals("打开保存菜单", activity.getOperationLabel());
    }

    @Test
    void clientActionRecordsWechatShareForCurrentVersion() {
        ProjectDocument document = document(200L, 1L, 300L);
        ProjectDocumentVersion version = version(300L, 200L, 1);
        when(documentMapper.selectById(200L)).thenReturn(document);
        when(versionMapper.selectById(300L)).thenReturn(version);

        ProjectDocumentActivityVO activity = service.recordClientAction(
                200L, clientAction("SHARE_WECHAT_FILE", null), user, null);

        ArgumentCaptor<OperationLog> logCaptor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogMapper).insert(logCaptor.capture());
        assertEquals("DOCUMENT_SHARE", logCaptor.getValue().getOperationType());
        assertEquals("发送《施工方案》V1给微信好友", logCaptor.getValue().getOperationDesc());
        assertEquals("发送文件", activity.getOperationLabel());
    }

    @Test
    void clientActionRejectsVersionBelongingToAnotherDocument() {
        ProjectDocument document = document(200L, 1L, 300L);
        ProjectDocumentVersion version = version(301L, 201L, 2);
        when(documentMapper.selectById(200L)).thenReturn(document);
        when(versionMapper.selectById(301L)).thenReturn(version);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.recordClientAction(
                        200L, clientAction("OPEN_SAVE_MENU", 301L), user, null));

        assertEquals(404, exception.getCode());
        assertEquals("资料版本不存在", exception.getMessage());
        verify(operationLogMapper, never()).insert(any());
    }

    @Test
    void clientActionRequiresDocumentViewPermission() {
        ProjectDocument document = document(200L, 1L, 300L);
        when(documentMapper.selectById(200L)).thenReturn(document);
        doThrow(BusinessException.forbidden("无资料查看权限"))
                .when(permissionService)
                .requireSystemPermission(7L, 1L, SystemPermissionCodes.DOCUMENT_VIEW);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.recordClientAction(
                        200L, clientAction("SHARE_WECHAT_FILE", null), user, null));

        assertEquals(403, exception.getCode());
        verify(versionMapper, never()).selectById(any());
        verify(operationLogMapper, never()).insert(any());
    }

    @Test
    void permanentDeleteDefersPhysicalCleanupUntilDatabaseCommit() {
        when(permissionService.isPlatformAdmin(7L)).thenReturn(true);
        ProjectDocument document = document(200L, 1L, 300L);
        document.setTitle("施工方案");
        ProjectDocumentVersion version = version(300L, 200L, 1);
        version.setFileResourceId(100L);
        FileResource resource = new FileResource();
        resource.setId(100L);
        resource.setStorageProvider("local");
        resource.setStorageKey("project-documents/1/file.pdf");
        when(documentMapper.selectDeletedById(200L)).thenReturn(document);
        when(versionMapper.selectList(any())).thenReturn(List.of(version));
        when(fileMapper.selectById(100L)).thenReturn(resource);
        when(fileMapper.deleteById(100L)).thenReturn(1);
        when(fileMapper.purgeById(100L)).thenReturn(1);

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.purge(200L, user, null);

            verify(storageManager, never()).delete(any());
            verify(fileMapper).deleteById(100L);
            for (TransactionSynchronization synchronization
                    : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }
            verify(storageManager).delete(resource);
            verify(fileMapper).purgeById(100L);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void permanentDeleteRollbackNeverTouchesPhysicalFile() {
        when(permissionService.isPlatformAdmin(7L)).thenReturn(true);
        ProjectDocument document = document(200L, 1L, 300L);
        ProjectDocumentVersion version = version(300L, 200L, 1);
        version.setFileResourceId(100L);
        FileResource resource = new FileResource();
        resource.setId(100L);
        when(documentMapper.selectDeletedById(200L)).thenReturn(document);
        when(versionMapper.selectList(any())).thenReturn(List.of(version));
        when(fileMapper.selectById(100L)).thenReturn(resource);
        when(fileMapper.deleteById(100L)).thenReturn(1);

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.purge(200L, user, null);
            for (TransactionSynchronization synchronization
                    : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            }
            verify(storageManager, never()).delete(any());
            verify(fileMapper, never()).purgeById(any());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private ProjectDocument document(Long id, Long projectId, Long currentVersionId) {
        ProjectDocument document = new ProjectDocument();
        document.setId(id);
        document.setProjectId(projectId);
        document.setTitle("施工方案");
        document.setCurrentVersionId(currentVersionId);
        return document;
    }

    private ProjectDocumentVersion version(Long id, Long documentId, int versionNo) {
        ProjectDocumentVersion version = new ProjectDocumentVersion();
        version.setId(id);
        version.setDocumentId(documentId);
        version.setVersionNo(versionNo);
        return version;
    }

    private ProjectDocumentClientActionRequest clientAction(String action, Long versionId) {
        ProjectDocumentClientActionRequest request = new ProjectDocumentClientActionRequest();
        request.setAction(action);
        request.setVersionId(versionId);
        return request;
    }
}
