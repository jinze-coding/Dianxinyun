package com.example.siteplatform.file.storage;

import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.file.entity.FileResource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FileStorageManager {
    private final Map<String, FileStorageService> providers;
    private final String currentProvider;

    public FileStorageManager(List<FileStorageService> services,
                              @Value("${file.storage.type:local}") String currentProvider) {
        this.providers = services.stream().collect(Collectors.toMap(
                service -> service.provider().toLowerCase(Locale.ROOT), Function.identity()));
        this.currentProvider = currentProvider.toLowerCase(Locale.ROOT);
    }

    public StoredFile store(String storageKey, MultipartFile file) {
        try {
            return provider(currentProvider).store(storageKey, file);
        } catch (Exception e) {
            throw new BusinessException("文件保存失败: " + e.getMessage());
        }
    }

    public Resource load(FileResource file) {
        try {
            String key = key(file);
            FileStorageService storage = provider(providerName(file));
            if (!storage.exists(key)) throw BusinessException.notFound("物理文件不存在");
            return storage.load(key);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("文件读取失败: " + e.getMessage());
        }
    }

    public void delete(FileResource file) {
        try {
            provider(providerName(file)).delete(key(file));
        } catch (Exception e) {
            throw new BusinessException("物理文件删除失败: " + e.getMessage());
        }
    }

    public void deleteQuietly(String providerName, String storageKey) {
        try {
            provider(providerName).delete(storageKey);
        } catch (Exception ignored) {
            // 数据库写入失败后的补偿清理不覆盖原始异常。
        }
    }

    private String providerName(FileResource file) {
        return file.getStorageProvider() == null ? "local" : file.getStorageProvider();
    }

    private String key(FileResource file) {
        return file.getStorageKey() == null ? file.getFilePath() : file.getStorageKey();
    }

    private FileStorageService provider(String name) {
        FileStorageService service = providers.get(name.toLowerCase(Locale.ROOT));
        if (service == null) throw new BusinessException("不支持的文件存储类型: " + name);
        return service;
    }
}
