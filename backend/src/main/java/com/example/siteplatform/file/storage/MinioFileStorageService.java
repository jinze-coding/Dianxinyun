package com.example.siteplatform.file.storage;

import com.example.siteplatform.file.security.FileUploadPolicy;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

@Service
public class MinioFileStorageService implements FileStorageService {
    private final String endpoint;
    private final String bucket;
    private final String accessKey;
    private final String secretKey;
    private volatile MinioClient client;

    public MinioFileStorageService(
            @Value("${file.storage.minio.endpoint:}") String endpoint,
            @Value("${file.storage.minio.bucket:site-platform}") String bucket,
            @Value("${file.storage.minio.access-key:}") String accessKey,
            @Value("${file.storage.minio.secret-key:}") String secretKey) {
        this.endpoint = endpoint;
        this.bucket = bucket;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
    }

    @Override
    public String provider() {
        return "minio";
    }

    @Override
    public StoredFile store(String storageKey, MultipartFile file) throws Exception {
        MinioClient minio = client();
        ensureBucket(minio);
        String sha256;
        try (InputStream digestStream = file.getInputStream()) {
            sha256 = StorageSupport.sha256(digestStream);
        }
        try (InputStream inputStream = file.getInputStream()) {
            PutObjectArgs.Builder builder = PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(storageKey)
                    .stream(inputStream, file.getSize(), -1L);
            builder.contentType(FileUploadPolicy.responseMediaType(file.getOriginalFilename()).toString());
            minio.putObject(builder.build());
        }
        String originalFileName = FileUploadPolicy.safeOriginalFileName(file.getOriginalFilename());
        return new StoredFile(provider(), storageKey, originalFileName,
                FileUploadPolicy.responseMediaType(originalFileName).toString(),
                StorageSupport.extension(originalFileName), file.getSize(), sha256);
    }

    @Override
    public Resource load(String storageKey) throws Exception {
        return new InputStreamResource(client().getObject(GetObjectArgs.builder()
                .bucket(bucket).object(storageKey).build()));
    }

    @Override
    public boolean exists(String storageKey) {
        try {
            client().statObject(StatObjectArgs.builder().bucket(bucket).object(storageKey).build());
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    @Override
    public void delete(String storageKey) throws Exception {
        client().removeObject(RemoveObjectArgs.builder().bucket(bucket).object(storageKey).build());
    }

    private MinioClient client() {
        if (client != null) return client;
        if (!StringUtils.hasText(endpoint) || !StringUtils.hasText(accessKey) || !StringUtils.hasText(secretKey)) {
            throw new IllegalStateException("MinIO 未配置，请设置 MINIO_ENDPOINT、MINIO_ACCESS_KEY 和 MINIO_SECRET_KEY");
        }
        synchronized (this) {
            if (client == null) {
                client = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
            }
        }
        return client;
    }

    private void ensureBucket(MinioClient minio) throws Exception {
        if (!minio.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            minio.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }
}
