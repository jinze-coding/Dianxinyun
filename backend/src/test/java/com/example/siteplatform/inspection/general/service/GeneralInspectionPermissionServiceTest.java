package com.example.siteplatform.inspection.general.service;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.project.constant.InspectionPermissionCodes;
import com.example.siteplatform.project.service.ProjectPermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class GeneralInspectionPermissionServiceTest {

    private ProjectPermissionService projectPermissionService;
    private GeneralInspectionPermissionService service;
    private SysUser user;

    @BeforeEach
    void setUp() {
        projectPermissionService = mock(ProjectPermissionService.class);
        service = new GeneralInspectionPermissionService(projectPermissionService);
        user = new SysUser();
        user.setId(9L);
    }

    @Test
    void projectSwitchIsNotPartOfEdgeInspectionAuthorization() {
        when(projectPermissionService.hasInspectionPermission(9L, 2L,
                InspectionPermissionCodes.EDGE_INSPECTION_VIEW)).thenReturn(true);

        service.requireView(2L, user);

        verify(projectPermissionService).checkProjectPermission(9L, 2L);
        verify(projectPermissionService).hasInspectionPermission(9L, 2L,
                InspectionPermissionCodes.EDGE_INSPECTION_VIEW);
    }

    @Test
    void submitRequiresDedicatedEdgeInspectionPermission() {
        when(projectPermissionService.hasInspectionPermission(9L, 2L,
                InspectionPermissionCodes.EDGE_INSPECTION_SUBMIT)).thenReturn(false);

        assertThatThrownBy(() -> service.requireSubmit(2L, user))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(403));

        when(projectPermissionService.hasInspectionPermission(9L, 2L,
                InspectionPermissionCodes.EDGE_INSPECTION_SUBMIT)).thenReturn(true);
        service.requireSubmit(2L, user);
    }

    @Test
    void workspaceSummaryAcceptsAnyDedicatedEdgePermission() {
        when(projectPermissionService.hasAnyInspectionPermission(eq(9L), eq(2L), any(String[].class)))
                .thenReturn(true);

        service.requireAnyEdgePermission(2L, user);

        verify(projectPermissionService).checkProjectPermission(9L, 2L);
        verify(projectPermissionService).hasAnyInspectionPermission(9L, 2L,
                InspectionPermissionCodes.EDGE_INSPECTION_VIEW,
                InspectionPermissionCodes.EDGE_INSPECTION_MANAGE,
                InspectionPermissionCodes.EDGE_INSPECTION_SUBMIT,
                InspectionPermissionCodes.EDGE_INSPECTION_RECTIFY,
                InspectionPermissionCodes.EDGE_INSPECTION_REVIEW,
                InspectionPermissionCodes.EDGE_INSPECTION_EXPORT);
    }

    @Test
    void workspaceSummaryRejectsMemberWithoutEdgePermission() {
        when(projectPermissionService.hasAnyInspectionPermission(eq(9L), eq(2L), any(String[].class)))
                .thenReturn(false);

        assertThatThrownBy(() -> service.requireAnyEdgePermission(2L, user))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(403));
    }

    @Test
    void exportRequiresDedicatedEdgeViewAndExportOnly() {
        when(projectPermissionService.hasInspectionPermission(9L, 2L,
                InspectionPermissionCodes.EDGE_INSPECTION_VIEW)).thenReturn(true);
        when(projectPermissionService.hasInspectionPermission(9L, 2L,
                InspectionPermissionCodes.EDGE_INSPECTION_EXPORT)).thenReturn(true);

        service.requireExport(2L, user);

        verify(projectPermissionService).checkProjectPermission(9L, 2L);
        verify(projectPermissionService).hasInspectionPermission(9L, 2L,
                InspectionPermissionCodes.EDGE_INSPECTION_VIEW);
        verify(projectPermissionService).hasInspectionPermission(9L, 2L,
                InspectionPermissionCodes.EDGE_INSPECTION_EXPORT);
        verify(projectPermissionService, never()).hasSystemPermission(anyLong(), anyLong(), anyString());
        verify(projectPermissionService, never()).hasInspectionPermission(9L, 2L,
                InspectionPermissionCodes.SUMMARY_EXPORT);
    }

    @Test
    void genericAndElectricBoxSummaryExportCannotAuthorizeEdgeExport() {
        when(projectPermissionService.hasInspectionPermission(9L, 2L,
                InspectionPermissionCodes.EDGE_INSPECTION_VIEW)).thenReturn(true);

        assertThatThrownBy(() -> service.requireExport(2L, user))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(403);
                    assertThat(exception.getMessage()).contains("临边巡检导出");
                });

        verify(projectPermissionService).hasInspectionPermission(9L, 2L,
                InspectionPermissionCodes.EDGE_INSPECTION_EXPORT);
        verify(projectPermissionService, never()).hasSystemPermission(anyLong(), anyLong(), anyString());
        verify(projectPermissionService, never()).hasInspectionPermission(9L, 2L,
                InspectionPermissionCodes.SUMMARY_EXPORT);
    }

    @Test
    void edgeExportPermissionDoesNotReplaceRequiredEdgeView() {
        when(projectPermissionService.hasInspectionPermission(9L, 2L,
                InspectionPermissionCodes.EDGE_INSPECTION_VIEW)).thenReturn(false);
        when(projectPermissionService.hasInspectionPermission(9L, 2L,
                InspectionPermissionCodes.EDGE_INSPECTION_EXPORT)).thenReturn(true);

        assertThatThrownBy(() -> service.requireExport(2L, user))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(403));

        verify(projectPermissionService, never()).hasInspectionPermission(9L, 2L,
                InspectionPermissionCodes.EDGE_INSPECTION_EXPORT);
    }
}
