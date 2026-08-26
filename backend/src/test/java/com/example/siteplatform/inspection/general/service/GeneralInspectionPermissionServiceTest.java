package com.example.siteplatform.inspection.general.service;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.inspection.general.entity.GeneralInspectionProjectSetting;
import com.example.siteplatform.inspection.general.mapper.GeneralInspectionProjectSettingMapper;
import com.example.siteplatform.project.constant.InspectionPermissionCodes;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.system.constant.SystemPermissionCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class GeneralInspectionPermissionServiceTest {

    private ProjectPermissionService projectPermissionService;
    private GeneralInspectionProjectSettingMapper settingMapper;
    private GeneralInspectionPermissionService service;
    private SysUser user;

    @BeforeEach
    void setUp() {
        projectPermissionService = mock(ProjectPermissionService.class);
        settingMapper = mock(GeneralInspectionProjectSettingMapper.class);
        service = new GeneralInspectionPermissionService(projectPermissionService, settingMapper);
        user = new SysUser();
        user.setId(9L);
    }

    @Test
    void absentProjectSettingIsDefaultOffAndReturnsReal403() {
        when(settingMapper.selectById(2L)).thenReturn(null);

        assertThatThrownBy(() -> service.requireView(2L, user))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(403);
                    assertThat(exception.getMessage()).contains("尚未启用");
                });

        verify(projectPermissionService).checkProjectPermission(9L, 2L);
        verify(projectPermissionService, never()).hasSystemPermission(anyLong(), anyLong(), anyString());
    }

    @Test
    void submitRequiresBothSystemAndCustomInspectionPermission() {
        GeneralInspectionProjectSetting setting = new GeneralInspectionProjectSetting();
        setting.setProjectId(2L);
        setting.setEnabled(1);
        when(settingMapper.selectById(2L)).thenReturn(setting);
        when(projectPermissionService.hasSystemPermission(9L, 2L,
                SystemPermissionCodes.INSPECTION_SUBMIT)).thenReturn(true);
        when(projectPermissionService.hasInspectionPermission(9L, 2L,
                InspectionPermissionCodes.CUSTOM_INSPECTION_SUBMIT)).thenReturn(false);

        assertThatThrownBy(() -> service.requireSubmit(2L, user))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(403));

        when(projectPermissionService.hasInspectionPermission(9L, 2L,
                InspectionPermissionCodes.CUSTOM_INSPECTION_SUBMIT)).thenReturn(true);
        service.requireSubmit(2L, user);
    }
}
