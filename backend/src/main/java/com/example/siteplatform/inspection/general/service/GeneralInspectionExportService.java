package com.example.siteplatform.inspection.general.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.file.constant.FileStatus;
import com.example.siteplatform.file.entity.FileResource;
import com.example.siteplatform.file.mapper.FileResourceMapper;
import com.example.siteplatform.file.storage.FileStorageManager;
import com.example.siteplatform.file.storage.StoredFile;
import com.example.siteplatform.inspection.general.dto.GeneralInspectionExportRequest;
import com.example.siteplatform.inspection.general.entity.*;
import com.example.siteplatform.inspection.general.mapper.*;
import com.example.siteplatform.inspection.general.vo.EdgeInspectionExportJobVO;
import com.example.siteplatform.notification.service.UserNotificationService;
import com.example.siteplatform.project.entity.ProjectInfo;
import com.example.siteplatform.project.mapper.ProjectInfoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.WorkbookUtil;
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
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeneralInspectionExportService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final String EXPORT_TYPE = "EDGE";
    private static final String EXPORT_BUSINESS_TYPE = "EDGE_INSPECTION_EXPORT";
    private static final String JOB_PENDING = "PENDING";
    private static final String JOB_RUNNING = "RUNNING";
    private static final String JOB_SUCCEEDED = "SUCCEEDED";
    private static final String JOB_FAILED = "FAILED";
    private static final String JOB_EXPIRED = "EXPIRED";
    private static final String XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final GeneralInspectionPermissionService permissionService;
    private final SysUserMapper userMapper;
    private final GeneralInspectionExportJobMapper exportJobMapper;
    private final GeneralInspectionExportJobTaskMapper exportJobTaskMapper;
    private final GeneralInspectionPointMapper pointMapper;
    private final GeneralInspectionTaskMapper taskMapper;
    private final GeneralInspectionTaskItemMapper taskItemMapper;
    private final GeneralInspectionRectificationMapper rectificationMapper;
    private final ProjectInfoMapper projectInfoMapper;
    private final FileResourceMapper fileMapper;
    private final FileStorageManager storageManager;
    private final UserNotificationService notificationService;
    private final TransactionTemplate transactionTemplate;

    @Value("${inspection.edge-export.max-tasks:500}") private int maxTasks;
    @Value("${inspection.edge-export.max-photos:800}") private int maxPhotos;
    @Value("${inspection.edge-export.max-photo-bytes:125829120}") private long maxPhotoBytes;

    @Transactional
    public EdgeInspectionExportJobVO create(GeneralInspectionExportRequest request, SysUser currentUser) {
        permissionService.requireExport(request.getProjectId(), currentUser);
        validatePeriod(request.getStartDate(), request.getEndDate());
        Set<Long> pointIds = normalizePointIds(request.getProjectId(), request.getPointIds());
        if (userMapper.selectByIdForUpdate(currentUser.getId()) == null) {
            throw BusinessException.unauthorized("当前账号不存在或已失效");
        }
        long activeJobs = exportJobMapper.selectCount(new LambdaQueryWrapper<GeneralInspectionExportJob>()
                .eq(GeneralInspectionExportJob::getExportType, EXPORT_TYPE)
                .eq(GeneralInspectionExportJob::getRequestedById, currentUser.getId())
                .in(GeneralInspectionExportJob::getStatus, List.of(JOB_PENDING, JOB_RUNNING)));
        if (activeJobs >= 2) throw conflict("当前已有两个临边巡检报表正在生成，请完成后再创建");

        List<GeneralInspectionTask> tasks = selectTasks(request.getProjectId(), request.getStartDate(),
                request.getEndDate(), pointIds);
        ExportData data = loadExportData(tasks);
        requireWithinLimits(tasks.size(), data.photoRefs().size(), data.photoBytes());

        GeneralInspectionExportJob job = new GeneralInspectionExportJob();
        job.setProjectId(request.getProjectId());
        job.setRequestedById(currentUser.getId());
        job.setRequestedByName(displayName(currentUser));
        job.setStartDate(request.getStartDate());
        job.setEndDate(request.getEndDate());
        job.setExportType(EXPORT_TYPE);
        job.setStatus(JOB_PENDING);
        job.setProgress(0);
        job.setPointCount((int) tasks.stream().map(GeneralInspectionTask::getPointId).filter(Objects::nonNull).distinct().count());
        job.setTaskCount(tasks.size());
        job.setPhotoCount(data.photoRefs().size());
        job.setPhotoBytes(data.photoBytes());
        job.setCreateTime(now());
        job.setUpdateTime(now());
        requireSingle(exportJobMapper.insert(job), "临边巡检导出任务创建");
        for (int index = 0; index < tasks.size(); index++) {
            GeneralInspectionTask task = tasks.get(index);
            GeneralInspectionExportJobTask item = new GeneralInspectionExportJobTask();
            item.setProjectId(job.getProjectId());
            item.setJobId(job.getId());
            item.setTaskId(task.getId());
            item.setItemOrder(index + 1);
            item.setOccurrenceDate(task.getOccurrenceDate());
            item.setCreateTime(now());
            requireSingle(exportJobTaskMapper.insert(item), "临边巡检导出任务快照创建");
        }
        return toVO(job);
    }

    public List<EdgeInspectionExportJobVO> list(Long projectId, SysUser currentUser) {
        permissionService.requireExport(projectId, currentUser);
        LambdaQueryWrapper<GeneralInspectionExportJob> query = new LambdaQueryWrapper<GeneralInspectionExportJob>()
                .eq(GeneralInspectionExportJob::getProjectId, projectId)
                .eq(GeneralInspectionExportJob::getExportType, EXPORT_TYPE)
                .orderByDesc(GeneralInspectionExportJob::getCreateTime)
                .orderByDesc(GeneralInspectionExportJob::getId);
        if (!permissionService.isPlatformAdmin(currentUser)) {
            query.eq(GeneralInspectionExportJob::getRequestedById, currentUser.getId());
        }
        return exportJobMapper.selectList(query.last("LIMIT 50")).stream().map(this::toVO).toList();
    }

    public EdgeInspectionExportJobVO get(Long id, SysUser currentUser) {
        return toVO(requireAuthorizedJob(id, currentUser));
    }

    public Download download(Long id, SysUser currentUser) {
        GeneralInspectionExportJob job = requireAuthorizedJob(id, currentUser);
        if (!JOB_SUCCEEDED.equals(job.getStatus()) || job.getFileResourceId() == null) {
            throw conflict("导出文件尚未生成或已经过期");
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

    @Scheduled(fixedDelayString = "${inspection.edge-export.process-delay-millis:5000}")
    public void processPendingJobs() {
        GeneralInspectionExportJob claimed = transactionTemplate.execute(status -> {
            GeneralInspectionExportJob job = exportJobMapper.selectNextPendingEdgeForUpdate();
            if (job == null) return null;
            job.setStatus(JOB_RUNNING);
            job.setProgress(5);
            job.setErrorMessage(null);
            job.setUpdateTime(now());
            requireSingle(exportJobMapper.updateById(job), "临边巡检导出任务领取");
            return job;
        });
        if (claimed == null) return;
        try {
            process(claimed.getId());
        } catch (Exception exception) {
            log.error("临边巡检报表生成失败，jobId={}", claimed.getId(), exception);
            String message = safeErrorMessage(exception);
            transactionTemplate.executeWithoutResult(status -> markFailed(claimed.getId(), message));
            notifyResult(claimed, false, message);
        }
    }

    @Scheduled(cron = "${inspection.edge-export.expire-cron:0 25 2 * * ?}")
    public void expireFiles() {
        List<GeneralInspectionExportJob> expired = exportJobMapper.selectList(
                new LambdaQueryWrapper<GeneralInspectionExportJob>()
                        .eq(GeneralInspectionExportJob::getExportType, EXPORT_TYPE)
                        .eq(GeneralInspectionExportJob::getStatus, JOB_SUCCEEDED)
                        .lt(GeneralInspectionExportJob::getExpiresTime, now()).last("LIMIT 100"));
        for (GeneralInspectionExportJob candidate : expired) {
            try {
                transactionTemplate.executeWithoutResult(status -> expire(candidate.getId()));
            } catch (RuntimeException exception) {
                log.warn("临边巡检报表过期标记失败，jobId={}", candidate.getId(), exception);
            }
        }
    }

    private void process(Long jobId) {
        GeneralInspectionExportJob job = exportJobMapper.selectById(jobId);
        if (job == null || !EXPORT_TYPE.equals(job.getExportType()) || !JOB_RUNNING.equals(job.getStatus())) return;
        List<GeneralInspectionExportJobTask> snapshots = exportJobTaskMapper.selectList(
                new LambdaQueryWrapper<GeneralInspectionExportJobTask>()
                        .eq(GeneralInspectionExportJobTask::getJobId, jobId)
                        .orderByAsc(GeneralInspectionExportJobTask::getItemOrder));
        List<Long> taskIds = snapshots.stream().map(GeneralInspectionExportJobTask::getTaskId).toList();
        Map<Long, GeneralInspectionTask> taskById = taskIds.isEmpty() ? Map.of()
                : taskMapper.selectBatchIds(taskIds).stream()
                .collect(Collectors.toMap(GeneralInspectionTask::getId, Function.identity()));
        List<GeneralInspectionTask> tasks = new ArrayList<>();
        for (GeneralInspectionExportJobTask snapshot : snapshots) {
            GeneralInspectionTask task = taskById.get(snapshot.getTaskId());
            if (task == null || !Objects.equals(job.getProjectId(), task.getProjectId())) {
                throw new BusinessException("导出任务快照已变化，请重新创建报表");
            }
            tasks.add(task);
        }
        ExportData data = loadExportData(tasks);
        requireWithinLimits(tasks.size(), data.photoRefs().size(), data.photoBytes());
        ProjectInfo project = projectInfoMapper.selectById(job.getProjectId());
        String projectName = project == null ? "项目" + job.getProjectId()
                : StringUtils.hasText(project.getShortName()) ? project.getShortName() : project.getProjectName();
        byte[] workbook = buildWorkbook(projectName, job, tasks, data);
        String filename = "临边巡检记录_" + safeFilename(projectName) + "_" + job.getStartDate() + "至" + job.getEndDate() + ".xlsx";
        String storageKey = "edge-inspection/exports/" + job.getProjectId() + "/" + job.getId() + "/" + UUID.randomUUID() + ".xlsx";
        StoredFile stored = storageManager.store(storageKey, new GeneratedMultipartFile(filename, XLSX_MIME, workbook));
        try {
            transactionTemplate.executeWithoutResult(status -> completeJob(jobId, stored));
        } catch (RuntimeException exception) {
            storageManager.deleteQuietly(stored.provider(), stored.storageKey());
            throw exception;
        }
        notifyResult(job, true, null);
    }

    private void completeJob(Long jobId, StoredFile stored) {
        GeneralInspectionExportJob job = exportJobMapper.selectByIdForUpdate(jobId);
        if (job == null || !JOB_RUNNING.equals(job.getStatus())) throw conflict("导出任务状态已变化");
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
        file.setRemark("[EDGE_EXPORT] 临边巡检含图报表");
        file.setDeleted(0);
        file.setCreateTime(now());
        file.setUpdateTime(now());
        requireSingle(fileMapper.insert(file), "临边巡检导出文件元数据创建");
        job.setFileResourceId(file.getId());
        job.setStatus(JOB_SUCCEEDED);
        job.setProgress(100);
        job.setExpiresTime(now().plusDays(7));
        job.setErrorMessage(null);
        job.setUpdateTime(now());
        requireSingle(exportJobMapper.updateById(job), "临边巡检导出任务完成");
    }

    private byte[] buildWorkbook(String projectName, GeneralInspectionExportJob job,
                                 List<GeneralInspectionTask> tasks, ExportData data) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            CellStyle titleStyle = titleStyle(workbook);
            CellStyle headerStyle = headerStyle(workbook);
            CellStyle bodyStyle = bodyStyle(workbook);
            writeSummarySheet(workbook, projectName, job, tasks, titleStyle, headerStyle, bodyStyle);
            Map<Long, List<GeneralInspectionTask>> byPoint = tasks.stream().collect(Collectors.groupingBy(
                    GeneralInspectionTask::getPointId, LinkedHashMap::new, Collectors.toList()));
            int index = 1;
            for (List<GeneralInspectionTask> pointTasks : byPoint.values()) {
                writePointSheet(workbook, projectName, job, index++, pointTasks, data,
                        titleStyle, headerStyle, bodyStyle);
            }
            writePhotoSheet(workbook, data, headerStyle, bodyStyle);
            writeRectificationSheet(workbook, data, headerStyle, bodyStyle);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new BusinessException("临边巡检报表生成失败");
        }
    }

    private void writeSummarySheet(Workbook workbook, String projectName, GeneralInspectionExportJob job,
                                   List<GeneralInspectionTask> tasks, CellStyle titleStyle,
                                   CellStyle headerStyle, CellStyle bodyStyle) {
        Sheet sheet = workbook.createSheet("巡检汇总");
        Cell titleCell = sheet.createRow(0).createCell(0);
        titleCell.setCellValue(projectName + " 临边巡检汇总");
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 10));
        values(sheet.createRow(1), List.of("日期范围", job.getStartDate() + " 至 " + job.getEndDate(),
                "导出人", text(job.getRequestedByName()), "任务数", text(job.getTaskCount()),
                "照片数", text(job.getPhotoCount()), "生成时间", text(now())), bodyStyle);
        writeHeader(sheet, 3, headerStyle, List.of("点位编码", "点位名称", "点位类型", "应检", "按时完成",
                "逾期补检", "仍未检", "待巡检", "异常任务", "已闭环", "已取消"));
        Map<Long, List<GeneralInspectionTask>> grouped = tasks.stream().collect(Collectors.groupingBy(
                GeneralInspectionTask::getPointId, LinkedHashMap::new, Collectors.toList()));
        int rowIndex = 4;
        for (List<GeneralInspectionTask> pointTasks : grouped.values()) {
            GeneralInspectionTask first = pointTasks.get(0);
            long cancelled = pointTasks.stream().filter(this::isCancelled).count();
            long eligible = pointTasks.size() - cancelled;
            long onTime = pointTasks.stream().filter(this::isOnTime).count();
            long late = pointTasks.stream().filter(this::isLateCompleted).count();
            long missed = pointTasks.stream().filter(this::isMissed).count();
            long pending = pointTasks.stream().filter(this::isPending).count();
            long abnormal = pointTasks.stream().filter(task -> !isCancelled(task)
                    && task.getAbnormalCount() != null && task.getAbnormalCount() > 0).count();
            long closed = pointTasks.stream().filter(task -> "CLOSED".equals(task.getStatus())).count();
            values(sheet.createRow(rowIndex++), List.of(text(first.getPointCode()), text(first.getPointName()),
                    text(first.getPointTypeName()), text(eligible), text(onTime), text(late), text(missed),
                    text(pending), text(abnormal), text(closed), text(cancelled)), bodyStyle);
        }
        sheet.createFreezePane(0, 4);
        autosize(sheet, 11);
    }

    private void writePointSheet(Workbook workbook, String projectName, GeneralInspectionExportJob job, int pointIndex,
                                 List<GeneralInspectionTask> tasks, ExportData data,
                                 CellStyle titleStyle, CellStyle headerStyle, CellStyle bodyStyle) {
        tasks.sort(Comparator.comparing(GeneralInspectionTask::getOccurrenceDate)
                .thenComparing(GeneralInspectionTask::getStartTime, Comparator.nullsLast(Comparator.naturalOrder())));
        GeneralInspectionTask first = tasks.get(0);
        String rawName = String.format(Locale.ROOT, "%02d_%s", pointIndex,
                StringUtils.hasText(first.getPointName()) ? first.getPointName() : first.getPointCode());
        Sheet sheet = workbook.createSheet(WorkbookUtil.createSafeSheetName(rawName));
        Cell titleCell = sheet.createRow(0).createCell(0);
        titleCell.setCellValue(projectName + " 临边检查记录表");
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 11));
        values(sheet.createRow(1), List.of("点位", text(first.getPointCode()) + " " + text(first.getPointName()),
                "类型", text(first.getPointTypeName()), "位置", pointLocation(first)), bodyStyle);
        values(sheet.createRow(2), List.of("日期范围", job.getStartDate() + " 至 " + job.getEndDate(),
                "检查表", text(first.getTemplateName()), "说明", "系统固定检查表；不替代专项施工方案和验收结论"), bodyStyle);
        List<GeneralInspectionTaskItem> columns = data.itemsByTask().getOrDefault(first.getId(), List.of());
        List<String> headings = new ArrayList<>(List.of("日期", "执行时段"));
        headings.addAll(columns.stream().map(GeneralInspectionTaskItem::getItemName).toList());
        headings.addAll(List.of("检查人", "状态", "异常数", "备注", "现场照片"));
        writeHeader(sheet, 4, headerStyle, headings);
        Drawing<?> drawing = sheet.createDrawingPatriarch();
        int rowIndex = 5;
        for (GeneralInspectionTask task : tasks) {
            Map<String, String> resultByKey = data.itemsByTask().getOrDefault(task.getId(), List.of()).stream()
                    .collect(Collectors.toMap(GeneralInspectionTaskItem::getItemKey,
                            item -> resultLabel(item.getResult()), (left, right) -> right));
            List<String> rowValues = new ArrayList<>(List.of(text(task.getOccurrenceDate()), slotText(task)));
            rowValues.addAll(columns.stream().map(item -> resultByKey.getOrDefault(item.getItemKey(), "未检")).toList());
            rowValues.add(text(task.getSubmittedByName()));
            rowValues.add(displayStatus(task));
            rowValues.add(text(task.getAbnormalCount() == null ? 0 : task.getAbnormalCount()));
            rowValues.add(isCancelled(task) ? text(task.getCancelReason()) : text(task.getRemark()));
            rowValues.add("");
            Row row = sheet.createRow(rowIndex);
            values(row, rowValues, bodyStyle);
            Long photoId = firstId(task.getOverallPhotoFileIds());
            if (photoId != null) {
                int photoColumn = headings.size() - 1;
                if (addThumbnail(workbook, drawing, data.fileById().get(photoId), rowIndex, photoColumn)) {
                    row.setHeightInPoints(82);
                } else {
                    row.getCell(photoColumn).setCellValue("图片不可读取");
                }
            }
            rowIndex++;
        }
        sheet.createFreezePane(2, 5);
        autosize(sheet, headings.size());
        sheet.setColumnWidth(headings.size() - 1, 24 * 256);
    }

    private void writePhotoSheet(Workbook workbook, ExportData data, CellStyle headerStyle, CellStyle bodyStyle) {
        Sheet sheet = workbook.createSheet("照片明细");
        writeHeader(sheet, 0, headerStyle, List.of("日期", "点位", "照片类型", "检查项", "说明", "文件名", "缩略图"));
        Drawing<?> drawing = sheet.createDrawingPatriarch();
        int rowIndex = 1;
        for (PhotoRef photo : data.photoRefs()) {
            FileResource file = data.fileById().get(photo.fileId());
            Row row = sheet.createRow(rowIndex);
            values(row, List.of(text(photo.date()), text(photo.pointName()), text(photo.stage()), text(photo.itemName()),
                    text(photo.description()), file == null ? "" : text(file.getOriginalFileName()), ""), bodyStyle);
            if (addThumbnail(workbook, drawing, file, rowIndex, 6)) row.setHeightInPoints(82);
            else row.getCell(6).setCellValue("图片不可读取");
            rowIndex++;
        }
        sheet.createFreezePane(0, 1);
        autosize(sheet, 7);
        sheet.setColumnWidth(6, 24 * 256);
    }

    private void writeRectificationSheet(Workbook workbook, ExportData data,
                                          CellStyle headerStyle, CellStyle bodyStyle) {
        Sheet sheet = workbook.createSheet("整改明细");
        writeHeader(sheet, 0, headerStyle, List.of("日期", "点位", "检查项", "问题描述", "整改人", "整改期限",
                "状态", "整改反馈", "复查人", "复查结论", "关闭时间"));
        int rowIndex = 1;
        for (GeneralInspectionRectification rectification : data.rectifications()) {
            GeneralInspectionTask task = data.taskById().get(rectification.getTaskId());
            values(sheet.createRow(rowIndex++), List.of(task == null ? "" : text(task.getOccurrenceDate()),
                    text(rectification.getPointName()), text(rectification.getItemName()), text(rectification.getProblemDesc()),
                    text(rectification.getAssigneeName()), text(rectification.getDeadline()), rectificationStatus(rectification.getStatus()),
                    text(rectification.getFeedback()), text(rectification.getReviewerName()), text(rectification.getReviewComment()),
                    text(rectification.getCloseTime())), bodyStyle);
        }
        sheet.createFreezePane(0, 1);
        autosize(sheet, 11);
    }

    private ExportData loadExportData(List<GeneralInspectionTask> tasks) {
        List<Long> taskIds = tasks.stream().map(GeneralInspectionTask::getId).toList();
        List<GeneralInspectionTaskItem> items = taskIds.isEmpty() ? List.of()
                : taskItemMapper.selectList(new LambdaQueryWrapper<GeneralInspectionTaskItem>()
                .in(GeneralInspectionTaskItem::getTaskId, taskIds)
                .orderByAsc(GeneralInspectionTaskItem::getTaskId)
                .orderByAsc(GeneralInspectionTaskItem::getSortOrder));
        List<GeneralInspectionRectification> rectifications = taskIds.isEmpty() ? List.of()
                : rectificationMapper.selectList(new LambdaQueryWrapper<GeneralInspectionRectification>()
                .in(GeneralInspectionRectification::getTaskId, taskIds)
                .orderByAsc(GeneralInspectionRectification::getTaskId)
                .orderByAsc(GeneralInspectionRectification::getId));
        Map<Long, GeneralInspectionTask> taskById = tasks.stream()
                .collect(Collectors.toMap(GeneralInspectionTask::getId, Function.identity()));
        Map<Long, List<GeneralInspectionTaskItem>> itemsByTask = items.stream()
                .collect(Collectors.groupingBy(GeneralInspectionTaskItem::getTaskId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, GeneralInspectionTaskItem> itemById = items.stream()
                .collect(Collectors.toMap(GeneralInspectionTaskItem::getId, Function.identity()));
        List<PhotoRef> photoRefs = new ArrayList<>();
        for (GeneralInspectionTask task : tasks) {
            for (Long fileId : parseIds(task.getOverallPhotoFileIds())) {
                photoRefs.add(new PhotoRef(fileId, task.getOccurrenceDate(), task.getPointName(), "现场全景", null, task.getRemark()));
            }
        }
        for (GeneralInspectionTaskItem item : items) {
            GeneralInspectionTask task = taskById.get(item.getTaskId());
            if (task == null) continue;
            for (Long fileId : parseIds(item.getPhotoFileIds())) {
                photoRefs.add(new PhotoRef(fileId, task.getOccurrenceDate(), task.getPointName(), "异常证据",
                        item.getItemName(), item.getDescription()));
            }
        }
        for (GeneralInspectionRectification rectification : rectifications) {
            GeneralInspectionTask task = taskById.get(rectification.getTaskId());
            GeneralInspectionTaskItem item = itemById.get(rectification.getTaskItemId());
            for (Long fileId : parseIds(rectification.getRectificationPhotoFileIds())) {
                photoRefs.add(new PhotoRef(fileId, task == null ? null : task.getOccurrenceDate(), rectification.getPointName(),
                        "整改后照片", item == null ? rectification.getItemName() : item.getItemName(), rectification.getFeedback()));
            }
        }
        Set<Long> fileIds = photoRefs.stream().map(PhotoRef::fileId).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, FileResource> fileById = fileIds.isEmpty() ? Map.of()
                : fileMapper.selectBatchIds(fileIds).stream()
                .filter(file -> !Integer.valueOf(1).equals(file.getDeleted()))
                .collect(Collectors.toMap(FileResource::getId, Function.identity()));
        long photoBytes = photoRefs.stream().map(PhotoRef::fileId).map(fileById::get).filter(Objects::nonNull)
                .map(FileResource::getFileSize).filter(Objects::nonNull).mapToLong(Long::longValue).sum();
        return new ExportData(taskById, itemsByTask, rectifications, photoRefs, fileById, photoBytes);
    }

    private List<GeneralInspectionTask> selectTasks(Long projectId, LocalDate startDate, LocalDate endDate,
                                                    Set<Long> pointIds) {
        LambdaQueryWrapper<GeneralInspectionTask> query = new LambdaQueryWrapper<GeneralInspectionTask>()
                .eq(GeneralInspectionTask::getProjectId, projectId)
                .isNotNull(GeneralInspectionTask::getPointTypeCode)
                .between(GeneralInspectionTask::getOccurrenceDate, startDate, endDate)
                .orderByAsc(GeneralInspectionTask::getOccurrenceDate)
                .orderByAsc(GeneralInspectionTask::getPointCode)
                .orderByAsc(GeneralInspectionTask::getStartTime);
        if (!pointIds.isEmpty()) query.in(GeneralInspectionTask::getPointId, pointIds);
        return taskMapper.selectList(query);
    }

    private Set<Long> normalizePointIds(Long projectId, Collection<Long> requestedPointIds) {
        Set<Long> ids = requestedPointIds == null ? new LinkedHashSet<>() : requestedPointIds.stream()
                .filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        if (ids.isEmpty()) return ids;
        List<GeneralInspectionPoint> points = pointMapper.selectBatchIds(ids);
        boolean invalid = points.size() != ids.size() || points.stream().anyMatch(point ->
                !Objects.equals(projectId, point.getProjectId()) || Integer.valueOf(1).equals(point.getDeleted())
                        || !StringUtils.hasText(point.getPointTypeCode()));
        if (invalid) throw new BusinessException("导出点位不存在或不属于当前项目");
        return ids;
    }

    private void expire(Long jobId) {
        GeneralInspectionExportJob job = exportJobMapper.selectByIdForUpdate(jobId);
        if (job == null || !EXPORT_TYPE.equals(job.getExportType()) || !JOB_SUCCEEDED.equals(job.getStatus())) return;
        if (job.getExpiresTime() == null || !now().isAfter(job.getExpiresTime())) return;
        if (job.getFileResourceId() != null) {
            FileResource file = fileMapper.selectById(job.getFileResourceId());
            if (file != null && !Integer.valueOf(1).equals(file.getDeleted())) {
                file.setDeleted(1);
                file.setStatus("PENDING_DELETE");
                file.setUpdateTime(now());
                requireSingle(fileMapper.updateById(file), "临边巡检过期导出文件标记");
            }
        }
        job.setStatus(JOB_EXPIRED);
        job.setProgress(100);
        job.setUpdateTime(now());
        requireSingle(exportJobMapper.updateById(job), "临边巡检导出任务过期标记");
    }

    private GeneralInspectionExportJob requireAuthorizedJob(Long id, SysUser user) {
        GeneralInspectionExportJob job = exportJobMapper.selectById(id);
        if (job == null || !EXPORT_TYPE.equals(job.getExportType())) throw BusinessException.notFound("导出任务不存在");
        permissionService.requireExport(job.getProjectId(), user);
        if (!permissionService.isPlatformAdmin(user) && !Objects.equals(job.getRequestedById(), user.getId())) {
            throw BusinessException.forbidden("只能查看和下载本人创建的导出文件");
        }
        return job;
    }

    private void markFailed(Long id, String error) {
        GeneralInspectionExportJob job = exportJobMapper.selectByIdForUpdate(id);
        if (job == null || !JOB_RUNNING.equals(job.getStatus())) return;
        job.setStatus(JOB_FAILED);
        job.setProgress(100);
        job.setErrorMessage(trim(error, 1000));
        job.setUpdateTime(now());
        requireSingle(exportJobMapper.updateById(job), "临边巡检导出失败状态更新");
    }

    private void notifyResult(GeneralInspectionExportJob job, boolean success, String error) {
        notificationService.notify(job.getRequestedById(), job.getProjectId(), EXPORT_BUSINESS_TYPE, job.getId(),
                success ? "EXPORT_SUCCEEDED" : "EXPORT_FAILED",
                success ? "临边巡检报表已生成" : "临边巡检报表生成失败",
                success ? job.getStartDate() + " 至 " + job.getEndDate() + "，文件保留7天" : trim(error, 300),
                "edge-export:" + job.getId() + ":" + (success ? "success" : "failed"), null, null);
    }

    private void validatePeriod(LocalDate start, LocalDate end) {
        if (start == null || end == null || end.isBefore(start)) throw new BusinessException("导出日期范围无效");
        if (end.isAfter(LocalDate.now(BUSINESS_ZONE))) throw new BusinessException("导出结束日期不能晚于今天");
        if (ChronoUnit.DAYS.between(start, end) + 1 > 31) throw new BusinessException("单次导出日期范围不能超过31天");
    }

    private void requireWithinLimits(int taskCount, int photoCount, long photoBytes) {
        if (taskCount > maxTasks) throw payloadTooLarge("任务数量超过" + maxTasks + "条");
        if (photoCount > maxPhotos) throw payloadTooLarge("照片数量超过" + maxPhotos + "张");
        if (photoBytes > maxPhotoBytes) throw payloadTooLarge("原始照片总量超过120MB");
    }

    private boolean addThumbnail(Workbook workbook, Drawing<?> drawing, FileResource file, int row, int column) {
        if (file == null) return false;
        try (InputStream input = storageManager.load(file).getInputStream()) {
            BufferedImage image = ImageIO.read(input);
            if (image == null) return false;
            BufferedImage thumbnail = scale(image, 220, 145);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ImageIO.write(thumbnail, "jpg", bytes);
            int pictureIndex = workbook.addPicture(bytes.toByteArray(), Workbook.PICTURE_TYPE_JPEG);
            ClientAnchor anchor = workbook.getCreationHelper().createClientAnchor();
            anchor.setCol1(column);
            anchor.setCol2(column + 1);
            anchor.setRow1(row);
            anchor.setRow2(row + 1);
            drawing.createPicture(anchor, pictureIndex);
            return true;
        } catch (Exception exception) {
            log.warn("临边巡检导出跳过不可读取图片，fileId={}", file.getId());
            return false;
        }
    }

    private BufferedImage scale(BufferedImage source, int maxWidth, int maxHeight) {
        double ratio = Math.min((double) maxWidth / source.getWidth(), (double) maxHeight / source.getHeight());
        ratio = Math.min(1D, ratio);
        int width = Math.max(1, (int) Math.round(source.getWidth() * ratio));
        int height = Math.max(1, (int) Math.round(source.getHeight() * ratio));
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        graphics.setColor(java.awt.Color.WHITE);
        graphics.fillRect(0, 0, width, height);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(source, 0, 0, width, height, null);
        graphics.dispose();
        return target;
    }

    private CellStyle titleStyle(Workbook workbook) {
        CellStyle style = bodyStyle(workbook);
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 16);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle headerStyle(Workbook workbook) {
        CellStyle style = bodyStyle(workbook);
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle bodyStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setWrapText(true);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private void writeHeader(Sheet sheet, int rowIndex, CellStyle style, List<String> titles) {
        Row row = sheet.createRow(rowIndex);
        for (int index = 0; index < titles.size(); index++) {
            Cell cell = row.createCell(index);
            cell.setCellValue(titles.get(index));
            cell.setCellStyle(style);
        }
    }

    private void values(Row row, List<String> values, CellStyle style) {
        for (int index = 0; index < values.size(); index++) {
            Cell cell = row.createCell(index);
            cell.setCellValue(values.get(index));
            cell.setCellStyle(style);
        }
    }

    private void autosize(Sheet sheet, int columnCount) {
        for (int index = 0; index < columnCount; index++) {
            sheet.autoSizeColumn(index);
            sheet.setColumnWidth(index, Math.min(Math.max(sheet.getColumnWidth(index) + 512, 10 * 256), 48 * 256));
        }
    }

    private boolean isCancelled(GeneralInspectionTask task) { return "CANCELLED".equals(task.getStatus()); }
    private boolean isOnTime(GeneralInspectionTask task) {
        return !isCancelled(task) && task.getSubmittedTime() != null && Integer.valueOf(1).equals(task.getOnTime());
    }
    private boolean isLateCompleted(GeneralInspectionTask task) {
        return !isCancelled(task) && task.getSubmittedTime() != null && Integer.valueOf(0).equals(task.getOnTime());
    }
    private boolean isMissed(GeneralInspectionTask task) {
        return !isCancelled(task) && task.getSubmittedTime() == null && task.getDueTime() != null && now().isAfter(task.getDueTime());
    }
    private boolean isPending(GeneralInspectionTask task) {
        return !isCancelled(task) && task.getSubmittedTime() == null && !isMissed(task);
    }

    private String displayStatus(GeneralInspectionTask task) {
        if (isCancelled(task)) return "已取消";
        if (isMissed(task)) return "逾期未检";
        if (isPending(task)) return "待巡检";
        if ("CLOSED".equals(task.getStatus())) return "已闭环";
        if ("RECTIFICATION_PENDING".equals(task.getStatus())) return "整改中";
        return isLateCompleted(task) ? "逾期补检" : "按时完成";
    }

    private String rectificationStatus(String status) {
        if (status == null) return "";
        return switch (status) {
            case "UNASSIGNED" -> "待分派";
            case "PENDING" -> "待整改";
            case "COMPLETED" -> "待复查";
            case "REJECTED" -> "退回整改";
            case "CLOSED" -> "已闭环";
            case "VOIDED" -> "已作废";
            default -> status;
        };
    }

    private String resultLabel(String result) {
        if (result == null) return "未检";
        return switch (result) { case "NORMAL" -> "正常"; case "ABNORMAL" -> "异常"; default -> result; };
    }

    private String pointLocation(GeneralInspectionTask task) {
        return Arrays.asList(task.getBuildingName(), task.getFloorName(), task.getLocationDesc()).stream()
                .filter(StringUtils::hasText).collect(Collectors.joining(" / "));
    }

    private String slotText(GeneralInspectionTask task) {
        if (StringUtils.hasText(task.getSlotName())) return task.getSlotName();
        if (task.getStartTime() == null || task.getDueTime() == null) return "";
        return task.getStartTime().toLocalTime() + "–" + task.getDueTime().toLocalTime();
    }

    private List<Long> parseIds(String csv) {
        if (!StringUtils.hasText(csv)) return List.of();
        List<Long> ids = new ArrayList<>();
        for (String value : csv.split(",")) {
            try { ids.add(Long.valueOf(value.trim())); } catch (NumberFormatException ignored) { }
        }
        return ids;
    }

    private Long firstId(String csv) {
        List<Long> ids = parseIds(csv);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private EdgeInspectionExportJobVO toVO(GeneralInspectionExportJob job) {
        EdgeInspectionExportJobVO vo = new EdgeInspectionExportJobVO();
        BeanUtils.copyProperties(job, vo);
        vo.setDownloadable(JOB_SUCCEEDED.equals(job.getStatus()) && job.getFileResourceId() != null
                && (job.getExpiresTime() == null || !now().isAfter(job.getExpiresTime())));
        return vo;
    }

    private LocalDateTime now() { return LocalDateTime.now(BUSINESS_ZONE); }
    private String displayName(SysUser user) { return StringUtils.hasText(user.getRealName()) ? user.getRealName() : user.getUsername(); }
    private String safeFilename(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim() : "项目";
        return normalized.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
    private String safeErrorMessage(Exception exception) {
        String message = exception == null ? null : exception.getMessage();
        return StringUtils.hasText(message) ? trim(message, 1000) : "报表生成失败，请稍后重试";
    }
    private String trim(String value, int max) {
        if (!StringUtils.hasText(value)) return null;
        String normalized = value.trim();
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }
    private String text(Object value) { return value == null ? "" : String.valueOf(value); }
    private void requireSingle(int rows, String action) { if (rows != 1) throw conflict(action + "未生效，请刷新后重试"); }
    private BusinessException conflict(String message) { return BusinessException.of(409, message); }
    private BusinessException payloadTooLarge(String message) { return BusinessException.of(HttpStatus.PAYLOAD_TOO_LARGE.value(), message); }

    public record Download(String filename, Resource resource) { }
    private record PhotoRef(Long fileId, LocalDate date, String pointName, String stage, String itemName, String description) { }
    private record ExportData(Map<Long, GeneralInspectionTask> taskById,
                              Map<Long, List<GeneralInspectionTaskItem>> itemsByTask,
                              List<GeneralInspectionRectification> rectifications,
                              List<PhotoRef> photoRefs,
                              Map<Long, FileResource> fileById,
                              long photoBytes) { }

    private static final class GeneratedMultipartFile implements MultipartFile {
        private final String filename;
        private final String contentType;
        private final byte[] bytes;
        private GeneratedMultipartFile(String filename, String contentType, byte[] bytes) {
            this.filename = filename;
            this.contentType = contentType;
            this.bytes = bytes;
        }
        @Override public String getName() { return "file"; }
        @Override public String getOriginalFilename() { return filename; }
        @Override public String getContentType() { return contentType; }
        @Override public boolean isEmpty() { return bytes.length == 0; }
        @Override public long getSize() { return bytes.length; }
        @Override public byte[] getBytes() { return bytes.clone(); }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(bytes); }
        @Override public void transferTo(File destination) throws IOException {
            try (OutputStream output = new FileOutputStream(destination)) { output.write(bytes); }
        }
    }
}
