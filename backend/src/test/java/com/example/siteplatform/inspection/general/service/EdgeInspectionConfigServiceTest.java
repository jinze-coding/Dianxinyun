package com.example.siteplatform.inspection.general.service;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.inspection.general.dto.GeneralInspectionFeatureRequest;
import com.example.siteplatform.inspection.general.entity.GeneralInspectionActionLog;
import com.example.siteplatform.inspection.general.entity.GeneralInspectionPlan;
import com.example.siteplatform.inspection.general.entity.GeneralInspectionProjectSetting;
import com.example.siteplatform.inspection.general.mapper.*;
import com.example.siteplatform.project.mapper.SysUserProjectMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EdgeInspectionConfigServiceTest {

    @Test
    void inactivePointIsTerminalAndMustBeRecreated() {
        assertThatCode(() -> EdgeInspectionConfigService.requirePointStatusTransition("ACTIVE", "INACTIVE"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> EdgeInspectionConfigService.requirePointStatusTransition("INACTIVE", "ACTIVE"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    org.assertj.core.api.Assertions.assertThat(exception.getCode()).isEqualTo(409);
                    org.assertj.core.api.Assertions.assertThat(exception.getMessage()).contains("新建点位");
                });
    }

    @Test
    void featureReenableLocksPlanAndAtomicallyAdvancesGenerationLowerBound() {
        GeneralInspectionPermissionService permissionService = mock(GeneralInspectionPermissionService.class);
        GeneralInspectionConfigService legacyConfigService = mock(GeneralInspectionConfigService.class);
        GeneralInspectionProjectSettingMapper settingMapper = mock(GeneralInspectionProjectSettingMapper.class);
        GeneralInspectionPlanMapper planMapper = mock(GeneralInspectionPlanMapper.class);
        GeneralInspectionActionLogMapper actionLogMapper = mock(GeneralInspectionActionLogMapper.class);
        EdgeInspectionConfigService service = service(permissionService, legacyConfigService, settingMapper,
                planMapper, actionLogMapper);

        SysUser admin = user(9L, "admin");
        GeneralInspectionFeatureRequest request = new GeneralInspectionFeatureRequest();
        request.setEnabled(true);
        request.setExpectedVersion(4);
        GeneralInspectionProjectSetting disabled = new GeneralInspectionProjectSetting();
        disabled.setProjectId(2L);
        disabled.setEnabled(0);
        disabled.setVersion(4);
        GeneralInspectionProjectSetting enabled = new GeneralInspectionProjectSetting();
        enabled.setProjectId(2L);
        enabled.setEnabled(1);
        enabled.setVersion(5);
        GeneralInspectionPlan plan = new GeneralInspectionPlan();
        plan.setId(31L);
        plan.setProjectId(2L);
        plan.setGeneratedThroughTime(LocalDateTime.now().minusDays(3));
        plan.setEdgeGenerationLowerBoundTime(LocalDateTime.now().minusDays(10));
        plan.setVersion(7);

        when(settingMapper.lockPilotGuard()).thenReturn(1L);
        when(settingMapper.selectById(2L)).thenReturn(disabled);
        when(planMapper.selectEdgePlanForUpdate(2L)).thenReturn(plan);
        when(legacyConfigService.updateFeature(2L, request, admin)).thenReturn(enabled);
        when(planMapper.updateById(plan)).thenReturn(1);
        when(actionLogMapper.insert(any())).thenReturn(1);

        LocalDateTime before = LocalDateTime.now();
        assertThat(service.updateFeature(2L, request, admin)).isSameAs(enabled);
        LocalDateTime after = LocalDateTime.now();

        assertThat(plan.getGeneratedThroughTime()).isBetween(before, after);
        assertThat(plan.getEdgeGenerationLowerBoundTime()).isBetween(before, after);
        assertThat(plan.getVersion()).isEqualTo(8);
        verify(planMapper).selectEdgePlanForUpdate(2L);
        verify(planMapper).updateById(plan);
        ArgumentCaptor<GeneralInspectionActionLog> logCaptor =
                ArgumentCaptor.forClass(GeneralInspectionActionLog.class);
        verify(actionLogMapper).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getActionType()).isEqualTo("FEATURE_REENABLE_BOUND");
        assertThat(logCaptor.getValue().getToStatus())
                .isEqualTo(String.valueOf(plan.getEdgeGenerationLowerBoundTime()));
    }

    @Test
    void alreadyEnabledFeatureDoesNotMovePlanCursorSoNormalDowntimeCanCatchUp() {
        GeneralInspectionPermissionService permissionService = mock(GeneralInspectionPermissionService.class);
        GeneralInspectionConfigService legacyConfigService = mock(GeneralInspectionConfigService.class);
        GeneralInspectionProjectSettingMapper settingMapper = mock(GeneralInspectionProjectSettingMapper.class);
        GeneralInspectionPlanMapper planMapper = mock(GeneralInspectionPlanMapper.class);
        GeneralInspectionActionLogMapper actionLogMapper = mock(GeneralInspectionActionLogMapper.class);
        EdgeInspectionConfigService service = service(permissionService, legacyConfigService, settingMapper,
                planMapper, actionLogMapper);
        SysUser admin = user(9L, "admin");
        GeneralInspectionFeatureRequest request = new GeneralInspectionFeatureRequest();
        request.setEnabled(true);
        request.setExpectedVersion(5);
        GeneralInspectionProjectSetting existing = new GeneralInspectionProjectSetting();
        existing.setProjectId(2L);
        existing.setEnabled(1);
        existing.setVersion(5);
        when(settingMapper.lockPilotGuard()).thenReturn(1L);
        when(settingMapper.selectById(2L)).thenReturn(existing);
        when(legacyConfigService.updateFeature(2L, request, admin)).thenReturn(existing);

        service.updateFeature(2L, request, admin);

        verify(planMapper, never()).selectEdgePlanForUpdate(any());
        verify(planMapper, never()).updateById(any());
        verifyNoInteractions(actionLogMapper);
    }

    @Test
    void featureDisableLocksPlanToSerializeWithTaskGenerationButDoesNotMoveLowerBound() {
        GeneralInspectionPermissionService permissionService = mock(GeneralInspectionPermissionService.class);
        GeneralInspectionConfigService legacyConfigService = mock(GeneralInspectionConfigService.class);
        GeneralInspectionProjectSettingMapper settingMapper = mock(GeneralInspectionProjectSettingMapper.class);
        GeneralInspectionPlanMapper planMapper = mock(GeneralInspectionPlanMapper.class);
        GeneralInspectionActionLogMapper actionLogMapper = mock(GeneralInspectionActionLogMapper.class);
        EdgeInspectionConfigService service = service(permissionService, legacyConfigService, settingMapper,
                planMapper, actionLogMapper);
        SysUser admin = user(9L, "admin");
        GeneralInspectionFeatureRequest request = new GeneralInspectionFeatureRequest();
        request.setEnabled(false);
        request.setExpectedVersion(5);
        GeneralInspectionProjectSetting enabled = new GeneralInspectionProjectSetting();
        enabled.setProjectId(2L);
        enabled.setEnabled(1);
        enabled.setVersion(5);
        GeneralInspectionProjectSetting disabled = new GeneralInspectionProjectSetting();
        disabled.setProjectId(2L);
        disabled.setEnabled(0);
        disabled.setVersion(6);
        GeneralInspectionPlan plan = new GeneralInspectionPlan();
        plan.setId(31L);
        plan.setProjectId(2L);
        LocalDateTime cursor = LocalDateTime.of(2026, 8, 27, 18, 0);
        LocalDateTime lowerBound = LocalDateTime.of(2026, 8, 20, 10, 0);
        plan.setGeneratedThroughTime(cursor);
        plan.setEdgeGenerationLowerBoundTime(lowerBound);
        when(settingMapper.lockPilotGuard()).thenReturn(1L);
        when(settingMapper.selectById(2L)).thenReturn(enabled);
        when(planMapper.selectEdgePlanForUpdate(2L)).thenReturn(plan);
        when(legacyConfigService.updateFeature(2L, request, admin)).thenReturn(disabled);

        service.updateFeature(2L, request, admin);

        org.mockito.InOrder lockOrder = inOrder(planMapper, legacyConfigService);
        lockOrder.verify(planMapper).selectEdgePlanForUpdate(2L);
        lockOrder.verify(legacyConfigService).updateFeature(2L, request, admin);
        verify(planMapper, never()).updateById(any());
        assertThat(plan.getGeneratedThroughTime()).isEqualTo(cursor);
        assertThat(plan.getEdgeGenerationLowerBoundTime()).isEqualTo(lowerBound);
        verifyNoInteractions(actionLogMapper);
    }

    @Test
    void settingSavePreservesCursorUnlessPausedPlanIsResumed() {
        LocalDateTime oldCursor = LocalDateTime.of(2026, 8, 24, 10, 0);
        LocalDateTime savedAt = LocalDateTime.of(2026, 8, 26, 15, 30);

        assertThat(EdgeInspectionConfigService.settingSaveCursor(oldCursor, false, savedAt))
                .isEqualTo(oldCursor);
        assertThat(EdgeInspectionConfigService.settingSaveCursor(oldCursor, true, savedAt))
                .isEqualTo(savedAt);
        assertThat(EdgeInspectionConfigService.settingSaveCursor(savedAt.plusHours(2), true, savedAt))
                .isEqualTo(savedAt.plusHours(2));
    }

    private EdgeInspectionConfigService service(GeneralInspectionPermissionService permissionService,
                                                GeneralInspectionConfigService legacyConfigService,
                                                GeneralInspectionProjectSettingMapper settingMapper,
                                                GeneralInspectionPlanMapper planMapper,
                                                GeneralInspectionActionLogMapper actionLogMapper) {
        return new EdgeInspectionConfigService(
                permissionService,
                legacyConfigService,
                settingMapper,
                mock(GeneralInspectionTemplateMapper.class),
                mock(GeneralInspectionTemplateVersionMapper.class),
                mock(GeneralInspectionTemplateItemMapper.class),
                mock(GeneralInspectionPointCategoryMapper.class),
                mock(GeneralInspectionPointMapper.class),
                planMapper,
                mock(GeneralInspectionPlanVersionMapper.class),
                mock(GeneralInspectionTaskMapper.class),
                actionLogMapper,
                mock(SysUserMapper.class),
                mock(SysUserProjectMapper.class),
                new ObjectMapper());
    }

    private SysUser user(Long id, String username) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(username);
        return user;
    }
}
