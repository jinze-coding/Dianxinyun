package com.example.siteplatform.seal.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.file.entity.FileResource;
import com.example.siteplatform.file.mapper.FileResourceMapper;
import com.example.siteplatform.file.storage.*;
import com.example.siteplatform.log.entity.OperationLog;
import com.example.siteplatform.log.mapper.OperationLogMapper;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.seal.dto.SealFormExportRequest;
import com.example.siteplatform.seal.entity.*;
import com.example.siteplatform.seal.mapper.*;
import com.example.siteplatform.seal.vo.SealFormExportJobVO;
import com.example.siteplatform.system.constant.SystemPermissionCodes;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class SealFormExportService {
    public static final String BUSINESS_TYPE = "SEAL_FORM_EXPORT";
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private final SealFormExportJobMapper jobs;
    private final SealFormExportItemMapper items;
    private final SealApplicationMapper applications;
    private final SealApplicationService applicationService;
    private final SealPdfService pdfService;
    private final SysUserMapper users;
    private final ProjectPermissionService permissions;
    private final FileResourceMapper files;
    private final FileStorageManager storage;
    private final OperationLogMapper audit;
    private final TransactionTemplate transactions;
    private final ObjectMapper json;
    @Value("${seal.form-export.max-applications:500}") private int maxApplications = 500;
    @Value("${file.storage.type:local}") private String storageProvider = "local";
    private final AtomicBoolean workerBusy = new AtomicBoolean();
    private final ExecutorService worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(1), runnable -> {
                Thread thread = new Thread(runnable, "seal-form-export");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());

    @Transactional
    public SealFormExportJobVO create(SealFormExportRequest request, SysUser currentUser) {
        requireProject(request.getProjectId(), currentUser);
        String hash = fingerprint(request);
        // Serializes per-user idempotency and the two-active-job limit, across all projects.
        SysUser owner = users.selectByIdForUpdate(currentUser.getId());
        requireEnabled(owner);
        SealFormExportJob existing = jobs.selectOne(new LambdaQueryWrapper<SealFormExportJob>()
                .eq(SealFormExportJob::getRequestedById, owner.getId())
                .eq(SealFormExportJob::getRequestKey, request.getRequestKey()));
        if (existing != null) {
            if (!hash.equals(existing.getRequestHash())) throw BusinessException.of(409, "请求编号已用于其他导出条件");
            validateItems(existing, owner);
            return SealFormExportJobVO.from(existing);
        }
        if (jobs.selectCount(new LambdaQueryWrapper<SealFormExportJob>()
                .eq(SealFormExportJob::getRequestedById, owner.getId())
                .in(SealFormExportJob::getStatus, List.of("PENDING", "RUNNING"))) >= 2) {
            throw BusinessException.of(409, "已有两个申请单合并任务正在生成，请完成后再试");
        }
        List<SealApplication> selected = selectApplications(request, owner);
        if (selected.isEmpty()) throw BusinessException.of(400, "当前条件下没有可导出的已通过申请");
        if (selected.size() > maxApplications) throw tooMany();
        for (SealApplication application : selected) requireExportable(application, owner, request.getProjectId());
        SealFormExportJob job = new SealFormExportJob();
        job.setProjectId(request.getProjectId());
        job.setRequestedById(owner.getId());
        job.setRequestedByName(displayName(owner));
        job.setRequestKey(request.getRequestKey());
        job.setRequestHash(hash);
        job.setSelectionMode(request.getSelectionMode());
        job.setStatus("PENDING");
        job.setApplicationCount(selected.size());
        job.setProcessedCount(0);
        job.setPageCount(0);
        job.setAttempts(0);
        job.setCreateTime(now());
        job.setUpdateTime(now());
        one(jobs.insert(job));
        job.setFileName("用印申请单合并-" + now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                + "-" + selected.size() + "份-" + job.getId() + ".pdf");
        one(jobs.updateById(job));
        for (int i = 0; i < selected.size(); i++) {
            SealFormExportItem item = new SealFormExportItem();
            item.setJobId(job.getId());
            item.setProjectId(job.getProjectId());
            item.setApplicationId(selected.get(i).getId());
            item.setItemOrder(i);
            one(items.insert(item));
        }
        return SealFormExportJobVO.from(job);
    }

    private List<SealApplication> selectApplications(SealFormExportRequest request, SysUser user) {
        if ("FILTER".equals(request.getSelectionMode())) {
            if (request.getApplicationIds() != null && !request.getApplicationIds().isEmpty())
                throw BusinessException.of(400, "按筛选导出不能同时指定申请编号");
            return applicationService.findFormsByFilter(request.getProjectId(), request.getScope(), request.getStatus(),
                    request.getKeyword(), request.getStartDate(), request.getEndDate(), maxApplications + 1, user);
        }
        if (!"SELECTED".equals(request.getSelectionMode())) throw BusinessException.of(400, "请选择导出范围");
        List<Long> ids = request.getApplicationIds();
        if (ids == null || ids.isEmpty()) throw BusinessException.of(400, "请先勾选已通过的申请");
        if (ids.size() > maxApplications) throw tooMany();
        if (ids.stream().anyMatch(id -> id == null || id <= 0)) throw BusinessException.of(400, "申请编号无效");
        Set<Long> unique = new HashSet<>(ids);
        List<SealApplication> selected = applications.selectList(new LambdaQueryWrapper<SealApplication>()
                .in(SealApplication::getId, unique).eq(SealApplication::getProjectId, request.getProjectId())
                .orderByDesc(SealApplication::getCreateTime).orderByDesc(SealApplication::getId));
        if (selected.size() != unique.size()) throw BusinessException.forbidden("所选申请不存在或不属于当前项目，请刷新列表");
        return selected;
    }

    public List<SealFormExportJobVO> list(Long projectId, SysUser user) {
        requireProject(projectId, user);
        return jobs.selectList(new LambdaQueryWrapper<SealFormExportJob>()
                .eq(SealFormExportJob::getProjectId, projectId).eq(SealFormExportJob::getRequestedById, user.getId())
                .orderByDesc(SealFormExportJob::getCreateTime).orderByDesc(SealFormExportJob::getId).last("LIMIT 20"))
                .stream().map(SealFormExportJobVO::from).toList();
    }

    public SealFormExportJobVO get(Long id, SysUser user) {
        return SealFormExportJobVO.from(authorizedJob(id, user));
    }

    @Transactional
    public SealFormExportJobVO retry(Long id, String requestKey, SysUser user) {
        SealFormExportJob job = authorizedJob(id, user);
        if (!Set.of("FAILED", "EXPIRED").contains(job.getStatus()))
            throw BusinessException.of(409, "仅失败或过期任务可以重新生成");
        SealFormExportRequest request = new SealFormExportRequest();
        request.setProjectId(job.getProjectId());
        request.setRequestKey(requestKey);
        request.setSelectionMode("SELECTED");
        request.setApplicationIds(jobItems(id).stream().map(SealFormExportItem::getApplicationId).toList());
        return create(request, user);
    }

    public Download download(Long id, SysUser user) {
        SealFormExportJob job = authorizedJob(id, user);
        if (!"SUCCEEDED".equals(job.getStatus()) || job.getFileResourceId() == null
                || job.getExpiresTime() == null || !job.getExpiresTime().isAfter(now()))
            throw BusinessException.of(409, "合并文件尚未生成或已过期，请重新生成");
        validateItems(job, user);
        FileResource file = files.selectById(job.getFileResourceId());
        if (file == null || !"UPLOADED".equals(file.getStatus())
                || !BUSINESS_TYPE.equals(file.getBusinessType()) || !id.equals(file.getBusinessId())
                || !job.getProjectId().equals(file.getProjectId())) throw BusinessException.notFound("合并文件不存在");
        return new Download(job.getFileName(), storage.load(file));
    }

    @Scheduled(fixedDelayString = "${seal.form-export.poll-ms:2000}")
    public void dispatch() {
        if (worker.isShutdown() || workerBusy.get() || jobs.migrationApplied() == 0) return;
        if (!workerBusy.compareAndSet(false, true)) return;
        try {
            worker.execute(() -> {
                try {
                    SealFormExportJob job = transactions.execute(status -> claim());
                    if (job != null) process(job);
                } catch (Exception error) {
                    log.warn("申请单导出调度失败: {}", error.getClass().getSimpleName());
                } finally { workerBusy.set(false); }
            });
        } catch (RejectedExecutionException ignored) { workerBusy.set(false); }
    }

    SealFormExportJob claim() {
        SealFormExportJob job = jobs.nextForUpdate();
        if (job == null) return null;
        stageFile(job);
        job.setFileResourceId(null);
        if (job.getAttempts() >= 3) {
            job.setStatus("FAILED");
            job.setErrorMessage("任务多次中断，请重新生成");
            job.setLeaseOwner(null);
            job.setLeaseUntil(null);
            job.setUpdateTime(now());
            one(jobs.updateById(job));
            return null;
        }
        job.setStatus("RUNNING");
        job.setLeaseOwner(UUID.randomUUID().toString());
        job.setLeaseUntil(now().plusMinutes(5));
        job.setAttempts(job.getAttempts() + 1);
        job.setProcessedCount(0);
        job.setPageCount(0);
        job.setErrorMessage(null);
        job.setUpdateTime(now());
        one(jobs.updateById(job));
        return job;
    }

    void process(SealFormExportJob job) {
        Path temporary = null;
        try {
            SysUser owner = currentOwner(job);
            List<SealFormExportItem> rows = validateItems(job, owner);
            temporary = Files.createTempFile("seal-form-export-", ".pdf");
            int pages = SealFormPdfMerger.merge(temporary, rows.stream().map(SealFormExportItem::getApplicationId).toList(),
                    id -> {
                        SysUser liveOwner = currentOwner(job);
                        SealApplication application = applicationService.requireApplication(id);
                        requireExportable(application, liveOwner, job.getProjectId());
                        return pdfService.render(applicationService.detail(id, liveOwner));
                    }, (processed, totalPages) -> transactions.executeWithoutResult(status -> {
                        SealFormExportJob live = ownedJob(job);
                        live.setProcessedCount(processed);
                        live.setPageCount(totalPages);
                        live.setLeaseUntil(now().plusMinutes(5));
                        live.setUpdateTime(now());
                        one(jobs.updateById(live));
                    }));
            String key = "seal/form-exports/" + job.getProjectId() + "/" + job.getId() + "/" + job.getLeaseOwner() + ".pdf";
            // Reserve metadata before writing storage so crashes and failed commits remain cleanable.
            Long fileId = transactions.execute(status -> reserveFile(job, key));
            StoredFile stored = storage.store(key, new PathMultipartFile("file", job.getFileName(), "application/pdf", temporary));
            transactions.executeWithoutResult(status -> complete(job, fileId, stored, pages));
        } catch (Exception error) {
            log.warn("申请单合并失败，jobId={}，type={}", job.getId(), error.getClass().getSimpleName());
            transactions.executeWithoutResult(status -> fail(job, error));
        } finally {
            if (temporary != null) try { Files.deleteIfExists(temporary); }
            catch (IOException error) { log.warn("申请单临时文件清理失败，jobId={}", job.getId()); }
        }
    }

    private Long reserveFile(SealFormExportJob claimed, String key) {
        SealFormExportJob job = ownedJob(claimed);
        currentOwner(job);
        FileResource file = new FileResource();
        file.setProjectId(job.getProjectId());
        file.setBusinessType(BUSINESS_TYPE);
        file.setBusinessId(job.getId());
        file.setUploaderId(job.getRequestedById());
        file.setFileName(job.getFileName());
        file.setOriginalFileName(job.getFileName());
        file.setFileType("pdf");
        file.setFileExtension("pdf");
        file.setMimeType("application/pdf");
        file.setFilePath(key);
        file.setStorageKey(key);
        file.setStorageProvider(storageProvider);
        file.setFileSize(0L);
        file.setStatus("GENERATING");
        file.setDeleted(0);
        file.setCreateTime(now());
        file.setUpdateTime(now());
        one(files.insert(file));
        job.setFileResourceId(file.getId());
        job.setLeaseUntil(now().plusMinutes(5));
        job.setUpdateTime(now());
        one(jobs.updateById(job));
        return file.getId();
    }

    void complete(SealFormExportJob claimed, Long fileId, StoredFile stored, int pages) {
        SealFormExportJob job = ownedJob(claimed);
        SysUser owner = currentOwner(job);
        List<SealFormExportItem> rows = validateItems(job, owner);
        if (!Objects.equals(job.getFileResourceId(), fileId)) throw BusinessException.of(409, "导出文件状态已变化");
        FileResource file = files.selectById(fileId);
        if (file == null || !"GENERATING".equals(file.getStatus())) throw BusinessException.of(409, "导出文件状态已变化");
        file.setStatus("UPLOADED");
        file.setFileSize(stored.size());
        file.setSha256(stored.sha256());
        file.setUpdateTime(now());
        one(files.updateById(file));
        job.setStatus("SUCCEEDED");
        job.setProcessedCount(job.getApplicationCount());
        job.setPageCount(pages);
        job.setLeaseOwner(null);
        job.setLeaseUntil(null);
        job.setExpiresTime(now().plusDays(7));
        job.setUpdateTime(now());
        one(jobs.updateById(job));
        for (SealFormExportItem row : rows) {
            applicationService.recordExternalAction(applicationService.requireApplication(row.getApplicationId()),
                    "EXPORT_MERGED_PDF", owner, null, "合并导出申请单 PDF，批次 #" + job.getId(), null);
        }
        OperationLog entry = new OperationLog();
        entry.setUserId(owner.getId());
        entry.setUsername(displayName(owner));
        entry.setOperationType("EXPORT_MERGED_PDF");
        entry.setBusinessType(BUSINESS_TYPE);
        entry.setBusinessId(job.getId());
        entry.setOperationDesc("合并导出用印申请单，项目 #" + job.getProjectId() + "，" + rows.size() + " 份，" + pages + " 页");
        entry.setCreateTime(now());
        one(audit.insert(entry));
    }

    void fail(SealFormExportJob claimed, Exception error) {
        SealFormExportJob job = jobs.lock(claimed.getId());
        if (job == null || !"RUNNING".equals(job.getStatus()) || !Objects.equals(job.getLeaseOwner(), claimed.getLeaseOwner())) return;
        stageFile(job);
        job.setFileResourceId(null);
        job.setStatus("FAILED");
        String message = error instanceof BusinessException ? error.getMessage() : "PDF 合并失败，请重新生成";
        job.setErrorMessage(message == null ? "导出失败，请重试" : message.substring(0, Math.min(message.length(), 450)));
        job.setLeaseOwner(null);
        job.setLeaseUntil(null);
        job.setUpdateTime(now());
        one(jobs.updateById(job));
    }

    @Scheduled(fixedDelayString = "${seal.form-export.cleanup-ms:60000}")
    public void expire() {
        if (jobs.migrationApplied() == 0) return;
        for (SealFormExportJob candidate : jobs.selectList(new LambdaQueryWrapper<SealFormExportJob>()
                .eq(SealFormExportJob::getStatus, "SUCCEEDED").lt(SealFormExportJob::getExpiresTime, now()).last("LIMIT 100"))) {
            transactions.executeWithoutResult(status -> {
                SealFormExportJob job = jobs.lock(candidate.getId());
                if (job == null || !"SUCCEEDED".equals(job.getStatus()) || !job.getExpiresTime().isBefore(now())) return;
                stageFile(job);
                job.setStatus("EXPIRED");
                job.setUpdateTime(now());
                one(jobs.updateById(job));
            });
        }
        // Only old files bearing this worker's unique prefix, never unrelated temporary files.
        try (var paths = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1);
            paths.filter(path -> path.getFileName().toString().startsWith("seal-form-export-")
                    && path.getFileName().toString().endsWith(".pdf") && !Files.isSymbolicLink(path)).forEach(path -> {
                try { if (Files.getLastModifiedTime(path).toMillis() < cutoff) Files.deleteIfExists(path); }
                catch (IOException ignored) { /* Retried next pass. */ }
            });
        } catch (IOException ignored) { log.warn("申请单临时目录清理暂不可用"); }
    }

    private void stageFile(SealFormExportJob job) {
        Long id = job.getFileResourceId();
        if (id == null) return;
        FileResource file = files.selectById(id);
        if (file == null) return;
        // Explicit SQL also updates the logical-delete flag, which updateById excludes.
        one(files.stageSealFormExportForDelete(id, job.getProjectId(), job.getId()));
    }

    private SealFormExportJob ownedJob(SealFormExportJob claimed) {
        SealFormExportJob live = jobs.lock(claimed.getId());
        if (live == null || !"RUNNING".equals(live.getStatus()) || !Objects.equals(live.getLeaseOwner(), claimed.getLeaseOwner()))
            throw BusinessException.of(409, "导出任务已由其他执行器接管");
        return live;
    }

    private SealFormExportJob authorizedJob(Long id, SysUser user) {
        SealFormExportJob job = jobs.selectById(id);
        if (job == null) throw BusinessException.notFound("合并导出任务不存在");
        requireProject(job.getProjectId(), user);
        if (!Objects.equals(job.getRequestedById(), user.getId())) throw BusinessException.forbidden("只能访问本人创建的合并任务");
        return job;
    }

    private List<SealFormExportItem> validateItems(SealFormExportJob job, SysUser user) {
        List<SealFormExportItem> rows = jobItems(job.getId());
        if (rows.size() != job.getApplicationCount()) throw BusinessException.of(409, "导出申请集合已变化，请重新生成");
        for (SealFormExportItem row : rows)
            requireExportable(applicationService.requireApplication(row.getApplicationId()), user, job.getProjectId());
        return rows;
    }

    private List<SealFormExportItem> jobItems(Long id) {
        return items.selectList(new LambdaQueryWrapper<SealFormExportItem>().eq(SealFormExportItem::getJobId, id)
                .orderByAsc(SealFormExportItem::getItemOrder));
    }

    private SysUser currentOwner(SealFormExportJob job) {
        SysUser owner = users.selectById(job.getRequestedById());
        requireProject(job.getProjectId(), owner);
        return owner;
    }

    private void requireProject(Long projectId, SysUser user) {
        requireEnabled(user);
        if (projectId == null) throw BusinessException.of(400, "请选择项目");
        permissions.checkProjectPermission(user.getId(), projectId);
        permissions.requireSystemPermission(user.getId(), projectId, SystemPermissionCodes.SEAL_APPLICATION_EXPORT);
    }

    private void requireExportable(SealApplication application, SysUser user, Long projectId) {
        if (!Objects.equals(projectId, application.getProjectId())) throw BusinessException.forbidden("申请不属于当前项目");
        applicationService.requireReadable(application, user);
        applicationService.requireFormExportPermission(application, user);
        if (!SealApplicationService.APPROVED.equals(application.getStatus()))
            throw BusinessException.of(409, "仅审批通过的申请可以合并导出，请刷新列表");
    }

    private void requireEnabled(SysUser user) {
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) throw BusinessException.unauthorized("账号已失效，请重新登录");
    }

    String fingerprint(SealFormExportRequest request) {
        if (request.getRequestKey() == null || request.getRequestKey().isBlank() || request.getRequestKey().length() > 64)
            throw BusinessException.of(400, "导出请求编号无效");
        try {
            List<Long> ids = request.getApplicationIds() == null ? List.of() : request.getApplicationIds().stream().distinct().sorted().toList();
            byte[] canonical = json.writeValueAsBytes(Arrays.asList(request.getProjectId(), request.getSelectionMode(),
                    request.getScope(), request.getStatus(), request.getKeyword(), request.getStartDate(), request.getEndDate(), ids));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (Exception error) { throw BusinessException.of(400, "导出条件无效"); }
    }

    private BusinessException tooMany() { return BusinessException.of(413, "单次最多导出 " + maxApplications + " 份申请单，请缩小范围"); }
    private static String displayName(SysUser user) { return Objects.toString(user.getRealName(), user.getUsername()); }
    private static LocalDateTime now() { return LocalDateTime.now(ZONE); }
    private static void one(int count) { if (count != 1) throw BusinessException.of(409, "导出状态已变化，请刷新重试"); }
    @PreDestroy public void shutdown() { worker.shutdownNow(); }
    public record Download(String fileName, Resource resource) {}
}
