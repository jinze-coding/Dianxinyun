package com.example.siteplatform.inspection.service;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.inspection.dto.ProjectInspectionSettingRequest;
import com.example.siteplatform.inspection.entity.ProjectInspectionSetting;
import com.example.siteplatform.inspection.mapper.ProjectInspectionSettingMapper;
import com.example.siteplatform.project.constant.InspectionPermissionCodes;
import com.example.siteplatform.project.service.ProjectPermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectInspectionSettingServiceTest {

    @Mock private ProjectInspectionSettingMapper mapper;
    @Mock private ProjectPermissionService permissionService;

    private ProjectInspectionSettingService service;
    private SysUser operator;

    @BeforeEach
    void setUp() {
        service = new ProjectInspectionSettingService(mapper, permissionService);
        operator = new SysUser();
        operator.setId(7L);
        when(permissionService.hasInspectionPermission(
                7L, 1L, InspectionPermissionCodes.PERMISSION_MANAGE)).thenReturn(true);
    }

    @Test
    void createReturnsConflictWhenInsertDidNotTakeEffect() {
        when(mapper.selectOne(any())).thenReturn(null);
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
        when(mapper.selectOne(any())).thenReturn(existing);
        when(mapper.updateById(existing)).thenReturn(0);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.save(1L, request(), operator));

        assertEquals(409, error.getCode());
    }

    private ProjectInspectionSettingRequest request() {
        ProjectInspectionSettingRequest request = new ProjectInspectionSettingRequest();
        request.setReviewDueHours(24);
        request.setEnabled(true);
        return request;
    }
}
