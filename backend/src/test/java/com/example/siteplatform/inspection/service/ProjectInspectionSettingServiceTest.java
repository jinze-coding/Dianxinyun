package com.example.siteplatform.inspection.service;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.electricbox.mapper.ElectricBoxMapper;
import com.example.siteplatform.electricbox.service.ElectricBoxInspectionScopeService;
import com.example.siteplatform.electricbox.entity.ElectricBox;
import com.example.siteplatform.inspection.dto.ProjectInspectionSettingRequest;
import com.example.siteplatform.inspection.entity.ProjectInspectionSetting;
import com.example.siteplatform.inspection.mapper.ProjectInspectionSettingMapper;
import com.example.siteplatform.inspection.vo.ProjectInspectionSettingVO;
import com.example.siteplatform.log.mapper.OperationLogMapper;
import com.example.siteplatform.project.constant.InspectionPermissionCodes;
import com.example.siteplatform.project.service.ProjectPermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectInspectionSettingServiceTest {

    @Mock private ProjectInspectionSettingMapper mapper;
    @Mock private ProjectPermissionService permissionService;
    @Mock private ElectricBoxMapper electricBoxMapper;
    @Mock private ElectricBoxInspectionScopeService inspectionScopeService;
    @Mock private SysUserMapper userMapper;
    @Mock private OperationLogMapper operationLogMapper;

    private ProjectInspectionSettingService service;
    private SysUser operator;

    @BeforeEach
    void setUp() {
        service = new ProjectInspectionSettingService(mapper, permissionService, electricBoxMapper,
                inspectionScopeService, userMapper, operationLogMapper);
        operator = new SysUser();
        operator.setId(7L);
        lenient().when(permissionService.hasInspectionPermission(
                7L, 1L, InspectionPermissionCodes.PERMISSION_MANAGE)).thenReturn(true);
    }

    @Test
    void createReturnsConflictWhenInsertDidNotTakeEffect() {
        when(mapper.insert(any())).thenReturn(0);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.save(1L, request(), operator));

        assertEquals(409, error.getCode());
    }

    @Test
    void updateReturnsConflictWhenRowChangedConcurrently() {
        ProjectInspectionSetting existing = new ProjectInspectionSetting();
        existing.setId(3L);
        existing.setProjectId(1L);
        when(mapper.selectByProjectIdForUpdate(1L)).thenReturn(existing);
        when(mapper.updateById(existing)).thenReturn(0);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.save(1L, request(), operator));

        assertEquals(409, error.getCode());
    }

    @Test
    void reminderUpdateRequiresExpectedVersion() {
        ProjectInspectionSetting existing = existingSetting(3);
        when(mapper.selectByProjectIdForUpdate(1L)).thenReturn(existing);
        ProjectInspectionSettingRequest request = request();
        request.setSubmissionReminderEnabled(true);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.save(1L, request, operator));

        assertEquals(409, error.getCode());
    }

    @Test
    void enablingReminderStartsEffectiveBoundaryAndIncrementsVersion() {
        ProjectInspectionSetting existing = existingSetting(3);
        when(mapper.selectByProjectIdForUpdate(1L)).thenReturn(existing);
        when(mapper.updateById(existing)).thenReturn(1);
        when(operationLogMapper.insert(any())).thenReturn(1);
        ProjectInspectionSettingRequest request = request();
        request.setSubmissionReminderEnabled(true);
        request.setExpectedVersion(3);

        ProjectInspectionSettingVO result = service.save(1L, request, operator);

        assertTrue(result.getSubmissionReminderEnabled());
        assertEquals(4, result.getVersion());
        assertNotNull(result.getReminderEffectiveTime());
        assertTrue(result.getReminderConfigurationHealthy());
    }

    @Test
    void legacyUpdateWithoutReminderFieldRemainsCompatible() {
        ProjectInspectionSetting existing = existingSetting(4);
        when(mapper.selectByProjectIdForUpdate(1L)).thenReturn(existing);
        when(mapper.updateById(existing)).thenReturn(1);
        when(operationLogMapper.insert(any())).thenReturn(1);

        ProjectInspectionSettingVO result = service.save(1L, request(), operator);

        assertEquals(5, result.getVersion());
        assertEquals(false, result.getSubmissionReminderEnabled());
    }

    @Test
    void nextReminderUsesNextStrictlyEligibleCutoff() {
        ProjectInspectionSetting setting = existingSetting(0);
        setting.setSubmissionReminderEnabled(1);
        setting.setDailyCutoffTime(java.time.LocalTime.of(18, 0));
        setting.setReminderEffectiveTime(LocalDateTime.of(2026, 8, 29, 10, 0));

        assertEquals(LocalDateTime.of(2026, 8, 30, 18, 0),
                ProjectInspectionSettingService.nextReminderTime(
                        setting, LocalDateTime.of(2026, 8, 29, 11, 0)));
        assertEquals(LocalDateTime.of(2026, 8, 30, 18, 0),
                ProjectInspectionSettingService.nextReminderTime(
                        setting, LocalDateTime.of(2026, 8, 29, 18, 1)));
    }

    @Test
    void getReportsInvalidResponsibleBoxWithoutBroadcastFallback() {
        ProjectInspectionSetting setting = existingSetting(2);
        when(mapper.selectOne(any())).thenReturn(setting);
        ElectricBox box = new ElectricBox();
        box.setId(10L);
        box.setProjectId(1L);
        box.setBoxCode("DX-01");
        box.setStatus("ACTIVE");
        when(electricBoxMapper.selectList(any())).thenReturn(List.of(box));
        when(inspectionScopeService.isRequired(eq(box), any())).thenReturn(true);

        ProjectInspectionSettingVO result = service.get(1L, operator);

        assertEquals(false, result.getReminderConfigurationHealthy());
        assertEquals(1, result.getInvalidReminderBoxCount());
        assertEquals("未配置责任电工", result.getReminderConfigurationIssues().get(0).getReason());
    }

    @Test
    void getRejectsProjectMemberWithoutSettingManagePermission() {
        SysUser projectMember = new SysUser();
        projectMember.setId(8L);
        when(permissionService.hasInspectionPermission(
                8L, 1L, InspectionPermissionCodes.PERMISSION_MANAGE)).thenReturn(false);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.get(1L, projectMember));

        assertEquals(403, error.getCode());
        verify(mapper, never()).selectOne(any());
        verify(electricBoxMapper, never()).selectList(any());
    }

    @Test
    void getDoesNotReuseSettingManagePermissionFromAnotherProject() {
        when(permissionService.hasInspectionPermission(
                7L, 2L, InspectionPermissionCodes.PERMISSION_MANAGE)).thenReturn(false);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.get(2L, operator));

        assertEquals(403, error.getCode());
        verify(mapper, never()).selectOne(any());
        verify(electricBoxMapper, never()).selectList(any());
    }

    private ProjectInspectionSetting existingSetting(int version) {
        ProjectInspectionSetting existing = new ProjectInspectionSetting();
        existing.setId(3L);
        existing.setProjectId(1L);
        existing.setDailyCutoffTime(java.time.LocalTime.of(18, 0));
        existing.setSubmissionReminderEnabled(0);
        existing.setVersion(version);
        return existing;
    }

    private ProjectInspectionSettingRequest request() {
        ProjectInspectionSettingRequest request = new ProjectInspectionSettingRequest();
        request.setReviewDueHours(24);
        request.setEnabled(true);
        return request;
    }
}
