package com.example.siteplatform.file.storage;

import io.minio.MinioClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class MinioDependencyCompatibilityTest {

    @Test
    void constructsMinioClientWithResolvedOkHttpJvmClasses() {
        MinioClient client = MinioClient.builder()
                .endpoint("http://127.0.0.1:9000")
                .credentials("test-access-key", "test-secret-key")
                .build();

        assertNotNull(client);
    }
}
