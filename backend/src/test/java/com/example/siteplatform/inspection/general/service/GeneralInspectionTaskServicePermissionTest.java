package com.example.siteplatform.inspection.general.service;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.file.service.FileResourceService;
import com.example.siteplatform.inspection.general.entity.GeneralInspectionTask;
import com.example.siteplatform.inspection.general.mapper.*;
import com.example.siteplatform.notification.service.UserNotificationService;
import com.example.siteplatform.project.constant.InspectionPermissionCodes;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GeneralInspectionTaskServicePermissionTest {

    @Test
    void revokedSubmitPermissionHidesExecutionAction() {
        GeneralInspectionPermissionService permissionService = mock(GeneralInspectionPermissionService.class);
        GeneralInspectionTaskService service = service(permissionService);
        SysUser user = new SysUser();
        user.setId(9L);
        GeneralInspectionTask task = new GeneralInspectionTask();
        task.setProjectId(2L);
        task.setAssigneeId(9L);
        task.setStatus("PENDING");
        task.setAvailableTime(LocalDateTime.now().minusMinutes(1));

        when(permissionService.hasInspectionPermission(2L, 9L,
                InspectionPermissionCodes.EDGE_INSPECTION_SUBMIT)).thenReturn(false);
        assertThat(service.canExecuteTask(task, user, LocalDateTime.now())).isFalse();

        when(permissionService.hasInspectionPermission(2L, 9L,
                InspectionPermissionCodes.EDGE_INSPECTION_SUBMIT)).thenReturn(true);
        assertThat(service.canExecuteTask(task, user, LocalDateTime.now())).isTrue();
    }

    private GeneralInspectionTaskService service(GeneralInspectionPermissionService permissionService) {
        return new GeneralInspectionTaskService(
                permissionService,
                mock(GeneralInspectionTaskMapper.class),
                mock(GeneralInspectionTaskItemMapper.class),
                mock(GeneralInspectionPointMapper.class),
                mock(GeneralInspectionTemplateVersionMapper.class),
                mock(GeneralInspectionRectificationMapper.class),
                mock(GeneralInspectionActionLogMapper.class),
                mock(SysUserMapper.class),
                mock(FileResourceService.class),
                mock(UserNotificationService.class),
                mock(GeneralInspectionEventPublisher.class),
                new ObjectMapper());
    }
}
