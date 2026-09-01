package com.example.siteplatform.document.service;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.document.dto.DocumentUploadInitRequest;
import com.example.siteplatform.document.entity.DocumentIncomingBatch;
import com.example.siteplatform.document.mapper.DocumentIncomingBatchMapper;
import com.example.siteplatform.document.vo.DocumentUploadSessionVO;
import com.example.siteplatform.file.constant.FileStatus;
import com.example.siteplatform.file.entity.FileResource;
import com.example.siteplatform.file.mapper.FileResourceMapper;
import com.example.siteplatform.file.security.FileUploadPolicy;
import com.example.siteplatform.file.storage.FileStorageManager;
import com.example.siteplatform.file.storage.PathMultipartFile;
import com.example.siteplatform.file.storage.StoredFile;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.system.constant.SystemPermissionCodes;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class DocumentUploadSessionService {
    public static final int CHUNK_SIZE = 8 * 1024 * 1024;
    public static final int SESSION_TTL_HOURS = 24;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final String REDIS_PREFIX = "document:upload:";

    private final DocumentIncomingBatchMapper batchMapper;
    private final FileResourceMapper fileMapper;
    private final FileStorageManager storageManager;
    private final ProjectPermissionService permissionService;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Path uploadRoot;
    private final Path tempRoot;

    public DocumentUploadSessionService(
            DocumentIncomingBatchMapper batchMapper,
            FileResourceMapper fileMapper,
            FileStorageManager storageManager,
            ProjectPermissionService permissionService,
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            @Value("${file.upload.path:./uploads}") String uploadPath) {
        this.batchMapper = batchMapper;
        this.fileMapper = fileMapper;
        this.storageManager = storageManager;
        this.permissionService = permissionService;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.uploadRoot = Paths.get(uploadPath).toAbsolutePath().normalize();
        this.tempRoot = this.uploadRoot.resolve(".document-upload-sessions").normalize();
        if (!this.tempRoot.startsWith(this.uploadRoot)) {
            throw new IllegalArgumentException("图纸分片暂存目录必须位于上传根目录内");
        }
    }

    public DocumentUploadSessionVO initialize(DocumentUploadInitRequest request, SysUser user) {
        requireReceive(user, request.getProjectId());
        DocumentIncomingBatch batch = batchMapper.selectById(request.getIncomingBatchId());
        if (batch == null || !request.getProjectId().equals(batch.getProjectId())) {
            throw BusinessException.notFound("收文草稿不存在");
        }
        if (!"DRAFT".equals(batch.getStatus())) throw new BusinessException("已发布收文不能继续上传文件");
        FileUploadPolicy.validateCirculationMetadata(request.getFileName(), request.getTotalSize());
        String expectedSha = normalizeSha256(request.getSha256(), false);
        String sessionId = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        UploadMetadata metadata = new UploadMetadata();
        metadata.setUserId(user.getId());
        metadata.setProjectId(request.getProjectId());
        metadata.setIncomingBatchId(request.getIncomingBatchId());
        metadata.setFileName(FileUploadPolicy.safeOriginalFileName(request.getFileName()));
        metadata.setTotalSize(request.getTotalSize());
        metadata.setExpectedSha256(expectedSha);
        metadata.setChunkCount((int) ((request.getTotalSize() + CHUNK_SIZE - 1) / CHUNK_SIZE));
        metadata.setCreatedAt(now);
        metadata.setExpiresAt(now.plusHours(SESSION_TTL_HOURS));
        try {
            Files.createDirectories(sessionDir(sessionId));
        } catch (IOException exception) {
            throw new BusinessException("无法创建图纸分片暂存目录");
        }
        save(sessionId, metadata);
        return toVO(sessionId, metadata);
    }

    public DocumentUploadSessionVO status(String sessionId, SysUser user) {
        UploadMetadata metadata = requireSession(sessionId, user);
        return toVO(sessionId, metadata);
    }

    public DocumentUploadSessionVO uploadChunk(String sessionId, int index, String chunkSha256,
                                                MultipartFile chunk, SysUser user) {
        UploadMetadata metadata = requireSession(sessionId, user);
        if (metadata.getFileResourceId() != null) return toVO(sessionId, metadata);
        if (index < 0 || index >= metadata.getChunkCount()) throw new BusinessException("分片序号不正确");
        if (chunk == null || chunk.isEmpty()) throw new BusinessException("上传分片不能为空");
        long expectedMax = index == metadata.getChunkCount() - 1
                ? metadata.getTotalSize() - (long) index * CHUNK_SIZE : CHUNK_SIZE;
        if (chunk.getSize() != expectedMax || chunk.getSize() > CHUNK_SIZE) {
            throw new BusinessException("上传分片大小不正确");
        }
        String expectedDigest = normalizeSha256(chunkSha256, true);
        String lockKey = redisKey(sessionId) + ":chunk:" + index;
        if (!acquireLock(lockKey, 2)) {
            throw BusinessException.of(409, "该分片正在上传，请稍后查询进度");
        }
        Path target = chunkPath(sessionId, index);
        Path partial = target.resolveSibling(target.getFileName() + ".partial");
        try {
            metadata = requireSession(sessionId, user);
            if (metadata.getFileResourceId() != null) return toVO(sessionId, metadata);
            String actual = sha256(chunk.getInputStream());
            if (!actual.equals(expectedDigest)) throw new BusinessException("上传分片摘要不一致");
            if (Files.exists(target)) {
                if (Files.size(target) == chunk.getSize() && actual.equals(sha256(Files.newInputStream(target)))) {
                    return toVO(sessionId, metadata);
                }
                throw BusinessException.of(409, "该分片内容已变化，请取消后重新上传");
            }
            Files.copy(chunk.getInputStream(), partial, StandardCopyOption.REPLACE_EXISTING);
            Files.move(partial, target, StandardCopyOption.ATOMIC_MOVE);
            Files.writeString(target.resolveSibling(target.getFileName() + ".sha256"), actual,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return toVO(sessionId, metadata);
        } catch (BusinessException exception) {
            deleteQuietly(partial);
            throw exception;
        } catch (IOException exception) {
            deleteQuietly(partial);
            throw new BusinessException("上传分片保存失败");
        } finally {
            redis.delete(lockKey);
        }
    }

    @Transactional
    public DocumentUploadSessionVO complete(String sessionId, SysUser user) {
        UploadMetadata metadata = requireSession(sessionId, user);
        if (metadata.getFileResourceId() != null) return toVO(sessionId, metadata);
        String lockKey = redisKey(sessionId) + ":complete";
        if (!acquireLock(lockKey, 30)) {
            throw BusinessException.of(409, "图纸分片正在合并，请稍后查询进度");
        }
        try {
            metadata = requireSession(sessionId, user);
            if (metadata.getFileResourceId() != null) return toVO(sessionId, metadata);
            ensureTemporarySpace(metadata.getTotalSize());
            Path assembled = sessionDir(sessionId).resolve("assembled").normalize();
            if (!assembled.startsWith(sessionDir(sessionId))) throw new BusinessException("非法图纸暂存路径");
            try (OutputStream output = Files.newOutputStream(assembled,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                for (int index = 0; index < metadata.getChunkCount(); index++) {
                    Path part = chunkPath(sessionId, index);
                    if (!Files.isRegularFile(part)) throw new BusinessException("图纸分片尚未上传完整");
                    Files.copy(part, output);
                }
            }
            try {
                if (Files.size(assembled) != metadata.getTotalSize()) throw new BusinessException("合并文件大小不一致");
                String actualSha = sha256(Files.newInputStream(assembled));
                if (StringUtils.hasText(metadata.getExpectedSha256())
                        && !actualSha.equals(metadata.getExpectedSha256())) {
                    throw new BusinessException("完整文件摘要不一致");
                }
                PathMultipartFile multipart = new PathMultipartFile("file", metadata.getFileName(),
                        "application/octet-stream", assembled);
                FileUploadPolicy.validateCirculationDocument(multipart);
                StoredFile stored = storageManager.store(objectKey(metadata), multipart);
                registerRollbackCleanup(stored);
                FileResource resource = new FileResource();
                resource.setProjectId(metadata.getProjectId());
                resource.setFileName(metadata.getFileName());
                resource.setFileType("DOCUMENT_INCOMING");
                resource.setFilePath(stored.storageKey());
                resource.setStorageProvider(stored.provider());
                resource.setStorageKey(stored.storageKey());
                resource.setOriginalFileName(stored.originalFileName());
                resource.setMimeType(stored.mimeType());
                resource.setFileExtension(stored.extension());
                resource.setSha256(stored.sha256());
                resource.setFileSize(stored.size());
                resource.setBusinessType("DOCUMENT_INCOMING_PENDING");
                resource.setBusinessId(metadata.getIncomingBatchId());
                resource.setUploaderId(user.getId());
                resource.setStatus(FileStatus.UPLOADED);
                resource.setDeleted(0);
                resource.setCreateTime(LocalDateTime.now(BUSINESS_ZONE));
                resource.setUpdateTime(resource.getCreateTime());
                if (fileMapper.insert(resource) != 1) {
                    throw BusinessException.of(409, "图纸文件元数据新增未生效，请重试");
                }
                metadata.setFileResourceId(resource.getId());
                UploadMetadata committedMetadata = metadata;
                save(sessionId, committedMetadata);
                restoreSessionAfterRollback(sessionId, committedMetadata);
                afterCommit(() -> deleteDirectoryQuietly(sessionDir(sessionId)));
                return toVO(sessionId, metadata);
            } catch (BusinessException exception) {
                throw exception;
            } catch (IOException exception) {
                throw new BusinessException("完整图纸读取失败");
            }
        } catch (IOException exception) {
            throw new BusinessException("图纸分片合并失败");
        } finally {
            releaseAfterCompletion(lockKey);
        }
    }

    public void cancel(String sessionId, SysUser user) {
        UploadMetadata metadata = requireSession(sessionId, user);
        if (metadata.getFileResourceId() != null) {
            throw new BusinessException("已完成的上传由收文草稿统一管理，不能直接取消");
        }
        redis.delete(redisKey(sessionId));
        deleteDirectoryQuietly(sessionDir(sessionId));
    }

    public int cleanupExpiredDirectories(LocalDateTime cutoff, int limit) {
        int deleted = 0;
        try {
            if (!Files.isDirectory(tempRoot)) return 0;
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(tempRoot)) {
                for (Path path : stream) {
                    if (deleted >= limit || !Files.isDirectory(path)) continue;
                    LocalDateTime modified = LocalDateTime.ofInstant(
                            Files.getLastModifiedTime(path).toInstant(), BUSINESS_ZONE);
                    if (modified.isBefore(cutoff)) {
                        deleteDirectoryQuietly(path);
                        deleted++;
                    }
                }
            }
            return deleted;
        } catch (IOException exception) {
            return deleted;
        }
    }

    private UploadMetadata requireSession(String sessionId, SysUser user) {
        validateSessionId(sessionId);
        String json = redis.opsForValue().get(redisKey(sessionId));
        if (!StringUtils.hasText(json)) throw BusinessException.notFound("图纸上传会话不存在或已过期");
        try {
            UploadMetadata metadata = objectMapper.readValue(json, UploadMetadata.class);
            if (!user.getId().equals(metadata.getUserId())) throw BusinessException.forbidden("无权访问该上传会话");
            requireReceive(user, metadata.getProjectId());
            return metadata;
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("图纸上传会话数据无效");
        }
    }

    private void requireReceive(SysUser user, Long projectId) {
        if (user == null || user.getId() == null) throw BusinessException.unauthorized("请先登录");
        permissionService.checkProjectPermission(user.getId(), projectId);
        permissionService.requireSystemPermission(user.getId(), projectId, SystemPermissionCodes.DOCUMENT_RECEIVE);
    }

    private void save(String sessionId, UploadMetadata metadata) {
        try {
            redis.opsForValue().set(redisKey(sessionId), objectMapper.writeValueAsString(metadata),
                    SESSION_TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception exception) {
            throw new BusinessException("图纸上传会话保存失败");
        }
    }

    private boolean acquireLock(String lockKey, long minutes) {
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(lockKey, "1", minutes, TimeUnit.MINUTES));
    }

    void ensureTemporarySpace(long totalSize) {
        try {
            Files.createDirectories(tempRoot);
            long required = Math.addExact(totalSize, CHUNK_SIZE);
            if (usableTemporarySpace() < required) {
                throw BusinessException.of(507, "图纸暂存空间不足，请联系管理员清理后重试");
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (ArithmeticException | IOException exception) {
            throw new BusinessException("无法检查图纸暂存空间");
        }
    }

    long usableTemporarySpace() throws IOException {
        return Files.getFileStore(tempRoot).getUsableSpace();
    }

    private void releaseAfterCompletion(String lockKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            redis.delete(lockKey);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) { redis.delete(lockKey); }
        });
    }

    private DocumentUploadSessionVO toVO(String sessionId, UploadMetadata metadata) {
        DocumentUploadSessionVO vo = new DocumentUploadSessionVO();
        vo.setSessionId(sessionId);
        vo.setFileName(metadata.getFileName());
        vo.setTotalSize(metadata.getTotalSize());
        vo.setChunkSize(CHUNK_SIZE);
        vo.setChunkCount(metadata.getChunkCount());
        vo.setUploadedChunks(uploadedChunks(sessionId, metadata.getChunkCount()));
        vo.setFileResourceId(metadata.getFileResourceId());
        vo.setExpiresAt(metadata.getExpiresAt());
        return vo;
    }

    private List<Integer> uploadedChunks(String sessionId, int count) {
        List<Integer> result = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            if (Files.isRegularFile(chunkPath(sessionId, index))) result.add(index);
        }
        return result;
    }

    private Path sessionDir(String sessionId) {
        validateSessionId(sessionId);
        Path resolved = tempRoot.resolve(sessionId).normalize();
        if (!resolved.startsWith(tempRoot)) throw new BusinessException("非法图纸上传会话");
        return resolved;
    }

    private Path chunkPath(String sessionId, int index) {
        Path resolved = sessionDir(sessionId).resolve(String.format(Locale.ROOT, "%06d.part", index)).normalize();
        if (!resolved.startsWith(sessionDir(sessionId))) throw new BusinessException("非法图纸分片路径");
        return resolved;
    }

    private String redisKey(String sessionId) {
        return REDIS_PREFIX + sha256Text(sessionId);
    }

    private void validateSessionId(String sessionId) {
        if (!StringUtils.hasText(sessionId) || !sessionId.matches("^[a-f0-9]{64}$")) {
            throw new BusinessException("图纸上传会话编号无效");
        }
    }

    private String objectKey(UploadMetadata metadata) {
        String extension = FileUploadPolicy.extensionOf(metadata.getFileName());
        return "project-documents/" + metadata.getProjectId() + "/" + LocalDate.now(BUSINESS_ZONE)
                + "/" + UUID.randomUUID() + (extension.isEmpty() ? "" : "." + extension);
    }

    private String normalizeSha256(String value, boolean required) {
        String normalized = StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : null;
        if (required && normalized == null) throw new BusinessException("分片摘要不能为空");
        if (normalized != null && !normalized.matches("^[a-f0-9]{64}$")) {
            throw new BusinessException("SHA-256格式不正确");
        }
        return normalized;
    }

    private String sha256(InputStream input) throws IOException {
        try (InputStream source = input) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = source.read(buffer)) >= 0) {
                if (read > 0) digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256不可用", exception);
        }
    }

    private String sha256Text(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256不可用", exception);
        }
    }

    private void registerRollbackCleanup(StoredFile stored) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                    storageManager.deleteQuietly(stored.provider(), stored.storageKey());
                }
            }
        });
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { action.run(); }
        });
    }

    private void restoreSessionAfterRollback(String sessionId, UploadMetadata metadata) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                    metadata.setFileResourceId(null);
                    save(sessionId, metadata);
                }
            }
        });
    }

    private void deleteDirectoryQuietly(Path directory) {
        try {
            if (!Files.exists(directory)) return;
            try (var paths = Files.walk(directory)) {
                paths.sorted(Comparator.reverseOrder()).forEach(this::deleteQuietly);
            }
        } catch (IOException ignored) {
            // 下次定时清理重试。
        }
    }

    private void deleteQuietly(Path path) {
        try { Files.deleteIfExists(path); } catch (IOException ignored) { }
    }

    @Data
    public static class UploadMetadata {
        private Long userId;
        private Long projectId;
        private Long incomingBatchId;
        private String fileName;
        private Long totalSize;
        private String expectedSha256;
        private Integer chunkCount;
        private Long fileResourceId;
        private LocalDateTime createdAt;
        private LocalDateTime expiresAt;
    }
}
