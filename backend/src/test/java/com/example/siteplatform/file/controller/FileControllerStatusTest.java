package com.example.siteplatform.file.controller;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.common.Result;
import com.example.siteplatform.file.constant.FileStatus;
import com.example.siteplatform.file.entity.FileResource;
import com.example.siteplatform.file.mapper.FileResourceMapper;
import com.example.siteplatform.file.service.FileOperationService;
import com.example.siteplatform.file.service.FileResourceService;
import com.example.siteplatform.project.service.ProjectPermissionService;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class FileControllerStatusTest {

    @Mock private FileResourceMapper fileMapper;
    @Mock private AuthService authService;
    @Mock private ProjectPermissionService projectPermissionService;
    @Mock private FileResourceService fileResourceService;
    @Mock private FileOperationService fileOperationService;
    @Mock private HttpServletRequest request;

    @TempDir
    Path uploadDir;

    private FileController controller;
    private SysUser currentUser;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), FileResourceMapper.class.getName()),
                FileResource.class);
        controller = new FileController();
        ReflectionTestUtils.setField(controller, "fileMapper", fileMapper);
        ReflectionTestUtils.setField(controller, "authService", authService);
        ReflectionTestUtils.setField(controller, "projectPermissionService", projectPermissionService);
        ReflectionTestUtils.setField(controller, "fileResourceService", fileResourceService);
        ReflectionTestUtils.setField(controller, "fileOperationService", fileOperationService);
        ReflectionTestUtils.setField(controller, "uploadPath", uploadDir.toString());

        currentUser = new SysUser();
        currentUser.setId(9L);
        when(authService.getCurrentUser("Bearer token")).thenReturn(currentUser);
        lenient().when(fileMapper.insert(any())).thenReturn(1);
    }

    @Test
    void listMatchesHistoricalStatusAndReturnsCanonicalStatus() {
        FileResource file = file(7L, "已上传");
        when(fileMapper.selectList(any())).thenReturn(List.of(file));
        when(fileResourceService.canReadInList(currentUser, file)).thenReturn(true);

        Result<List<FileResource>> result = controller.getFileList(
                12L, null, FileStatus.UPLOADED, null, null, null, "Bearer token");

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<LambdaQueryWrapper> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(fileMapper).selectList(wrapperCaptor.capture());
        wrapperCaptor.getValue().getSqlSegment();
        Map<String, Object> params = wrapperCaptor.getValue().getParamNameValuePairs();
        assertTrue(params.containsValue(FileStatus.UPLOADED));
        assertTrue(params.containsValue("已上传"));
        assertEquals(FileStatus.UPLOADED, result.getData().get(0).getStatus());
    }

    @Test
    void uploadWritesCanonicalUploadedStatus() {
        MockMultipartFile multipart = new MockMultipartFile(
                "file", "现场照片.jpg", "image/jpeg",
                new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x00});
        when(fileResourceService.authorizeUpload(
                currentUser, 12L, "QUALITY_DOCUMENT", 88L))
                .thenReturn("QUALITY_DOCUMENT");
        when(fileResourceService.allowsDuplicateNameForUpload("QUALITY_DOCUMENT", 88L))
                .thenReturn(true);

        Result<FileResource> result = controller.uploadFile(
                multipart,
                12L,
                null,
                "照片",
                "QUALITY_DOCUMENT",
                "88",
                null,
                "Bearer token",
                request);

        ArgumentCaptor<FileResource> fileCaptor = ArgumentCaptor.forClass(FileResource.class);
        verify(fileMapper).insert(fileCaptor.capture());
        assertEquals(FileStatus.UPLOADED, fileCaptor.getValue().getStatus());
        assertEquals(FileStatus.UPLOADED, result.getData().getStatus());
    }

    @Test
    void legacyActiveContentPreviewIsForcedToAttachment() throws Exception {
        Path legacy = uploadDir.resolve("legacy.html");
        Files.writeString(legacy, "<script>alert(1)</script>");
        FileResource file = file(8L, FileStatus.UPLOADED);
        file.setFileName("legacy.html");
        file.setFilePath(legacy.toString());
        when(fileMapper.selectById(8L)).thenReturn(file);

        var response = controller.previewFile(8L, "Bearer token", request);

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getHeaders().getFirst("Content-Disposition").startsWith("attachment;"));
        assertEquals("nosniff", response.getHeaders().getFirst("X-Content-Type-Options"));
        assertEquals("application/octet-stream",
                response.getHeaders().getContentType().toString());
    }

    @Test
    void fileOutsideManagedUploadRootCannotBeRead() {
        FileResource file = file(9L, FileStatus.UPLOADED);
        file.setFilePath("/etc/hosts");
        when(fileMapper.selectById(9L)).thenReturn(file);

        var response = controller.previewFile(9L, "Bearer token", request);

        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    void statusUpdateConvertsChineseAliasBeforePersistence() {
        FileResource file = file(7L, "已上传");
        when(fileMapper.selectById(7L)).thenReturn(file);

        controller.updateStatus(7L, "已归档", "Bearer token", request);

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<LambdaUpdateWrapper> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(fileMapper).update(isNull(), wrapperCaptor.capture());
        assertTrue(wrapperCaptor.getValue().getParamNameValuePairs()
                .containsValue(FileStatus.ARCHIVED));
        assertEquals(FileStatus.ARCHIVED, file.getStatus());
        verify(fileOperationService).record(
                currentUser, file, "FILE_ARCHIVE", "归档《测试文件.pdf》", request);
    }

    @Test
    void statusUpdateRejectsUnknownValue() {
        FileResource file = file(7L, FileStatus.UPLOADED);
        when(fileMapper.selectById(7L)).thenReturn(file);

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> controller.updateStatus(7L, "ACTIVE", "Bearer token", request));

        assertEquals(400, error.getCode());
        verify(fileMapper, never()).update(any(), any());
    }

    @Test
    void uploadRollbackRemovesNewPhysicalFile() {
        MockMultipartFile multipart = new MockMultipartFile(
                "file", "现场照片.jpg", "image/jpeg",
                new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x00});
        when(fileResourceService.authorizeUpload(
                currentUser, 12L, "QUALITY_DOCUMENT", 88L))
                .thenReturn("QUALITY_DOCUMENT");
        when(fileResourceService.allowsDuplicateNameForUpload("QUALITY_DOCUMENT", 88L))
                .thenReturn(true);

        TransactionSynchronizationManager.initSynchronization();
        try {
            Result<FileResource> result = controller.uploadFile(
                    multipart, 12L, null, "照片", "QUALITY_DOCUMENT", "88",
                    null, "Bearer token", request);
            Path written = Path.of(result.getData().getFilePath());
            assertTrue(Files.exists(written));

            for (TransactionSynchronization synchronization
                    : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            }
            assertFalse(Files.exists(written));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void replacementKeepsOldFileUntilDatabaseCommit() throws Exception {
        Path oldFile = uploadDir.resolve("old.jpg");
        Files.write(oldFile, new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x00});
        FileResource existing = file(10L, FileStatus.UPLOADED);
        existing.setFilePath(oldFile.toString());
        existing.setStorageKey(oldFile.toString());
        existing.setBusinessType("QUALITY_DOCUMENT");
        when(fileMapper.selectById(10L)).thenReturn(existing);
        when(fileMapper.updateById(existing)).thenReturn(1);
        MockMultipartFile replacement = new MockMultipartFile(
                "file", "new.jpg", "image/jpeg",
                new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x01});

        TransactionSynchronizationManager.initSynchronization();
        try {
            controller.replaceFileContent(10L, replacement, "Bearer token", request);
            Path newFile = Path.of(existing.getFilePath());
            assertTrue(Files.exists(oldFile));
            assertTrue(Files.exists(newFile));

            for (TransactionSynchronization synchronization
                    : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }
            assertFalse(Files.exists(oldFile));
            assertTrue(Files.exists(newFile));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void deleteKeepsPhysicalFileUntilDatabaseCommit() throws Exception {
        Path physicalFile = uploadDir.resolve("delete.jpg");
        Files.write(physicalFile, new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x00});
        FileResource existing = file(11L, FileStatus.UPLOADED);
        existing.setFilePath(physicalFile.toString());
        when(fileMapper.selectById(11L)).thenReturn(existing);
        when(fileMapper.deleteById(11L)).thenReturn(1);

        TransactionSynchronizationManager.initSynchronization();
        try {
            controller.deleteFile(11L, "Bearer token", request);
            assertTrue(Files.exists(physicalFile));

            for (TransactionSynchronization synchronization
                    : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }
            assertFalse(Files.exists(physicalFile));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private FileResource file(Long id, String status) {
        FileResource file = new FileResource();
        file.setId(id);
        file.setProjectId(12L);
        file.setFileName("测试文件.pdf");
        file.setStatus(status);
        return file;
    }
}
