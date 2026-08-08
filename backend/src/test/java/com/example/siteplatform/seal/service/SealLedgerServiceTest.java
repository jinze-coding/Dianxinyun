package com.example.siteplatform.seal.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.log.mapper.OperationLogMapper;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.seal.entity.SealApplication;
import com.example.siteplatform.seal.entity.SealApplicationFile;
import com.example.siteplatform.seal.entity.SealApplicationItem;
import com.example.siteplatform.seal.mapper.SealApplicationFileMapper;
import com.example.siteplatform.seal.mapper.SealApplicationItemMapper;
import com.example.siteplatform.seal.mapper.SealApplicationMapper;
import com.example.siteplatform.system.constant.SystemPermissionCodes;
import com.example.siteplatform.workflow.entity.WorkflowCcRecipient;
import com.example.siteplatform.workflow.mapper.WorkflowCcRecipientMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.ArgumentCaptor;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SealLedgerServiceTest {

    @Test
    void workbookHasTwoAuditableSheetsAndNeutralizesFormulaText() throws Exception {
        SealLedgerService service = new SealLedgerService(null, null, null, null, null, null, null);
        SealApplication application = application();
        SealApplicationItem item = new SealApplicationItem();
        item.setApplicationId(42L);
        item.setDocumentName("专项施工方案");
        item.setCopies(3);
        WorkflowCcRecipient recipient = new WorkflowCcRecipient();
        recipient.setBusinessId(42L);
        recipient.setUserName("抄送人王五");
        SealApplicationFile file = new SealApplicationFile();
        file.setApplicationId(42L);
        file.setFileRole("STAMPED_RESULT");
        file.setArchivedDocumentId(501L);
        file.setArchivedVersionId(601L);
        byte[] bytes = service.buildWorkbook(List.of(application),
                new LedgerDateRange(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)),
                Map.of(42L, List.of(item)), Map.of(42L, List.of(recipient)), Map.of(42L, List.of(file)));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertEquals(List.of("用印台账", "文件明细"),
                    java.util.stream.IntStream.range(0, workbook.getNumberOfSheets())
                            .mapToObj(workbook::getSheetName).toList());
            XSSFSheet ledger = workbook.getSheetAt(0);
            XSSFSheet detail = workbook.getSheetAt(1);
            assertTrue(ledger.getPaneInformation().isFreezePane());
            assertTrue(detail.getPaneInformation().isFreezePane());
            assertTrue(ledger.getCTWorksheet().isSetAutoFilter());
            assertTrue(detail.getCTWorksheet().isSetAutoFilter());
            assertNotNull(workbook.getPrintArea(0));
            assertNotNull(workbook.getPrintArea(1));
            assertEquals(PrintSetup.A3_PAPERSIZE, ledger.getPrintSetup().getPaperSize());
            assertEquals(2, ledger.getPrintSetup().getFitWidth());
            assertEquals(1, detail.getPrintSetup().getFitWidth());
            assertTrue(ledger.getRow(0).getCell(0).getCellStyle().getWrapText());
            assertNotNull(ledger.getRepeatingColumns());
            assertNotNull(detail.getRepeatingColumns());
            assertEquals(CellType.STRING, ledger.getRow(1).getCell(7).getCellType());
            assertEquals("'=SUM(1,1)", ledger.getRow(1).getCell(7).getStringCellValue());
            assertEquals("抄送人王五", ledger.getRow(1).getCell(12).getStringCellValue());
            assertEquals("已上传 1 份", ledger.getRow(1).getCell(15).getStringCellValue());
            assertTrue(ledger.getRow(1).getCell(16).getStringCellValue().contains("资料501-版本601"));
            assertEquals("Microsoft YaHei", ledger.getRow(0).getCell(0).getCellStyle().getFont().getFontName());
        }

        Path artifact = Path.of("target", "test-artifacts", "seal-ledger-sample.xlsx");
        Files.createDirectories(artifact.getParent());
        Files.write(artifact, bytes);
    }

    @Test
    void safeCellTextProtectsAllSpreadsheetFormulaPrefixes() {
        assertEquals("'=1+1", SealLedgerService.safeCellText("=1+1"));
        assertEquals("'+cmd", SealLedgerService.safeCellText("+cmd"));
        assertEquals("'-2", SealLedgerService.safeCellText("-2"));
        assertEquals("'@SUM(A1)", SealLedgerService.safeCellText("@SUM(A1)"));
        assertEquals("'   =1+1", SealLedgerService.safeCellText("   =1+1"));
        assertEquals("'\t@SUM(A1)", SealLedgerService.safeCellText("\t@SUM(A1)"));
        assertEquals("normal", SealLedgerService.safeCellText("normal"));
    }

    @Test
    void exportUsesApprovedStatusAndHalfOpenApprovalTimeDayBoundary() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), SealApplicationMapper.class.getName()),
                SealApplication.class);
        SealApplicationMapper applicationMapper = mock(SealApplicationMapper.class);
        SealApplicationItemMapper itemMapper = mock(SealApplicationItemMapper.class);
        SealApplicationFileMapper fileMapper = mock(SealApplicationFileMapper.class);
        WorkflowCcRecipientMapper ccMapper = mock(WorkflowCcRecipientMapper.class);
        OperationLogMapper operationLogMapper = mock(OperationLogMapper.class);
        ProjectPermissionService permissionService = mock(ProjectPermissionService.class);
        SealLedgerService service = new SealLedgerService(applicationMapper, itemMapper, fileMapper, ccMapper,
                operationLogMapper, permissionService, null);
        when(applicationMapper.selectList(any())).thenReturn(List.of());
        when(operationLogMapper.insert(any())).thenReturn(1);
        SysUser operator = new SysUser();
        operator.setId(7L);
        operator.setUsername("ledger_exporter");

        var export = service.export(9L, "DAY", LocalDate.of(2026, 8, 8), null, null,
                null, null, operator, null);

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<LambdaQueryWrapper> query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(applicationMapper).selectList(query.capture());
        String sql = query.getValue().getSqlSegment().toLowerCase();
        assertTrue(sql.contains("status"));
        assertTrue(sql.contains("approval_time"));
        assertTrue(sql.contains("approval_time <"));
        assertTrue(query.getValue().getParamNameValuePairs().containsValue(SealApplicationService.APPROVED));
        assertTrue(query.getValue().getParamNameValuePairs()
                .containsValue(LocalDateTime.of(2026, 8, 8, 0, 0)));
        assertTrue(query.getValue().getParamNameValuePairs()
                .containsValue(LocalDateTime.of(2026, 8, 9, 0, 0)));
        assertEquals(LocalDate.of(2026, 8, 8), export.range().startDate());
        assertEquals(LocalDate.of(2026, 8, 8), export.range().endDate());
        verify(permissionService).checkProjectPermission(7L, 9L);
        verify(permissionService).requireSystemPermission(7L, 9L, SystemPermissionCodes.SEAL_EXPORT);
        verify(operationLogMapper).insert(any());
    }

    @Test
    void exportRejectsNonApprovedStatusBeforeQueryingLedgerRows() {
        SealApplicationMapper applicationMapper = mock(SealApplicationMapper.class);
        ProjectPermissionService permissionService = mock(ProjectPermissionService.class);
        SealLedgerService service = new SealLedgerService(applicationMapper, mock(SealApplicationItemMapper.class),
                mock(SealApplicationFileMapper.class), mock(WorkflowCcRecipientMapper.class),
                mock(OperationLogMapper.class), permissionService, null);
        SysUser operator = new SysUser();
        operator.setId(7L);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.export(9L, "DAY", LocalDate.of(2026, 8, 8), null, null,
                        null, "REJECTED", operator, null));

        assertTrue(error.getMessage().contains("仅导出审批通过记录"));
        verify(applicationMapper, never()).selectList(any());
    }

    private SealApplication application() {
        SealApplication application = new SealApplication();
        application.setId(42L);
        application.setApplicationNo("YYSQ-20260808-00000042");
        application.setApplicationDate(LocalDate.of(2026, 8, 8));
        application.setApprovalTime(LocalDateTime.of(2026, 8, 8, 14, 30));
        application.setDepartmentName("智慧营造演示项目");
        application.setCompanyName("上海建工智慧营造有限公司");
        application.setSealName("项目章");
        application.setPurpose("=SUM(1,1)");
        application.setApplicantName("张三");
        application.setApplicantPhone("19900000000");
        application.setApproverName("李四");
        application.setApprovalOpinion("同意");
        return application;
    }
}
