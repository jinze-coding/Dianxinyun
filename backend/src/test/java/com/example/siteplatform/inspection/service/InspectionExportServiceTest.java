package com.example.siteplatform.inspection.service;

import com.example.siteplatform.auth.entity.SysUser;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
        when(scopeService.isRequired(eq(box), any(LocalDate.class)))
                .thenAnswer(invocation -> !LocalDate.of(2026, 7, 1).equals(invocation.getArgument(1)));

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
}
