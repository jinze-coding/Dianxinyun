package com.example.siteplatform.seal.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.log.entity.OperationLog;
import com.example.siteplatform.log.mapper.OperationLogMapper;
import com.example.siteplatform.seal.entity.SealApplication;
import com.example.siteplatform.seal.entity.SealApplicationFile;
import com.example.siteplatform.seal.entity.SealApplicationItem;
import com.example.siteplatform.seal.mapper.SealApplicationItemMapper;
import com.example.siteplatform.seal.mapper.SealApplicationMapper;
import com.example.siteplatform.seal.mapper.SealApplicationFileMapper;
import com.example.siteplatform.workflow.entity.WorkflowCcRecipient;
import com.example.siteplatform.workflow.mapper.WorkflowCcRecipientMapper;
import com.example.siteplatform.system.constant.SystemPermissionCodes;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.Objects;

@Service
public class SealLedgerService {
    private static final int MAX_EXPORT_APPLICATIONS = 10_000;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private final SealApplicationMapper applicationMapper;
    private final SealApplicationItemMapper itemMapper;
    private final SealApplicationFileMapper fileMapper;
    private final WorkflowCcRecipientMapper ccMapper;
    private final OperationLogMapper operationLogMapper;
    private final ProjectPermissionService permissionService;
    private final SealApplicationService applicationService;

    public SealLedgerService(SealApplicationMapper applicationMapper,
                             SealApplicationItemMapper itemMapper,
                             SealApplicationFileMapper fileMapper,
                             WorkflowCcRecipientMapper ccMapper,
                             OperationLogMapper operationLogMapper,
                             ProjectPermissionService permissionService,
                             SealApplicationService applicationService) {
        this.applicationMapper = applicationMapper;
        this.itemMapper = itemMapper;
        this.fileMapper = fileMapper;
        this.ccMapper = ccMapper;
        this.operationLogMapper = operationLogMapper;
        this.permissionService = permissionService;
        this.applicationService = applicationService;
    }

    @Transactional
    public LedgerExport export(Long projectId, String period, LocalDate anchorDate,
                               LocalDate startDate, LocalDate endDate, String keyword, String status,
                               SysUser currentUser, HttpServletRequest request) {
        if (projectId == null) throw new BusinessException("项目不能为空");
        permissionService.checkProjectPermission(currentUser.getId(), projectId);
        permissionService.requireSystemPermission(currentUser.getId(), projectId, SystemPermissionCodes.SEAL_EXPORT);
        LedgerDateRange range = LedgerDateRange.resolve(period, anchorDate, startDate, endDate);
        if (org.springframework.util.StringUtils.hasText(status)
                && !SealApplicationService.APPROVED.equalsIgnoreCase(status.trim())) {
            throw new BusinessException("用印台账仅导出审批通过记录");
        }
        LambdaQueryWrapper<SealApplication> query = new LambdaQueryWrapper<SealApplication>()
                .eq(SealApplication::getProjectId, projectId)
                .eq(SealApplication::getStatus, SealApplicationService.APPROVED)
                .ge(SealApplication::getApprovalTime, range.startDate().atStartOfDay())
                .lt(SealApplication::getApprovalTime, range.endDate().plusDays(1).atStartOfDay());
        if (org.springframework.util.StringUtils.hasText(keyword)) {
            String value = keyword.trim();
            query.and(row -> row.like(SealApplication::getApplicationNo, value)
                    .or().like(SealApplication::getPurpose, value)
                    .or().like(SealApplication::getApplicantName, value)
                    .or().like(SealApplication::getSealName, value));
        }
        query.orderByAsc(SealApplication::getApprovalTime).orderByAsc(SealApplication::getId);
        List<SealApplication> applications = applicationMapper.selectList(query);
        if (applications.size() > MAX_EXPORT_APPLICATIONS) throw new BusinessException("导出记录超过10000条，请缩小日期范围");
        List<Long> applicationIds = applications.stream().map(SealApplication::getId).toList();
        Map<Long, List<SealApplicationItem>> itemsByApplication = groupItems(applicationIds);
        Map<Long, List<WorkflowCcRecipient>> ccByApplication = groupCc(applicationIds);
        Map<Long, List<SealApplicationFile>> filesByApplication = groupFiles(applicationIds);
        byte[] bytes = buildWorkbook(applications, range, itemsByApplication, ccByApplication, filesByApplication);
        writeExportAudit(projectId, range, applications.size(), currentUser, request);
        String fileName = "用印台账_" + range.startDate() + "_" + range.endDate() + ".xlsx";
        return new LedgerExport(fileName, bytes, range);
    }

    byte[] buildWorkbook(List<SealApplication> applications, LedgerDateRange range,
                         Map<Long, List<SealApplicationItem>> itemsByApplication,
                         Map<Long, List<WorkflowCcRecipient>> ccByApplication,
                         Map<Long, List<SealApplicationFile>> filesByApplication) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Styles styles = new Styles(workbook);
            Sheet ledger = workbook.createSheet("用印台账");
            String[] ledgerHeaders = {"序号", "申请单编号", "申请日期", "审批通过时间", "项目/部门", "公司名称",
                    "印章名称", "用印事由", "申请人", "联系方式", "审批人", "项目经理审批意见",
                    "抄送人", "资料项数", "总份数", "盖章件上传状态", "资料归档结果"};
            header(ledger, ledgerHeaders, styles.header);
            int ledgerRowIndex = 1;
            int sequence = 1;
            for (SealApplication application : applications) {
                List<SealApplicationItem> items = itemsByApplication.getOrDefault(application.getId(), List.of());
                List<WorkflowCcRecipient> recipients = ccByApplication.getOrDefault(application.getId(), List.of());
                List<SealApplicationFile> files = filesByApplication.getOrDefault(application.getId(), List.of());
                Row row = ledger.createRow(ledgerRowIndex++);
                row.setHeightInPoints(34);
                int column = 0;
                numeric(row, column++, sequence++, styles.body);
                text(row, column++, application.getApplicationNo(), styles.body);
                text(row, column++, format(application.getApplicationDate()), styles.body);
                text(row, column++, format(application.getApprovalTime()), styles.body);
                text(row, column++, application.getDepartmentName(), styles.body);
                text(row, column++, application.getCompanyName(), styles.body);
                text(row, column++, application.getSealName(), styles.body);
                text(row, column++, application.getPurpose(), styles.wrap);
                text(row, column++, application.getApplicantName(), styles.body);
                text(row, column++, application.getApplicantPhone(), styles.body);
                text(row, column++, application.getApproverName(), styles.body);
                text(row, column++, application.getApprovalOpinion(), styles.wrap);
                text(row, column++, recipients.stream().map(WorkflowCcRecipient::getUserName)
                        .filter(Objects::nonNull).distinct().collect(Collectors.joining("、")), styles.wrap);
                numeric(row, column++, items.size(), styles.body);
                numeric(row, column++, items.stream().map(SealApplicationItem::getCopies)
                        .filter(Objects::nonNull).mapToInt(Integer::intValue).sum(), styles.body);
                List<SealApplicationFile> stamped = files.stream()
                        .filter(file -> "STAMPED_RESULT".equals(file.getFileRole())).toList();
                text(row, column++, stamped.isEmpty() ? "未上传" : "已上传 " + stamped.size() + " 份", styles.body);
                long archived = stamped.stream().filter(file -> file.getArchivedDocumentId() != null).count();
                String archiveText = stamped.isEmpty() ? "无盖章件"
                        : (archived == 0 ? "未归档" : "已归档 " + archived + "/" + stamped.size() + "；"
                        + stamped.stream().filter(file -> file.getArchivedDocumentId() != null)
                        .map(file -> "资料" + file.getArchivedDocumentId() + "-版本" + file.getArchivedVersionId())
                        .collect(Collectors.joining("、")));
                text(row, column, archiveText, styles.wrap);
            }
            configure(ledger, ledgerHeaders.length, ledgerRowIndex,
                    new int[]{8, 25, 14, 20, 24, 32, 24, 50, 14, 18, 14, 50, 28, 12, 12, 18, 50}, (short) 2);

            Sheet detail = workbook.createSheet("文件明细");
            String[] detailHeaders = {"序号", "申请单编号", "审批通过时间", "项目/部门", "印章名称",
                    "用印文件名称", "份数", "盖章件数量", "归档关联"};
            header(detail, detailHeaders, styles.header);
            int detailRowIndex = 1;
            int detailSequence = 1;
            for (SealApplication application : applications) {
                List<SealApplicationFile> stampedFiles = filesByApplication.getOrDefault(application.getId(), List.of())
                        .stream().filter(file -> "STAMPED_RESULT".equals(file.getFileRole())).toList();
                String archiveLinks = stampedFiles.stream().filter(file -> file.getArchivedDocumentId() != null)
                        .map(file -> "资料" + file.getArchivedDocumentId() + "-版本" + file.getArchivedVersionId())
                        .collect(Collectors.joining("、"));
                for (SealApplicationItem item : itemsByApplication.getOrDefault(application.getId(), List.of())) {
                    Row row = detail.createRow(detailRowIndex++);
                    row.setHeightInPoints(30);
                    int column = 0;
                    numeric(row, column++, detailSequence++, styles.body);
                    text(row, column++, application.getApplicationNo(), styles.body);
                    text(row, column++, format(application.getApprovalTime()), styles.body);
                    text(row, column++, application.getDepartmentName(), styles.body);
                    text(row, column++, application.getSealName(), styles.body);
                    text(row, column++, item.getDocumentName(), styles.wrap);
                    numeric(row, column++, item.getCopies(), styles.body);
                    numeric(row, column++, stampedFiles.size(), styles.body);
                    text(row, column, archiveLinks, styles.wrap);
                }
            }
            configure(detail, detailHeaders.length, detailRowIndex,
                    new int[]{8, 25, 20, 24, 24, 60, 12, 14, 50}, (short) 1);
            workbook.getProperties().getCoreProperties().setTitle("用印台账 " + range.startDate() + " 至 " + range.endDate());
            workbook.getProperties().getCoreProperties().setCreator("智慧营造");
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new BusinessException("用印台账生成失败");
        }
    }

    private Map<Long, List<SealApplicationItem>> groupItems(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return itemMapper.selectList(new LambdaQueryWrapper<SealApplicationItem>()
                        .in(SealApplicationItem::getApplicationId, ids)
                        .orderByAsc(SealApplicationItem::getApplicationId)
                        .orderByAsc(SealApplicationItem::getSortOrder))
                .stream().collect(Collectors.groupingBy(SealApplicationItem::getApplicationId,
                        java.util.LinkedHashMap::new, Collectors.toList()));
    }

    private Map<Long, List<WorkflowCcRecipient>> groupCc(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return ccMapper.selectList(new LambdaQueryWrapper<WorkflowCcRecipient>()
                        .eq(WorkflowCcRecipient::getBusinessCode, SealApplicationService.BUSINESS_CODE)
                        .in(WorkflowCcRecipient::getBusinessId, ids)
                        .orderByAsc(WorkflowCcRecipient::getBusinessId).orderByAsc(WorkflowCcRecipient::getId))
                .stream().collect(Collectors.groupingBy(WorkflowCcRecipient::getBusinessId,
                        java.util.LinkedHashMap::new, Collectors.toList()));
    }

    private Map<Long, List<SealApplicationFile>> groupFiles(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return fileMapper.selectList(new LambdaQueryWrapper<SealApplicationFile>()
                        .in(SealApplicationFile::getApplicationId, ids)
                        .orderByAsc(SealApplicationFile::getApplicationId).orderByAsc(SealApplicationFile::getId))
                .stream().collect(Collectors.groupingBy(SealApplicationFile::getApplicationId,
                        java.util.LinkedHashMap::new, Collectors.toList()));
    }

    private void writeExportAudit(Long projectId, LedgerDateRange range, int count,
                                  SysUser user, HttpServletRequest request) {
        OperationLog log = new OperationLog();
        log.setUserId(user.getId());
        log.setUsername(org.springframework.util.StringUtils.hasText(user.getRealName())
                ? user.getRealName() : user.getUsername());
        log.setOperationType("EXPORT_SEAL_LEDGER");
        log.setOperationDesc("导出审批通过日期 " + range.startDate() + " 至 " + range.endDate()
                + " 的项目用印台账，共 " + count + " 条申请");
        log.setBusinessType("SEAL_LEDGER");
        log.setBusinessId(projectId);
        log.setIpAddress(request == null ? null : request.getRemoteAddr());
        log.setCreateTime(LocalDateTime.now(BUSINESS_ZONE));
        if (operationLogMapper.insert(log) != 1) throw BusinessException.of(409, "用印台账导出审计未写入");
    }

    private void header(Sheet sheet, String[] values, CellStyle style) {
        Row row = sheet.createRow(0);
        row.setHeightInPoints(30);
        for (int i = 0; i < values.length; i++) text(row, i, values[i], style);
    }

    private void configure(Sheet sheet, int columns, int rowCount, int[] widths, short fitWidth) {
        sheet.createFreezePane(0, 1);
        if (rowCount > 1) sheet.setAutoFilter(new CellRangeAddress(0, rowCount - 1, 0, columns - 1));
        sheet.setDefaultRowHeightInPoints(24);
        for (int i = 0; i < widths.length; i++) sheet.setColumnWidth(i, Math.min(255, widths[i]) * 256);
        sheet.getPrintSetup().setLandscape(true);
        sheet.getPrintSetup().setPaperSize(PrintSetup.A3_PAPERSIZE);
        sheet.setFitToPage(true);
        sheet.getPrintSetup().setFitWidth(fitWidth);
        sheet.getPrintSetup().setFitHeight((short) 0);
        sheet.setHorizontallyCenter(true);
        sheet.setMargin(Sheet.LeftMargin, 0.25);
        sheet.setMargin(Sheet.RightMargin, 0.25);
        sheet.setMargin(Sheet.TopMargin, 0.4);
        sheet.setMargin(Sheet.BottomMargin, 0.4);
        sheet.setRepeatingRows(CellRangeAddress.valueOf("1:1"));
        sheet.setRepeatingColumns(CellRangeAddress.valueOf("A:B"));
        if (rowCount > 0) sheet.getWorkbook().setPrintArea(
                sheet.getWorkbook().getSheetIndex(sheet), 0, columns - 1, 0, Math.max(0, rowCount - 1));
    }

    private void text(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(safeCellText(value));
        cell.setCellStyle(style);
    }

    private void numeric(Row row, int column, Number value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value == null ? 0 : value.doubleValue());
        cell.setCellStyle(style);
    }

    static String safeCellText(String value) {
        if (value == null) return "";
        String trimmed = value.stripLeading();
        if (!trimmed.isEmpty() && "=+-@".indexOf(trimmed.charAt(0)) >= 0) return "'" + value;
        return value;
    }

    private String format(LocalDate value) {
        return value == null ? "" : value.format(DateTimeFormatter.ISO_DATE);
    }

    private String format(LocalDateTime value) {
        return value == null ? "" : value.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    public record LedgerExport(String fileName, byte[] content, LedgerDateRange range) { }

    private static final class Styles {
        private final CellStyle header;
        private final CellStyle body;
        private final CellStyle wrap;

        private Styles(XSSFWorkbook workbook) {
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerFont.setFontHeightInPoints((short) 11);
            headerFont.setFontName("Microsoft YaHei");
            header = workbook.createCellStyle();
            header.setFont(headerFont);
            header.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setAlignment(HorizontalAlignment.CENTER);
            header.setVerticalAlignment(VerticalAlignment.CENTER);
            header.setWrapText(true);
            borders(header);

            Font bodyFont = workbook.createFont();
            bodyFont.setFontHeightInPoints((short) 10);
            bodyFont.setFontName("Microsoft YaHei");
            body = workbook.createCellStyle();
            body.setFont(bodyFont);
            body.setVerticalAlignment(VerticalAlignment.CENTER);
            borders(body);
            wrap = workbook.createCellStyle();
            wrap.cloneStyleFrom(body);
            wrap.setWrapText(true);
        }

        private static void borders(CellStyle style) {
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
        }
    }
}
