package com.example.siteplatform.file.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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

    @Test
    void acceptsLegacyAbsolutePathInsideUploadRoot() throws Exception {
        Path uploadRoot = Files.createDirectories(tempDir.resolve("uploads"));
        LocalFileStorageService service = new LocalFileStorageService(uploadRoot.toString());
        Path managedFile = uploadRoot.resolve("legacy/inside.txt").toAbsolutePath();
        MockMultipartFile file = new MockMultipartFile(
                "file", "inside.txt", "text/plain", "managed".getBytes(StandardCharsets.UTF_8));

        service.store(managedFile.toString(), file);

        assertTrue(service.exists(managedFile.toString()));
        assertEquals("managed", service.load(managedFile.toString())
                .getContentAsString(StandardCharsets.UTF_8));
        service.delete(managedFile.toString());
        assertFalse(Files.exists(managedFile));
    }

    @Test
    void rejectsAbsolutePathOutsideUploadRootForEveryOperation() throws Exception {
        Path uploadRoot = Files.createDirectories(tempDir.resolve("uploads"));
        Path outside = tempDir.resolve("outside.txt").toAbsolutePath();
        Files.writeString(outside, "must-stay", StandardCharsets.UTF_8);
        LocalFileStorageService service = new LocalFileStorageService(uploadRoot.toString());
        MockMultipartFile replacement = new MockMultipartFile(
                "file", "outside.txt", "text/plain", "overwrite".getBytes(StandardCharsets.UTF_8));

        assertThrows(IllegalArgumentException.class, () -> service.store(outside.toString(), replacement));
        assertThrows(IllegalArgumentException.class, () -> service.exists(outside.toString()));
        assertThrows(IllegalArgumentException.class, () -> service.load(outside.toString()));
        assertThrows(IllegalArgumentException.class, () -> service.delete(outside.toString()));
        assertEquals("must-stay", Files.readString(outside, StandardCharsets.UTF_8));
    }
}
