package com.example.siteplatform.person.service;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.file.entity.FileResource;
import com.example.siteplatform.file.mapper.FileResourceMapper;
import com.example.siteplatform.person.constant.PersonnelStatus;
import com.example.siteplatform.person.dto.PersonCertificateRequest;
import com.example.siteplatform.person.dto.PersonMovementRequest;
import com.example.siteplatform.person.entity.PersonCertificate;
import com.example.siteplatform.person.entity.PersonEntryExitLog;
import com.example.siteplatform.person.entity.TemporaryPerson;
import com.example.siteplatform.person.mapper.PersonCertificateMapper;
import com.example.siteplatform.person.mapper.PersonEntryExitLogMapper;
import com.example.siteplatform.person.mapper.TemporaryPersonMapper;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PersonnelWorkflowServiceTest {
    private TemporaryPersonMapper personMapper;
    private PersonEntryExitLogMapper movementMapper;
    private PersonCertificateMapper certificateMapper;
    private FileResourceMapper fileMapper;
    private ProjectPermissionService permissionService;
    private PersonnelWorkflowService service;
    private SysUser operator;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), FileResourceMapper.class.getName()),
                FileResource.class);
        personMapper = mock(TemporaryPersonMapper.class);
        movementMapper = mock(PersonEntryExitLogMapper.class);
        certificateMapper = mock(PersonCertificateMapper.class);
        fileMapper = mock(FileResourceMapper.class);
        permissionService = mock(ProjectPermissionService.class);
        service = new PersonnelWorkflowService(personMapper, movementMapper,
                certificateMapper, fileMapper, permissionService);
        operator = new SysUser();
        operator.setId(1L);
        operator.setUsername("admin");

        lenient().when(personMapper.insert(any())).thenAnswer(invocation -> {
            TemporaryPerson person = invocation.getArgument(0);
            person.setId(11L);
            return 1;
        });
        lenient().when(personMapper.updateById(any())).thenReturn(1);
        lenient().when(personMapper.deleteById(anyLong())).thenReturn(1);
        lenient().when(movementMapper.insert(any())).thenReturn(1);
        lenient().when(certificateMapper.insert(any())).thenAnswer(invocation -> {
            PersonCertificate certificate = invocation.getArgument(0);
            certificate.setId(21L);
            return 1;
        });
        lenient().when(certificateMapper.updateById(any())).thenReturn(1);
        lenient().when(certificateMapper.deleteById(anyLong())).thenReturn(1);
        lenient().when(fileMapper.update(any(), any())).thenReturn(1);
    }

    @Test
    void exitWritesStatusAndMovementInOneWorkflow() {
        TemporaryPerson person = person(11L);
        person.setStatus(PersonnelStatus.EDUCATED);
        when(personMapper.selectById(11L)).thenReturn(person);
        when(permissionService.canManagePersonnel(1L, 1L)).thenReturn(true);

        PersonEntryExitLog result = service.move(11L, "EXIT", new PersonMovementRequest(), operator);

        assertEquals(PersonnelStatus.LEFT, person.getStatus());
        assertEquals("EXIT", result.getActionType());
        verify(personMapper).updateById(person);
        verify(movementMapper).insert(any(PersonEntryExitLog.class));
    }

    @Test
    void createIgnoresClientControlledPersistenceFields() {
        when(permissionService.canManagePersonnel(1L, 1L)).thenReturn(true);
        TemporaryPerson request = person(99L);
        request.setName("  张三  ");
        request.setStatus(PersonnelStatus.LEFT);
        request.setDeleted(1);
        request.setCreateTime(LocalDateTime.of(2030, 1, 1, 0, 0));

        TemporaryPerson created = service.create(request, operator);

        ArgumentCaptor<TemporaryPerson> captor = ArgumentCaptor.forClass(TemporaryPerson.class);
        verify(personMapper).insert(captor.capture());
        assertEquals(11L, created.getId());
        assertEquals(1L, created.getProjectId());
        assertEquals("张三", created.getName());
        assertEquals(PersonnelStatus.WAIT_EDUCATION, created.getStatus());
        assertNull(created.getDeleted());
        assertEquals(created, captor.getValue());
    }

    @Test
    void createStopsBeforeMovementWhenPersonInsertDidNotTakeEffect() {
        when(permissionService.canManagePersonnel(1L, 1L)).thenReturn(true);
        doReturn(0).when(personMapper).insert(any());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.create(person(null), operator));

        assertEquals(409, error.getCode());
        verify(movementMapper, never()).insert(any());
    }

    @Test
    void updatePreservesProjectStatusAndPersistenceFields() {
        TemporaryPerson existing = person(11L);
        existing.setStatus(PersonnelStatus.EDUCATED);
        existing.setDeleted(0);
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 1, 8, 0);
        existing.setCreateTime(createdAt);
        when(personMapper.selectById(11L)).thenReturn(existing);
        when(permissionService.canManagePersonnel(1L, 1L)).thenReturn(true);
        TemporaryPerson request = person(99L);
        request.setProjectId(66L);
        request.setName("  李四  ");
        request.setStatus(null);
        request.setDeleted(1);
        request.setCreateTime(LocalDateTime.of(2030, 1, 1, 0, 0));

        TemporaryPerson updated = service.update(11L, request, operator);

        assertEquals(11L, updated.getId());
        assertEquals(1L, updated.getProjectId());
        assertEquals(PersonnelStatus.EDUCATED, updated.getStatus());
        assertEquals(0, updated.getDeleted());
        assertEquals(createdAt, updated.getCreateTime());
        assertEquals("李四", updated.getName());
    }

    @Test
    void moveReturnsConflictAndDoesNotWriteLogWhenStatusUpdateFails() {
        TemporaryPerson person = person(11L);
        person.setStatus(PersonnelStatus.EDUCATED);
        when(personMapper.selectById(11L)).thenReturn(person);
        when(permissionService.canManagePersonnel(1L, 1L)).thenReturn(true);
        when(personMapper.updateById(person)).thenReturn(0);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.move(11L, "EXIT", new PersonMovementRequest(), operator));

        assertEquals(409, error.getCode());
        verify(movementMapper, never()).insert(any());
    }

    @Test
    void createCertificateRejectsUnboundFileUploadedByAnotherUser() {
        when(personMapper.selectById(11L)).thenReturn(person(11L));
        when(permissionService.canManagePersonnel(1L, 1L)).thenReturn(true);
        FileResource file = certificateFile(31L, 2L);
        when(fileMapper.selectById(31L)).thenReturn(file);
        PersonCertificateRequest request = certificateRequest(31L);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.createCertificate(11L, request, operator));

        assertEquals(403, error.getCode());
        verify(certificateMapper, never()).insert(any());
    }

    @Test
    void createCertificateReturnsConflictWhenInsertDidNotTakeEffect() {
        when(personMapper.selectById(11L)).thenReturn(person(11L));
        when(permissionService.canManagePersonnel(1L, 1L)).thenReturn(true);
        doReturn(0).when(certificateMapper).insert(any());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.createCertificate(11L, certificateRequest(null), operator));

        assertEquals(409, error.getCode());
    }

    @Test
    void createCertificateRollsBackWhenFileBindingDidNotTakeEffect() {
        when(personMapper.selectById(11L)).thenReturn(person(11L));
        when(permissionService.canManagePersonnel(1L, 1L)).thenReturn(true);
        when(fileMapper.selectById(31L)).thenReturn(certificateFile(31L, 1L));
        when(fileMapper.update(any(), any())).thenReturn(0);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.createCertificate(11L, certificateRequest(31L), operator));

        assertEquals(409, error.getCode());
    }

    @Test
    void deleteReturnsConflictWhenTargetDisappeared() {
        when(personMapper.selectById(11L)).thenReturn(person(11L));
        when(permissionService.canManagePersonnel(1L, 1L)).thenReturn(true);
        when(personMapper.deleteById(11L)).thenReturn(0);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.delete(11L, operator));

        assertEquals(409, error.getCode());
    }

    private TemporaryPerson person(Long id) {
        TemporaryPerson person = new TemporaryPerson();
        person.setId(id);
        person.setProjectId(1L);
        person.setName("测试人员");
        person.setStatus(PersonnelStatus.WAIT_EDUCATION);
        return person;
    }

    private PersonCertificateRequest certificateRequest(Long fileId) {
        PersonCertificateRequest request = new PersonCertificateRequest();
        request.setCertificateType("特种作业证");
        request.setCertificateNo("CERT-001");
        request.setFileId(fileId);
        return request;
    }

    private FileResource certificateFile(Long id, Long uploaderId) {
        FileResource file = new FileResource();
        file.setId(id);
        file.setProjectId(1L);
        file.setUploaderId(uploaderId);
        file.setBusinessType("PERSON_CERTIFICATE");
        file.setDeleted(0);
        return file;
    }
}
