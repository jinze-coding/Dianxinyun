package com.example.siteplatform.quality.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.file.constant.FileStatus;
import com.example.siteplatform.file.entity.FileResource;
import com.example.siteplatform.file.mapper.FileResourceMapper;
import com.example.siteplatform.file.storage.FileStorageManager;
import com.example.siteplatform.file.storage.PathMultipartFile;
import com.example.siteplatform.file.storage.StoredFile;
import com.example.siteplatform.notification.service.UserNotificationService;
import com.example.siteplatform.project.entity.ProjectInfo;
import com.example.siteplatform.project.mapper.ProjectInfoMapper;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.quality.dto.QualityIssueExportRequest;
import com.example.siteplatform.quality.entity.QualityIssue;
import com.example.siteplatform.quality.entity.QualityIssueExportJob;
import com.example.siteplatform.quality.entity.QualityIssueExportJobItem;
import com.example.siteplatform.quality.entity.QualityWeeklyInspection;
import com.example.siteplatform.quality.mapper.QualityIssueExportJobItemMapper;
import com.example.siteplatform.quality.mapper.QualityIssueExportJobMapper;
import com.example.siteplatform.quality.mapper.QualityIssueMapper;
import com.example.siteplatform.quality.mapper.QualityWeeklyInspectionMapper;
import com.example.siteplatform.quality.vo.QualityIssueExportJobVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QualityIssueExportService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final String JOB_PENDING = "PENDING";
    private static final String JOB_RUNNING = "RUNNING";
    private static final String JOB_SUCCEEDED = "SUCCEEDED";
    private static final String JOB_FAILED = "FAILED";
    private static final String JOB_EXPIRED = "EXPIRED";
    private static final String EXPORT_BUSINESS_TYPE = "QUALITY_ISSUE_EXPORT";
    private static final String XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final float PHOTO_THUMBNAIL_ROW_HEIGHT = 110F;
    private static final float EMPTY_PHOTO_ROW_HEIGHT = 56F;

    private final QualityIssueService issueService;
    private final SysUserMapper userMapper;
    private final QualityIssueMapper issueMapper;
    private final QualityIssueExportJobMapper jobMapper;
    private final QualityIssueExportJobItemMapper itemMapper;
    private final QualityWeeklyInspectionMapper weeklyInspectionMapper;
    private final FileResourceMapper fileMapper;
    private final FileStorageManager storageManager;
    private final ProjectInfoMapper projectInfoMapper;
    private final ProjectPermissionService projectPermissionService;
    private final UserNotificationService notificationService;
    private final TransactionTemplate transactionTemplate;

    @Value("${quality.issue-export.max-issues:300}")
    private int maxIssues;

    @Value("${quality.issue-export.max-photos:800}")
    private int maxPhotos;

    @Value("${quality.issue-export.max-photo-bytes:125829120}")
    private long maxPhotoBytes;

    @Transactional
    public QualityIssueExportJobVO create(QualityIssueExportRequest request, SysUser currentUser) {
        String source = normalizeSource(request.getSource());
        String issueStatus = normalizeIssueStatus(request.getStatus());
        String keyword = trimToNull(request.getKeyword());
        List<QualityIssue> issues = issueService.listIssueEntitiesForExport(
                request.getProjectId(), issueStatus, keyword, source,
                request.getStartDate(), request.getEndDate(), currentUser);
        if (issues.size() > maxIssues) {
            throw BusinessException.of(HttpStatus.PAYLOAD_TOO_LARGE.value(),
                    "单次导出最多包含" + maxIssues + "个质量问题，请缩短日期范围");
        }
        if (userMapper.selectByIdForUpdate(currentUser.getId()) == null) {
            throw BusinessException.unauthorized("当前账号不存在或已失效");
        }
        long activeJobs = jobMapper.selectCount(new LambdaQueryWrapper<QualityIssueExportJob>()
                .eq(QualityIssueExportJob::getRequestedById, currentUser.getId())
                .in(QualityIssueExportJob::getStatus, List.of(JOB_PENDING, JOB_RUNNING)));
        if (activeJobs >= 2) {
            throw BusinessException.of(409, "当前已有两个质量报表正在生成，请完成后再创建");
        }

        PhotoPlan photoPlan = loadPhotoPlan(issues, false);
        requireWithinLimits(issues.size(), photoPlan.photoCount(), photoPlan.photoBytes());

        QualityIssueExportJob job = new QualityIssueExportJob();
        job.setProjectId(request.getProjectId());
        job.setRequestedById(currentUser.getId());
        job.setRequestedByName(displayName(currentUser));
        job.setStartDate(request.getStartDate());
        job.setEndDate(request.getEndDate());
        job.setIssueSource(source);
        job.setIssueStatus(issueStatus);
        job.setKeyword(keyword);
        job.setStatus(JOB_PENDING);
        job.setProgress(0);
        job.setIssueCount(issues.size());
        job.setPhotoCount(photoPlan.photoCount());
        job.setPhotoBytes(photoPlan.photoBytes());
        job.setCreateTime(now());
        job.setUpdateTime(now());
        requireSingle(jobMapper.insert(job), "质量报表任务创建");

        for (int index = 0; index < issues.size(); index++) {
            QualityIssue issue = issues.get(index);
            QualityIssueExportJobItem item = new QualityIssueExportJobItem();
            item.setProjectId(job.getProjectId());
            item.setJobId(job.getId());
            item.setIssueId(issue.getId());
            item.setItemOrder(index + 1);
            item.setRecordDate(issue.getRecordDate());
            item.setCreateTime(now());
            requireSingle(itemMapper.insert(item), "质量报表问题快照创建");
        }
        return toVO(job);
    }

    public List<QualityIssueExportJobVO> list(Long projectId, SysUser currentUser) {
        requireView(projectId, currentUser);
        LambdaQueryWrapper<QualityIssueExportJob> query = new LambdaQueryWrapper<QualityIssueExportJob>()
                .eq(QualityIssueExportJob::getProjectId, projectId)
                .orderByDesc(QualityIssueExportJob::getCreateTime)
                .orderByDesc(QualityIssueExportJob::getId)
                .last("LIMIT 50");
        if (!projectPermissionService.isPlatformAdmin(currentUser.getId())) {
            query.eq(QualityIssueExportJob::getRequestedById, currentUser.getId());
        }
        return jobMapper.selectList(query).stream().map(this::toVO).toList();
    }

    public QualityIssueExportJobVO get(Long id, SysUser currentUser) {
        return toVO(requireAuthorizedJob(id, currentUser));
    }

    public Download download(Long id, SysUser currentUser) {
        QualityIssueExportJob job = requireAuthorizedJob(id, currentUser);
        if (!JOB_SUCCEEDED.equals(job.getStatus()) || job.getFileResourceId() == null) {
            throw BusinessException.of(409, "导出文件尚未生成或已经过期");
        }
        if (job.getExpiresTime() != null && now().isAfter(job.getExpiresTime())) {
            throw BusinessException.notFound("导出文件已经过期");
        }
        FileResource file = fileMapper.selectById(job.getFileResourceId());
        if (file == null || Integer.valueOf(1).equals(file.getDeleted())) {
            throw BusinessException.notFound("导出文件不存在");
        }
        return new Download(file.getOriginalFileName(), storageManager.load(file));
    }

    @Scheduled(fixedDelayString = "${quality.issue-export.process-delay-millis:5000}")
    public void processPendingJobs() {
        if (jobMapper.countAppliedMigration() == 0) {
            return;
        }
        QualityIssueExportJob claimed = transactionTemplate.execute(status -> {
            QualityIssueExportJob job = jobMapper.selectNextPendingForUpdate();
            if (job == null) return null;
            job.setStatus(JOB_RUNNING);
            job.setProgress(5);
            job.setErrorMessage(null);
            job.setUpdateTime(now());
            requireSingle(jobMapper.updateById(job), "质量报表任务领取");
            return job;
        });
        if (claimed == null) return;
        try {
            process(claimed.getId());
        } catch (Exception exception) {
            log.error("质量问题报表生成失败，jobId={}", claimed.getId(), exception);
            String message = safeErrorMessage(exception);
            transactionTemplate.executeWithoutResult(status -> markFailed(claimed.getId(), message));
            notifyResult(claimed, false, message);
        }
    }

    @Scheduled(cron = "${quality.issue-export.expire-cron:0 15 2 * * ?}")
    public void expireFiles() {
        if (jobMapper.countAppliedMigration() == 0) {
            return;
        }
        List<QualityIssueExportJob> expired = jobMapper.selectList(
                new LambdaQueryWrapper<QualityIssueExportJob>()
                        .eq(QualityIssueExportJob::getStatus, JOB_SUCCEEDED)
                        .lt(QualityIssueExportJob::getExpiresTime, now())
                        .last("LIMIT 100"));
        for (QualityIssueExportJob candidate : expired) {
            try {
                transactionTemplate.executeWithoutResult(status -> expire(candidate.getId()));
            } catch (RuntimeException exception) {
                log.warn("质量报表过期清理失败，jobId={}", candidate.getId(), exception);
            }
        }
    }

    private void process(Long jobId) {
        QualityIssueExportJob job = jobMapper.selectById(jobId);
        if (job == null || !JOB_RUNNING.equals(job.getStatus())) return;
        List<QualityIssueExportJobItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<QualityIssueExportJobItem>()
                        .eq(QualityIssueExportJobItem::getJobId, jobId)
                        .orderByAsc(QualityIssueExportJobItem::getItemOrder));
        List<Long> issueIds = items.stream().map(QualityIssueExportJobItem::getIssueId).toList();
        Map<Long, QualityIssue> issueById = issueIds.isEmpty() ? Map.of()
                : issueMapper.selectBatchIds(issueIds).stream()
                .collect(Collectors.toMap(QualityIssue::getId, Function.identity()));
        List<QualityIssue> issues = new ArrayList<>();
        for (QualityIssueExportJobItem item : items) {
            QualityIssue issue = issueById.get(item.getIssueId());
            if (issue == null || !Objects.equals(issue.getProjectId(), job.getProjectId())) {
                throw new BusinessException("导出问题快照已变化，请重新创建报表");
            }
            issues.add(issue);
        }
        if (issues.size() != Objects.requireNonNullElse(job.getIssueCount(), 0)) {
            throw new BusinessException("导出问题数量已变化，请重新创建报表");
        }
        PhotoPlan photoPlan = loadPhotoPlan(issues, true);
        requireWithinLimits(issues.size(), photoPlan.photoCount(), photoPlan.photoBytes());
        updateProgress(jobId, 20);

        ProjectInfo project = projectInfoMapper.selectById(job.getProjectId());
        if (project == null) throw BusinessException.notFound("项目不存在");
        Map<Long, QualityWeeklyInspection> weeklyById = loadWeeklyInspections(issues);
        Path tempFile = null;
        StoredFile stored = null;
        try {
            tempFile = Files.createTempFile("quality-issue-export-", ".xlsx");
            buildWorkbook(tempFile, project, job, issues, weeklyById, photoPlan);
            updateProgress(jobId, 85);
            String fileName = buildFileName(project, job);
            String storageKey = "quality/exports/" + job.getProjectId() + "/" + job.getId()
                    + "/" + UUID.randomUUID() + ".xlsx";
            stored = storageManager.store(storageKey,
                    new PathMultipartFile("file", fileName, XLSX_MIME, tempFile));
            StoredFile completedFile = stored;
            transactionTemplate.executeWithoutResult(status -> complete(jobId, completedFile));
            notifyResult(job, true, null);
        } catch (IOException exception) {
            throw new BusinessException("质量报表临时文件生成失败");
        } catch (RuntimeException exception) {
            if (stored != null) storageManager.deleteQuietly(stored.provider(), stored.storageKey());
            throw exception;
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException exception) {
                    log.warn("质量报表临时文件清理失败，path={}", tempFile, exception);
                }
            }
        }
    }

    private void buildWorkbook(Path target, ProjectInfo project, QualityIssueExportJob job,
                               List<QualityIssue> issues,
                               Map<Long, QualityWeeklyInspection> weeklyById,
                               PhotoPlan photoPlan) {
        try (Workbook workbook = new XSSFWorkbook(); OutputStream output = Files.newOutputStream(target)) {
            WorkbookStyles styles = new WorkbookStyles(workbook);
            writeSummarySheet(workbook, styles, project, job, issues);
            writeDetailSheet(workbook, styles, issues, weeklyById, photoPlan);
            workbook.write(output);
        } catch (IOException exception) {
            throw new BusinessException("质量问题 Excel 生成失败");
        }
    }

    private void writeSummarySheet(Workbook workbook, WorkbookStyles styles, ProjectInfo project,
                                   QualityIssueExportJob job, List<QualityIssue> issues) {
        Sheet sheet = workbook.createSheet("按日汇总");
        sheet.setDefaultRowHeightInPoints(22);
        sheet.setColumnWidth(0, 15 * 256);
        for (int column = 1; column <= 11; column++) sheet.setColumnWidth(column, 13 * 256);
        Row title = sheet.createRow(0);
        title.setHeightInPoints(32);
        cell(title, 0, "质量问题按日汇总", styles.title());
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 11));
        writeMetadata(sheet, styles, project, job);
        int headerRow = 7;
        Row header = sheet.createRow(headerRow);
        writeRow(header, styles.header(), List.of(
                "问题日期", "总数", "待整改", "待复查", "已关闭", "已作废", "已逾期",
                "一般", "重要", "严重", "周检问题", "历史独立"));
        Map<LocalDate, List<QualityIssue>> byDate = issues.stream()
                .collect(Collectors.groupingBy(QualityIssue::getRecordDate, LinkedHashMap::new, Collectors.toList()));
        int rowIndex = headerRow + 1;
        for (LocalDate date = job.getStartDate(); !date.isAfter(job.getEndDate()); date = date.plusDays(1)) {
            List<QualityIssue> daily = byDate.getOrDefault(date, List.of());
            Row row = sheet.createRow(rowIndex++);
            writeRow(row, styles.body(), List.of(
                    date.toString(), daily.size(), countStatus(daily, QualityIssueService.STATUS_PENDING),
                    countStatus(daily, QualityIssueService.STATUS_RECHECK),
                    countStatus(daily, QualityIssueService.STATUS_CLOSED),
                    countStatus(daily, QualityIssueService.STATUS_VOIDED), countOverdue(daily),
                    countSeverity(daily, "NORMAL"), countSeverity(daily, "WARNING"),
                    countSeverity(daily, "DANGER"),
                    daily.stream().filter(item -> item.getWeeklyInspectionId() != null).count(),
                    daily.stream().filter(item -> item.getWeeklyInspectionId() == null).count()));
        }
        Row total = sheet.createRow(rowIndex + 1);
        int effective = countStatus(issues, QualityIssueService.STATUS_PENDING)
                + countStatus(issues, QualityIssueService.STATUS_RECHECK)
                + countStatus(issues, QualityIssueService.STATUS_CLOSED);
        int closed = countStatus(issues, QualityIssueService.STATUS_CLOSED);
        writeRow(total, styles.summary(), List.of(
                "合计", issues.size(), countStatus(issues, QualityIssueService.STATUS_PENDING),
                countStatus(issues, QualityIssueService.STATUS_RECHECK), closed,
                countStatus(issues, QualityIssueService.STATUS_VOIDED), countOverdue(issues),
                countSeverity(issues, "NORMAL"), countSeverity(issues, "WARNING"),
                countSeverity(issues, "DANGER"),
                issues.stream().filter(item -> item.getWeeklyInspectionId() != null).count(),
                issues.stream().filter(item -> item.getWeeklyInspectionId() == null).count()));
        Row rate = sheet.createRow(rowIndex + 2);
        cell(rate, 0, "闭环率", styles.summary());
        cell(rate, 1, effective == 0 ? "0%" : Math.round(closed * 100F / effective) + "%", styles.summary());
        sheet.createFreezePane(0, headerRow + 1);
        sheet.setAutoFilter(new CellRangeAddress(headerRow, headerRow, 0, 11));
    }

    private void writeMetadata(Sheet sheet, WorkbookStyles styles, ProjectInfo project,
                               QualityIssueExportJob job) {
        List<List<Object>> rows = List.of(
                List.of("项目", displayProjectName(project)),
                List.of("统计范围", job.getStartDate() + " 至 " + job.getEndDate()),
                List.of("问题来源", sourceLabel(job.getIssueSource())),
                List.of("问题状态", statusLabel(job.getIssueStatus())),
                List.of("关键词", Objects.requireNonNullElse(job.getKeyword(), "全部")),
                List.of("导出信息", Objects.requireNonNullElse(job.getRequestedByName(), "-") + " · " + formatTime(now())));
        for (int index = 0; index < rows.size(); index++) {
            Row row = sheet.createRow(index + 1);
            cell(row, 0, rows.get(index).get(0), styles.label());
            cell(row, 1, rows.get(index).get(1), styles.body());
            sheet.addMergedRegion(new CellRangeAddress(index + 1, index + 1, 1, 11));
        }
    }

    private void writeDetailSheet(Workbook workbook, WorkbookStyles styles, List<QualityIssue> issues,
                                  Map<Long, QualityWeeklyInspection> weeklyById,
                                  PhotoPlan photoPlan) {
        Sheet sheet = workbook.createSheet("问题明细");
        List<String> headings = List.of(
                "序号", "问题日期", "问题编号", "来源", "周检编号", "标题", "位置", "问题描述",
                "等级", "整改负责人", "闭环期限", "当前状态", "逾期天数", "整改说明", "整改时间",
                "复查人", "复查时间", "复查意见", "整改前照片", "最新整改后照片");
        Row header = sheet.createRow(0);
        writeRow(header, styles.header(), headings);
        Drawing<?> drawing = sheet.createDrawingPatriarch();
        int rowIndex = 1;
        int issueSequence = 1;
        long actualBytes = 0;
        for (QualityIssue issue : issues) {
            QualityWeeklyInspection weekly = issue.getWeeklyInspectionId() == null
                    ? null : weeklyById.get(issue.getWeeklyInspectionId());
            IssuePhotos photos = photoPlan.byIssue().getOrDefault(issue.getId(), IssuePhotos.EMPTY);
            List<Object> issueValues = List.of(
                    issueSequence++, text(issue.getRecordDate()), text(issue.getIssueNo()),
                    issue.getWeeklyInspectionId() == null ? "历史独立" : "周检问题",
                    weekly == null ? "-" : text(weekly.getInspectionNo()), text(issue.getTitle()),
                    text(issue.getLocation()), text(issue.getDescription()), severityLabel(issue.getSeverity()),
                    text(issue.getAssigneeName()), text(issue.getDeadline()), statusLabel(issue.getStatus()),
                    overdueDays(issue), text(issue.getRectificationDescription()), formatTime(issue.getRectifiedTime()),
                    text(issue.getReviewerName()), formatTime(issue.getReviewTime()), text(issue.getReviewComment()));
            int imageRows = Math.max(1, Math.max(photos.before().size(), photos.after().size()));
            int issueStartRow = rowIndex;
            boolean hasAnyPhoto = !photos.before().isEmpty() || !photos.after().isEmpty();
            for (int imageIndex = 0; imageIndex < imageRows; imageIndex++) {
                Row imageRow = sheet.createRow(rowIndex);
                imageRow.setHeightInPoints(hasAnyPhoto
                        ? PHOTO_THUMBNAIL_ROW_HEIGHT : EMPTY_PHOTO_ROW_HEIGHT);
                if (imageIndex == 0) {
                    writeRow(imageRow, styles.body(), issueValues);
                } else {
                    for (int column = 0; column < 18; column++) {
                        cell(imageRow, column, "", styles.body());
                    }
                }
                if (imageIndex < photos.before().size()) {
                    cell(imageRow, 18, "", styles.empty());
                    actualBytes += addOriginalPicture(workbook, drawing, rowIndex, 18, 19,
                            photos.before().get(imageIndex), issue);
                } else {
                    cell(imageRow, 18,
                            photos.before().isEmpty() && imageIndex > 0 ? "" : "无", styles.empty());
                }
                if (imageIndex < photos.after().size()) {
                    cell(imageRow, 19, "", styles.empty());
                    actualBytes += addOriginalPicture(workbook, drawing, rowIndex, 19, 20,
                            photos.after().get(imageIndex), issue);
                } else {
                    cell(imageRow, 19,
                            photos.after().isEmpty() && imageIndex > 0 ? "" : "尚无最新整改后照片",
                            styles.empty());
                }
                if (actualBytes > maxPhotoBytes) {
                    throw BusinessException.of(HttpStatus.PAYLOAD_TOO_LARGE.value(),
                            "导出照片实际大小超过限制，请缩短日期范围");
                }
                rowIndex++;
            }
            if (imageRows > 1) {
                int issueEndRow = rowIndex - 1;
                for (int column = 0; column < 18; column++) {
                    sheet.addMergedRegion(new CellRangeAddress(
                            issueStartRow, issueEndRow, column, column));
                }
            }
        }
        int[] widths = {8, 13, 24, 12, 24, 28, 24, 38, 10, 14, 13, 12, 10, 38, 18, 14, 18, 38, 30, 30};
        for (int index = 0; index < widths.length; index++) sheet.setColumnWidth(index, widths[index] * 256);
        sheet.createFreezePane(0, 1);
        sheet.setPrintGridlines(false);
        sheet.setDisplayGridlines(false);
        sheet.setFitToPage(true);
        sheet.getPrintSetup().setLandscape(true);
        sheet.getPrintSetup().setFitWidth((short) 1);
        sheet.getPrintSetup().setFitHeight((short) 0);
        sheet.setRepeatingRows(CellRangeAddress.valueOf("1:1"));
    }

    private long addOriginalPicture(Workbook workbook, Drawing<?> drawing, int row,
                                    int firstColumn, int lastColumnExclusive,
                                    FileResource file, QualityIssue issue) {
        byte[] original;
        try {
            Resource resource = storageManager.load(file);
            try (InputStream input = resource.getInputStream()) {
                original = input.readAllBytes();
            }
        } catch (Exception exception) {
            throw new BusinessException("问题" + text(issue.getIssueNo()) + "的照片读取失败：" + fileName(file));
        }
        if (original.length == 0) {
            throw new BusinessException("问题" + text(issue.getIssueNo()) + "的照片为空：" + fileName(file));
        }
        PicturePayload payload = picturePayload(file, original, issue);
        int pictureIndex = workbook.addPicture(payload.bytes(), payload.pictureType());
        ClientAnchor anchor = workbook.getCreationHelper().createClientAnchor();
        anchor.setAnchorType(ClientAnchor.AnchorType.MOVE_AND_RESIZE);
        anchor.setCol1(firstColumn);
        anchor.setCol2(lastColumnExclusive);
        anchor.setRow1(row);
        anchor.setRow2(row + 1);
        drawing.createPicture(anchor, pictureIndex);
        return original.length;
    }

    private PicturePayload picturePayload(FileResource file, byte[] original, QualityIssue issue) {
        String extension = extension(file);
        if (Set.of("jpg", "jpeg").contains(extension)) {
            return new PicturePayload(original, Workbook.PICTURE_TYPE_JPEG);
        }
        if ("png".equals(extension)) {
            return new PicturePayload(original, Workbook.PICTURE_TYPE_PNG);
        }
        try (InputStream input = new java.io.ByteArrayInputStream(original);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            BufferedImage image = ImageIO.read(input);
            if (image == null || !ImageIO.write(image, "png", output)) {
                throw new IOException("unsupported image");
            }
            return new PicturePayload(output.toByteArray(), Workbook.PICTURE_TYPE_PNG);
        } catch (IOException exception) {
            throw new BusinessException("问题" + text(issue.getIssueNo())
                    + "的照片格式无法嵌入Excel：" + fileName(file));
        }
    }

    private PhotoPlan loadPhotoPlan(List<QualityIssue> issues, boolean executionPhase) {
        if (issues.isEmpty()) return new PhotoPlan(Map.of(), 0, 0L);
        List<Long> issueIds = issues.stream().map(QualityIssue::getId).toList();
        List<FileResource> beforeFiles = fileMapper.selectList(new LambdaQueryWrapper<FileResource>()
                .eq(FileResource::getProjectId, issues.get(0).getProjectId())
                .eq(FileResource::getBusinessType, "QUALITY_ISSUE")
                .in(FileResource::getBusinessId, issueIds)
                .eq(FileResource::getStatus, FileStatus.UPLOADED)
                .orderByAsc(FileResource::getCreateTime)
                .orderByAsc(FileResource::getId));
        Map<Long, List<FileResource>> beforeByIssue = beforeFiles.stream()
                .collect(Collectors.groupingBy(FileResource::getBusinessId, LinkedHashMap::new, Collectors.toList()));
        LinkedHashSet<Long> afterIds = issues.stream()
                .flatMap(issue -> splitIds(issue.getRectificationPhotoFileIds()).stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, FileResource> afterFileById = afterIds.isEmpty() ? Map.of()
                : fileMapper.selectBatchIds(afterIds).stream()
                .collect(Collectors.toMap(FileResource::getId, Function.identity()));
        Map<Long, IssuePhotos> byIssue = new LinkedHashMap<>();
        int photoCount = 0;
        long photoBytes = 0L;
        for (QualityIssue issue : issues) {
            List<FileResource> before = beforeByIssue.getOrDefault(issue.getId(), List.of());
            List<FileResource> after = new ArrayList<>();
            for (Long id : splitIds(issue.getRectificationPhotoFileIds())) {
                FileResource file = afterFileById.get(id);
                if (file == null || Integer.valueOf(1).equals(file.getDeleted())
                        || !Objects.equals(file.getProjectId(), issue.getProjectId())
                        || !Objects.equals(file.getBusinessId(), issue.getId())
                        || !"QUALITY_RECTIFICATION".equalsIgnoreCase(file.getBusinessType())
                        || !FileStatus.UPLOADED.equalsIgnoreCase(file.getStatus())) {
                    throw new BusinessException("问题" + text(issue.getIssueNo()) + "的最新整改照片不存在：" + id);
                }
                after.add(file);
            }
            if (executionPhase) {
                for (FileResource file : before) validatePhotoMetadata(file, issue, "QUALITY_ISSUE");
            }
            byIssue.put(issue.getId(), new IssuePhotos(List.copyOf(before), List.copyOf(after)));
            photoCount += before.size() + after.size();
            photoBytes += before.stream().mapToLong(this::fileSize).sum();
            photoBytes += after.stream().mapToLong(this::fileSize).sum();
        }
        return new PhotoPlan(Map.copyOf(byIssue), photoCount, photoBytes);
    }

    private void validatePhotoMetadata(FileResource file, QualityIssue issue, String businessType) {
        if (file == null || Integer.valueOf(1).equals(file.getDeleted())
                || !Objects.equals(file.getProjectId(), issue.getProjectId())
                || !Objects.equals(file.getBusinessId(), issue.getId())
                || !businessType.equalsIgnoreCase(file.getBusinessType())
                || !FileStatus.UPLOADED.equalsIgnoreCase(file.getStatus())) {
            throw new BusinessException("问题" + text(issue.getIssueNo()) + "的整改前照片元数据已变化");
        }
    }

    private Map<Long, QualityWeeklyInspection> loadWeeklyInspections(List<QualityIssue> issues) {
        Set<Long> ids = issues.stream().map(QualityIssue::getWeeklyInspectionId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();
        return weeklyInspectionMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(QualityWeeklyInspection::getId, Function.identity()));
    }

    private void complete(Long jobId, StoredFile stored) {
        QualityIssueExportJob job = jobMapper.selectByIdForUpdate(jobId);
        if (job == null || !JOB_RUNNING.equals(job.getStatus())) {
            throw BusinessException.of(409, "质量报表任务状态已变化");
        }
        FileResource file = new FileResource();
        file.setProjectId(job.getProjectId());
        file.setFileName(stored.originalFileName());
        file.setFileType("xlsx");
        file.setFilePath(stored.storageKey());
        file.setFileSize(stored.size());
        file.setBusinessType(EXPORT_BUSINESS_TYPE);
        file.setBusinessId(jobId);
        file.setUploaderId(job.getRequestedById());
        file.setStorageProvider(stored.provider());
        file.setStorageKey(stored.storageKey());
        file.setOriginalFileName(stored.originalFileName());
        file.setMimeType(stored.mimeType());
        file.setFileExtension(stored.extension());
        file.setSha256(stored.sha256());
        file.setStatus(FileStatus.UPLOADED);
        file.setDeleted(0);
        file.setCreateTime(now());
        file.setUpdateTime(now());
        requireSingle(fileMapper.insert(file), "质量报表文件元数据创建");
        job.setFileResourceId(file.getId());
        job.setStatus(JOB_SUCCEEDED);
        job.setProgress(100);
        job.setErrorMessage(null);
        job.setExpiresTime(now().plusDays(7));
        job.setUpdateTime(now());
        requireSingle(jobMapper.updateById(job), "质量报表任务完成");
    }

    private void markFailed(Long jobId, String message) {
        QualityIssueExportJob job = jobMapper.selectByIdForUpdate(jobId);
        if (job == null || !JOB_RUNNING.equals(job.getStatus())) return;
        job.setStatus(JOB_FAILED);
        job.setProgress(100);
        job.setErrorMessage(message);
        job.setUpdateTime(now());
        requireSingle(jobMapper.updateById(job), "质量报表任务失败状态写入");
    }

    private void expire(Long jobId) {
        QualityIssueExportJob job = jobMapper.selectByIdForUpdate(jobId);
        if (job == null || !JOB_SUCCEEDED.equals(job.getStatus())
                || job.getExpiresTime() == null || !job.getExpiresTime().isBefore(now())) return;
        FileResource file = job.getFileResourceId() == null ? null : fileMapper.selectById(job.getFileResourceId());
        if (file != null && !Integer.valueOf(1).equals(file.getDeleted())) {
            file.setDeleted(1);
            file.setStatus("PENDING_DELETE");
            file.setUpdateTime(now());
            requireSingle(fileMapper.updateById(file), "过期质量报表文件标记");
        }
        job.setStatus(JOB_EXPIRED);
        job.setProgress(100);
        job.setUpdateTime(now());
        requireSingle(jobMapper.updateById(job), "质量报表任务过期");
    }

    private void updateProgress(Long jobId, int progress) {
        transactionTemplate.executeWithoutResult(status -> {
            QualityIssueExportJob job = jobMapper.selectByIdForUpdate(jobId);
            if (job == null || !JOB_RUNNING.equals(job.getStatus())) {
                throw BusinessException.of(409, "质量报表任务状态已变化");
            }
            job.setProgress(progress);
            job.setUpdateTime(now());
            requireSingle(jobMapper.updateById(job), "质量报表进度更新");
        });
    }

    private QualityIssueExportJob requireAuthorizedJob(Long id, SysUser currentUser) {
        QualityIssueExportJob job = jobMapper.selectById(id);
        if (job == null) throw BusinessException.notFound("质量报表任务不存在");
        requireView(job.getProjectId(), currentUser);
        if (!projectPermissionService.isPlatformAdmin(currentUser.getId())
                && !Objects.equals(job.getRequestedById(), currentUser.getId())) {
            throw BusinessException.forbidden("只能访问本人创建的质量报表");
        }
        return job;
    }

    private void requireView(Long projectId, SysUser user) {
        projectPermissionService.checkProjectPermission(user.getId(), projectId);
        projectPermissionService.requireSystemPermission(user.getId(), projectId, "quality.view");
    }

    private void notifyResult(QualityIssueExportJob job, boolean succeeded, String error) {
        try {
            String event = succeeded ? "EXPORT_SUCCEEDED" : "EXPORT_FAILED";
            String title = succeeded ? "质量问题汇总已生成" : "质量问题汇总生成失败";
            String summary = succeeded
                    ? job.getStartDate() + " 至 " + job.getEndDate() + "，文件保留7天"
                    : Objects.requireNonNullElse(error, "请重新选择范围后再试");
            notificationService.notify(job.getRequestedById(), job.getProjectId(), EXPORT_BUSINESS_TYPE,
                    job.getId(), event, title, summary, "quality-export:" + job.getId() + ":" + event,
                    null, null);
        } catch (RuntimeException exception) {
            log.warn("质量报表结果通知写入失败，jobId={}", job.getId(), exception);
        }
    }

    private void requireWithinLimits(int issueCount, int photoCount, long photoBytes) {
        if (issueCount > maxIssues) {
            throw BusinessException.of(HttpStatus.PAYLOAD_TOO_LARGE.value(),
                    "单次导出最多包含" + maxIssues + "个质量问题，请缩短日期范围");
        }
        if (photoCount > maxPhotos) {
            throw BusinessException.of(HttpStatus.PAYLOAD_TOO_LARGE.value(),
                    "单次导出最多嵌入" + maxPhotos + "张照片，请缩短日期范围");
        }
        if (photoBytes > maxPhotoBytes) {
            throw BusinessException.of(HttpStatus.PAYLOAD_TOO_LARGE.value(),
                    "单次导出照片总大小不能超过" + (maxPhotoBytes / 1024 / 1024) + "MB，请缩短日期范围");
        }
    }

    private QualityIssueExportJobVO toVO(QualityIssueExportJob job) {
        QualityIssueExportJobVO vo = new QualityIssueExportJobVO();
        BeanUtils.copyProperties(job, vo);
        vo.setDownloadable(JOB_SUCCEEDED.equals(job.getStatus())
                && job.getFileResourceId() != null
                && (job.getExpiresTime() == null || !now().isAfter(job.getExpiresTime())));
        return vo;
    }

    private String normalizeSource(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "ALL";
        if (!List.of("ALL", "WEEKLY", "HISTORICAL").contains(normalized)) {
            throw new BusinessException("质量问题来源只支持 ALL、WEEKLY 或 HISTORICAL");
        }
        return normalized;
    }

    private String normalizeIssueStatus(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "ALL";
        if (!List.of("ALL", "PENDING", "OVERDUE", "RECHECK", "CLOSED", "VOIDED").contains(normalized)) {
            throw new BusinessException("质量问题状态筛选值无效");
        }
        return normalized;
    }

    private String buildFileName(ProjectInfo project, QualityIssueExportJob job) {
        String projectName = StringUtils.hasText(project.getShortName()) ? project.getShortName() : project.getProjectName();
        String safeProject = Objects.requireNonNullElse(projectName, "项目")
                .replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (safeProject.length() > 60) safeProject = safeProject.substring(0, 60);
        return "质量问题汇总_" + safeProject + "_" + job.getStartDate() + "至" + job.getEndDate() + ".xlsx";
    }

    private int countStatus(Collection<QualityIssue> issues, String status) {
        return (int) issues.stream().filter(issue -> status.equals(issue.getStatus())).count();
    }

    private int countSeverity(Collection<QualityIssue> issues, String severity) {
        return (int) issues.stream().filter(issue -> severity.equals(issue.getSeverity())).count();
    }

    private int countOverdue(Collection<QualityIssue> issues) {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        return (int) issues.stream().filter(issue -> List.of(
                        QualityIssueService.STATUS_PENDING, QualityIssueService.STATUS_RECHECK).contains(issue.getStatus())
                && issue.getDeadline() != null && issue.getDeadline().isBefore(today)).count();
    }

    private long overdueDays(QualityIssue issue) {
        if (issue.getDeadline() == null || !List.of(
                QualityIssueService.STATUS_PENDING, QualityIssueService.STATUS_RECHECK).contains(issue.getStatus())) return 0;
        return Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(issue.getDeadline(), LocalDate.now(BUSINESS_ZONE)));
    }

    private List<Long> splitIds(String value) {
        if (!StringUtils.hasText(value)) return List.of();
        return Arrays.stream(value.split(","))
                .map(String::trim).filter(StringUtils::hasText)
                .map(id -> {
                    try {
                        return Long.valueOf(id);
                    } catch (NumberFormatException exception) {
                        throw new BusinessException("质量问题整改照片ID格式无效");
                    }
                })
                .filter(id -> id > 0).distinct().toList();
    }

    private long fileSize(FileResource file) {
        return Math.max(0L, Objects.requireNonNullElse(file.getFileSize(), 0L));
    }

    private String extension(FileResource file) {
        String extension = StringUtils.hasText(file.getFileExtension()) ? file.getFileExtension() : fileName(file);
        int dot = extension.lastIndexOf('.');
        return (dot >= 0 ? extension.substring(dot + 1) : extension).toLowerCase(Locale.ROOT);
    }

    private String fileName(FileResource file) {
        if (StringUtils.hasText(file.getOriginalFileName())) return file.getOriginalFileName();
        return Objects.requireNonNullElse(file.getFileName(), "文件" + file.getId());
    }

    private String displayProjectName(ProjectInfo project) {
        return StringUtils.hasText(project.getProjectName()) ? project.getProjectName()
                : Objects.requireNonNullElse(project.getShortName(), "-");
    }

    private String displayName(SysUser user) {
        return StringUtils.hasText(user.getRealName()) ? user.getRealName() : user.getUsername();
    }

    private String safeErrorMessage(Exception exception) {
        String message = exception instanceof BusinessException && StringUtils.hasText(exception.getMessage())
                ? exception.getMessage() : "质量报表生成失败，请稍后重试";
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String text(Object value) {
        return value == null || !StringUtils.hasText(String.valueOf(value)) ? "-" : String.valueOf(value);
    }

    private String formatTime(LocalDateTime value) {
        return value == null ? "-" : value.toString().replace('T', ' ').substring(0, 16);
    }

    private String severityLabel(String value) {
        return Map.of("NORMAL", "一般", "WARNING", "重要", "DANGER", "严重")
                .getOrDefault(Objects.requireNonNullElse(value, ""), text(value));
    }

    private String sourceLabel(String value) {
        return Map.of("ALL", "全部问题", "WEEKLY", "周检问题", "HISTORICAL", "历史独立问题")
                .getOrDefault(Objects.requireNonNullElse(value, ""), text(value));
    }

    private String statusLabel(String value) {
        return Map.of("ALL", "全部状态", "PENDING", "待整改", "OVERDUE", "已逾期",
                        "RECHECK", "待复查", "CLOSED", "已关闭", "VOIDED", "已作废")
                .getOrDefault(Objects.requireNonNullElse(value, ""), text(value));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(BUSINESS_ZONE);
    }

    private void requireSingle(int rows, String action) {
        if (rows != 1) throw BusinessException.of(409, action + "未生效，请重试");
    }

    private void cell(Row row, int column, Object value, CellStyle style) {
        Cell cell = row.createCell(column);
        if (value instanceof Number number) cell.setCellValue(number.doubleValue());
        else cell.setCellValue(Objects.requireNonNullElse(value, "").toString());
        cell.setCellStyle(style);
    }

    private void writeRow(Row row, CellStyle style, List<?> values) {
        for (int index = 0; index < values.size(); index++) cell(row, index, values.get(index), style);
    }

    public record Download(String fileName, Resource resource) {
    }

    private record PicturePayload(byte[] bytes, int pictureType) {
    }

    private record IssuePhotos(List<FileResource> before, List<FileResource> after) {
        private static final IssuePhotos EMPTY = new IssuePhotos(List.of(), List.of());
    }

    private record PhotoPlan(Map<Long, IssuePhotos> byIssue, int photoCount, long photoBytes) {
    }

    private record WorkbookStyles(CellStyle title, CellStyle header, CellStyle label,
                                  CellStyle body, CellStyle summary, CellStyle empty) {
        private WorkbookStyles(Workbook workbook) {
            this(titleStyle(workbook), headerStyle(workbook), labelStyle(workbook), bodyStyle(workbook),
                    summaryStyle(workbook), emptyStyle(workbook));
        }

        private static CellStyle titleStyle(Workbook workbook) {
            CellStyle style = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            font.setFontHeightInPoints((short) 18);
            style.setFont(font);
            style.setAlignment(HorizontalAlignment.CENTER);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            return style;
        }

        private static CellStyle headerStyle(Workbook workbook) {
            CellStyle style = bordered(workbook);
            Font font = workbook.createFont();
            font.setBold(true);
            font.setColor(IndexedColors.WHITE.getIndex());
            style.setFont(font);
            style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            style.setAlignment(HorizontalAlignment.CENTER);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            style.setWrapText(true);
            return style;
        }

        private static CellStyle labelStyle(Workbook workbook) {
            CellStyle style = bordered(workbook);
            Font font = workbook.createFont();
            font.setBold(true);
            style.setFont(font);
            style.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            return style;
        }

        private static CellStyle bodyStyle(Workbook workbook) {
            CellStyle style = bordered(workbook);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            style.setWrapText(true);
            return style;
        }

        private static CellStyle summaryStyle(Workbook workbook) {
            CellStyle style = bodyStyle(workbook);
            Font font = workbook.createFont();
            font.setBold(true);
            style.setFont(font);
            style.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            return style;
        }

        private static CellStyle emptyStyle(Workbook workbook) {
            CellStyle style = bodyStyle(workbook);
            style.setAlignment(HorizontalAlignment.CENTER);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            Font font = workbook.createFont();
            font.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            style.setFont(font);
            return style;
        }

        private static CellStyle bordered(Workbook workbook) {
            CellStyle style = workbook.createCellStyle();
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            return style;
        }
    }
}
