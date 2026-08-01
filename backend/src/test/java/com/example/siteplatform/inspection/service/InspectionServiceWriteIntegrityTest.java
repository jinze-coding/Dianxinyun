package com.example.siteplatform.inspection.service;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.electricbox.entity.ElectricBox;
import com.example.siteplatform.electricbox.mapper.ElectricBoxMapper;
import com.example.siteplatform.electricbox.service.ElectricBoxInspectionScopeService;
import com.example.siteplatform.file.service.FileResourceService;
import com.example.siteplatform.inspection.dto.InspectionItemRequest;
import com.example.siteplatform.inspection.dto.InspectionRecordRequest;
import com.example.siteplatform.inspection.entity.InspectionRecord;
import com.example.siteplatform.inspection.entity.InspectionRecordItem;
import com.example.siteplatform.inspection.mapper.InspectionRecordItemMapper;
import com.example.siteplatform.inspection.mapper.InspectionRecordMapper;
import com.example.siteplatform.project.constant.InspectionPermissionCodes;
import com.example.siteplatform.project.service.ProjectPermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InspectionServiceWriteIntegrityTest {

    @Mock private ElectricBoxMapper electricBoxMapper;
    @Mock private InspectionRecordMapper recordMapper;
    @Mock private InspectionRecordItemMapper itemMapper;
    @Mock private ProjectPermissionService permissionService;
    @Mock private ElectricBoxInspectionScopeService scopeService;
    @Mock private FileResourceService fileResourceService;

    private InspectionService service;
    private SysUser operator;

    @BeforeEach
    void setUp() {
        service = new InspectionService();
        ReflectionTestUtils.setField(service, "electricBoxMapper", electricBoxMapper);
        ReflectionTestUtils.setField(service, "inspectionRecordMapper", recordMapper);
        ReflectionTestUtils.setField(service, "inspectionRecordItemMapper", itemMapper);
        ReflectionTestUtils.setField(service, "projectPermissionService", permissionService);
        ReflectionTestUtils.setField(service, "inspectionScopeService", scopeService);
        ReflectionTestUtils.setField(service, "fileResourceService", fileResourceService);
        operator = new SysUser();
        operator.setId(7L);
        operator.setUsername("inspector");
        operator.setRealName("现场巡检员");

        lenient().when(permissionService.hasInspectionPermission(anyLong(), anyLong(), anyString()))
                .thenReturn(true);
        lenient().when(scopeService.isRequired(any(ElectricBox.class), any(LocalDate.class))).thenReturn(true);
        lenient().when(recordMapper.selectCount(any())).thenReturn(0L);
        lenient().doAnswer(invocation -> {
            InspectionRecord record = invocation.getArgument(0);
            record.setId(100L);
            return 1;
        }).when(recordMapper).insert(any());
        lenient().when(itemMapper.insert(any())).thenReturn(1);
    }

    @Test
    void rejectsNullDuplicateAndIncompleteCheckItemsBeforeDatabaseAccess() {
        BusinessException nullError = assertThrows(BusinessException.class,
                () -> service.createRecord(null, operator));
        InspectionRecordRequest duplicate = request();
        duplicate.getItems().set(5, item("APPEARANCE", "伪造名称", "NORMAL", null));
        BusinessException duplicateError = assertThrows(BusinessException.class,
                () -> service.createRecord(duplicate, operator));

        assertTrue(nullError.getMessage().contains("巡检记录不能为空"));
        assertTrue(duplicateError.getMessage().contains("不能重复"));
        verify(electricBoxMapper, never()).selectByIdForUpdate(anyLong());
    }

    @Test
    void rejectsAbnormalItemWithoutDescriptionAndInvalidPhotoIds() {
        InspectionRecordRequest abnormal = request();
        abnormal.getItems().get(0).setResult("ABNORMAL");
        BusinessException abnormalError = assertThrows(BusinessException.class,
                () -> service.createRecord(abnormal, operator));
        InspectionRecordRequest invalidPhoto = request();
        invalidPhoto.setOuterPhotoFileIds(List.of(3L, 3L));
        BusinessException photoError = assertThrows(BusinessException.class,
                () -> service.createRecord(invalidPhoto, operator));

        assertTrue(abnormalError.getMessage().contains("异常时必须填写说明"));
        assertTrue(photoError.getMessage().contains("重复或无效"));
        verify(recordMapper, never()).insert(any());
    }

    @Test
    void locksBoxAndUsesServerControlledTemplateAndCanonicalItemNames() {
        when(electricBoxMapper.selectByIdForUpdate(10L)).thenReturn(box());
        InspectionRecordRequest request = request();
        request.setTemplateCode("BYPASS-TEMPLATE");
        request.setSource("SAFETY_SPOT_CHECK");
        request.getItems().forEach(item -> item.setItemName("<script>伪造名称</script>"));

        service.createRecord(request, operator);

        ArgumentCaptor<InspectionRecord> recordCaptor = ArgumentCaptor.forClass(InspectionRecord.class);
        verify(recordMapper).insert(recordCaptor.capture());
        assertEquals(InspectionService.TEMPLATE_ELECTRIC_BOX_DAILY,
                recordCaptor.getValue().getTemplateCode());
        assertEquals(InspectionService.SOURCE_ELECTRICIAN_DAILY,
                recordCaptor.getValue().getSource());
        assertEquals("现场巡检员", recordCaptor.getValue().getInspectorName());
        ArgumentCaptor<InspectionRecordItem> itemCaptor = ArgumentCaptor.forClass(InspectionRecordItem.class);
        verify(itemMapper, org.mockito.Mockito.times(6)).insert(itemCaptor.capture());
        assertEquals(List.of("内外观", "漏电保护器", "熔断", "保护接零", "220V插座", "380V插座"),
                itemCaptor.getAllValues().stream().map(InspectionRecordItem::getItemName).toList());
        verify(electricBoxMapper).selectByIdForUpdate(10L);
    }

    @Test
    void returnsConflictWhenMainRecordInsertDidNotTakeEffect() {
        when(electricBoxMapper.selectByIdForUpdate(10L)).thenReturn(box());
        doReturn(0).when(recordMapper).insert(any());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.createRecord(request(), operator));

        assertEquals(409, error.getCode());
        verify(itemMapper, never()).insert(any());
    }

    @Test
    void returnsConflictWhenDailyRecordAlreadyExistsAfterLock() {
        when(electricBoxMapper.selectByIdForUpdate(10L)).thenReturn(box());
        when(recordMapper.selectCount(any())).thenReturn(1L);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.createRecord(request(), operator));

        assertEquals(409, error.getCode());
        verify(electricBoxMapper).selectByIdForUpdate(10L);
        verify(recordMapper, never()).insert(any());
    }

    @Test
    void returnsConflictWhenAnyItemInsertDidNotTakeEffect() {
        when(electricBoxMapper.selectByIdForUpdate(10L)).thenReturn(box());
        doReturn(0).when(itemMapper).insert(any());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.createRecord(request(), operator));

        assertEquals(409, error.getCode());
    }

    @Test
    void compatibleSubmitReturnsConflictWhenUpdateDidNotTakeEffect() {
        InspectionRecord record = new InspectionRecord();
        record.setId(100L);
        record.setProjectId(1L);
        record.setElectricBoxId(10L);
        record.setInspectorId(7L);
        when(recordMapper.selectById(100L)).thenReturn(record);
        when(recordMapper.updateById(record)).thenReturn(0);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.submitRecord(100L, operator));

        assertEquals(409, error.getCode());
    }

    private InspectionRecordRequest request() {
        InspectionRecordRequest request = new InspectionRecordRequest();
        request.setProjectId(1L);
        request.setElectricBoxId(10L);
        request.setTemplateCode(InspectionService.TEMPLATE_ELECTRIC_BOX_DAILY);
        request.setSource(InspectionService.SOURCE_ELECTRICIAN_DAILY);
        request.setCheckDate(LocalDate.now());
        request.setRemark("现场正常");
        request.setItems(new ArrayList<>(List.of(
                item("APPEARANCE", "内外观", "NORMAL", null),
                item("LEAKAGE_PROTECTOR", "漏电保护器", "NORMAL", null),
                item("FUSE", "熔断", "NORMAL", null),
                item("PROTECTIVE_ZERO", "保护接零", "NORMAL", null),
                item("SOCKET_220V", "220V插座", "NORMAL", null),
                item("SOCKET_380V", "380V插座", "NORMAL", null)
        )));
        return request;
    }

    private InspectionItemRequest item(String code, String name, String result, String description) {
        InspectionItemRequest item = new InspectionItemRequest();
        item.setItemCode(code);
        item.setItemName(name);
        item.setResult(result);
        item.setDescription(description);
        return item;
    }

    private ElectricBox box() {
        ElectricBox box = new ElectricBox();
        box.setId(10L);
        box.setProjectId(1L);
        box.setBoxCode("BOX-001");
        box.setBoxName("一级配电箱");
        box.setInstallLocation("一层东侧");
        box.setStatus("ACTIVE");
        return box;
    }
}
