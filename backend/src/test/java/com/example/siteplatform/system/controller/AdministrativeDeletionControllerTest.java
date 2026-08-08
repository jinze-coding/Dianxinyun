package com.example.siteplatform.system.controller;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.system.dto.AdministrativeDeletionExecuteRequest;
import com.example.siteplatform.system.dto.AdministrativeDeletionPreviewRequest;
import com.example.siteplatform.system.dto.DeletionImpactVO;
import com.example.siteplatform.system.service.AdministrativeDeletionService;
import com.example.siteplatform.system.service.SystemPermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdministrativeDeletionControllerTest {
    @Mock private AdministrativeDeletionService deletionService;
    @Mock private SystemPermissionService permissionService;
    @Mock private AuthService authService;

    private AdministrativeDeletionController controller;
    private SysUser operator;

    @BeforeEach
    void setUp() {
        controller = new AdministrativeDeletionController(deletionService, permissionService, authService);
        operator = new SysUser();
        operator.setId(9L);
        when(authService.getCurrentUser("token")).thenReturn(operator);
    }

    @Test
    void platformAdministratorCanPreviewAndExecuteWithTheSameTokenPayload() {
        AdministrativeDeletionPreviewRequest preview = new AdministrativeDeletionPreviewRequest();
        preview.setTargetType("USER");
        preview.setTargetId(12L);
        DeletionImpactVO impact = new DeletionImpactVO();
        impact.setTargetType("USER");
        impact.setTargetId(12L);
        when(deletionService.preview(preview, operator)).thenReturn(impact);

        assertEquals(impact, controller.preview(preview, "token").getData());

        AdministrativeDeletionExecuteRequest execute = new AdministrativeDeletionExecuteRequest();
        execute.setTargetType("USER");
        execute.setTargetId(12L);
        execute.setConfirmationToken("one-time-token");
        execute.setAcknowledged(true);
        controller.execute(execute, "token");

        verify(permissionService, org.mockito.Mockito.times(2)).requirePlatformAdmin(operator);
        verify(deletionService).execute(execute, operator);
    }

    @Test
    void ordinaryUserGetsRealForbiddenAndCannotReachDeletionService() {
        AdministrativeDeletionPreviewRequest preview = new AdministrativeDeletionPreviewRequest();
        preview.setTargetType("PROJECT");
        preview.setTargetId(3L);
        doThrow(BusinessException.forbidden("仅平台管理员可执行该操作"))
                .when(permissionService).requirePlatformAdmin(operator);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> controller.preview(preview, "token"));

        assertEquals(403, exception.getCode());
        verify(deletionService, never()).preview(preview, operator);
    }
}
