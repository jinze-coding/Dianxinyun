package com.example.siteplatform.document.service;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.document.dto.DocumentUploadInitRequest;
import com.example.siteplatform.document.entity.DocumentIncomingBatch;
import com.example.siteplatform.document.mapper.DocumentIncomingBatchMapper;
import com.example.siteplatform.document.vo.DocumentUploadSessionVO;
import com.example.siteplatform.file.entity.FileResource;
import com.example.siteplatform.file.mapper.FileResourceMapper;
import com.example.siteplatform.file.storage.FileStorageManager;
import com.example.siteplatform.file.storage.StoredFile;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentUploadSessionServiceTest {
    private final DocumentIncomingBatchMapper batchMapper = mock(DocumentIncomingBatchMapper.class);
    private final FileResourceMapper fileMapper = mock(FileResourceMapper.class);
    private final FileStorageManager storageManager = mock(FileStorageManager.class);
    private final ProjectPermissionService permissionService = mock(ProjectPermissionService.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final Map<String, String> redisValues = new ConcurrentHashMap<>();
    private final AtomicBoolean rejectCompleteLock = new AtomicBoolean(false);
    private final SysUser user = new SysUser();

    @TempDir
    Path uploadRoot;

    private DocumentUploadSessionService service;

    @BeforeEach
    void setUp() {
        user.setId(8L);
        DocumentIncomingBatch batch = new DocumentIncomingBatch();
        batch.setId(20L);
        batch.setProjectId(3L);
        batch.setStatus("DRAFT");
        when(batchMapper.selectById(20L)).thenReturn(batch);

        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenAnswer(invocation -> redisValues.get(invocation.getArgument(0)));
        doAnswer(invocation -> {
            redisValues.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(values).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
        when(values.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
                .thenAnswer(invocation -> {
                    String key = invocation.getArgument(0);
                    if (rejectCompleteLock.get() && key.endsWith(":complete")) return false;
                    return redisValues.putIfAbsent(key, invocation.getArgument(1)) == null;
                });
        when(redis.delete(anyString())).thenAnswer(invocation ->
                redisValues.remove(invocation.getArgument(0)) != null);

        when(fileMapper.insert(any(FileResource.class))).thenAnswer(invocation -> {
            FileResource resource = invocation.getArgument(0);
            resource.setId(99L);
            return 1;
        });
        when(storageManager.store(anyString(), any(MultipartFile.class))).thenAnswer(invocation -> {
            MultipartFile file = invocation.getArgument(1);
            return new StoredFile("local", "stored/document.pdf", file.getOriginalFilename(),
                    "application/pdf", "pdf", file.getSize(), sha256(file.getBytes()));
        });

        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        service = spy(new DocumentUploadSessionService(batchMapper, fileMapper, storageManager,
                permissionService, redis, objectMapper, uploadRoot.toString()));
    }

    @Test
    void supportsOutOfOrderResumeDuplicateChunksAndServerSideFullDigest() {
        byte[] first = new byte[DocumentUploadSessionService.CHUNK_SIZE];
        byte[] header = "%PDF-1.7\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(header, 0, first, 0, header.length);
        byte[] last = new byte[]{1, 2, 3, 4};
        DocumentUploadSessionVO initialized = service.initialize(request("drawing.pdf",
                (long) first.length + last.length), user);

        assertEquals(2, initialized.getChunkCount());
        DocumentUploadSessionVO outOfOrder = service.uploadChunk(initialized.getSessionId(), 1,
                sha256(last), chunk("drawing.pdf", last), user);
        assertEquals(java.util.List.of(1), outOfOrder.getUploadedChunks());
        assertEquals(java.util.List.of(1), service.uploadChunk(initialized.getSessionId(), 1,
                sha256(last), chunk("drawing.pdf", last), user).getUploadedChunks());

        BusinessException digestError = assertThrows(BusinessException.class, () ->
                service.uploadChunk(initialized.getSessionId(), 0, "0".repeat(64),
                        chunk("drawing.pdf", first), user));
        assertEquals(400, digestError.getCode());

        service.uploadChunk(initialized.getSessionId(), 0, sha256(first),
                chunk("drawing.pdf", first), user);
        DocumentUploadSessionVO completed = service.complete(initialized.getSessionId(), user);

        assertEquals(99L, completed.getFileResourceId());
        verify(storageManager).store(anyString(), any(MultipartFile.class));
        verify(fileMapper).insert(any(FileResource.class));
    }

    @Test
    void rejectsConcurrentCompleteAndAllowsRetryAfterTheLockIsReleased() {
        byte[] pdf = "%PDF-1.7\nsmall".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        DocumentUploadSessionVO initialized = service.initialize(request("drawing.pdf", (long) pdf.length), user);
        service.uploadChunk(initialized.getSessionId(), 0, sha256(pdf), chunk("drawing.pdf", pdf), user);

        rejectCompleteLock.set(true);
        BusinessException conflict = assertThrows(BusinessException.class,
                () -> service.complete(initialized.getSessionId(), user));
        assertEquals(409, conflict.getCode());
        verify(storageManager, never()).store(anyString(), any(MultipartFile.class));

        rejectCompleteLock.set(false);
        assertEquals(99L, service.complete(initialized.getSessionId(), user).getFileResourceId());
    }

    @Test
    void expiredSessionAndInsufficientTemporarySpaceFailWithoutPersistingAFile() throws Exception {
        byte[] pdf = "%PDF-1.7\nsmall".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        DocumentUploadSessionVO expired = service.initialize(request("expired.pdf", (long) pdf.length), user);
        redisValues.clear();
        BusinessException missing = assertThrows(BusinessException.class,
                () -> service.status(expired.getSessionId(), user));
        assertEquals(404, missing.getCode());

        DocumentUploadSessionVO lowSpace = service.initialize(request("low-space.pdf", (long) pdf.length), user);
        service.uploadChunk(lowSpace.getSessionId(), 0, sha256(pdf), chunk("low-space.pdf", pdf), user);
        doReturn(0L).when(service).usableTemporarySpace();
        BusinessException insufficient = assertThrows(BusinessException.class,
                () -> service.complete(lowSpace.getSessionId(), user));
        assertEquals(507, insufficient.getCode());
        verify(storageManager, never()).store(anyString(), any(MultipartFile.class));
        verify(fileMapper, never()).insert(any(FileResource.class));
        assertTrue(service.status(lowSpace.getSessionId(), user).getUploadedChunks().contains(0));
    }

    private DocumentUploadInitRequest request(String name, long size) {
        DocumentUploadInitRequest request = new DocumentUploadInitRequest();
        request.setProjectId(3L);
        request.setIncomingBatchId(20L);
        request.setFileName(name);
        request.setTotalSize(size);
        return request;
    }

    private MockMultipartFile chunk(String fileName, byte[] content) {
        return new MockMultipartFile("chunk", fileName, "application/octet-stream", content);
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
