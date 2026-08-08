package com.example.siteplatform.project.service;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.project.entity.ProjectInfo;
import com.example.siteplatform.project.mapper.ProjectInfoMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceDeletionTest {

    @Mock private ProjectInfoMapper projectMapper;
    @Mock private ProjectPermissionService permissionService;
    @Mock private JdbcTemplate jdbcTemplate;

    private ProjectService service;
    private SysUser platformAdmin;

    @BeforeEach
    void setUp() {
        service = new ProjectService();
        ReflectionTestUtils.setField(service, "projectMapper", projectMapper);
        ReflectionTestUtils.setField(service, "projectPermissionService", permissionService);
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbcTemplate);
        platformAdmin = new SysUser();
        platformAdmin.setId(9L);
    }

    @Test
    void rejectsDeletionWhenAnyProjectDataRemains() {
        ProjectInfo project = new ProjectInfo();
        project.setId(7L);
        when(permissionService.isPlatformAdmin(9L)).thenReturn(true);
        when(projectMapper.selectByIdForUpdate(7L)).thenReturn(project);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq(7L))).thenReturn(0L, 1L);

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.deleteProject(7L, platformAdmin));

        assertEquals(409, error.getCode());
        verify(projectMapper, never()).deleteById(7L);
    }

    @Test
    void deletesOnlyCompletelyEmptyProject() {
        ProjectInfo project = new ProjectInfo();
        project.setId(7L);
        when(permissionService.isPlatformAdmin(9L)).thenReturn(true);
        when(projectMapper.selectByIdForUpdate(7L)).thenReturn(project);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq(7L))).thenReturn(0L);
        when(projectMapper.deleteById(7L)).thenReturn(1);

        service.deleteProject(7L, platformAdmin);

        verify(projectMapper).deleteById(7L);
    }

    @Test
    void rejectsNonPlatformAdministratorBeforeReadingProjectData() {
        when(permissionService.isPlatformAdmin(9L)).thenReturn(false);

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.deleteProject(7L, platformAdmin));

        assertEquals(403, error.getCode());
        verify(projectMapper, never()).selectByIdForUpdate(7L);
        verify(jdbcTemplate, never()).queryForObject(anyString(), eq(Long.class), eq(7L));
    }
}
