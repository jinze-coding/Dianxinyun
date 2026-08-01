package com.example.siteplatform.project.service;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.log.mapper.OperationLogMapper;
import com.example.siteplatform.project.dto.ProjectLocationUpdateRequest;
import com.example.siteplatform.project.entity.ProjectInfo;
import com.example.siteplatform.project.mapper.ProjectInfoMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceWriteIntegrityTest {

    @Mock private ProjectInfoMapper projectMapper;
    @Mock private ProjectPermissionService permissionService;
    @Mock private OperationLogMapper operationLogMapper;

    private ProjectService service;
    private SysUser platformAdmin;

    @BeforeEach
    void setUp() {
        service = new ProjectService();
        ReflectionTestUtils.setField(service, "projectMapper", projectMapper);
        ReflectionTestUtils.setField(service, "projectPermissionService", permissionService);
        ReflectionTestUtils.setField(service, "operationLogMapper", operationLogMapper);
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
        when(projectMapper.selectById(7L)).thenReturn(existing);
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
        when(projectMapper.selectById(7L)).thenReturn(project(7L));
        doReturn(0).when(projectMapper).updateById(any());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.updateProject(7L, project(null), platformAdmin));

        assertEquals(409, error.getCode());
    }

    @Test
    void locationUpdateStopsBeforeAuditWhenProjectWriteFails() {
        when(permissionService.canManageProject(9L, 7L)).thenReturn(true);
        when(projectMapper.selectById(7L)).thenReturn(project(7L));
        doReturn(0).when(projectMapper).updateById(any());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.updateProjectLocation(7L, location(), platformAdmin));

        assertEquals(409, error.getCode());
        verify(operationLogMapper, never()).insert(any());
    }

    @Test
    void locationUpdateReturnsConflictWhenAuditWriteFails() {
        when(permissionService.canManageProject(9L, 7L)).thenReturn(true);
        when(projectMapper.selectById(7L)).thenReturn(project(7L));
        doReturn(0).when(operationLogMapper).insert(any());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.updateProjectLocation(7L, location(), platformAdmin));

        assertEquals(409, error.getCode());
    }

    private ProjectInfo project(Long id) {
        ProjectInfo project = new ProjectInfo();
        project.setId(id);
        project.setProjectName("测试项目");
        project.setProjectStatus("normal");
        return project;
    }

    private ProjectLocationUpdateRequest location() {
        ProjectLocationUpdateRequest request = new ProjectLocationUpdateRequest();
        request.setLongitude(new BigDecimal("121.543743"));
        request.setLatitude(new BigDecimal("31.233568"));
        request.setCoordinateType("BD09");
        request.setProvince("上海市");
        request.setAddress("浦东新区测试路1号");
        return request;
    }
}
