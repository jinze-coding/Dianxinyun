package com.example.siteplatform.person.service;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.file.mapper.FileResourceMapper;
import com.example.siteplatform.person.constant.PersonnelStatus;
import com.example.siteplatform.person.dto.PersonMovementRequest;
import com.example.siteplatform.person.entity.PersonEntryExitLog;
import com.example.siteplatform.person.entity.TemporaryPerson;
import com.example.siteplatform.person.mapper.PersonCertificateMapper;
import com.example.siteplatform.person.mapper.PersonEntryExitLogMapper;
import com.example.siteplatform.person.mapper.TemporaryPersonMapper;
import com.example.siteplatform.project.service.ProjectPermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PersonnelWorkflowServiceTest {
    private TemporaryPersonMapper personMapper;
    private PersonEntryExitLogMapper movementMapper;
    private ProjectPermissionService permissionService;
    private PersonnelWorkflowService service;

    @BeforeEach
    void setUp() {
        personMapper = mock(TemporaryPersonMapper.class);
        movementMapper = mock(PersonEntryExitLogMapper.class);
        permissionService = mock(ProjectPermissionService.class);
        service = new PersonnelWorkflowService(personMapper, movementMapper,
                mock(PersonCertificateMapper.class), mock(FileResourceMapper.class), permissionService);
    }

    @Test
    void exitWritesStatusAndMovementInOneWorkflow() {
        TemporaryPerson person = new TemporaryPerson();
        person.setId(11L);
        person.setProjectId(1L);
        person.setStatus(PersonnelStatus.EDUCATED);
        when(personMapper.selectById(11L)).thenReturn(person);
        when(permissionService.canManagePersonnel(1L, 1L)).thenReturn(true);

        SysUser operator = new SysUser();
        operator.setId(1L);
        operator.setUsername("admin");
        PersonEntryExitLog result = service.move(11L, "EXIT", new PersonMovementRequest(), operator);

        assertEquals(PersonnelStatus.LEFT, person.getStatus());
        assertEquals("EXIT", result.getActionType());
        verify(personMapper).updateById(person);
        verify(movementMapper).insert(any(PersonEntryExitLog.class));
    }
}
