package com.example.siteplatform.document.service;

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
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class DocumentCirculationLedgerServiceTest {
    @Test
    void exportAlwaysContainsFiveFixedAuditableSheets() throws Exception {
        DocumentCirculationLedgerService service = new DocumentCirculationLedgerService(
                mock(DocumentIncomingBatchMapper.class), mock(DocumentIncomingItemMapper.class),
                mock(DocumentDistributionBatchMapper.class), mock(DocumentDistributionItemMapper.class),
                mock(DocumentDistributionRecipientMapper.class), mock(DocumentDistributionRecipientItemMapper.class),
                mock(DocumentCirculationEventMapper.class),
                mock(ProjectDocumentMapper.class), mock(ProjectDocumentVersionMapper.class),
                mock(ProjectPermissionService.class));

        DocumentCirculationLedgerExport export = service.build(9L, List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(export.content()))) {
            assertEquals(5, workbook.getNumberOfSheets());
            assertEquals(List.of("收文批次", "发放批次", "接收人明细", "文件与版本追溯", "预览下载明细"),
                    List.of(workbook.getSheetName(0), workbook.getSheetName(1), workbook.getSheetName(2),
                            workbook.getSheetName(3), workbook.getSheetName(4)));
            for (int index = 0; index < workbook.getNumberOfSheets(); index++) {
                assertEquals(0, workbook.getSheetAt(index).getLastRowNum());
            }
        }
    }
}
