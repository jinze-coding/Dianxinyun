package com.example.siteplatform.document.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.document.entity.DocumentCirculationEvent;
import com.example.siteplatform.document.entity.DocumentDistributionBatch;
import com.example.siteplatform.document.entity.DocumentDistributionItem;
import com.example.siteplatform.document.entity.DocumentDistributionRecipient;
import com.example.siteplatform.document.entity.DocumentDistributionRecipientItem;
import com.example.siteplatform.document.entity.DocumentIncomingBatch;
import com.example.siteplatform.document.entity.DocumentIncomingItem;
import com.example.siteplatform.document.entity.ProjectDocument;
import com.example.siteplatform.document.entity.ProjectDocumentVersion;
import com.example.siteplatform.document.mapper.DocumentCirculationEventMapper;
import com.example.siteplatform.document.mapper.DocumentDistributionBatchMapper;
import com.example.siteplatform.document.mapper.DocumentDistributionItemMapper;
import com.example.siteplatform.document.mapper.DocumentDistributionRecipientMapper;
import com.example.siteplatform.document.mapper.DocumentDistributionRecipientItemMapper;
import com.example.siteplatform.document.mapper.DocumentIncomingBatchMapper;
import com.example.siteplatform.document.mapper.DocumentIncomingItemMapper;
import com.example.siteplatform.document.mapper.ProjectDocumentMapper;
import com.example.siteplatform.document.mapper.ProjectDocumentVersionMapper;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.system.constant.SystemPermissionCodes;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class DocumentCirculationLedgerService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final DocumentIncomingBatchMapper incomingBatchMapper;
    private final DocumentIncomingItemMapper incomingItemMapper;
    private final DocumentDistributionBatchMapper distributionBatchMapper;
    private final DocumentDistributionItemMapper distributionItemMapper;
    private final DocumentDistributionRecipientMapper recipientMapper;
    private final DocumentDistributionRecipientItemMapper recipientItemMapper;
    private final DocumentCirculationEventMapper eventMapper;
    private final ProjectDocumentMapper documentMapper;
    private final ProjectDocumentVersionMapper versionMapper;
    private final ProjectPermissionService permissionService;

    public DocumentCirculationLedgerService(DocumentIncomingBatchMapper incomingBatchMapper,
                                            DocumentIncomingItemMapper incomingItemMapper,
                                            DocumentDistributionBatchMapper distributionBatchMapper,
                                            DocumentDistributionItemMapper distributionItemMapper,
                                            DocumentDistributionRecipientMapper recipientMapper,
                                            DocumentDistributionRecipientItemMapper recipientItemMapper,
                                            DocumentCirculationEventMapper eventMapper,
                                            ProjectDocumentMapper documentMapper,
                                            ProjectDocumentVersionMapper versionMapper,
                                            ProjectPermissionService permissionService) {
        this.incomingBatchMapper = incomingBatchMapper;
        this.incomingItemMapper = incomingItemMapper;
        this.distributionBatchMapper = distributionBatchMapper;
        this.distributionItemMapper = distributionItemMapper;
        this.recipientMapper = recipientMapper;
        this.recipientItemMapper = recipientItemMapper;
        this.eventMapper = eventMapper;
        this.documentMapper = documentMapper;
        this.versionMapper = versionMapper;
        this.permissionService = permissionService;
    }

    public DocumentCirculationLedgerExport export(Long projectId, SysUser user) {
        permissionService.checkProjectPermission(user.getId(), projectId);
        permissionService.requireSystemPermission(user.getId(), projectId,
                SystemPermissionCodes.DOCUMENT_CIRCULATION_EXPORT);
        List<DocumentIncomingBatch> incoming = incomingBatchMapper.selectList(
                new LambdaQueryWrapper<DocumentIncomingBatch>().eq(DocumentIncomingBatch::getProjectId, projectId)
                        .orderByAsc(DocumentIncomingBatch::getId));
        List<DocumentIncomingItem> incomingItems = incomingItemMapper.selectList(
                new LambdaQueryWrapper<DocumentIncomingItem>().eq(DocumentIncomingItem::getProjectId, projectId)
                        .orderByAsc(DocumentIncomingItem::getBatchId).orderByAsc(DocumentIncomingItem::getItemOrder));
        List<DocumentDistributionBatch> distributions = distributionBatchMapper.selectList(
                new LambdaQueryWrapper<DocumentDistributionBatch>().eq(DocumentDistributionBatch::getProjectId, projectId)
                        .orderByAsc(DocumentDistributionBatch::getId));
        List<DocumentDistributionItem> distributionItems = distributionItemMapper.selectList(
                new LambdaQueryWrapper<DocumentDistributionItem>().eq(DocumentDistributionItem::getProjectId, projectId)
                        .orderByAsc(DocumentDistributionItem::getBatchId).orderByAsc(DocumentDistributionItem::getItemOrder));
        List<DocumentDistributionRecipient> recipients = recipientMapper.selectList(
                new LambdaQueryWrapper<DocumentDistributionRecipient>().eq(DocumentDistributionRecipient::getProjectId, projectId)
                        .orderByAsc(DocumentDistributionRecipient::getBatchId).orderByAsc(DocumentDistributionRecipient::getId));
        List<Long> recipientIds = recipients.stream().map(DocumentDistributionRecipient::getId).toList();
        List<DocumentDistributionRecipientItem> recipientItems = recipientIds.isEmpty() ? List.of()
                : recipientItemMapper.selectList(new LambdaQueryWrapper<DocumentDistributionRecipientItem>()
                .in(DocumentDistributionRecipientItem::getRecipientId, recipientIds)
                .orderByAsc(DocumentDistributionRecipientItem::getRecipientId)
                .orderByAsc(DocumentDistributionRecipientItem::getDistributionItemId));
        List<ProjectDocument> documents = documentMapper.selectList(new LambdaQueryWrapper<ProjectDocument>()
                .eq(ProjectDocument::getProjectId, projectId)
                .in(ProjectDocument::getDocumentType, Set.of("DRAWING", "TECHNICAL_DOCUMENT"))
                .orderByAsc(ProjectDocument::getId));
        List<Long> documentIds = documents.stream().map(ProjectDocument::getId).toList();
        List<ProjectDocumentVersion> versions = documentIds.isEmpty() ? List.of() : versionMapper.selectList(
                new LambdaQueryWrapper<ProjectDocumentVersion>().in(ProjectDocumentVersion::getDocumentId, documentIds)
                        .orderByAsc(ProjectDocumentVersion::getDocumentId).orderByAsc(ProjectDocumentVersion::getVersionNo));
        List<DocumentCirculationEvent> accessEvents = eventMapper.selectList(
                new LambdaQueryWrapper<DocumentCirculationEvent>().eq(DocumentCirculationEvent::getProjectId, projectId)
                        .in(DocumentCirculationEvent::getEventType, List.of("PREVIEW", "DOWNLOAD"))
                        .orderByAsc(DocumentCirculationEvent::getCreateTime).orderByAsc(DocumentCirculationEvent::getId));
        return build(projectId, incoming, incomingItems, distributions, distributionItems,
                recipients, recipientItems, documents, versions, accessEvents);
    }

    DocumentCirculationLedgerExport build(Long projectId,
                                                   List<DocumentIncomingBatch> incoming,
                                                   List<DocumentIncomingItem> incomingItems,
                                                   List<DocumentDistributionBatch> distributions,
                                                   List<DocumentDistributionItem> distributionItems,
                                                   List<DocumentDistributionRecipient> recipients,
                                                   List<DocumentDistributionRecipientItem> recipientItems,
                                                   List<ProjectDocument> documents,
                                                   List<ProjectDocumentVersion> versions,
                                                   List<DocumentCirculationEvent> accessEvents) {
        Map<Long, DocumentIncomingBatch> incomingById = index(incoming);
        Map<Long, DocumentDistributionBatch> distributionById = indexDistribution(distributions);
        Map<Long, ProjectDocument> documentById = indexDocument(documents);
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            CellStyle header = headerStyle(workbook);
            Sheet incomingSheet = sheet(workbook, "收文批次", header,
                    "收文批次号", "来源单位", "发件人", "来文编号", "接收方式", "收文时间", "接收技术员", "状态", "发布时间", "备注");
            for (DocumentIncomingBatch row : incoming) append(incomingSheet, row.getIncomingNo(), row.getSourceOrganization(),
                    row.getSenderName(), row.getSourceReferenceNo(), row.getReceiveMethod(), time(row.getReceivedAt()),
                    row.getReceiverName(), row.getStatus(), time(row.getPublishedTime()), row.getRemark());

            Sheet distributionSheet = sheet(workbook, "发放批次", header,
                    "发放批次号", "关联收文批次", "签收期限", "状态", "发布人", "发布时间", "默认通知内容", "电子签名要求", "纸质签名要求", "补充说明", "作废原因");
            for (DocumentDistributionBatch row : distributions) append(distributionSheet, row.getDistributionNo(),
                    row.getIncomingBatchId() == null ? null : Objects.toString(incomingById.get(row.getIncomingBatchId()).getIncomingNo(), ""),
                    time(row.getDeadline()), row.getStatus(), row.getPublishedByName(), time(row.getPublishedTime()),
                    row.getNotificationTemplate(), yes(row.getElectronicSignatureRequired()), yes(row.getPaperSignatureRequired()),
                    row.getMessageNote(), row.getVoidReason());

            Sheet recipientSheet = sheet(workbook, "接收人明细", header,
                    "发放批次号", "姓名", "账号", "手机号", "项目角色", "成员状态快照", "渠道", "逐文件纸质份数", "是否强制接收人", "签收状态", "通知时间", "确认时间", "异议时间", "异议说明", "签名附件ID");
            Map<Long, DocumentDistributionItem> distributionItemById = new HashMap<>();
            distributionItems.forEach(row -> distributionItemById.put(row.getId(), row));
            Map<Long, List<DocumentDistributionRecipientItem>> recipientItemsByRecipient = recipientItems.stream()
                    .collect(java.util.stream.Collectors.groupingBy(DocumentDistributionRecipientItem::getRecipientId));
            for (DocumentDistributionRecipient row : recipients) append(recipientSheet,
                    distributionById.get(row.getBatchId()).getDistributionNo(), row.getRealNameSnapshot(), row.getUsernameSnapshot(),
                    row.getPhoneSnapshot(), row.getRoleNamesSnapshot(), row.getMemberStatusSnapshot(), row.getChannel(),
                    paperCopies(recipientItemsByRecipient.getOrDefault(row.getId(), List.of()), distributionItemById),
                    yes(row.getMandatory()), row.getStatus(), time(row.getNotifiedTime()), time(row.getConfirmedTime()),
                    time(row.getDisputeTime()), row.getDisputeNote(), row.getSignatureFileId());

            Sheet versionSheet = sheet(workbook, "文件与版本追溯", header,
                    "收文批次号", "发放批次号", "资料ID", "资料类型", "图号/编号", "标题", "系统版本", "外部版次", "版本状态", "版本ID", "文件名", "SHA-256", "发布时间", "发布人");
            Map<Long, DocumentDistributionItem> distributionItemByVersion = new HashMap<>();
            distributionItems.forEach(row -> distributionItemByVersion.putIfAbsent(row.getVersionId(), row));
            Map<Long, DocumentIncomingItem> incomingItemByVersion = new HashMap<>();
            incomingItems.forEach(row -> { if (row.getPublishedVersionId() != null) incomingItemByVersion.put(row.getPublishedVersionId(), row); });
            for (ProjectDocumentVersion row : versions) {
                ProjectDocument document = documentById.get(row.getDocumentId());
                DocumentIncomingItem incomingItem = incomingItemByVersion.get(row.getId());
                DocumentDistributionItem distributionItem = distributionItemByVersion.get(row.getId());
                append(versionSheet,
                        incomingItem == null ? null : incomingById.get(incomingItem.getBatchId()).getIncomingNo(),
                        distributionItem == null ? null : distributionById.get(distributionItem.getBatchId()).getDistributionNo(),
                        document.getId(), document.getDocumentType(), document.getDocumentNo(), document.getTitle(),
                        "V" + row.getVersionNo(), row.getExternalRevision(), row.getVersionStatus(), row.getId(),
                        distributionItem == null ? null : distributionItem.getFileNameSnapshot(),
                        distributionItem == null ? null : distributionItem.getSha256Snapshot(),
                        time(row.getPublishedTime()), row.getPublishedByName());
            }

            Sheet accessSheet = sheet(workbook, "预览下载明细", header,
                    "事件时间", "操作类型", "结果", "用户ID", "用户姓名", "资料ID", "版本ID", "系统版本", "发放批次号", "渠道", "客户端IP", "说明");
            Map<Long, ProjectDocumentVersion> versionById = new HashMap<>();
            versions.forEach(row -> versionById.put(row.getId(), row));
            for (DocumentCirculationEvent row : accessEvents) {
                ProjectDocumentVersion version = versionById.get(row.getVersionId());
                append(accessSheet, time(row.getCreateTime()), row.getEventType(), row.getEventResult(), row.getUserId(),
                        row.getUserName(), row.getDocumentId(), row.getVersionId(), version == null ? null : "V" + version.getVersionNo(),
                        row.getDistributionBatchId() == null ? null : distributionById.get(row.getDistributionBatchId()).getDistributionNo(),
                        row.getChannel(), row.getClientIp(), row.getEventSummary());
            }
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) autoWidth(workbook.getSheetAt(i));
            workbook.write(output);
            String name = "图纸收发综合台账-项目" + projectId + "-"
                    + LocalDateTime.now(BUSINESS_ZONE).format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".xlsx";
            return new DocumentCirculationLedgerExport(name, output.toByteArray());
        } catch (IOException exception) {
            throw new BusinessException("图纸收发综合台账生成失败");
        }
    }

    private <T extends DocumentIncomingBatch> Map<Long, T> index(List<T> rows) {
        Map<Long, T> result = new HashMap<>(); rows.forEach(row -> result.put(row.getId(), row)); return result;
    }
    private Map<Long, DocumentDistributionBatch> indexDistribution(List<DocumentDistributionBatch> rows) {
        Map<Long, DocumentDistributionBatch> result = new HashMap<>(); rows.forEach(row -> result.put(row.getId(), row)); return result;
    }
    private Map<Long, ProjectDocument> indexDocument(List<ProjectDocument> rows) {
        Map<Long, ProjectDocument> result = new HashMap<>(); rows.forEach(row -> result.put(row.getId(), row)); return result;
    }
    private Sheet sheet(XSSFWorkbook workbook, String name, CellStyle header, String... columns) {
        Sheet sheet = workbook.createSheet(name); Row row = sheet.createRow(0);
        for (int i = 0; i < columns.length; i++) { Cell cell = row.createCell(i); cell.setCellValue(columns[i]); cell.setCellStyle(header); }
        sheet.createFreezePane(0, 1); return sheet;
    }
    private void append(Sheet sheet, Object... values) {
        Row row = sheet.createRow(sheet.getLastRowNum() + 1);
        for (int i = 0; i < values.length; i++) row.createCell(i).setCellValue(Objects.toString(values[i], ""));
    }
    private CellStyle headerStyle(XSSFWorkbook workbook) {
        Font font = workbook.createFont(); font.setBold(true);
        CellStyle style = workbook.createCellStyle(); style.setFont(font); return style;
    }
    private void autoWidth(Sheet sheet) {
        if (sheet.getRow(0) == null) return;
        for (int i = 0; i < sheet.getRow(0).getLastCellNum(); i++) { sheet.autoSizeColumn(i); sheet.setColumnWidth(i, Math.min(sheet.getColumnWidth(i) + 512, 16000)); }
    }
    private String time(LocalDateTime value) { return value == null ? null : value.format(TIME); }
    private String yes(Integer value) { return Integer.valueOf(1).equals(value) ? "是" : "否"; }
    private String paperCopies(List<DocumentDistributionRecipientItem> copies,
                               Map<Long, DocumentDistributionItem> items) {
        return copies.stream().filter(row -> row.getPaperCopyCount() != null && row.getPaperCopyCount() > 0)
                .map(row -> {
                    DocumentDistributionItem item = items.get(row.getDistributionItemId());
                    String label = item == null ? "文件" + row.getDistributionItemId()
                            : Objects.toString(item.getDocumentNoSnapshot(), item.getTitleSnapshot());
                    return label + " " + row.getPaperCopyCount() + "份";
                }).collect(java.util.stream.Collectors.joining("；"));
    }
}
