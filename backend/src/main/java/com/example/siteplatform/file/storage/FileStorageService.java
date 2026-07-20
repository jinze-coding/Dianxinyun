package com.example.siteplatform.file.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {
    String provider();

    StoredFile store(String storageKey, MultipartFile file) throws Exception;

    Resource load(String storageKey) throws Exception;

    boolean exists(String storageKey) throws Exception;

    void delete(String storageKey) throws Exception;
}
