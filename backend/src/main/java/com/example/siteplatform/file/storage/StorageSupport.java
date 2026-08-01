package com.example.siteplatform.file.storage;

import com.example.siteplatform.file.security.FileUploadPolicy;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;

final class StorageSupport {
    private StorageSupport() {
    }

    static String extension(String fileName) {
        return FileUploadPolicy.extensionOf(fileName);
    }

    static String sha256(InputStream inputStream) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[8192];
        int read;
        while ((read = inputStream.read(buffer)) >= 0) {
            if (read > 0) digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
