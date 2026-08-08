package com.example.siteplatform.system.service;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.file.storage.FileStorageManager;
import com.example.siteplatform.log.mapper.OperationLogMapper;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.system.dto.AdministrativeDeletionExecuteRequest;
import com.example.siteplatform.system.dto.AdministrativeDeletionPreviewRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdministrativeDeletionServiceTest {
    @Mock private JdbcTemplate jdbc;
    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> values;
    @Mock private FileStorageManager storageManager;
    @Mock private OperationLogMapper operationLogMapper;
    @Mock private ProjectPermissionService projectPermissionService;
    @Mock private AuthService authService;
    @Mock private ResponsibilityReleaseService responsibilityReleaseService;

    private AdministrativeDeletionService service;
    private SysUser operator;
    private final Map<String, String> tokenStore = new HashMap<>();
    private final AtomicLong fileSize = new AtomicLong(1024L);

    @BeforeEach
    void setUp() {
        service = new AdministrativeDeletionService(jdbc, redis, new ObjectMapper(), storageManager,
                operationLogMapper, projectPermissionService, authService, responsibilityReleaseService);
        operator = new SysUser();
        operator.setId(7L);
        operator.setUsername("admin");
        lenient().when(redis.opsForValue()).thenReturn(values);
        lenient().doAnswer(invocation -> {
            tokenStore.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(values).set(anyString(), anyString(), any(Duration.class));
        lenient().when(values.get(anyString())).thenAnswer(invocation -> tokenStore.get(invocation.getArgument(0)));
        lenient().doAnswer(invocation -> tokenStore.remove(invocation.getArgument(0)) != null)
                .when(redis).delete(anyString());
        lenient().when(jdbc.queryForList(anyString(), any(Object[].class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("file_name")) {
                Map<String, Object> row = new HashMap<>();
                row.put("id", 21L);
                row.put("file_name", "质量方案.pdf");
                row.put("file_size", fileSize.get());
                row.put("business_type", "QUALITY_DOCUMENT");
                return List.of(row);
            }
            return List.of(Map.of("id", 21L));
        });
    }

    @Test
    void previewCreatesShortLivedOneTimeTokenAndFileImpact() {
        AdministrativeDeletionPreviewRequest request = new AdministrativeDeletionPreviewRequest();
        request.setTargetType("FILE");
        request.setTargetId(21L);

        var impact = service.preview(request, operator);

        assertNotNull(impact.getConfirmationToken());
        assertEquals("质量方案.pdf", impact.getTargetName());
        assertEquals(1L, impact.getFileCount());
        assertEquals(1024L, impact.getFileBytes());
        assertFalse(impact.isTypedConfirmationRequired());
        verify(values).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void historicalUserRoleCanBePreviewedForDeletion() {
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.argThat(
                sql -> sql != null && sql.contains("SELECT id, role_name, role_code FROM sys_role")),
                any(Object[].class))).thenReturn(List.of(Map.of(
                        "id", 30L,
                        "role_name", "项目成员",
                        "role_code", "USER")));
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.argThat(
                sql -> sql != null && sql.contains("SELECT DISTINCT user_id, project_id")),
                any(Object[].class))).thenReturn(List.of());
        AdministrativeDeletionPreviewRequest request = new AdministrativeDeletionPreviewRequest();
        request.setTargetType("ROLE");
        request.setTargetId(30L);

        var impact = service.preview(request, operator);

        assertEquals("项目成员", impact.getTargetName());
        assertEquals("removedMemberships", impact.getItems().get(2).getCode());
        assertNotNull(impact.getConfirmationToken());
    }

    @Test
    void approvedRegistrationPreviewPreservesCreatedUserAndAuditLogs() {
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.argThat(
                sql -> sql != null && sql.contains("FROM registration_application")
                        && sql.contains("created_user_id")),
                any(Object[].class))).thenReturn(List.of(Map.of(
                        "id", 42L,
                        "username", "17600000000",
                        "real_name", "申请人",
                        "status", "APPROVED",
                        "password_hash", "",
                        "desired_project_ids", "[9,10]",
                        "app_id", "",
                        "openid", "",
                        "created_user_id", 88L)));
        when(jdbc.queryForObject(org.mockito.ArgumentMatchers.argThat(
                        sql -> sql != null && sql.contains("business_type = 'REGISTRATION_APPLICATION'")),
                org.mockito.ArgumentMatchers.eq(Long.class), any(Object[].class))).thenReturn(1L);
        AdministrativeDeletionPreviewRequest request = new AdministrativeDeletionPreviewRequest();
        request.setTargetType("REGISTRATION_APPLICATION");
        request.setTargetId(42L);

        var impact = service.preview(request, operator);

        assertEquals("已通过 · 申请人（17600000000）", impact.getTargetName());
        assertEquals(2L, impact.getItems().stream()
                .filter(item -> "desiredProjects".equals(item.getCode()))
                .findFirst().orElseThrow().getCount());
        assertEquals(1L, impact.getItems().stream()
                .filter(item -> "preservedUser".equals(item.getCode()))
                .findFirst().orElseThrow().getCount());
        assertEquals(1L, impact.getItems().stream()
                .filter(item -> "preservedAuditLogs".equals(item.getCode()))
                .findFirst().orElseThrow().getCount());
    }

    @Test
    void executeDeletesOnlyRegistrationApplicationRecord() {
        Map<String, Object> application = new HashMap<>();
        application.put("id", 43L);
        application.put("username", "17700000000");
        application.put("real_name", "待审核申请人");
        application.put("status", "PENDING");
        application.put("password_hash", "bcrypt-summary");
        application.put("desired_project_ids", "[9]");
        application.put("app_id", "");
        application.put("openid", "");
        application.put("created_user_id", null);
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.argThat(
                sql -> sql != null && sql.contains("FROM registration_application")
                        && sql.contains("created_user_id")),
                any(Object[].class))).thenReturn(List.of(application));
        when(jdbc.update("DELETE FROM registration_application WHERE id = ?", 43L)).thenReturn(1);
        AdministrativeDeletionPreviewRequest preview = new AdministrativeDeletionPreviewRequest();
        preview.setTargetType("REGISTRATION_APPLICATION");
        preview.setTargetId(43L);
        var impact = service.preview(preview, operator);
        AdministrativeDeletionExecuteRequest execute = new AdministrativeDeletionExecuteRequest();
        execute.setTargetType("REGISTRATION_APPLICATION");
        execute.setTargetId(43L);
        execute.setConfirmationToken(impact.getConfirmationToken());
        execute.setAcknowledged(true);

        service.execute(execute, operator);

        verify(jdbc).update("DELETE FROM registration_application WHERE id = ?", 43L);
        verify(operationLogMapper).insert(any());
    }

    @Test
    void siteAccessInvitationPreviewShowsPeopleAndBusinessAuditImpact() {
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.argThat(
                sql -> sql != null && sql.contains("FROM site_visit_invitation")
                        && sql.contains("effective_status")),
                any(Object[].class))).thenReturn(List.of(Map.of(
                        "id", 91L,
                        "invite_no", "VIS-202608081200-202608081400-ABC12345",
                        "effective_status", "SUBMITTED")));
        when(jdbc.queryForObject(anyString(), org.mockito.ArgumentMatchers.eq(Long.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0);
                    if (sql.contains("site_visit_person")) return 2L;
                    if (sql.contains("site_visit_audit_log")) return 3L;
                    if (sql.contains("sys_operation_log")) return 4L;
                    return 0L;
                });
        AdministrativeDeletionPreviewRequest request = new AdministrativeDeletionPreviewRequest();
        request.setTargetType("SITE_ACCESS_INVITATION");
        request.setTargetId(91L);

        var impact = service.preview(request, operator);

        assertEquals("已提交 · VIS-202608081200-202608081400-ABC12345", impact.getTargetName());
        assertEquals(2L, impact.getItems().stream()
                .filter(item -> "visitors".equals(item.getCode()))
                .findFirst().orElseThrow().getCount());
        assertEquals(3L, impact.getItems().stream()
                .filter(item -> "businessAuditLogs".equals(item.getCode()))
                .findFirst().orElseThrow().getCount());
        assertEquals(4L, impact.getItems().stream()
                .filter(item -> "preservedOperationLogs".equals(item.getCode()))
                .findFirst().orElseThrow().getCount());
        assertNotNull(impact.getConfirmationToken());
    }

    @Test
    void executeDeletesSiteAccessInvitationPeopleAndBusinessAuditInDependencyOrder() {
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.argThat(
                sql -> sql != null && sql.contains("FROM site_visit_invitation")
                        && sql.contains("effective_status")),
                any(Object[].class))).thenReturn(List.of(Map.of(
                        "id", 92L,
                        "invite_no", "VIS-202608081500-202608081700-DEF67890",
                        "effective_status", "VOIDED")));
        when(jdbc.queryForObject(anyString(), org.mockito.ArgumentMatchers.eq(Long.class), any(Object[].class)))
                .thenReturn(0L);
        when(jdbc.update("DELETE FROM site_visit_person WHERE invitation_id = ?", 92L)).thenReturn(2);
        when(jdbc.update("DELETE FROM site_visit_audit_log WHERE invitation_id = ?", 92L)).thenReturn(3);
        when(jdbc.update("DELETE FROM site_visit_invitation WHERE id = ? AND deleted = 0", 92L)).thenReturn(1);
        AdministrativeDeletionPreviewRequest preview = new AdministrativeDeletionPreviewRequest();
        preview.setTargetType("SITE_ACCESS_INVITATION");
        preview.setTargetId(92L);
        var impact = service.preview(preview, operator);
        AdministrativeDeletionExecuteRequest execute = new AdministrativeDeletionExecuteRequest();
        execute.setTargetType("SITE_ACCESS_INVITATION");
        execute.setTargetId(92L);
        execute.setConfirmationToken(impact.getConfirmationToken());
        execute.setAcknowledged(true);

        service.execute(execute, operator);

        InOrder order = inOrder(jdbc);
        order.verify(jdbc).update("DELETE FROM site_visit_person WHERE invitation_id = ?", 92L);
        order.verify(jdbc).update("DELETE FROM site_visit_audit_log WHERE invitation_id = ?", 92L);
        order.verify(jdbc).update("DELETE FROM site_visit_invitation WHERE id = ? AND deleted = 0", 92L);
        verify(operationLogMapper).insert(any());
    }

    @Test
    void previewRejectsDeletingCurrentAdministratorAccount() {
        AdministrativeDeletionPreviewRequest request = new AdministrativeDeletionPreviewRequest();
        request.setTargetType("USER");
        request.setTargetId(operator.getId());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.preview(request, operator));

        assertEquals("不能删除当前登录账号", exception.getMessage());
        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void previewUserShowsAccessWechatResponsibilitiesAndPreservedHistory() {
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.argThat(
                sql -> sql != null && sql.contains("SELECT id, username, real_name FROM sys_user")),
                any(Object[].class))).thenReturn(List.of(Map.of(
                        "id", 22L,
                        "username", "13800000000",
                        "real_name", "测试成员")));
        when(jdbc.queryForObject(anyString(), org.mockito.ArgumentMatchers.eq(Long.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0);
                    if (sql.contains("u.password REGEXP")) return 0L;
                    if (sql.contains("sys_user_project") || sql.contains("sys_user_project_role")) return 2L;
                    if (sql.contains("sys_user_wechat_binding") || sql.contains("wechat_subscription_state")) return 1L;
                    if (sql.contains("responsible_electrician_id")) return 3L;
                    if (sql.contains("sys_operation_log")) return 4L;
                    return 0L;
                });
        AdministrativeDeletionPreviewRequest request = new AdministrativeDeletionPreviewRequest();
        request.setTargetType("USER");
        request.setTargetId(22L);

        var impact = service.preview(request, operator);

        assertEquals("测试成员（13800000000）", impact.getTargetName());
        assertEquals(2L, impact.getItems().stream()
                .filter(item -> "projectMemberships".equals(item.getCode()))
                .findFirst().orElseThrow().getCount());
        assertEquals(3L, impact.getItems().stream()
                .filter(item -> "responsibleBoxes".equals(item.getCode()))
                .findFirst().orElseThrow().getCount());
        assertEquals(4L, impact.getItems().stream()
                .filter(item -> "preservedHistory".equals(item.getCode()))
                .findFirst().orElseThrow().getCount());
        assertNotNull(impact.getConfirmationToken());
    }

    @Test
    void previewProtectsLastRecoverablePlatformAdministrator() {
        when(jdbc.queryForObject(anyString(), org.mockito.ArgumentMatchers.eq(Long.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0);
                    return sql.contains("u.password REGEXP") ? 1L : 0L;
                });
        AdministrativeDeletionPreviewRequest request = new AdministrativeDeletionPreviewRequest();
        request.setTargetType("USER");
        request.setTargetId(22L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.preview(request, operator));

        assertEquals("不能删除最后一个可恢复的平台管理员", exception.getMessage());
        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void projectWithVisitorHistoryCannotBePhysicallyDeleted() {
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.argThat(
                sql -> sql != null && sql.contains("SELECT id, project_name FROM project_info")),
                any(Object[].class))).thenReturn(List.of(Map.of(
                        "id", 12L,
                        "project_name", "保留外访历史项目")));
        when(jdbc.queryForObject(anyString(),
                org.mockito.ArgumentMatchers.eq(Long.class), any(Object[].class))).thenAnswer(invocation ->
                ((String) invocation.getArgument(0)).contains("site_visit_invitation") ? 1L : 0L);
        AdministrativeDeletionPreviewRequest request = new AdministrativeDeletionPreviewRequest();
        request.setTargetType("PROJECT");
        request.setTargetId(12L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.preview(request, operator));

        assertEquals(409, exception.getCode());
        assertEquals("项目存在需长期保留的外访数据，禁止物理删除；请停用项目并保留审计", exception.getMessage());
        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void projectWithSubmittedSealHistoryCannotBePhysicallyDeleted() {
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.argThat(
                sql -> sql != null && sql.contains("SELECT id, project_name FROM project_info")),
                any(Object[].class))).thenReturn(List.of(Map.of(
                        "id", 12L,
                        "project_name", "保留用印台账项目")));
        when(jdbc.queryForObject(anyString(),
                org.mockito.ArgumentMatchers.eq(Long.class), any(Object[].class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            return sql.contains("status <> 'DRAFT'") ? 1L : 0L;
        });
        AdministrativeDeletionPreviewRequest request = new AdministrativeDeletionPreviewRequest();
        request.setTargetType("PROJECT");
        request.setTargetId(12L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.preview(request, operator));

        assertEquals(409, exception.getCode());
        assertEquals("项目存在已提交用印申请及审批台账，禁止物理删除；请停用项目并保留审计", exception.getMessage());
        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void executeRejectsConcurrentImpactChangeAndConsumesToken() {
        AdministrativeDeletionPreviewRequest preview = new AdministrativeDeletionPreviewRequest();
        preview.setTargetType("FILE");
        preview.setTargetId(21L);
        String token = service.preview(preview, operator).getConfirmationToken();
        fileSize.set(2048L);
        AdministrativeDeletionExecuteRequest execute = new AdministrativeDeletionExecuteRequest();
        execute.setTargetType("FILE");
        execute.setTargetId(21L);
        execute.setConfirmationToken(token);
        execute.setAcknowledged(true);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.execute(execute, operator));

        assertEquals(409, exception.getCode());
        assertEquals("关联数据已发生变化，请重新预览确认", exception.getMessage());
        assertFalse(tokenStore.containsKey("admin:deletion:" + token));
        verify(storageManager, never()).delete(any());
        verify(operationLogMapper, never()).insert(any());
    }
}
