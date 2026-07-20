package com.example.siteplatform.file.storage;

import com.example.siteplatform.file.entity.FileResource;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileStorageManagerTest {
    @Test
    void routesNewFilesToConfiguredMinioProvider() throws Exception {
        FileStorageService local = mock(FileStorageService.class);
        FileStorageService minio = mock(FileStorageService.class);
        when(local.provider()).thenReturn("local");
        when(minio.provider()).thenReturn("minio");
        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", new byte[]{1});
        StoredFile stored = new StoredFile("minio", "documents/test.pdf", "test.pdf", "application/pdf", "pdf", 1L, "sha");
        when(minio.store("documents/test.pdf", file)).thenReturn(stored);

        FileStorageManager manager = new FileStorageManager(List.of(local, minio), "minio");

        assertSame(stored, manager.store("documents/test.pdf", file));
        verify(minio).store("documents/test.pdf", file);
    }

    @Test
    void readsLegacyPathFromLocalProvider() throws Exception {
        FileStorageService local = mock(FileStorageService.class);
        FileStorageService minio = mock(FileStorageService.class);
        when(local.provider()).thenReturn("local");
        when(minio.provider()).thenReturn("minio");
        when(local.exists("/legacy/file.pdf")).thenReturn(true);
        ByteArrayResource resource = new ByteArrayResource(new byte[]{1, 2});
        when(local.load("/legacy/file.pdf")).thenReturn(resource);
        FileResource file = new FileResource();
        file.setFilePath("/legacy/file.pdf");

        FileStorageManager manager = new FileStorageManager(List.of(local, minio), "minio");

        assertSame(resource, manager.load(file));
        verify(local).load("/legacy/file.pdf");
    }
}
