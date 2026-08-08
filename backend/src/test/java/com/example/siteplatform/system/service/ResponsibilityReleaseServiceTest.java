package com.example.siteplatform.system.service;

import com.example.siteplatform.notification.service.UserNotificationService;
import com.example.siteplatform.project.dto.ResponsibilityImpactVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResponsibilityReleaseServiceTest {

    @Mock private JdbcTemplate jdbc;
    @Mock private SystemPermissionService permissionService;
    @Mock private UserNotificationService notificationService;

    private ResponsibilityReleaseService service;

    @BeforeEach
    void setUp() {
        service = spy(new ResponsibilityReleaseService(jdbc, permissionService, notificationService));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void completeMemberRemovalRemovesConfigCancelsPendingTaskAndNotifiesAdminWhenNoCandidateRemains() {
        ResponsibilityImpactVO impact = impact(1, 1);
        doReturn(impact).when(service).impact(9L, 7L);
        when(jdbc.query(argThat(sql -> sql != null && sql.contains("workflow_approval_config_user")),
                any(RowMapper.class), any(Object[].class))).thenReturn(List.of(81L));
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("FROM workflow_approval_task")) {
                return List.of(Map.of("id", 701L, "business_id", 42L));
            }
            if (sql.contains("FROM seal_application")) {
                return List.of(Map.of("application_no", "YYSQ-20260808-00000042", "seal_name", "项目章"));
            }
            return List.of();
        });
        when(jdbc.queryForObject(argThat(sql -> sql != null && sql.contains("workflow_approval_task")),
                any(Class.class), any(Object[].class))).thenReturn(0L);
        when(jdbc.queryForObject(argThat(sql -> sql != null
                        && sql.contains("SELECT id FROM workflow_approval_config")),
                any(Class.class), any(Object[].class))).thenReturn(81L);
        when(jdbc.query(org.mockito.ArgumentMatchers.<String>argThat(
                        sql -> sql != null && sql.contains("PLATFORM_ADMIN")),
                any(RowMapper.class))).thenReturn(List.of(99L));
        when(jdbc.update(anyString(), any(Object[].class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("DELETE FROM workflow_approval_config_user")) return 1;
            if (sql.contains("UPDATE workflow_approval_config config")) return 1;
            if (sql.contains("UPDATE workflow_approval_task")) return 1;
            return 0;
        });

        ResponsibilityImpactVO result = service.releaseAll(9L, 7L);

        assertEquals(2, result.getTotalCount());
        verify(jdbc).update(argThat(sql -> sql.contains("DELETE FROM workflow_approval_config_user")),
                any(Object[].class));
        verify(jdbc).update(argThat(sql -> sql.contains("UPDATE workflow_approval_config config")
                        && sql.contains("config_version = config.config_version + 1")),
                any(Object[].class));
        verify(jdbc).update(argThat(sql -> sql.contains("UPDATE workflow_approval_task")
                        && sql.contains("status = 'CANCELLED'")),
                any(Object[].class));
        verify(notificationService).notify(99L, 9L, "SEAL_APPLICATION", 42L,
                "SEAL_REASSIGN_REQUIRED", "用印申请待改派：项目章",
                "YYSQ-20260808-00000042 的原审批人资格已失效，请重新指派审批人",
                "seal:reassign-required:42:7:99");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void removingOneOfSeveralCandidatesLeavesOtherPendingTaskAndDoesNotSendReassignAlarm() {
        doReturn(impact(0, 1)).when(service).impact(9L, 7L);
        when(jdbc.query(argThat(sql -> sql != null && sql.contains("workflow_approval_config_user")),
                any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        when(jdbc.queryForList(argThat(sql -> sql != null && sql.contains("FROM workflow_approval_task")),
                any(Object[].class))).thenReturn(List.of(Map.of("id", 701L, "business_id", 42L)));
        when(jdbc.queryForObject(argThat(sql -> sql != null && sql.contains("workflow_approval_task")),
                any(Class.class), any(Object[].class))).thenReturn(1L);
        when(jdbc.update(anyString(), any(Object[].class))).thenAnswer(invocation ->
                ((String) invocation.getArgument(0)).contains("UPDATE workflow_approval_task") ? 1 : 0);

        service.releaseAll(9L, 7L);

        verify(notificationService, never()).notify(any(), any(), anyString(), any(), anyString(),
                anyString(), anyString(), anyString());
        verify(jdbc, never()).queryForList(argThat(sql -> sql != null && sql.contains("FROM seal_application")),
                any(Object[].class));
    }

    @Test
    void projectRoleCapabilityChangeDoesNotRemoveDirectUserApprovalConfigOrAssignedTask() {
        doReturn(impact(4, 3)).when(service).impact(9L, 7L);
        when(permissionService.hasProjectPermission(any(), any(), anyString())).thenReturn(true);

        ResponsibilityImpactVO result = service.releaseForCapabilityLoss(9L, 7L);

        assertEquals(7, result.getTotalCount());
        verify(jdbc, never()).update(argThat(sql -> sql != null && sql.contains("workflow_approval")),
                any(Object[].class));
        verify(jdbc, never()).queryForList(argThat(sql -> sql != null && sql.contains("workflow_approval")),
                any(Object[].class));
        verify(notificationService, never()).notify(any(), any(), anyString(), any(), anyString(),
                anyString(), anyString(), anyString());
    }

    private ResponsibilityImpactVO impact(long configCount, long pendingCount) {
        ResponsibilityImpactVO impact = new ResponsibilityImpactVO();
        impact.setProjectId(9L);
        impact.setProjectName("智慧营造项目");
        impact.setUserId(7L);
        impact.setSealApprovalConfigCount(configCount);
        impact.setPendingSealApprovalCount(pendingCount);
        return impact;
    }
}
