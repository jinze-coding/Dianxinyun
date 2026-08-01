package com.example.siteplatform.device.controller;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.device.entity.DeviceInfo;
import com.example.siteplatform.device.mapper.DeviceInfoMapper;
import com.example.siteplatform.project.entity.ProjectInfo;
import com.example.siteplatform.project.service.ProjectPermissionService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceControllerAuthorizationTest {

    @Mock private DeviceInfoMapper deviceMapper;
    @Mock private AuthService authService;
    @Mock private ProjectPermissionService projectPermissionService;

    private DeviceController controller;
    private SysUser currentUser;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), DeviceInfoMapper.class.getName()),
                DeviceInfo.class);
        controller = new DeviceController();
        ReflectionTestUtils.setField(controller, "deviceMapper", deviceMapper);
        ReflectionTestUtils.setField(controller, "authService", authService);
        ReflectionTestUtils.setField(controller, "projectPermissionService", projectPermissionService);

        currentUser = new SysUser();
        currentUser.setId(9L);
        when(authService.getCurrentUser("Bearer token")).thenReturn(currentUser);
        lenient().when(deviceMapper.insert(any())).thenReturn(1);
        lenient().when(deviceMapper.updateById(any())).thenReturn(1);
        lenient().when(deviceMapper.deleteById(anyLong())).thenReturn(1);
    }

    @Test
    void towerCraneListWithoutProjectIdScopesOrdinaryUserToAuthorizedProjects() {
        ProjectInfo project = new ProjectInfo();
        project.setId(12L);
        when(projectPermissionService.isPlatformAdmin(9L)).thenReturn(false);
        when(projectPermissionService.getUserProjects(9L)).thenReturn(List.of(project));
        when(deviceMapper.selectList(any())).thenReturn(List.of());

        controller.getTowerCranes(null, "Bearer token");

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<LambdaQueryWrapper> wrapperCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(deviceMapper).selectList(wrapperCaptor.capture());
        String sql = wrapperCaptor.getValue().getSqlSegment();
        assertTrue(sql.contains("project_id IN"));
        assertTrue(sql.contains("device_type IN"));
        Map<String, Object> params = wrapperCaptor.getValue().getParamNameValuePairs();
        assertTrue(params.containsValue(12L));
        assertTrue(params.containsValue("tower_crane"));
        assertTrue(params.containsValue("塔吊"));
    }

    @Test
    void detailRejectsAccessOutsideAuthorizedProject() {
        DeviceInfo device = device(7L, 12L);
        when(deviceMapper.selectById(7L)).thenReturn(device);
        doThrow(BusinessException.forbidden("无项目访问权限"))
                .when(projectPermissionService).checkProjectPermission(9L, 12L);

        BusinessException error = assertThrows(BusinessException.class,
                () -> controller.getDeviceById(7L, "Bearer token"));

        assertEquals(403, error.getCode());
    }

    @Test
    void ordinaryProjectMemberCannotCreateDevice() {
        DeviceInfo request = device(88L, 12L);
        request.setDeviceType("塔吊");
        request.setDeleted(1);
        when(projectPermissionService.canManageProject(9L, 12L)).thenReturn(false);

        BusinessException error = assertThrows(BusinessException.class,
                () -> controller.createDevice(request, "Bearer token"));

        assertEquals(403, error.getCode());
        verify(deviceMapper, never()).insert(any());
    }

    @Test
    void managerCreateIgnoresClientControlledPersistenceFields() {
        DeviceInfo request = device(88L, 12L);
        request.setStatus("运行中");
        request.setDeleted(1);
        request.setCreateTime(LocalDateTime.of(2030, 1, 1, 0, 0));
        when(projectPermissionService.canManageProject(9L, 12L)).thenReturn(true);

        controller.createDevice(request, "Bearer token");

        ArgumentCaptor<DeviceInfo> deviceCaptor = ArgumentCaptor.forClass(DeviceInfo.class);
        verify(deviceMapper).insert(deviceCaptor.capture());
        DeviceInfo created = deviceCaptor.getValue();
        assertEquals(null, created.getId());
        assertEquals(12L, created.getProjectId());
        assertEquals(null, created.getDeleted());
        assertEquals("tower_crane", created.getDeviceType());
        assertEquals("running", created.getStatus());
        assertTrue(created.getCreateTime().isBefore(LocalDateTime.of(2030, 1, 1, 0, 0)));
    }

    @Test
    void updateRejectsUnknownDeviceStatus() {
        DeviceInfo existing = device(7L, 12L);
        when(deviceMapper.selectById(7L)).thenReturn(existing);
        when(projectPermissionService.canManageProject(9L, 12L)).thenReturn(true);

        DeviceInfo request = new DeviceInfo();
        request.setStatus("vendor_custom");

        BusinessException error = assertThrows(BusinessException.class,
                () -> controller.updateDevice(7L, request, "Bearer token"));

        assertEquals(400, error.getCode());
        verify(deviceMapper, never()).updateById(any());
    }

    @Test
    void updateUsesStoredProjectAndIgnoresOwnershipFieldsFromRequest() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 1, 8, 0);
        DeviceInfo existing = device(7L, 12L);
        existing.setDeleted(0);
        existing.setCreateTime(createdAt);
        when(deviceMapper.selectById(7L)).thenReturn(existing);
        when(projectPermissionService.canManageProject(9L, 12L)).thenReturn(true);

        DeviceInfo request = device(999L, 66L);
        request.setDeviceName("更新后的设备");
        request.setDeviceType("施工电梯");
        request.setDeleted(1);
        request.setCreateTime(LocalDateTime.of(2030, 1, 1, 0, 0));

        controller.updateDevice(7L, request, "Bearer token");

        ArgumentCaptor<DeviceInfo> deviceCaptor = ArgumentCaptor.forClass(DeviceInfo.class);
        verify(deviceMapper).updateById(deviceCaptor.capture());
        DeviceInfo updated = deviceCaptor.getValue();
        assertEquals(7L, updated.getId());
        assertEquals(12L, updated.getProjectId());
        assertEquals(0, updated.getDeleted());
        assertEquals(createdAt, updated.getCreateTime());
        assertEquals("更新后的设备", updated.getDeviceName());
        assertEquals("elevator", updated.getDeviceType());
        assertFalse(updated.getUpdateTime().isBefore(createdAt));
        verify(projectPermissionService).checkProjectPermission(9L, 12L);
        verify(projectPermissionService).canManageProject(9L, 12L);
    }

    @Test
    void createRejectsBlankNameAndOversizedRemark() {
        when(projectPermissionService.canManageProject(9L, 12L)).thenReturn(true);
        DeviceInfo blankName = device(null, 12L);
        blankName.setDeviceName("  ");
        DeviceInfo oversizedRemark = device(null, 12L);
        oversizedRemark.setRemark("a".repeat(501));

        BusinessException blankError = assertThrows(BusinessException.class,
                () -> controller.createDevice(blankName, "Bearer token"));
        BusinessException remarkError = assertThrows(BusinessException.class,
                () -> controller.createDevice(oversizedRemark, "Bearer token"));

        assertEquals(400, blankError.getCode());
        assertEquals(400, remarkError.getCode());
        verify(deviceMapper, never()).insert(any());
    }

    @Test
    void createReturnsConflictWhenInsertDidNotTakeEffect() {
        when(projectPermissionService.canManageProject(9L, 12L)).thenReturn(true);
        when(deviceMapper.insert(any())).thenReturn(0);

        BusinessException error = assertThrows(BusinessException.class,
                () -> controller.createDevice(device(null, 12L), "Bearer token"));

        assertEquals(409, error.getCode());
    }

    @Test
    void updateReturnsConflictWhenTargetDisappeared() {
        when(deviceMapper.selectById(7L)).thenReturn(device(7L, 12L));
        when(projectPermissionService.canManageProject(9L, 12L)).thenReturn(true);
        when(deviceMapper.updateById(any())).thenReturn(0);

        BusinessException error = assertThrows(BusinessException.class,
                () -> controller.updateDevice(7L, new DeviceInfo(), "Bearer token"));

        assertEquals(409, error.getCode());
    }

    @Test
    void deleteReturnsConflictWhenTargetDisappeared() {
        when(deviceMapper.selectById(7L)).thenReturn(device(7L, 12L));
        when(projectPermissionService.canManageProject(9L, 12L)).thenReturn(true);
        when(deviceMapper.deleteById(7L)).thenReturn(0);

        BusinessException error = assertThrows(BusinessException.class,
                () -> controller.deleteDevice(7L, "Bearer token"));

        assertEquals(409, error.getCode());
    }

    private DeviceInfo device(Long id, Long projectId) {
        DeviceInfo device = new DeviceInfo();
        device.setId(id);
        device.setProjectId(projectId);
        device.setDeviceName("测试设备");
        device.setDeviceType("tower_crane");
        device.setStatus("running");
        return device;
    }
}
