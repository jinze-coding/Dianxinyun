package com.example.siteplatform.electricbox.service;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.electricbox.dto.ElectricBoxScopeRequest;
import com.example.siteplatform.electricbox.entity.ElectricBox;
import com.example.siteplatform.electricbox.entity.ElectricBoxInspectionScope;
import com.example.siteplatform.electricbox.mapper.ElectricBoxInspectionScopeMapper;
import com.example.siteplatform.electricbox.mapper.ElectricBoxMapper;
import com.example.siteplatform.project.service.ProjectPermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ElectricBoxInspectionScopeWriteIntegrityTest {

    @Mock private ElectricBoxInspectionScopeMapper scopeMapper;
    @Mock private ElectricBoxMapper electricBoxMapper;
    @Mock private ProjectPermissionService permissionService;

    private ElectricBoxInspectionScopeService service;
    private SysUser operator;

    @BeforeEach
    void setUp() {
        service = new ElectricBoxInspectionScopeService(scopeMapper, electricBoxMapper, permissionService);
        operator = new SysUser();
        operator.setId(7L);
        operator.setUsername("inspector");
        lenient().when(permissionService.hasInspectionPermission(anyLong(), anyLong(), anyString()))
                .thenReturn(true);
        lenient().when(scopeMapper.selectList(any())).thenReturn(List.of());
        lenient().when(scopeMapper.insert(any())).thenReturn(1);
        lenient().when(scopeMapper.updateById(any())).thenReturn(1);
        lenient().when(scopeMapper.selectOne(any())).thenReturn(null);
    }

    @Test
    void updateLocksElectricBoxBeforeReadingOpenScopeHistory() {
        when(electricBoxMapper.selectByIdForUpdate(10L)).thenReturn(box());

        service.update(10L, request(), operator);

        verify(electricBoxMapper).selectByIdForUpdate(10L);
        verify(scopeMapper).insert(any(ElectricBoxInspectionScope.class));
    }

    @Test
    void updateStopsBeforeInsertWhenOpenHistoryCouldNotBeClosed() {
        when(electricBoxMapper.selectByIdForUpdate(10L)).thenReturn(box());
        ElectricBoxInspectionScope open = new ElectricBoxInspectionScope();
        open.setId(3L);
        open.setEffectiveDate(LocalDate.now().minusDays(3));
        when(scopeMapper.selectList(any())).thenReturn(List.of(open));
        doReturn(0).when(scopeMapper).updateById(open);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.update(10L, request(), operator));

        assertEquals(409, error.getCode());
        verify(scopeMapper, never()).insert(any());
    }

    @Test
    void updateReturnsConflictWhenNewHistoryInsertDidNotTakeEffect() {
        when(electricBoxMapper.selectByIdForUpdate(10L)).thenReturn(box());
        doReturn(0).when(scopeMapper).insert(any());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.update(10L, request(), operator));

        assertEquals(409, error.getCode());
    }

    @Test
    void updateRejectsOversizedReasonBeforeHistoryWrite() {
        when(electricBoxMapper.selectByIdForUpdate(10L)).thenReturn(box());
        ElectricBoxScopeRequest request = request();
        request.setReason("a".repeat(301));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.update(10L, request, operator));

        assertEquals(400, error.getCode());
        verify(scopeMapper, never()).insert(any());
    }

    private ElectricBox box() {
        ElectricBox box = new ElectricBox();
        box.setId(10L);
        box.setProjectId(1L);
        box.setStatus("ACTIVE");
        return box;
    }

    private ElectricBoxScopeRequest request() {
        ElectricBoxScopeRequest request = new ElectricBoxScopeRequest();
        request.setIncluded(true);
        request.setEffectiveDate(LocalDate.now());
        request.setReason("纳入日检");
        return request;
    }
}
