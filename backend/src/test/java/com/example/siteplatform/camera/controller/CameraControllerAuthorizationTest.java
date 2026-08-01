package com.example.siteplatform.camera.controller;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.camera.entity.CameraResource;
import com.example.siteplatform.camera.mapper.CameraResourceMapper;
import com.example.siteplatform.common.BusinessException;
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
import static org.junit.jupiter.api.Assertions.assertNull;
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
class CameraControllerAuthorizationTest {

    @Mock private CameraResourceMapper cameraMapper;
    @Mock private AuthService authService;
    @Mock private ProjectPermissionService projectPermissionService;

    private CameraController controller;
    private SysUser currentUser;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), CameraResourceMapper.class.getName()),
                CameraResource.class);
        controller = new CameraController();
        ReflectionTestUtils.setField(controller, "cameraMapper", cameraMapper);
        ReflectionTestUtils.setField(controller, "authService", authService);
        ReflectionTestUtils.setField(controller, "projectPermissionService", projectPermissionService);

        currentUser = new SysUser();
        currentUser.setId(9L);
        when(authService.getCurrentUser("Bearer token")).thenReturn(currentUser);
        lenient().when(cameraMapper.insert(any())).thenReturn(1);
        lenient().when(cameraMapper.updateById(any())).thenReturn(1);
        lenient().when(cameraMapper.deleteById(anyLong())).thenReturn(1);
    }

    @Test
    void listWithoutProjectIdScopesOrdinaryUserToAuthorizedProjects() {
        ProjectInfo project = new ProjectInfo();
        project.setId(12L);
        when(projectPermissionService.isPlatformAdmin(9L)).thenReturn(false);
        when(projectPermissionService.getUserProjects(9L)).thenReturn(List.of(project));
        when(cameraMapper.selectList(any())).thenReturn(List.of());

        controller.getCameraList(null, null, "Bearer token");

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<LambdaQueryWrapper> wrapperCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(cameraMapper).selectList(wrapperCaptor.capture());
        assertTrue(wrapperCaptor.getValue().getSqlSegment().contains("project_id IN"));
        assertTrue(wrapperCaptor.getValue().getParamNameValuePairs().containsValue(12L));
    }

    @Test
    void detailRejectsAccessOutsideAuthorizedProject() {
        CameraResource camera = camera(7L, 12L);
        when(cameraMapper.selectById(7L)).thenReturn(camera);
        doThrow(BusinessException.forbidden("无项目访问权限"))
                .when(projectPermissionService).checkProjectPermission(9L, 12L);

        BusinessException error = assertThrows(BusinessException.class,
                () -> controller.getCameraById(7L, "Bearer token"));

        assertEquals(403, error.getCode());
    }

    @Test
    void ordinaryProjectMemberCannotReadRawRtspCredentials() {
        CameraResource camera = camera(7L, 12L);
        camera.setRtspUrl("rtsp://device-user:device-password@10.0.0.8/live");
        when(cameraMapper.selectById(7L)).thenReturn(camera);
        when(projectPermissionService.canManageProject(9L, 12L)).thenReturn(false);

        Map<String, Object> response = controller.getCameraById(7L, "Bearer token").getData();

        assertEquals(true, response.get("rtspConfigured"));
        assertNull(response.get("rtspUrl"));
        verify(projectPermissionService).checkProjectPermission(9L, 12L);
    }

    @Test
    void projectManagerCanReadRawRtspAddressForAdministration() {
        CameraResource camera = camera(7L, 12L);
        camera.setRtspUrl("rtsp://device-user:device-password@10.0.0.8/live");
        when(cameraMapper.selectById(7L)).thenReturn(camera);
        when(projectPermissionService.canManageProject(9L, 12L)).thenReturn(true);

        Map<String, Object> response = controller.getCameraById(7L, "Bearer token").getData();

        assertEquals(true, response.get("rtspConfigured"));
        assertEquals(camera.getRtspUrl(), response.get("rtspUrl"));
    }

    @Test
    void ordinaryProjectMemberCannotCreateCamera() {
        CameraResource request = camera(88L, 12L);
        request.setDeleted(1);
        when(projectPermissionService.canManageProject(9L, 12L)).thenReturn(false);

        BusinessException error = assertThrows(BusinessException.class,
                () -> controller.createCamera(request, "Bearer token"));

        assertEquals(403, error.getCode());
        verify(cameraMapper, never()).insert(any());
    }

    @Test
    void managerCreateIgnoresClientControlledPersistenceFields() {
        CameraResource request = camera(88L, 12L);
        request.setDeleted(1);
        request.setCreateTime(LocalDateTime.of(2030, 1, 1, 0, 0));
        when(projectPermissionService.canManageProject(9L, 12L)).thenReturn(true);

        controller.createCamera(request, "Bearer token");

        ArgumentCaptor<CameraResource> cameraCaptor = ArgumentCaptor.forClass(CameraResource.class);
        verify(cameraMapper).insert(cameraCaptor.capture());
        CameraResource created = cameraCaptor.getValue();
        assertEquals(null, created.getId());
        assertEquals(12L, created.getProjectId());
        assertEquals(null, created.getDeleted());
        assertTrue(created.getCreateTime().isBefore(LocalDateTime.of(2030, 1, 1, 0, 0)));
    }

    @Test
    void updateUsesStoredProjectAndIgnoresOwnershipFieldsFromRequest() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 1, 8, 0);
        CameraResource existing = camera(7L, 12L);
        existing.setDeleted(0);
        existing.setCreateTime(createdAt);
        when(cameraMapper.selectById(7L)).thenReturn(existing);
        when(projectPermissionService.canManageProject(9L, 12L)).thenReturn(true);

        CameraResource request = camera(999L, 66L);
        request.setCameraName("更新后的摄像头");
        request.setDeleted(1);
        request.setCreateTime(LocalDateTime.of(2030, 1, 1, 0, 0));

        controller.updateCamera(7L, request, "Bearer token");

        ArgumentCaptor<CameraResource> cameraCaptor = ArgumentCaptor.forClass(CameraResource.class);
        verify(cameraMapper).updateById(cameraCaptor.capture());
        CameraResource updated = cameraCaptor.getValue();
        assertEquals(7L, updated.getId());
        assertEquals(12L, updated.getProjectId());
        assertEquals(0, updated.getDeleted());
        assertEquals(createdAt, updated.getCreateTime());
        assertEquals("更新后的摄像头", updated.getCameraName());
        assertFalse(updated.getUpdateTime().isBefore(createdAt));
        verify(projectPermissionService).checkProjectPermission(9L, 12L);
        verify(projectPermissionService).canManageProject(9L, 12L);
    }

    @Test
    void createRejectsBlankNameAndInvalidOnlineStatus() {
        when(projectPermissionService.canManageProject(9L, 12L)).thenReturn(true);
        CameraResource blankName = camera(null, 12L);
        blankName.setCameraName("  ");
        CameraResource invalidStatus = camera(null, 12L);
        invalidStatus.setOnlineStatus(2);

        BusinessException blankError = assertThrows(BusinessException.class,
                () -> controller.createCamera(blankName, "Bearer token"));
        BusinessException statusError = assertThrows(BusinessException.class,
                () -> controller.createCamera(invalidStatus, "Bearer token"));

        assertEquals(400, blankError.getCode());
        assertEquals(400, statusError.getCode());
        verify(cameraMapper, never()).insert(any());
    }

    @Test
    void createReturnsConflictWhenInsertDidNotTakeEffect() {
        when(projectPermissionService.canManageProject(9L, 12L)).thenReturn(true);
        when(cameraMapper.insert(any())).thenReturn(0);

        BusinessException error = assertThrows(BusinessException.class,
                () -> controller.createCamera(camera(null, 12L), "Bearer token"));

        assertEquals(409, error.getCode());
    }

    @Test
    void updateReturnsConflictWhenTargetDisappeared() {
        when(cameraMapper.selectById(7L)).thenReturn(camera(7L, 12L));
        when(projectPermissionService.canManageProject(9L, 12L)).thenReturn(true);
        when(cameraMapper.updateById(any())).thenReturn(0);

        BusinessException error = assertThrows(BusinessException.class,
                () -> controller.updateCamera(7L, new CameraResource(), "Bearer token"));

        assertEquals(409, error.getCode());
    }

    @Test
    void deleteReturnsConflictWhenTargetDisappeared() {
        when(cameraMapper.selectById(7L)).thenReturn(camera(7L, 12L));
        when(projectPermissionService.canManageProject(9L, 12L)).thenReturn(true);
        when(cameraMapper.deleteById(7L)).thenReturn(0);

        BusinessException error = assertThrows(BusinessException.class,
                () -> controller.deleteCamera(7L, "Bearer token"));

        assertEquals(409, error.getCode());
    }

    private CameraResource camera(Long id, Long projectId) {
        CameraResource camera = new CameraResource();
        camera.setId(id);
        camera.setProjectId(projectId);
        camera.setCameraName("测试摄像头");
        camera.setOnlineStatus(1);
        return camera;
    }
}
