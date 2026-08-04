package com.example.siteplatform.inspection.service;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.electricbox.entity.ElectricBox;
import com.example.siteplatform.electricbox.mapper.ElectricBoxMapper;
import com.example.siteplatform.electricbox.service.ElectricBoxInspectionScopeService;
import com.example.siteplatform.inspection.entity.InspectionRecord;
import com.example.siteplatform.inspection.entity.InspectionRecordItem;
import com.example.siteplatform.inspection.mapper.InspectionRecordItemMapper;
import com.example.siteplatform.inspection.mapper.InspectionRecordMapper;
import com.example.siteplatform.project.entity.ProjectInfo;
import com.example.siteplatform.project.mapper.ProjectInfoMapper;
import com.example.siteplatform.project.service.ProjectPermissionService;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InspectionExportServiceTest {

    @Mock
    private ElectricBoxMapper electricBoxMapper;
    @Mock
    private InspectionRecordMapper inspectionRecordMapper;
    @Mock
    private InspectionRecordItemMapper inspectionRecordItemMapper;
    @Mock
    private ProjectInfoMapper projectInfoMapper;
    @Mock
    private ProjectPermissionService permissionService;
    @Mock
    private ElectricBoxInspectionScopeService scopeService;

    private InspectionService service;
    private ElectricBox box;

    @BeforeEach
    void setUp() {
        service = new InspectionService();
        ReflectionTestUtils.setField(service, "electricBoxMapper", electricBoxMapper);
        ReflectionTestUtils.setField(service, "inspectionRecordMapper", inspectionRecordMapper);
        ReflectionTestUtils.setField(service, "inspectionRecordItemMapper", inspectionRecordItemMapper);
        ReflectionTestUtils.setField(service, "projectInfoMapper", projectInfoMapper);
        ReflectionTestUtils.setField(service, "projectPermissionService", permissionService);
        ReflectionTestUtils.setField(service, "inspectionScopeService", scopeService);

        box = new ElectricBox();
        box.setId(10L);
        box.setProjectId(1L);
        box.setBoxCode("EB-TEST-001");
        box.setBoxName("测试二级电箱");
        box.setInstallLocation("一层东侧材料通道");
        box.setStatus("ACTIVE");
    }

    @Test
    void exportsSingleBoxAsOneFormalMonthlySheet() throws Exception {
        ProjectInfo project = new ProjectInfo();
        project.setId(1L);
        project.setProjectName("测试工程");
        InspectionRecord record = new InspectionRecord();
        record.setId(100L);
        record.setProjectId(1L);
        record.setElectricBoxId(10L);
        record.setCheckDate(LocalDate.of(2026, 7, 2));
        record.setInspectorName("张电工");
        record.setRemark("现场正常");
        InspectionRecordItem item = new InspectionRecordItem();
        item.setRecordId(100L);
        item.setItemCode("APPEARANCE");
        item.setItemName("内外观");
        item.setResult("NORMAL");

        when(electricBoxMapper.selectById(10L)).thenReturn(box);
        when(electricBoxMapper.selectList(any())).thenReturn(List.of(box));
        when(inspectionRecordMapper.selectList(any())).thenReturn(List.of(record));
        when(inspectionRecordItemMapper.selectList(any())).thenReturn(List.of(item));
        when(projectInfoMapper.selectById(1L)).thenReturn(project);
        when(permissionService.hasAnyInspectionPermission(eq(5L), eq(1L), any(String[].class))).thenReturn(true);
        when(scopeService.requiredDates(box, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)))
                .thenReturn(LocalDate.of(2026, 7, 2).datesUntil(LocalDate.of(2026, 8, 1)).collect(java.util.stream.Collectors.toSet()));

        SysUser user = new SysUser();
        user.setId(5L);
        InspectionService.ExportFile file = service.exportRecords(
                1L, InspectionService.TEMPLATE_ELECTRIC_BOX_DAILY, "2026-07", 10L,
                99L, "ABNORMAL", user);

        assertEquals("EB-TEST-001-电箱检查记录表-2026-07.xlsx", file.fileName());
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file.content()))) {
            assertEquals(1, workbook.getNumberOfSheets());
            assertEquals("电箱检查记录表", workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue());
            assertEquals("日期", workbook.getSheetAt(0).getRow(4).getCell(0).getStringCellValue());
            assertEquals("非巡检范围", workbook.getSheetAt(0).getRow(5).getCell(1).getStringCellValue());
            assertEquals("正常", workbook.getSheetAt(0).getRow(6).getCell(1).getStringCellValue());
            assertEquals("张电工", workbook.getSheetAt(0).getRow(6).getCell(7).getStringCellValue());
            assertEquals("未检", workbook.getSheetAt(0).getRow(7).getCell(1).getStringCellValue());
        }
    }

    @Test
    void exportsSingleBoxForInclusiveDateRange() throws Exception {
        ProjectInfo project = new ProjectInfo();
        project.setProjectName("测试工程");
        when(electricBoxMapper.selectList(any())).thenReturn(List.of(box));
        when(inspectionRecordMapper.selectList(any())).thenReturn(List.of());
        when(projectInfoMapper.selectById(1L)).thenReturn(project);
        when(permissionService.hasAnyInspectionPermission(eq(5L), eq(1L), any(String[].class))).thenReturn(true);
        when(scopeService.requiredDates(box, LocalDate.of(2026, 6, 30), LocalDate.of(2026, 7, 2)))
                .thenReturn(Set.of(LocalDate.of(2026, 6, 30), LocalDate.of(2026, 7, 2)));

        InspectionService.ExportFile file = service.exportRecords(
                1L, InspectionService.TEMPLATE_ELECTRIC_BOX_DAILY, null, null,
                "2026-06-30", "2026-07-02", "10", null, null, user());

        assertEquals("EB-TEST-001-电箱检查记录表-2026-06-30至2026-07-02.xlsx", file.fileName());
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file.content()))) {
            assertEquals(1, workbook.getNumberOfSheets());
            assertEquals("检查区间：2026-06-30 至 2026-07-02",
                    workbook.getSheetAt(0).getRow(2).getCell(6).getStringCellValue());
            assertEquals("2026.6.30", workbook.getSheetAt(0).getRow(5).getCell(0).getStringCellValue());
            assertEquals("非巡检范围", workbook.getSheetAt(0).getRow(6).getCell(1).getStringCellValue());
            assertEquals("2026.7.2", workbook.getSheetAt(0).getRow(7).getCell(0).getStringCellValue());
        }
    }

    @Test
    void exportsOnlySelectedBoxesWithSummaryAndDetailSheets() throws Exception {
        ElectricBox secondBox = box(20L, 1L, "EB-TEST-002");
        ElectricBox unselectedBox = box(30L, 1L, "EB-TEST-003");
        InspectionRecord matchedRecord = new InspectionRecord();
        matchedRecord.setId(200L);
        matchedRecord.setProjectId(1L);
        matchedRecord.setElectricBoxId(10L);
        matchedRecord.setCheckDate(LocalDate.of(2026, 6, 30));
        matchedRecord.setAbnormalCount(1);
        when(electricBoxMapper.selectList(any())).thenReturn(List.of(box, secondBox));
        when(inspectionRecordMapper.selectList(any())).thenReturn(List.of(matchedRecord));
        when(inspectionRecordItemMapper.selectList(any())).thenReturn(List.of());
        when(permissionService.hasInspectionPermission(5L, 1L, "SUMMARY_EXPORT")).thenReturn(true);
        LocalDate start = LocalDate.of(2026, 6, 30);
        LocalDate end = LocalDate.of(2026, 7, 2);
        when(scopeService.requiredDates(box, start, end)).thenReturn(Set.of(start, start.plusDays(1), end));
        when(scopeService.requiredDates(secondBox, start, end)).thenReturn(Set.of(start, start.plusDays(1), end));

        InspectionService.ExportFile file = service.exportRecords(
                1L, InspectionService.TEMPLATE_ELECTRIC_BOX_DAILY, null, null,
                start.toString(), end.toString(), "10,20", null, null, user());

        assertEquals("电箱巡检记录-1-2026-06-30至2026-07-02.xlsx", file.fileName());
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file.content()))) {
            assertEquals(3, workbook.getNumberOfSheets());
            assertEquals("巡检汇总", workbook.getSheetName(0));
            assertEquals("EB-TEST-001", workbook.getSheetName(1));
            assertEquals("EB-TEST-002", workbook.getSheetName(2));
            assertEquals(-1, workbook.getSheetIndex(unselectedBox.getBoxCode()));
            assertEquals("统计区间：2026-06-30 至 2026-07-02",
                    workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue());
            assertEquals(3, (int) workbook.getSheetAt(0).getRow(2).getCell(2).getNumericCellValue());
            assertEquals(1, (int) workbook.getSheetAt(0).getRow(2).getCell(3).getNumericCellValue());
            assertEquals(2, (int) workbook.getSheetAt(0).getRow(2).getCell(4).getNumericCellValue());
            assertEquals(1, (int) workbook.getSheetAt(0).getRow(2).getCell(5).getNumericCellValue());
        }
    }

    @Test
    void exportsAllProjectBoxesWhenBoxIdsAreOmitted() throws Exception {
        LocalDate date = LocalDate.of(2026, 8, 1);
        when(electricBoxMapper.selectList(any())).thenReturn(List.of(box));
        when(inspectionRecordMapper.selectList(any())).thenReturn(List.of());
        when(permissionService.hasInspectionPermission(5L, 1L, "SUMMARY_EXPORT")).thenReturn(true);
        when(scopeService.requiredDates(box, date, date)).thenReturn(Set.of(date));

        InspectionService.ExportFile file = service.exportRecords(
                1L, InspectionService.TEMPLATE_ELECTRIC_BOX_DAILY, null, null,
                date.toString(), date.toString(), null, null, null, user());

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file.content()))) {
            assertEquals(2, workbook.getNumberOfSheets());
            assertEquals("巡检汇总", workbook.getSheetName(0));
            assertEquals("EB-TEST-001", workbook.getSheetName(1));
        }
    }

    @Test
    void rejectsInvalidAndConflictingRangeParameters() {
        LocalDate today = LocalDate.now();

        assertEquals("开始日期和结束日期必须同时传入", assertThrows(BusinessException.class,
                () -> service.exportRecords(1L, null, null, null,
                        today.toString(), null, null, null, null, user())).getMessage());
        assertEquals("开始日期不能晚于结束日期", assertThrows(BusinessException.class,
                () -> service.exportRecords(1L, null, null, null,
                        today.toString(), today.minusDays(1).toString(), null, null, null, user())).getMessage());
        assertEquals("结束日期不能晚于今天", assertThrows(BusinessException.class,
                () -> service.exportRecords(1L, null, null, null,
                        today.toString(), today.plusDays(1).toString(), null, null, null, user())).getMessage());
        assertEquals("导出日期范围不能超过366天", assertThrows(BusinessException.class,
                () -> service.exportRecords(1L, null, null, null,
                        today.minusDays(366).toString(), today.toString(), null, null, null, user())).getMessage());
        assertEquals("日期范围参数不能与月份或单箱月报参数同时传入", assertThrows(BusinessException.class,
                () -> service.exportRecords(1L, null, "2026-07", null,
                        today.toString(), today.toString(), null, null, null, user())).getMessage());
        assertEquals("日期范围正式报表不支持巡检员或结果筛选", assertThrows(BusinessException.class,
                () -> service.exportRecords(1L, null, null, null,
                        today.toString(), today.toString(), null, 9L, null, user())).getMessage());
    }

    @Test
    void allows366InclusiveDaysAndRequiresSummaryPermissionForMultipleBoxes() {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(365);
        ElectricBox secondBox = box(20L, 1L, "EB-TEST-002");
        when(electricBoxMapper.selectList(any())).thenReturn(List.of(box, secondBox));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.exportRecords(1L, null, null, null,
                        start.toString(), end.toString(), "10,20", null, null, user()));

        assertEquals(403, error.getCode());
        assertTrue(error.getMessage().contains("汇总导出"));
        verify(permissionService).hasInspectionPermission(5L, 1L, "SUMMARY_EXPORT");
    }

    @Test
    void rejectsCrossProjectBoxInRangeSelection() {
        ElectricBox otherProjectBox = box(20L, 2L, "OTHER-BOX");
        when(electricBoxMapper.selectList(any())).thenReturn(List.of(otherProjectBox));
        LocalDate today = LocalDate.now();

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.exportRecords(1L, null, null, null,
                        today.toString(), today.toString(), "20", null, null, user()));

        assertEquals(403, error.getCode());
        assertEquals("电箱不属于当前项目", error.getMessage());
    }

    private SysUser user() {
        SysUser user = new SysUser();
        user.setId(5L);
        return user;
    }

    private ElectricBox box(Long id, Long projectId, String boxCode) {
        ElectricBox result = new ElectricBox();
        result.setId(id);
        result.setProjectId(projectId);
        result.setBoxCode(boxCode);
        result.setBoxName(boxCode);
        result.setInstallLocation("测试位置");
        result.setStatus("ACTIVE");
        return result;
    }
}
