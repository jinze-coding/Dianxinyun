package com.example.siteplatform.project.service;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.log.entity.OperationLog;
import com.example.siteplatform.log.mapper.OperationLogMapper;
import com.example.siteplatform.project.dto.InspectionPermissionTemplateRequest;
import com.example.siteplatform.project.dto.InspectionPermissionTemplateStatusRequest;
import com.example.siteplatform.project.entity.InspectionPermissionTemplate;
import com.example.siteplatform.project.mapper.InspectionPermissionTemplateMapper;
import com.example.siteplatform.project.mapper.SysUserProjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InspectionPermissionTemplateSessionInvalidationTest {

    @Mock private InspectionPermissionTemplateMapper templateMapper;
    @Mock private ProjectPermissionService projectPermissionService;
    @Mock private SysUserProjectMapper userProjectMapper;
    @Mock private OperationLogMapper operationLogMapper;
    @Mock private AuthService authService;

    private InspectionPermissionTemplateService service;
    private SysUser operator;

    @BeforeEach
    void setUp() {
        service = new InspectionPermissionTemplateService();
        ReflectionTestUtils.setField(service, "templateMapper", templateMapper);
        ReflectionTestUtils.setField(service, "projectPermissionService", projectPermissionService);
        ReflectionTestUtils.setField(service, "userProjectMapper", userProjectMapper);
        ReflectionTestUtils.setField(service, "operationLogMapper", operationLogMapper);
        ReflectionTestUtils.setField(service, "authService", authService);
        operator = new SysUser();
        operator.setId(1L);
        operator.setUsername("operator");
        when(projectPermissionService.isPlatformAdmin(1L)).thenReturn(true);
    }

    @Test
    void updatingTemplateAuditsAndInvalidatesOnlyActiveBoundUsers() {
        InspectionPermissionTemplate template = template(88L);
        when(templateMapper.selectById(88L)).thenReturn(template);
        when(templateMapper.selectByTemplateCode("CUSTOM_TEMPLATE")).thenReturn(template);
        when(userProjectMapper.selectActiveUserIdsByInspectionPermissionTemplateId(88L))
                .thenReturn(List.of(2L, 3L, 2L));

        service.updateTemplate(88L, request(), operator);

        verify(projectPermissionService).clearUserProjectsCache(2L);
        verify(projectPermissionService).clearUserProjectsCache(3L);
        verify(authService).logout(2L);
        verify(authService).logout(3L);
        verify(authService, never()).logout(4L);
        verify(operationLogMapper).insert(argThat(log ->
                "UPDATE_PERMISSION_TEMPLATE".equals(log.getOperationType())
                        && "INSPECTION_PERMISSION_TEMPLATE".equals(log.getBusinessType())
                        && Long.valueOf(88L).equals(log.getBusinessId())));
        verifyNoMoreInteractions(authService);
    }

    @Test
    void statusChangeUsesSameTargetedInvalidationAndAuditBoundary() {
        InspectionPermissionTemplate template = template(88L);
        when(templateMapper.selectById(88L)).thenReturn(template);
        when(userProjectMapper.selectActiveUserIdsByInspectionPermissionTemplateId(88L))
                .thenReturn(List.of(2L));
        InspectionPermissionTemplateStatusRequest request = new InspectionPermissionTemplateStatusRequest();
        request.setEnabled(false);

        service.updateStatus(88L, request, operator);

        verify(projectPermissionService).clearUserProjectsCache(2L);
        verify(authService).logout(2L);
        verify(operationLogMapper).insert(argThat(log ->
                "CHANGE_PERMISSION_TEMPLATE_STATUS".equals(log.getOperationType())
                        && Long.valueOf(88L).equals(log.getBusinessId())));
    }

    @Test
    void creatingUnassignedTemplateAuditsWithoutRevokingSessions() {
        doAnswer(invocation -> {
            InspectionPermissionTemplate template = invocation.getArgument(0);
            template.setId(91L);
            return 1;
        }).when(templateMapper).insert(any(InspectionPermissionTemplate.class));

        service.createTemplate(request(), operator);

        verify(userProjectMapper, never()).selectActiveUserIdsByInspectionPermissionTemplateId(any());
        verifyNoMoreInteractions(authService);
        verify(operationLogMapper).insert(argThat(log ->
                "CREATE_PERMISSION_TEMPLATE".equals(log.getOperationType())
                        && Long.valueOf(91L).equals(log.getBusinessId())));
    }

    @Test
    void builtinTemplateCannotChangeEnabledThroughEdit() {
        InspectionPermissionTemplate template = template(88L);
        template.setBuiltin(1);
        when(templateMapper.selectById(88L)).thenReturn(template);
        InspectionPermissionTemplateRequest request = request();
        request.setEnabled(0);

        assertThrows(BusinessException.class, () -> service.updateTemplate(88L, request, operator));

        verify(templateMapper, never()).updateById(any());
        verify(userProjectMapper, never()).selectActiveUserIdsByInspectionPermissionTemplateId(any());
        verifyNoMoreInteractions(authService);
    }

    @Test
    void builtinTemplateCannotChangeEnabledThroughStatusEndpoint() {
        InspectionPermissionTemplate template = template(88L);
        template.setBuiltin(1);
        when(templateMapper.selectById(88L)).thenReturn(template);
        InspectionPermissionTemplateStatusRequest request = new InspectionPermissionTemplateStatusRequest();
        request.setEnabled(false);

        assertThrows(BusinessException.class, () -> service.updateStatus(88L, request, operator));

        verify(templateMapper, never()).updateById(any());
        verify(userProjectMapper, never()).selectActiveUserIdsByInspectionPermissionTemplateId(any());
        verifyNoMoreInteractions(authService);
    }

    @Test
    void builtinTemplateCanBeEditedWhenEnabledRemainsUnchanged() {
        InspectionPermissionTemplate template = template(88L);
        template.setBuiltin(1);
        when(templateMapper.selectById(88L)).thenReturn(template);
        when(userProjectMapper.selectActiveUserIdsByInspectionPermissionTemplateId(88L))
                .thenReturn(List.of());

        service.updateTemplate(88L, request(), operator);

        verify(templateMapper).updateById(template);
        verify(operationLogMapper).insert(argThat(log ->
                "UPDATE_PERMISSION_TEMPLATE".equals(log.getOperationType())
                        && Long.valueOf(88L).equals(log.getBusinessId())));
    }

    private InspectionPermissionTemplate template(Long id) {
        InspectionPermissionTemplate template = new InspectionPermissionTemplate();
        template.setId(id);
        template.setTemplateName("自定义模板");
        template.setTemplateCode("CUSTOM_TEMPLATE");
        template.setPermissionCodes("BOX_VIEW");
        template.setEnabled(1);
        template.setBuiltin(0);
        template.setDeleted(0);
        return template;
    }

    private InspectionPermissionTemplateRequest request() {
        InspectionPermissionTemplateRequest request = new InspectionPermissionTemplateRequest();
        request.setTemplateName("自定义模板");
        request.setTemplateCode("CUSTOM_TEMPLATE");
        request.setPermissionCodes(List.of("BOX_VIEW", "INSPECTION_RECORD_VIEW"));
        request.setEnabled(1);
        return request;
    }
}
