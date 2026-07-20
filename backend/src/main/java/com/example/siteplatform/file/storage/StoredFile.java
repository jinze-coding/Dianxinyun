package com.example.siteplatform.file.storage;

public record StoredFile(
        String provider,
        String storageKey,
        String originalFileName,
        String mimeType,
        String extension,
        long size,
        String sha256
) {
}
