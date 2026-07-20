package com.example.siteplatform.file.service;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.file.dto.FileActivityVO;
import com.example.siteplatform.file.entity.FileResource;
import com.example.siteplatform.file.mapper.FileResourceMapper;
import com.example.siteplatform.log.entity.OperationLog;
import com.example.siteplatform.log.mapper.OperationLogMapper;
import com.example.siteplatform.project.service.ProjectPermissionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileOperationServiceTest {
    private final OperationLogMapper operationLogMapper = mock(OperationLogMapper.class);
    private final FileResourceMapper fileMapper = mock(FileResourceMapper.class);
    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final ProjectPermissionService permissionService = mock(ProjectPermissionService.class);
    private final FileOperationService service = new FileOperationService(
            operationLogMapper, fileMapper, userMapper, permissionService);

    @Test
    void recordStoresProjectScopedFileOperation() {
        SysUser operator = new SysUser();
        operator.setId(3L);
        operator.setUsername("zhangsan");
        operator.setRealName("张三");
        FileResource file = new FileResource();
        file.setId(12L);
        file.setProjectId(7L);
        file.setFileName("施工图纸.pdf");

        service.record(operator, file, "FILE_DOWNLOAD", "下载《施工图纸.pdf》", null);

        ArgumentCaptor<OperationLog> captor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogMapper).insert(captor.capture());
        OperationLog log = captor.getValue();
        assertEquals("FILE_PROJECT_7", log.getBusinessType());
        assertEquals(12L, log.getBusinessId());
        assertEquals("张三", log.getUsername());
        assertEquals("FILE_DOWNLOAD", log.getOperationType());
    }

    @Test
    void getActivitiesMapsOperatorAndActionLabel() {
        SysUser currentUser = new SysUser();
        currentUser.setId(1L);
        OperationLog log = new OperationLog();
        log.setId(5L);
        log.setUserId(3L);
        log.setUsername("张三");
        log.setOperationType("FILE_UPDATE");
        log.setOperationDesc("修改《检查表.xlsx》的资料信息");
        log.setBusinessId(12L);
        log.setCreateTime(LocalDateTime.now());
        FileResource file = new FileResource();
        file.setId(12L);
        file.setProjectId(7L);
        file.setFileName("检查表.xlsx");
        when(operationLogMapper.selectList(any())).thenReturn(List.of(log));
        when(fileMapper.selectBatchIds(List.of(12L))).thenReturn(List.of(file));

        List<FileActivityVO> activities = service.getActivities(currentUser, 7L, null, 20);

        verify(permissionService).checkProjectPermission(1L, 7L);
        assertEquals(1, activities.size());
        assertEquals("修改", activities.get(0).getOperationLabel());
        assertEquals("张三", activities.get(0).getOperatorName());
        assertEquals("检查表.xlsx", activities.get(0).getFileName());
    }
}
