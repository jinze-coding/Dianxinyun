package com.example.siteplatform.file.storage;

import org.springframework.core.io.FileSystemResource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Read-only MultipartFile facade for a server-assembled resumable upload. */
public final class PathMultipartFile implements MultipartFile {
    private final String name;
    private final String originalFilename;
    private final String contentType;
    private final Path path;

    public PathMultipartFile(String name, String originalFilename, String contentType, Path path) {
        this.name = name;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.path = path;
    }

    @Override public String getName() { return name; }
    @Override public String getOriginalFilename() { return originalFilename; }
    @Override public String getContentType() { return contentType; }
    @Override public boolean isEmpty() { return size() == 0; }
    @Override public long getSize() { return size(); }
    @Override public byte[] getBytes() throws IOException { return Files.readAllBytes(path); }
    @Override public InputStream getInputStream() throws IOException { return Files.newInputStream(path); }
    @Override public void transferTo(java.io.File dest) throws IOException {
        try (InputStream input = new FileSystemResource(path).getInputStream();
             java.io.OutputStream output = Files.newOutputStream(dest.toPath())) {
            input.transferTo(output);
        }
    }

    private long size() {
        try {
            return Files.size(path);
        } catch (IOException exception) {
            throw new IllegalStateException("暂存文件大小读取失败", exception);
        }
    }
}
