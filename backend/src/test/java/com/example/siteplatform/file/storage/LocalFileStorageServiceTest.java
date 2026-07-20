package com.example.siteplatform.file.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalFileStorageServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void storesLoadsHashesAndDeletesFile() throws Exception {
        LocalFileStorageService service = new LocalFileStorageService(tempDir.toString());
        MockMultipartFile file = new MockMultipartFile(
                "file", "施工方案.txt", "text/plain", "site-platform".getBytes(StandardCharsets.UTF_8));

        StoredFile stored = service.store("documents/1/test.txt", file);

        assertEquals("local", stored.provider());
        assertEquals("txt", stored.extension());
        assertEquals("6f5df1dc1761f34fce83afac5a002190e9cd1dc5166c9d9aa8abf887b2f7ca3c", stored.sha256());
        assertTrue(service.exists(stored.storageKey()));
        assertEquals("site-platform", service.load(stored.storageKey()).getContentAsString(StandardCharsets.UTF_8));

        service.delete(stored.storageKey());
        assertFalse(service.exists(stored.storageKey()));
    }

    @Test
    void rejectsPathTraversal() {
        LocalFileStorageService service = new LocalFileStorageService(tempDir.toString());
        assertThrows(IllegalArgumentException.class, () -> service.exists("../outside.txt"));
    }
}
