package com.example.siteplatform.inspection.general.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.file.constant.FileStatus;
import com.example.siteplatform.file.entity.FileResource;
import com.example.siteplatform.file.mapper.FileResourceMapper;
import com.example.siteplatform.file.storage.FileStorageManager;
import com.example.siteplatform.file.storage.StoredFile;
import com.example.siteplatform.inspection.general.dto.GeneralInspectionExportRequest;
import com.example.siteplatform.inspection.general.entity.*;
import com.example.siteplatform.inspection.general.mapper.*;
import com.example.siteplatform.notification.service.UserNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.util.WorkbookUtil;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeneralInspectionExportService {

    private final GeneralInspectionPermissionService permissionService;
    private final GeneralInspectionExportJobMapper exportJobMapper;
    private final GeneralInspectionTaskMapper taskMapper;
    private final GeneralInspectionTaskItemMapper taskItemMapper;
    private final GeneralInspectionRectificationMapper rectificationMapper;
    private final GeneralInspectionTemplateVersionMapper templateVersionMapper;
    private final FileResourceMapper fileMapper;
    private final FileStorageManager storageManager;
    private final UserNotificationService notificationService;
    private final TransactionTemplate transactionTemplate;

    public GeneralInspectionExportJob create(GeneralInspectionExportRequest request, SysUser currentUser) {
        permissionService.requireExport(request.getProjectId(), currentUser);
        validatePeriod(request.getStartDate(), request.getEndDate());
        long count = taskMapper.selectCount(new LambdaQueryWrapper<GeneralInspectionTask>()
                .eq(GeneralInspectionTask::getProjectId, request.getProjectId())
                .between(GeneralInspectionTask::getOccurrenceDate, request.getStartDate(), request.getEndDate())
                .ne(GeneralInspectionTask::getStatus, "CANCELLED"));
        if (count > 500) throw new BusinessException("单次导出最多包含500个任务，请缩小日期范围");
        GeneralInspectionExportJob job = new GeneralInspectionExportJob();
        job.setProjectId(request.getProjectId());
        job.setRequestedById(currentUser.getId());
        job.setRequestedByName(displayName(currentUser));
        job.setStartDate(request.getStartDate());
        job.setEndDate(request.getEndDate());
        job.setStatus("PENDING");
        job.setProgress(0);
        if (exportJobMapper.insert(job) != 1) throw conflict("导出任务创建未生效");
        return job;
    }

    public List<GeneralInspectionExportJob> list(Long projectId, SysUser currentUser) {
        permissionService.requireExport(projectId, currentUser);
        LambdaQueryWrapper<GeneralInspectionExportJob> query = new LambdaQueryWrapper<GeneralInspectionExportJob>()
                .eq(GeneralInspectionExportJob::getProjectId, projectId)
                .orderByDesc(GeneralInspectionExportJob::getCreateTime);
        if (!permissionService.isPlatformAdmin(currentUser)) {
            query.eq(GeneralInspectionExportJob::getRequestedById, currentUser.getId());
        }
        return exportJobMapper.selectList(query.last("LIMIT 100"));
    }

    public Download download(Long id, SysUser currentUser) {
        GeneralInspectionExportJob job = requireAuthorizedJob(id, currentUser);
        if (!"SUCCEEDED".equals(job.getStatus()) || job.getFileResourceId() == null) {
            throw conflict("导出文件尚未生成或已经过期");
        }
        if (job.getExpiresTime() != null && LocalDateTime.now().isAfter(job.getExpiresTime())) {
            throw BusinessException.notFound("导出文件已经过期");
        }
        FileResource file = fileMapper.selectById(job.getFileResourceId());
        if (file == null || Integer.valueOf(1).equals(file.getDeleted())) throw BusinessException.notFound("导出文件不存在");
        return new Download(file.getOriginalFileName(), storageManager.load(file));
    }

    public void processPendingJobs() {
        List<GeneralInspectionExportJob> pending = exportJobMapper.selectList(
                new LambdaQueryWrapper<GeneralInspectionExportJob>()
                        .eq(GeneralInspectionExportJob::getStatus, "PENDING")
                        .orderByAsc(GeneralInspectionExportJob::getCreateTime).last("LIMIT 2"));
        for (GeneralInspectionExportJob candidate : pending) {
            Boolean claimed = transactionTemplate.execute(status -> {
                GeneralInspectionExportJob locked = exportJobMapper.selectByIdForUpdate(candidate.getId());
                if (locked == null || !"PENDING".equals(locked.getStatus())) return false;
                locked.setStatus("RUNNING");
                locked.setProgress(5);
                if (exportJobMapper.updateById(locked) != 1) throw conflict("导出任务领取未生效");
                return true;
            });
            if (!Boolean.TRUE.equals(claimed)) continue;
            try {
                process(candidate.getId());
            } catch (RuntimeException ex) {
                log.error("通用巡检导出失败，jobId={}", candidate.getId(), ex);
                transactionTemplate.executeWithoutResult(status -> markFailed(candidate.getId(), ex.getMessage()));
            }
        }
    }

    public void expireFiles() {
        List<GeneralInspectionExportJob> expired = exportJobMapper.selectList(
                new LambdaQueryWrapper<GeneralInspectionExportJob>()
                        .eq(GeneralInspectionExportJob::getStatus, "SUCCEEDED")
                        .lt(GeneralInspectionExportJob::getExpiresTime, LocalDateTime.now())
                        .last("LIMIT 100"));
        for (GeneralInspectionExportJob job : expired) {
            try {
                FileResource file = job.getFileResourceId() == null ? null : fileMapper.selectById(job.getFileResourceId());
                transactionTemplate.executeWithoutResult(status -> {
                    GeneralInspectionExportJob locked = exportJobMapper.selectByIdForUpdate(job.getId());
                    if (locked == null || !"SUCCEEDED".equals(locked.getStatus())) return;
                    locked.setStatus("EXPIRED");
                    locked.setProgress(100);
                    if (exportJobMapper.updateById(locked) != 1) throw conflict("导出任务过期状态更新未生效");
                    if (file != null) {
                        file.setDeleted(1);
                        // 先提交元数据，再由统一待删除文件清理器处理物理对象；失败会保留
                        // DELETE_FAILED 元数据，不能在事务提交前删除导出文件。
                        file.setStatus("PENDING_DELETE");
                        if (fileMapper.updateById(file) != 1) throw conflict("导出文件过期状态更新未生效");
                    }
                });
            } catch (RuntimeException ex) {
                log.warn("通用巡检导出过期清理失败，jobId={}", job.getId(), ex);
            }
        }
    }

    private void process(Long jobId) {
        GeneralInspectionExportJob job = exportJobMapper.selectById(jobId);
        if (job == null) return;
        List<GeneralInspectionTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<GeneralInspectionTask>()
                .eq(GeneralInspectionTask::getProjectId, job.getProjectId())
                .between(GeneralInspectionTask::getOccurrenceDate, job.getStartDate(), job.getEndDate())
                .ne(GeneralInspectionTask::getStatus, "CANCELLED")
                .orderByAsc(GeneralInspectionTask::getOccurrenceDate)
                .orderByAsc(GeneralInspectionTask::getStartTime));
        if (tasks.size() > 500) throw new BusinessException("导出执行时任务数量已超过500条，请重新选择范围");
        byte[] workbook = buildWorkbook(tasks);
        String filename = "通用巡检_" + job.getStartDate() + "_" + job.getEndDate() + ".xlsx";
        String storageKey = "general-inspection/exports/" + job.getProjectId() + "/" + job.getId() + "/" + UUID.randomUUID() + ".xlsx";
        MultipartFile generated = new GeneratedMultipartFile(filename,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", workbook);
        StoredFile stored = storageManager.store(storageKey, generated);
        try {
            transactionTemplate.executeWithoutResult(status -> completeJob(jobId, job, stored));
        } catch (RuntimeException ex) {
            storageManager.deleteQuietly(stored.provider(), stored.storageKey());
            throw ex;
        }
    }

    private void completeJob(Long jobId, GeneralInspectionExportJob original, StoredFile stored) {
        GeneralInspectionExportJob job = exportJobMapper.selectByIdForUpdate(jobId);
        if (job == null || !"RUNNING".equals(job.getStatus())) throw conflict("导出任务状态已变化");
        FileResource file = new FileResource();
        file.setProjectId(job.getProjectId());
        file.setFileName(stored.originalFileName());
        file.setFileType("xlsx");
        file.setFilePath(stored.storageKey());
        file.setFileSize(stored.size());
        file.setBusinessType("INSPECTION_CUSTOM_EXPORT");
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
        file.setCreateTime(LocalDateTime.now());
        file.setUpdateTime(LocalDateTime.now());
        if (fileMapper.insert(file) != 1) throw conflict("导出文件元数据新增未生效");
        job.setFileResourceId(file.getId());
        job.setStatus("SUCCEEDED");
        job.setProgress(100);
        job.setExpiresTime(LocalDateTime.now().plusDays(7));
        job.setErrorMessage(null);
        if (exportJobMapper.updateById(job) != 1) throw conflict("导出任务完成状态更新未生效");
        notificationService.notify(job.getRequestedById(), job.getProjectId(), "GENERAL_INSPECTION_EXPORT",
                job.getId(), "EXPORT_SUCCEEDED", "通用巡检导出已完成",
                job.getStartDate() + " 至 " + job.getEndDate() + "，文件保留7天",
                "general-export:" + job.getId(), "GENERAL_INSPECTION_EXPORT",
                "{\"exportJobId\":" + job.getId() + "}");
    }

    private byte[] buildWorkbook(List<GeneralInspectionTask> tasks) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            CellStyle header = headerStyle(workbook);
            Sheet taskSheet = workbook.createSheet("任务");
            writeHeader(taskSheet, header, List.of("日期", "时段", "计划", "模板", "点位编码", "点位名称", "位置",
                    "主巡检人", "开始时间", "截止时间", "状态", "按时", "异常数", "总备注", "公开备注", "整体照片"));
            Drawing<?> taskDrawing = taskSheet.createDrawingPatriarch();
            int taskRow = 1;
            for (GeneralInspectionTask task : tasks) {
                Row row = taskSheet.createRow(taskRow);
                values(row, List.of(text(task.getOccurrenceDate()), text(task.getSlotName()), text(task.getPlanName()),
                        text(task.getTemplateName()), text(task.getPointCode()), text(task.getPointName()), text(task.getLocationDesc()),
                        text(task.getAssigneeName()), text(task.getStartTime()), text(task.getDueTime()), displayStatus(task),
                        task.getSubmittedTime() == null ? "" : Integer.valueOf(1).equals(task.getOnTime()) ? "是" : "否",
                        String.valueOf(task.getAbnormalCount() == null ? 0 : task.getAbnormalCount()), text(task.getRemark()), text(task.getPublicRemark()), ""));
                Long overallPhoto = firstId(task.getOverallPhotoFileIds());
                if (overallPhoto != null && addThumbnail(workbook, taskDrawing, taskSheet, overallPhoto, taskRow, 15)) {
                    row.setHeightInPoints(72);
                }
                taskRow++;
            }
            autosize(taskSheet, 15);
            taskSheet.setColumnWidth(15, 22 * 256);

            writeVersionSheets(workbook, header, tasks);

            Sheet itemSheet = workbook.createSheet("检查项明细");
            writeHeader(itemSheet, header, List.of("日期", "时段", "点位", "模板", "检查项", "结果", "说明", "规范依据", "照片缩略图"));
            Drawing<?> drawing = itemSheet.createDrawingPatriarch();
            int itemRow = 1;
            for (GeneralInspectionTask task : tasks) {
                List<GeneralInspectionTaskItem> items = taskItemMapper.selectList(new LambdaQueryWrapper<GeneralInspectionTaskItem>()
                        .eq(GeneralInspectionTaskItem::getTaskId, task.getId()).orderByAsc(GeneralInspectionTaskItem::getSortOrder));
                for (GeneralInspectionTaskItem item : items) {
                    Row row = itemSheet.createRow(itemRow);
                    values(row, List.of(text(task.getOccurrenceDate()), text(task.getSlotName()), text(task.getPointName()),
                            text(task.getTemplateName()), text(item.getItemName()), resultLabel(item.getResult()),
                            text(item.getDescription()), text(item.getStandardReference()), ""));
                    Long firstPhoto = firstId(item.getPhotoFileIds());
                    if (firstPhoto != null && addThumbnail(workbook, drawing, itemSheet, firstPhoto, itemRow, 8)) {
                        row.setHeightInPoints(72);
                    }
                    itemRow++;
                }
            }
            autosize(itemSheet, 8);
            itemSheet.setColumnWidth(8, 22 * 256);

            Sheet rectSheet = workbook.createSheet("整改明细");
            writeHeader(rectSheet, header, List.of("点位", "检查项", "问题", "整改要求", "整改人", "期限", "状态", "整改说明", "复查意见", "关闭时间", "整改照片"));
            Drawing<?> rectDrawing = rectSheet.createDrawingPatriarch();
            Set<Long> taskIds = tasks.stream().map(GeneralInspectionTask::getId).collect(Collectors.toSet());
            List<GeneralInspectionRectification> rectifications = taskIds.isEmpty() ? List.of()
                    : rectificationMapper.selectList(new LambdaQueryWrapper<GeneralInspectionRectification>()
                    .in(GeneralInspectionRectification::getTaskId, taskIds).orderByAsc(GeneralInspectionRectification::getId));
            int rectRow = 1;
            for (GeneralInspectionRectification rectification : rectifications) {
                Row row = rectSheet.createRow(rectRow);
                values(row, List.of(text(rectification.getPointName()), text(rectification.getItemName()), text(rectification.getProblemDesc()),
                        text(rectification.getRequirement()), text(rectification.getAssigneeName()), text(rectification.getDeadline()),
                        text(rectification.getStatus()), text(rectification.getFeedback()), text(rectification.getReviewComment()), text(rectification.getCloseTime()), ""));
                Long rectificationPhoto = firstId(rectification.getRectificationPhotoFileIds());
                if (rectificationPhoto != null && addThumbnail(workbook, rectDrawing, rectSheet, rectificationPhoto, rectRow, 10)) {
                    row.setHeightInPoints(72);
                }
                rectRow++;
            }
            autosize(rectSheet, 10);
            rectSheet.setColumnWidth(10, 22 * 256);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new BusinessException("巡检导出文件生成失败");
        }
    }

    private void writeVersionSheets(Workbook workbook, CellStyle header, List<GeneralInspectionTask> tasks) {
        Map<Long, List<GeneralInspectionTask>> grouped = tasks.stream().collect(Collectors.groupingBy(
                GeneralInspectionTask::getTemplateVersionId, LinkedHashMap::new, Collectors.toList()));
        int sheetIndex = 1;
        for (Map.Entry<Long, List<GeneralInspectionTask>> entry : grouped.entrySet()) {
            GeneralInspectionTemplateVersion version = templateVersionMapper.selectById(entry.getKey());
            String versionLabel = version == null ? "历史" : "V" + version.getVersionNo();
            String sheetName = WorkbookUtil.createSafeSheetName("检查表" + sheetIndex + "_" + versionLabel);
            Sheet sheet = workbook.createSheet(sheetName);
            GeneralInspectionTask firstTask = entry.getValue().get(0);
            List<GeneralInspectionTaskItem> columns = taskItemMapper.selectList(
                    new LambdaQueryWrapper<GeneralInspectionTaskItem>()
                            .eq(GeneralInspectionTaskItem::getTaskId, firstTask.getId())
                            .orderByAsc(GeneralInspectionTaskItem::getSortOrder));
            List<String> headings = new ArrayList<>(List.of("日期", "时段", "点位", "检查人", "状态"));
            headings.addAll(columns.stream().map(GeneralInspectionTaskItem::getItemName).toList());
            headings.add("公开备注");
            writeHeader(sheet, header, headings);
            int rowIndex = 1;
            for (GeneralInspectionTask task : entry.getValue()) {
                Map<String, String> results = taskItemMapper.selectList(
                                new LambdaQueryWrapper<GeneralInspectionTaskItem>()
                                        .eq(GeneralInspectionTaskItem::getTaskId, task.getId()))
                        .stream().collect(Collectors.toMap(GeneralInspectionTaskItem::getItemKey,
                                item -> resultLabel(item.getResult()), (a, b) -> b));
                List<String> rowValues = new ArrayList<>(List.of(text(task.getOccurrenceDate()), text(task.getSlotName()),
                        text(task.getPointCode()) + " " + text(task.getPointName()), text(task.getSubmittedByName()), displayStatus(task)));
                rowValues.addAll(columns.stream().map(item -> results.getOrDefault(item.getItemKey(), "未检")).toList());
                rowValues.add(text(task.getPublicRemark()));
                values(sheet.createRow(rowIndex++), rowValues);
            }
            autosize(sheet, Math.min(headings.size(), 55));
            sheetIndex++;
        }
    }

    private boolean addThumbnail(Workbook workbook, Drawing<?> drawing, Sheet sheet, Long fileId, int row, int column) {
        try {
            FileResource file = fileMapper.selectById(fileId);
            if (file == null || Integer.valueOf(1).equals(file.getDeleted())) return false;
            Resource resource = storageManager.load(file);
            try (InputStream input = resource.getInputStream()) {
                BufferedImage image = ImageIO.read(input);
                if (image == null) return false;
                BufferedImage thumbnail = scale(image, 200, 140);
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
            }
        } catch (Exception ex) {
            log.debug("跳过无法生成缩略图的巡检附件，fileId={}", fileId, ex);
            return false;
        }
    }

    private BufferedImage scale(BufferedImage source, int maxWidth, int maxHeight) {
        double ratio = Math.min((double) maxWidth / source.getWidth(), (double) maxHeight / source.getHeight());
        ratio = Math.min(1D, ratio);
        int width = Math.max(1, (int) (source.getWidth() * ratio));
        int height = Math.max(1, (int) (source.getHeight() * ratio));
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        graphics.setColor(java.awt.Color.WHITE);
        graphics.fillRect(0, 0, width, height);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(source, 0, 0, width, height, null);
        graphics.dispose();
        return target;
    }

    private GeneralInspectionExportJob requireAuthorizedJob(Long id, SysUser user) {
        GeneralInspectionExportJob job = exportJobMapper.selectById(id);
        if (job == null) throw BusinessException.notFound("导出任务不存在");
        permissionService.requireExport(job.getProjectId(), user);
        if (!permissionService.isPlatformAdmin(user) && !Objects.equals(job.getRequestedById(), user.getId())) {
            throw BusinessException.forbidden("只能下载本人创建的导出文件");
        }
        return job;
    }

    private void markFailed(Long id, String error) {
        GeneralInspectionExportJob job = exportJobMapper.selectByIdForUpdate(id);
        if (job == null || !"RUNNING".equals(job.getStatus())) return;
        job.setStatus("FAILED");
        job.setProgress(100);
        job.setErrorMessage(trim(error, 1000));
        if (exportJobMapper.updateById(job) != 1) throw conflict("导出失败状态更新未生效");
    }

    private void validatePeriod(LocalDate start, LocalDate end) {
        if (start == null || end == null || end.isBefore(start)) throw new BusinessException("导出日期范围无效");
        if (ChronoUnit.DAYS.between(start, end) + 1 > 31) throw new BusinessException("单次导出日期范围不能超过31天");
    }

    private CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        org.apache.poi.ss.usermodel.Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private void writeHeader(Sheet sheet, CellStyle style, List<String> titles) {
        Row row = sheet.createRow(0);
        for (int i = 0; i < titles.size(); i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(titles.get(i));
            cell.setCellStyle(style);
        }
        sheet.createFreezePane(0, 1);
    }

    private void values(Row row, List<String> values) {
        for (int i = 0; i < values.size(); i++) row.createCell(i).setCellValue(values.get(i));
    }

    private void autosize(Sheet sheet, int count) {
        for (int i = 0; i < count; i++) {
            sheet.autoSizeColumn(i);
            sheet.setColumnWidth(i, Math.min(sheet.getColumnWidth(i) + 512, 80 * 256));
        }
    }

    private Long firstId(String csv) {
        if (!StringUtils.hasText(csv)) return null;
        try {
            return Long.valueOf(csv.split(",", 2)[0].trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String displayStatus(GeneralInspectionTask task) {
        if (task.getSubmittedTime() == null && LocalDateTime.now().isAfter(task.getDueTime())) return "逾期未检";
        if (task.getSubmittedTime() != null && Integer.valueOf(0).equals(task.getOnTime())) return "逾期补检";
        return task.getStatus();
    }

    private String resultLabel(String result) {
        if (result == null) return "未检";
        return switch (result) {
            case "NORMAL" -> "正常";
            case "ABNORMAL" -> "异常";
            case "NA" -> "不适用";
            default -> result;
        };
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String displayName(SysUser user) {
        return StringUtils.hasText(user.getRealName()) ? user.getRealName() : user.getUsername();
    }

    private String trim(String value, int max) {
        if (!StringUtils.hasText(value)) return null;
        String normalized = value.trim();
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }

    private BusinessException conflict(String message) {
        return BusinessException.of(409, message);
    }

    public record Download(String filename, Resource resource) {
    }

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
        @Override public void transferTo(File dest) throws IOException { try (OutputStream output = new FileOutputStream(dest)) { output.write(bytes); } }
    }
}
