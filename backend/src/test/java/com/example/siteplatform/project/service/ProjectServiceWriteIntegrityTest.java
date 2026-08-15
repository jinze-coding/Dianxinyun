package com.example.siteplatform.project.service;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.camera.mapper.CameraResourceMapper;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.device.mapper.DeviceInfoMapper;
import com.example.siteplatform.file.mapper.FileResourceMapper;
import com.example.siteplatform.log.entity.OperationLog;
import com.example.siteplatform.log.mapper.OperationLogMapper;
import com.example.siteplatform.project.dto.ProjectLocationUpdateRequest;
import com.example.siteplatform.project.dto.ProjectRouteImageVO;
import com.example.siteplatform.project.entity.ProjectInfo;
import com.example.siteplatform.project.mapper.ProjectInfoMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceWriteIntegrityTest {

    @Mock private ProjectInfoMapper projectMapper;
    @Mock private ProjectPermissionService permissionService;
    @Mock private OperationLogMapper operationLogMapper;
    @Mock private CameraResourceMapper cameraResourceMapper;
    @Mock private DeviceInfoMapper deviceInfoMapper;
    @Mock private FileResourceMapper fileResourceMapper;
    @Mock private ProjectRouteImageService projectRouteImageService;

    private ProjectService service;
    private SysUser platformAdmin;

    @BeforeEach
    void setUp() {
        service = new ProjectService();
        ReflectionTestUtils.setField(service, "projectMapper", projectMapper);
        ReflectionTestUtils.setField(service, "projectPermissionService", permissionService);
        ReflectionTestUtils.setField(service, "operationLogMapper", operationLogMapper);
        ReflectionTestUtils.setField(service, "cameraResourceMapper", cameraResourceMapper);
        ReflectionTestUtils.setField(service, "deviceInfoMapper", deviceInfoMapper);
        ReflectionTestUtils.setField(service, "fileResourceMapper", fileResourceMapper);
        ReflectionTestUtils.setField(service, "projectRouteImageService", projectRouteImageService);
        platformAdmin = new SysUser();
        platformAdmin.setId(9L);
        platformAdmin.setUsername("admin");
        lenient().when(permissionService.isPlatformAdmin(9L)).thenReturn(true);
        lenient().when(projectMapper.insert(any())).thenAnswer(invocation -> {
            ProjectInfo project = invocation.getArgument(0);
            project.setId(7L);
            return 1;
        });
        lenient().when(projectMapper.updateById(any())).thenReturn(1);
        lenient().when(operationLogMapper.insert(any())).thenReturn(1);
        lenient().when(projectRouteImageService.applyLocationAction(any(), any(), any(), any()))
                .thenReturn(ProjectRouteImageService.ACTION_KEEP);
    }

    @Test
    void addIgnoresClientControlledPersistenceFields() {
        ProjectInfo request = project(null);
        request.setId(99L);
        request.setProjectName("  智慧工地项目  ");
        request.setDeleted(1);
        request.setCreateTime(LocalDateTime.of(2030, 1, 1, 0, 0));

        ProjectInfo created = service.addProject(request, platformAdmin);

        ArgumentCaptor<ProjectInfo> captor = ArgumentCaptor.forClass(ProjectInfo.class);
        verify(projectMapper).insert(captor.capture());
        assertEquals(7L, created.getId());
        assertEquals("智慧工地项目", created.getProjectName());
        assertEquals("normal", created.getProjectStatus());
        assertNull(created.getDeleted());
        assertEquals(created, captor.getValue());
    }

    @Test
    void addRejectsBlankNameBeforeInsert() {
        ProjectInfo request = project(null);
        request.setProjectName("  ");

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.addProject(request, platformAdmin));

        assertEquals(400, error.getCode());
        verify(projectMapper, never()).insert(any());
    }

    @Test
    void addReturnsConflictWhenInsertDidNotTakeEffect() {
        doReturn(0).when(projectMapper).insert(any());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.addProject(project(null), platformAdmin));

        assertEquals(409, error.getCode());
    }

    @Test
    void updatePreservesPersistenceFieldsAndProjectIdentity() {
        ProjectInfo existing = project(7L);
        existing.setDeleted(0);
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 1, 8, 0);
        existing.setCreateTime(createdAt);
        when(projectMapper.selectByIdForUpdate(7L)).thenReturn(existing);
        ProjectInfo request = project(99L);
        request.setProjectName("  更新项目  ");
        request.setDeleted(1);
        request.setCreateTime(LocalDateTime.of(2030, 1, 1, 0, 0));

        ProjectInfo updated = service.updateProject(7L, request, platformAdmin);

        assertEquals(7L, updated.getId());
        assertEquals("更新项目", updated.getProjectName());
        assertEquals(0, updated.getDeleted());
        assertEquals(createdAt, updated.getCreateTime());
    }

    @Test
    void updateReturnsConflictWhenTargetDisappeared() {
        when(projectMapper.selectByIdForUpdate(7L)).thenReturn(project(7L));
        doReturn(0).when(projectMapper).updateById(any());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.updateProject(7L, project(null), platformAdmin));

        assertEquals(409, error.getCode());
    }

    @Test
    void generalProjectUpdateRejectsLocationChangesAndPreservesUniqueLocationWritePath() {
        ProjectInfo existing = project(7L);
        existing.setAddress("原地址");
        existing.setLongitude(new BigDecimal("121.500000"));
        existing.setLatitude(new BigDecimal("31.200000"));
        existing.setCoordinateType("BD09");
        when(projectMapper.selectByIdForUpdate(7L)).thenReturn(existing);
        ProjectInfo request = project(null);
        request.setAddress("新地址");

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.updateProject(7L, request, platformAdmin));

        assertEquals(400, error.getCode());
        assertEquals("请通过项目定位接口同步修改地址与导航点", error.getMessage());
        verify(projectMapper, never()).updateById(any());
    }

    @Test
    void generalProjectUpdateAllowsEquivalentLocationPayloadButDoesNotRewriteIt() {
        ProjectInfo existing = project(7L);
        existing.setProvince(" 上海市 ");
        existing.setAddress(" 原地址 ");
        existing.setLongitude(new BigDecimal("121.500000"));
        existing.setLatitude(new BigDecimal("31.200000"));
        existing.setCoordinateType("BD09");
        when(projectMapper.selectByIdForUpdate(7L)).thenReturn(existing);
        ProjectInfo request = project(null);
        request.setProjectName("更新名称");
        request.setProvince("上海市");
        request.setAddress("原地址");
        request.setLongitude(new BigDecimal("121.5"));
        request.setLatitude(new BigDecimal("31.20"));
        request.setCoordinateType("bd09");

        service.updateProject(7L, request, platformAdmin);

        assertEquals(" 上海市 ", existing.getProvince());
        assertEquals(" 原地址 ", existing.getAddress());
        assertEquals("BD09", existing.getCoordinateType());
        verify(projectMapper).updateById(existing);
    }

    @Test
    void newProjectMaySetItsInitialLocation() {
        ProjectInfo request = project(null);
        request.setAddress("初始地址");
        request.setLongitude(new BigDecimal("121.500000"));
        request.setLatitude(new BigDecimal("31.200000"));
        request.setCoordinateType("BD09");

        ProjectInfo created = service.addProject(request, platformAdmin);

        assertEquals("初始地址", created.getAddress());
        assertEquals(new BigDecimal("121.500000"), created.getLongitude());
        assertEquals(new BigDecimal("31.200000"), created.getLatitude());
    }

    @Test
    void mapDetailReturnsCurrentRouteImageMetadata() {
        ProjectInfo project = project(7L);
        when(projectMapper.selectById(7L)).thenReturn(project);
        ProjectRouteImageVO image = new ProjectRouteImageVO();
        image.setFileId(19L);
        image.setFileName("东门到访路线.jpg");
        when(projectRouteImageService.activeMetadata(7L)).thenReturn(image);

        var detail = service.getProjectMapDetail(7L, platformAdmin);

        assertEquals(image, detail.getRouteImage());
    }

    @Test
    void projectManagerCannotUpdateProjectLocation() {
        when(permissionService.isPlatformAdmin(9L)).thenReturn(false);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.updateProjectLocation(7L, location(), platformAdmin));

        assertEquals(403, error.getCode());
        verify(projectMapper, never()).selectByIdForUpdate(any());
        verify(projectMapper, never()).updateById(any());
        verify(operationLogMapper, never()).insert(any());
    }

    @Test
    void locationUpdateStopsBeforeAuditWhenProjectWriteFails() {
        when(projectMapper.selectByIdForUpdate(7L)).thenReturn(project(7L));
        doReturn(0).when(projectMapper).updateById(any());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.updateProjectLocation(7L, location(), platformAdmin));

        assertEquals(409, error.getCode());
        verify(operationLogMapper, never()).insert(any());
    }

    @Test
    void locationUpdateReturnsConflictWhenAuditWriteFails() {
        when(projectMapper.selectByIdForUpdate(7L)).thenReturn(project(7L));
        doReturn(0).when(operationLogMapper).insert(any());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.updateProjectLocation(7L, location(), platformAdmin));

        assertEquals(409, error.getCode());
    }

    @Test
    void locationUpdateLocksProjectChecksVersionAndIncrementsSharedProfileVersion() {
        ProjectInfo project = project(7L);
        project.setProfileVersion(4);
        when(projectMapper.selectByIdForUpdate(7L)).thenReturn(project);
        ProjectLocationUpdateRequest request = location();
        request.setExpectedVersion(4);

        var result = service.updateProjectLocation(7L, request, platformAdmin);

        assertEquals(5, project.getProfileVersion());
        assertEquals(5, result.getProfileVersion());
        assertEquals("浦东新区测试路1号", project.getAddress());
        verify(projectMapper).selectByIdForUpdate(7L);
        ArgumentCaptor<OperationLog> audit = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogMapper).insert(audit.capture());
        assertEquals("PROJECT", audit.getValue().getBusinessType());
        assertEquals(7L, audit.getValue().getBusinessId());
        assertEquals("UPDATE_PROJECT_LOCATION", audit.getValue().getOperationType());
        assertEquals(true, audit.getValue().getOperationDesc().contains("版本 5"));
    }

    @Test
    void locationUpdateBindsRouteImageBeforeAuditAndReturnsInternalMetadata() {
        ProjectInfo project = project(7L);
        when(projectMapper.selectByIdForUpdate(7L)).thenReturn(project);
        ProjectLocationUpdateRequest request = location();
        request.setRouteImageAction(ProjectRouteImageService.ACTION_REPLACE);
        request.setRouteImageFileId(19L);
        ProjectRouteImageVO image = new ProjectRouteImageVO();
        image.setFileId(19L);
        image.setFileName("东门到访路线.jpg");
        when(projectRouteImageService.applyLocationAction(
                7L, ProjectRouteImageService.ACTION_REPLACE, 19L, platformAdmin))
                .thenReturn(ProjectRouteImageService.ACTION_REPLACE);
        when(projectRouteImageService.activeMetadata(7L)).thenReturn(image);

        var result = service.updateProjectLocation(7L, request, platformAdmin);

        assertEquals(image, result.getRouteImage());
        InOrder writes = inOrder(projectMapper, projectRouteImageService, operationLogMapper);
        writes.verify(projectMapper).updateById(project);
        writes.verify(projectRouteImageService).applyLocationAction(
                7L, ProjectRouteImageService.ACTION_REPLACE, 19L, platformAdmin);
        writes.verify(operationLogMapper).insert(any());
    }

    @Test
    void routeImageConflictStopsAuditSoTransactionCanRollBackLocationWrite() {
        ProjectInfo project = project(7L);
        when(projectMapper.selectByIdForUpdate(7L)).thenReturn(project);
        ProjectLocationUpdateRequest request = location();
        request.setRouteImageAction(ProjectRouteImageService.ACTION_REPLACE);
        request.setRouteImageFileId(19L);
        org.mockito.Mockito.doThrow(BusinessException.of(409, "路线图已变化"))
                .when(projectRouteImageService).applyLocationAction(
                        7L, ProjectRouteImageService.ACTION_REPLACE, 19L, platformAdmin);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.updateProjectLocation(7L, request, platformAdmin));

        assertEquals(409, error.getCode());
        verify(projectMapper).updateById(project);
        verify(operationLogMapper, never()).insert(any());
    }

    @Test
    void locationUpdateRejectsStaleVersionBeforeProjectOrAuditWrite() {
        ProjectInfo project = project(7L);
        project.setProfileVersion(3);
        when(projectMapper.selectByIdForUpdate(7L)).thenReturn(project);
        ProjectLocationUpdateRequest request = location();
        request.setExpectedVersion(2);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.updateProjectLocation(7L, request, platformAdmin));

        assertEquals(409, error.getCode());
        verify(projectMapper, never()).updateById(any());
        verify(operationLogMapper, never()).insert(any());
    }

    @Test
    void locationUpdateRequiresVersionAndAddressBeforeWriting() {
        ProjectLocationUpdateRequest missingVersion = location();
        missingVersion.setExpectedVersion(null);

        BusinessException versionError = assertThrows(BusinessException.class,
                () -> service.updateProjectLocation(7L, missingVersion, platformAdmin));

        assertEquals(400, versionError.getCode());
        verify(projectMapper, never()).selectByIdForUpdate(any());

        ProjectInfo project = project(7L);
        when(projectMapper.selectByIdForUpdate(7L)).thenReturn(project);
        ProjectLocationUpdateRequest missingAddress = location();
        missingAddress.setAddress("  ");
        BusinessException addressError = assertThrows(BusinessException.class,
                () -> service.updateProjectLocation(7L, missingAddress, platformAdmin));
        assertEquals(400, addressError.getCode());
        verify(projectMapper, never()).updateById(any());
    }

    private ProjectInfo project(Long id) {
        ProjectInfo project = new ProjectInfo();
        project.setId(id);
        project.setProjectName("测试项目");
        project.setProjectStatus("normal");
        project.setProfileVersion(0);
        return project;
    }

    private ProjectLocationUpdateRequest location() {
        ProjectLocationUpdateRequest request = new ProjectLocationUpdateRequest();
        request.setLongitude(new BigDecimal("121.543743"));
        request.setLatitude(new BigDecimal("31.233568"));
        request.setCoordinateType("BD09");
        request.setProvince("上海市");
        request.setAddress("浦东新区测试路1号");
        request.setExpectedVersion(0);
        return request;
    }
}
