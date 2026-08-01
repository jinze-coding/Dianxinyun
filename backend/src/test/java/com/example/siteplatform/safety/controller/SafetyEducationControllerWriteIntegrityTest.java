package com.example.siteplatform.safety.controller;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.file.constant.FileStatus;
import com.example.siteplatform.file.entity.FileResource;
import com.example.siteplatform.file.mapper.FileResourceMapper;
import com.example.siteplatform.person.constant.PersonnelStatus;
import com.example.siteplatform.person.entity.TemporaryPerson;
import com.example.siteplatform.person.mapper.TemporaryPersonMapper;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.safety.entity.SafetyEducationBatch;
import com.example.siteplatform.safety.entity.SafetyEducationPerson;
import com.example.siteplatform.safety.mapper.SafetyEducationBatchMapper;
import com.example.siteplatform.safety.mapper.SafetyEducationPersonMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SafetyEducationControllerWriteIntegrityTest {

    @Mock private SafetyEducationBatchMapper batchMapper;
    @Mock private SafetyEducationPersonMapper relationMapper;
    @Mock private TemporaryPersonMapper personnelMapper;
    @Mock private AuthService authService;
    @Mock private FileResourceMapper fileMapper;
    @Mock private ProjectPermissionService permissionService;

    private SafetyEducationController controller;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), FileResourceMapper.class.getName()),
                FileResource.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), TemporaryPersonMapper.class.getName()),
                TemporaryPerson.class);
        controller = new SafetyEducationController();
        ReflectionTestUtils.setField(controller, "batchMapper", batchMapper);
        ReflectionTestUtils.setField(controller, "personRelationMapper", relationMapper);
        ReflectionTestUtils.setField(controller, "personnelMapper", personnelMapper);
        ReflectionTestUtils.setField(controller, "authService", authService);
        ReflectionTestUtils.setField(controller, "fileMapper", fileMapper);
        ReflectionTestUtils.setField(controller, "projectPermissionService", permissionService);

        SysUser operator = new SysUser();
        operator.setId(1L);
        operator.setUsername("admin");
        lenient().when(authService.getCurrentUser("Bearer token")).thenReturn(operator);
        lenient().when(permissionService.canManagePersonnel(1L, 1L)).thenReturn(true);
        lenient().when(personnelMapper.selectById(2L)).thenReturn(person(2L));
        lenient().doAnswer(invocation -> {
            SafetyEducationBatch batch = invocation.getArgument(0);
            batch.setId(10L);
            return 1;
        }).when(batchMapper).insert(any());
        lenient().when(batchMapper.updateById(any())).thenReturn(1);
        lenient().when(batchMapper.deleteById(anyLong())).thenReturn(1);
        lenient().when(relationMapper.insert(any())).thenReturn(1);
        lenient().when(relationMapper.updateById(any())).thenReturn(1);
        lenient().when(fileMapper.update(any(), any())).thenReturn(1);
    }

    @Test
    void createRejectsDuplicatePeopleBeforeAnyWrite() {
        Map<String, Object> params = validParams();
        params.put("personIds", List.of(2, 2));

        BusinessException error = assertThrows(BusinessException.class,
                () -> controller.createBatch(params, "Bearer token"));

        assertEquals(400, error.getCode());
        verify(batchMapper, never()).insert(any());
    }

    @Test
    void createUsesServerControlledStatusAndWritesValidatedRelations() {
        Map<String, Object> params = validParams();
        params.put("status", "COMPLETED");

        controller.createBatch(params, "Bearer token");

        ArgumentCaptor<SafetyEducationBatch> batchCaptor = ArgumentCaptor.forClass(SafetyEducationBatch.class);
        verify(batchMapper).insert(batchCaptor.capture());
        assertEquals("IN_PROGRESS", batchCaptor.getValue().getStatus());
        assertEquals("入场三级教育", batchCaptor.getValue().getBatchName());
        ArgumentCaptor<SafetyEducationPerson> relationCaptor = ArgumentCaptor.forClass(SafetyEducationPerson.class);
        verify(relationMapper).insert(relationCaptor.capture());
        assertEquals(10L, relationCaptor.getValue().getBatchId());
        assertEquals(2L, relationCaptor.getValue().getPersonId());
    }

    @Test
    void createRejectsUnboundFileUploadedByAnotherUser() {
        Map<String, Object> params = validParams();
        params.put("fileIds", List.of(31));
        when(fileMapper.selectById(31L)).thenReturn(file(31L, 9L));

        BusinessException error = assertThrows(BusinessException.class,
                () -> controller.createBatch(params, "Bearer token"));

        assertEquals(403, error.getCode());
        verify(batchMapper, never()).insert(any());
    }

    @Test
    void createReturnsConflictWhenBatchInsertDidNotTakeEffect() {
        doReturn(0).when(batchMapper).insert(any());

        BusinessException error = assertThrows(BusinessException.class,
                () -> controller.createBatch(validParams(), "Bearer token"));

        assertEquals(409, error.getCode());
        verify(relationMapper, never()).insert(any());
    }

    @Test
    void createReturnsConflictWhenFileBindingDidNotTakeEffect() {
        Map<String, Object> params = validParams();
        params.put("fileIds", List.of(31));
        when(fileMapper.selectById(31L)).thenReturn(file(31L, 1L));
        doReturn(0).when(fileMapper).update(any(), any());

        BusinessException error = assertThrows(BusinessException.class,
                () -> controller.createBatch(params, "Bearer token"));

        assertEquals(409, error.getCode());
        verify(relationMapper, never()).insert(any());
    }

    @Test
    void updateReturnsConflictWhenTargetDisappeared() {
        when(batchMapper.selectById(10L)).thenReturn(batch(10L));
        doReturn(0).when(batchMapper).updateById(any());

        BusinessException error = assertThrows(BusinessException.class,
                () -> controller.updateBatch(10L, Map.of("batchName", "更新名称"), "Bearer token"));

        assertEquals(409, error.getCode());
    }

    @Test
    void completeStopsBeforeRelationUpdateWhenPersonStatusWriteFails() {
        SafetyEducationBatch batch = batch(10L);
        batch.setStatus("IN_PROGRESS");
        when(batchMapper.selectById(10L)).thenReturn(batch);
        SafetyEducationPerson relation = new SafetyEducationPerson();
        relation.setId(20L);
        relation.setBatchId(10L);
        relation.setPersonId(2L);
        when(relationMapper.selectList(any())).thenReturn(List.of(relation));
        doReturn(0).when(personnelMapper).update(any(), any());

        BusinessException error = assertThrows(BusinessException.class,
                () -> controller.markComplete(10L, "Bearer token"));

        assertEquals(409, error.getCode());
        verify(relationMapper, never()).updateById(any());
    }

    @Test
    void deleteReturnsConflictWhenRelationsWereOnlyPartiallyDeleted() {
        when(batchMapper.selectById(10L)).thenReturn(batch(10L));
        when(relationMapper.selectCount(any())).thenReturn(2L);
        when(relationMapper.delete(any())).thenReturn(1);

        BusinessException error = assertThrows(BusinessException.class,
                () -> controller.deleteBatch(10L, "Bearer token"));

        assertEquals(409, error.getCode());
        verify(batchMapper, never()).deleteById(anyLong());
    }

    @Test
    void missingBatchUsesRealNotFoundError() {
        when(batchMapper.selectById(99L)).thenReturn(null);

        BusinessException error = assertThrows(BusinessException.class,
                () -> controller.getBatchById(99L, "Bearer token"));

        assertEquals(404, error.getCode());
    }

    private Map<String, Object> validParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("projectId", 1);
        params.put("batchName", " 入场三级教育 ");
        params.put("personIds", List.of(2));
        return params;
    }

    private TemporaryPerson person(Long id) {
        TemporaryPerson person = new TemporaryPerson();
        person.setId(id);
        person.setProjectId(1L);
        person.setName("测试人员");
        person.setStatus(PersonnelStatus.WAIT_EDUCATION);
        return person;
    }

    private SafetyEducationBatch batch(Long id) {
        SafetyEducationBatch batch = new SafetyEducationBatch();
        batch.setId(id);
        batch.setProjectId(1L);
        batch.setBatchName("入场三级教育");
        return batch;
    }

    private FileResource file(Long id, Long uploaderId) {
        FileResource file = new FileResource();
        file.setId(id);
        file.setProjectId(1L);
        file.setUploaderId(uploaderId);
        file.setBusinessType("safety_education");
        file.setStatus(FileStatus.UPLOADED);
        file.setDeleted(0);
        return file;
    }
}
