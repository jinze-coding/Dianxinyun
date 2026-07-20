package com.example.siteplatform.electricbox.service;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.electricbox.entity.ElectricBox;
import com.example.siteplatform.electricbox.mapper.ElectricBoxMapper;
import com.example.siteplatform.electricbox.vo.UnifiedElectricBoxScanVO;
import com.example.siteplatform.inspection.entity.InspectionRecord;
import com.example.siteplatform.inspection.mapper.InspectionRecordMapper;
import com.example.siteplatform.project.constant.InspectionPermissionCodes;
import com.example.siteplatform.project.service.ProjectPermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnifiedElectricBoxScanServiceTest {

    @Mock
    private ElectricBoxMapper electricBoxMapper;
    @Mock
    private InspectionRecordMapper inspectionRecordMapper;
    @Mock
    private ProjectPermissionService permissionService;
    @Mock
    private ElectricBoxInspectionScopeService scopeService;

    private UnifiedElectricBoxScanService service;
    private ElectricBox box;

    @BeforeEach
    void setUp() {
        service = new UnifiedElectricBoxScanService(
                electricBoxMapper, inspectionRecordMapper, permissionService, scopeService);
        box = new ElectricBox();
        box.setId(10L);
        box.setProjectId(1L);
        box.setPublicCode("PUB-TEST-001");
        box.setBoxCode("EB-TEST-001");
        box.setInstallLocation("测试区域");
        box.setResponsibleElectricianId(2L);
        box.setStatus("ACTIVE");
        box.setPublicAccessEnabled(1);
        when(electricBoxMapper.selectOne(any())).thenReturn(box);
    }

    @Test
    void projectInspectorOpensReadonlyInspectionPageWhenTodayRecordExists() {
        InspectionRecord todayRecord = new InspectionRecord();
        todayRecord.setId(99L);
        todayRecord.setCheckDate(LocalDate.now());
        when(inspectionRecordMapper.selectOne(any())).thenReturn(todayRecord);
        when(scopeService.isRequired(eq(box), any(LocalDate.class))).thenReturn(true);
        when(permissionService.getInspectionPermissionCodes(2L, 1L))
                .thenReturn(List.of(InspectionPermissionCodes.INSPECTION_DAILY_SUBMIT));
        when(permissionService.hasInspectionPermission(
                2L, 1L, InspectionPermissionCodes.INSPECTION_DAILY_SUBMIT)).thenReturn(true);

        SysUser user = new SysUser();
        user.setId(2L);
        UnifiedElectricBoxScanVO result = service.resolve("B:PUB-TEST-001", user);

        assertEquals("START_INSPECTION", result.getDirectAction());
        assertEquals(99L, result.getTodayRecordId());
    }

    @Test
    void projectInspectorCanOpenUnassignedBox() {
        when(inspectionRecordMapper.selectOne(any())).thenReturn(null);
        when(scopeService.isRequired(eq(box), any(LocalDate.class))).thenReturn(true);
        when(permissionService.getInspectionPermissionCodes(3L, 1L))
                .thenReturn(List.of(InspectionPermissionCodes.INSPECTION_DAILY_SUBMIT));
        when(permissionService.hasInspectionPermission(
                3L, 1L, InspectionPermissionCodes.INSPECTION_DAILY_SUBMIT)).thenReturn(true);

        SysUser user = new SysUser();
        user.setId(3L);
        UnifiedElectricBoxScanVO result = service.resolve("B:PUB-TEST-001", user);

        assertEquals("START_INSPECTION", result.getDirectAction());
        assertEquals(10L, result.getElectricBoxId());
        assertNull(result.getTodayRecordId());
    }

    @Test
    void publicScanNeverExposesInternalRecordId() {
        InspectionRecord todayRecord = new InspectionRecord();
        todayRecord.setId(99L);
        when(inspectionRecordMapper.selectOne(any())).thenReturn(todayRecord);
        when(scopeService.isRequired(eq(box), any(LocalDate.class))).thenReturn(true);

        UnifiedElectricBoxScanVO result = service.resolve("B:PUB-TEST-001", null);

        assertEquals("VIEW_PUBLIC_MONTHLY", result.getDirectAction());
        assertNull(result.getTodayRecordId());
    }
}
