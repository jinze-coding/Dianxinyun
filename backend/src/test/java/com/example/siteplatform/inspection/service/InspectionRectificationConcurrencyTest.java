package com.example.siteplatform.inspection.service;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.inspection.dto.RectificationCompleteRequest;
import com.example.siteplatform.inspection.dto.RectificationReviewRequest;
import com.example.siteplatform.inspection.entity.InspectionRectification;
import com.example.siteplatform.inspection.mapper.InspectionRectificationMapper;
import com.example.siteplatform.project.service.ProjectPermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InspectionRectificationConcurrencyTest {

    @Mock private InspectionRectificationMapper rectificationMapper;
    @Mock private ProjectPermissionService permissionService;

    private InspectionService service;
    private SysUser platformAdmin;

    @BeforeEach
    void setUp() {
        service = new InspectionService();
        ReflectionTestUtils.setField(service, "inspectionRectificationMapper", rectificationMapper);
        ReflectionTestUtils.setField(service, "projectPermissionService", permissionService);
        platformAdmin = new SysUser();
        platformAdmin.setId(1L);
        platformAdmin.setUsername("admin");
        when(permissionService.isPlatformAdmin(1L)).thenReturn(true);
    }

    @Test
    void repeatedRectificationCompletionReturnsConflictAfterRowLock() {
        InspectionRectification rectification = rectification("COMPLETED");
        when(rectificationMapper.selectByIdForUpdate(20L)).thenReturn(rectification);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.completeRectification(20L, new RectificationCompleteRequest(), platformAdmin));

        assertEquals(409, error.getCode());
        verify(rectificationMapper).selectByIdForUpdate(20L);
        verify(rectificationMapper, never()).updateById(rectification);
    }

    @Test
    void repeatedReviewCloseReturnsConflictAfterRowLock() {
        InspectionRectification rectification = rectification("CLOSED");
        when(rectificationMapper.selectByIdForUpdate(20L)).thenReturn(rectification);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.closeRectification(20L, new RectificationReviewRequest(), platformAdmin));

        assertEquals(409, error.getCode());
        verify(rectificationMapper).selectByIdForUpdate(20L);
        verify(rectificationMapper, never()).updateById(rectification);
    }

    private InspectionRectification rectification(String status) {
        InspectionRectification rectification = new InspectionRectification();
        rectification.setId(20L);
        rectification.setProjectId(1L);
        rectification.setStatus(status);
        return rectification;
    }
}
