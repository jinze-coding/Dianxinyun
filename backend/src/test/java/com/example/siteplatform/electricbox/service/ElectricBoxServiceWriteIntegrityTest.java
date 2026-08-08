package com.example.siteplatform.electricbox.service;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.auth.service.WechatPlatformClient;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.electricbox.dto.ElectricBoxRequest;
import com.example.siteplatform.electricbox.dto.ElectricBoxQrRebindRequest;
import com.example.siteplatform.electricbox.entity.ElectricBox;
import com.example.siteplatform.electricbox.mapper.ElectricBoxMapper;
import com.example.siteplatform.electricbox.mapper.ElectricBoxQrLogMapper;
import com.example.siteplatform.electricbox.vo.ElectricBoxScopeVO;
import com.example.siteplatform.inspection.mapper.InspectionRecordMapper;
import com.example.siteplatform.inspection.mapper.InspectionRectificationMapper;
import com.example.siteplatform.project.constant.InspectionPermissionCodes;
import com.example.siteplatform.project.service.ProjectPermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ElectricBoxServiceWriteIntegrityTest {

    @Mock private ElectricBoxMapper electricBoxMapper;
    @Mock private ElectricBoxQrLogMapper qrLogMapper;
    @Mock private ProjectPermissionService permissionService;
    @Mock private InspectionRecordMapper inspectionRecordMapper;
    @Mock private InspectionRectificationMapper inspectionRectificationMapper;
    @Mock private SysUserMapper sysUserMapper;
    @Mock private ElectricBoxInspectionScopeService inspectionScopeService;
    @Mock private WechatPlatformClient wechatPlatformClient;

    private ElectricBoxService service;
    private SysUser operator;

    @BeforeEach
    void setUp() {
        service = new ElectricBoxService();
        ReflectionTestUtils.setField(service, "electricBoxMapper", electricBoxMapper);
        ReflectionTestUtils.setField(service, "qrLogMapper", qrLogMapper);
        ReflectionTestUtils.setField(service, "projectPermissionService", permissionService);
        ReflectionTestUtils.setField(service, "inspectionRecordMapper", inspectionRecordMapper);
        ReflectionTestUtils.setField(service, "inspectionRectificationMapper", inspectionRectificationMapper);
        ReflectionTestUtils.setField(service, "sysUserMapper", sysUserMapper);
        ReflectionTestUtils.setField(service, "inspectionScopeService", inspectionScopeService);
        ReflectionTestUtils.setField(service, "wechatPlatformClient", wechatPlatformClient);
        lenient().when(permissionService.hasInspectionPermission(anyLong(), anyLong(), anyString())).thenReturn(true);
        lenient().when(permissionService.hasProjectPermission(anyLong(), anyLong())).thenReturn(true);
        lenient().when(qrLogMapper.insert(any())).thenReturn(1);
        lenient().when(inspectionScopeService.getCurrentForBox(any())).thenAnswer(invocation -> {
            ElectricBoxScopeVO scope = new ElectricBoxScopeVO();
            scope.setEffectiveToday(true);
            return scope;
        });
        operator = activeUser(7L, "operator", "操作员");
    }

    @Test
    void createUsesServerControlledLifecycleAndCanonicalAssigneeName() {
        ElectricBoxRequest request = request();
        request.setStatus("REMOVED");
        request.setQrStatus("DISABLED");
        request.setResponsibleElectricianId(9L);
        request.setResponsibleElectricianName("<伪造姓名>");
        when(sysUserMapper.selectById(9L)).thenReturn(activeUser(9L, "electrician", "真实电工"));
        doAnswer(invocation -> {
            ElectricBox box = invocation.getArgument(0);
            box.setId(12L);
            return 1;
        }).when(electricBoxMapper).insert(any());

        service.create(request, operator);

        ArgumentCaptor<ElectricBox> captor = ArgumentCaptor.forClass(ElectricBox.class);
        verify(electricBoxMapper).insert(captor.capture());
        ElectricBox inserted = captor.getValue();
        assertEquals("ACTIVE", inserted.getStatus());
        assertEquals("BOUND", inserted.getQrStatus());
        assertEquals("真实电工", inserted.getResponsibleElectricianName());
        verify(permissionService).hasProjectPermission(9L, 1L);
    }

    @Test
    void createRejectsUnknownAssigneeBeforeWritingBoxOrMembership() {
        ElectricBoxRequest request = request();
        request.setResponsibleElectricianId(999L);
        when(sysUserMapper.selectById(999L)).thenReturn(null);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.create(request, operator));

        assertTrue(error.getMessage().contains("有效系统账号"));
        verify(electricBoxMapper, never()).insert(any());
        verify(permissionService, never()).hasProjectPermission(999L, 1L);
    }

    @Test
    void updateRejectsMovingBoxToAnotherProject() {
        ElectricBox existing = existingBox();
        when(electricBoxMapper.selectById(12L)).thenReturn(existing);
        ElectricBoxRequest request = request();
        request.setProjectId(2L);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.update(12L, request, operator));

        assertTrue(error.getMessage().contains("所属项目创建后不可变更"));
        verify(electricBoxMapper, never()).updateById(any());
    }

    @Test
    void ordinaryUpdateCannotBypassLifecycleEndpoints() {
        ElectricBox existing = existingBox();
        when(electricBoxMapper.selectById(12L)).thenReturn(existing);
        when(electricBoxMapper.updateById(any())).thenReturn(1);
        ElectricBoxRequest request = request();
        request.setStatus("REMOVED");
        request.setQrStatus("DISABLED");

        service.update(12L, request, operator);

        assertEquals("ACTIVE", existing.getStatus());
        assertEquals("BOUND", existing.getQrStatus());
    }

    @Test
    void changingQrCodeRequiresDedicatedQrPermission() {
        ElectricBox existing = existingBox();
        when(electricBoxMapper.selectById(12L)).thenReturn(existing);
        when(permissionService.hasInspectionPermission(
                7L, 1L, InspectionPermissionCodes.BOX_QR_MANAGE)).thenReturn(false);
        ElectricBoxRequest request = request();
        request.setQrCode("QR-NEW");

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.update(12L, request, operator));

        assertEquals(403, error.getCode());
        verify(electricBoxMapper, never()).updateById(any());
    }

    @Test
    void publicAccessEndpointRequiresDedicatedPermission() {
        when(electricBoxMapper.selectById(12L)).thenReturn(existingBox());
        when(permissionService.hasInspectionPermission(
                7L, 1L, InspectionPermissionCodes.BOX_PUBLIC_ACCESS)).thenReturn(false);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.setPublicAccess(12L, false, operator));

        assertEquals(403, error.getCode());
        verify(electricBoxMapper, never()).updateById(any());
    }

    @Test
    void directQrRebindRequiresQrPermissionRatherThanPublicAccessPermission() {
        when(electricBoxMapper.selectById(12L)).thenReturn(existingBox());
        when(permissionService.hasInspectionPermission(
                7L, 1L, InspectionPermissionCodes.BOX_QR_MANAGE)).thenReturn(false);
        lenient().when(permissionService.hasInspectionPermission(
                7L, 1L, InspectionPermissionCodes.BOX_PUBLIC_ACCESS)).thenReturn(true);
        ElectricBoxQrRebindRequest request = new ElectricBoxQrRebindRequest();
        request.setQrCode("QR-NEW");

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.rebindQrCode(12L, request, operator));

        assertEquals(403, error.getCode());
        verify(electricBoxMapper, never()).updateById(any());
    }

    @Test
    void disableRequiresBoxManagePermissionRatherThanQrPermission() {
        when(electricBoxMapper.selectById(12L)).thenReturn(existingBox());
        when(permissionService.hasInspectionPermission(
                7L, 1L, InspectionPermissionCodes.BOX_MANAGE)).thenReturn(false);
        lenient().when(permissionService.hasInspectionPermission(
                7L, 1L, InspectionPermissionCodes.BOX_QR_MANAGE)).thenReturn(true);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.disable(12L, null, operator));

        assertEquals(403, error.getCode());
        verify(electricBoxMapper, never()).updateById(any());
    }

    @Test
    void printLogRequiresQrManagePermission() {
        when(electricBoxMapper.selectById(12L)).thenReturn(existingBox());
        when(permissionService.hasInspectionPermission(
                7L, 1L, InspectionPermissionCodes.BOX_QR_MANAGE)).thenReturn(false);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.recordPrintLog(12L, null, operator));

        assertEquals(403, error.getCode());
        verify(qrLogMapper, never()).insert(any());
    }

    @Test
    void qrLogListRequiresQrManagePermission() {
        when(electricBoxMapper.selectById(12L)).thenReturn(existingBox());
        when(permissionService.hasInspectionPermission(
                7L, 1L, InspectionPermissionCodes.BOX_QR_MANAGE)).thenReturn(false);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.listQrLogs(12L, operator));

        assertEquals(403, error.getCode());
        verify(qrLogMapper, never()).selectList(any());
    }

    @Test
    void rejectsOversizedFieldBeforeDatabaseWrite() {
        ElectricBoxRequest request = request();
        request.setInstallLocation("位".repeat(201));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.create(request, operator));

        assertTrue(error.getMessage().contains("安装位置不能超过200个字符"));
        verify(electricBoxMapper, never()).insert(any());
    }

    @Test
    void updateReturnsConflictWhenDatabaseWriteDidNotAffectOneRow() {
        when(electricBoxMapper.selectById(12L)).thenReturn(existingBox());
        when(electricBoxMapper.updateById(any())).thenReturn(0);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.update(12L, request(), operator));

        assertEquals(409, error.getCode());
    }

    private ElectricBoxRequest request() {
        ElectricBoxRequest request = new ElectricBoxRequest();
        request.setProjectId(1L);
        request.setBoxCode("BOX-001");
        request.setBoxName("一级配电箱");
        request.setInstallLocation("一层东侧");
        request.setQrCode("QR-001");
        request.setPublicAccessEnabled(1);
        request.setRemark("现场电箱");
        return request;
    }

    private ElectricBox existingBox() {
        ElectricBox box = new ElectricBox();
        box.setId(12L);
        box.setProjectId(1L);
        box.setBoxCode("BOX-001");
        box.setBoxName("一级配电箱");
        box.setInstallLocation("一层东侧");
        box.setQrCode("QR-001");
        box.setPublicCode("PUBLIC-001");
        box.setPublicAccessEnabled(1);
        box.setStatus("ACTIVE");
        box.setQrStatus("BOUND");
        box.setDeleted(0);
        return box;
    }

    private SysUser activeUser(Long id, String username, String realName) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(username);
        user.setRealName(realName);
        user.setStatus(1);
        return user;
    }
}
