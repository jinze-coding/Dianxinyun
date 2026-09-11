package com.example.siteplatform.seal.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.log.mapper.OperationLogMapper;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.seal.entity.SealApplication;
import com.example.siteplatform.seal.entity.SealApplicationItem;
import com.example.siteplatform.seal.mapper.SealApplicationItemMapper;
import com.example.siteplatform.seal.mapper.SealApplicationMapper;
import com.example.siteplatform.system.constant.SystemPermissionCodes;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SealLedgerServiceTest {
    private final SealApplicationMapper applications = mock(SealApplicationMapper.class);
    private final SealApplicationItemMapper items = mock(SealApplicationItemMapper.class);
    private final OperationLogMapper logs = mock(OperationLogMapper.class);
    private final ProjectPermissionService permissions = mock(ProjectPermissionService.class);
    private final SealLedgerWordRenderer renderer = mock(SealLedgerWordRenderer.class);
    private final SealLedgerService service = new SealLedgerService(applications, items, logs, permissions, renderer);
    private final SysUser user = new SysUser();

    @BeforeEach
    void metadata() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), SealApplicationMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, SealApplication.class);
        TableInfoHelper.initTableInfo(assistant, SealApplicationItem.class);
        user.setId(7L);
        user.setUsername("ledger_exporter");
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void exportRetainsProjectKeywordAndApprovalDayFiltersAndStableDetailOrder() {
        var application = new SealApplication();
        application.setId(42L);
        var first = new SealApplicationItem();
        first.setApplicationId(42L);
        first.setDocumentName("第一份文件");
        var second = new SealApplicationItem();
        second.setApplicationId(42L);
        second.setDocumentName("第二份文件");
        when(applications.selectList(any())).thenReturn(List.of(application));
        when(items.selectList(any())).thenReturn(List.of(first, second));
        when(renderer.render(anyList(), anyMap())).thenReturn(new byte[]{80, 75});
        when(logs.insert(any())).thenReturn(1);

        var export = export("APPROVED", "  合同  ");
        ArgumentCaptor<LambdaQueryWrapper> query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(applications).selectList(query.capture());
        String sql = query.getValue().getSqlSegment().toLowerCase();
        assertTrue(sql.contains("project_id"));
        assertTrue(sql.contains("status"));
        assertTrue(sql.contains("approval_time <"));
        assertTrue(sql.contains("order by approval_time asc,application_no asc,id asc"));
        assertTrue(sql.contains("limit 10001"));
        assertTrue(query.getValue().getParamNameValuePairs().containsValue(9L));
        assertTrue(query.getValue().getParamNameValuePairs().containsValue("APPROVED"));
        assertTrue(query.getValue().getParamNameValuePairs().containsValue("%合同%"));
        assertTrue(query.getValue().getParamNameValuePairs().containsValue(LocalDateTime.of(2026, 9, 11, 0, 0)));
        assertTrue(query.getValue().getParamNameValuePairs().containsValue(LocalDateTime.of(2026, 9, 12, 0, 0)));
        ArgumentCaptor<LambdaQueryWrapper> detailQuery = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(items).selectList(detailQuery.capture());
        assertTrue(detailQuery.getValue().getSqlSegment().toLowerCase()
                .contains("order by application_id asc,sort_order asc,id asc"));
        verify(renderer).render(List.of(application), java.util.Map.of(42L, List.of(first, second)));
        verify(permissions).checkProjectPermission(7L, 9L);
        verify(permissions).requireSystemPermission(7L, 9L, SystemPermissionCodes.SEAL_EXPORT);
        verify(logs).insert(any());
        assertEquals("用印台账_2026-09-11_2026-09-11.docx", export.fileName());
        assertArrayEquals(new byte[]{80, 75}, export.content());
    }

    @Test
    void noMatchesSkipsItemQueryAndRendersEmptyLedger() {
        when(applications.selectList(any())).thenReturn(List.of());
        when(renderer.render(anyList(), anyMap())).thenReturn(new byte[]{80, 75});
        when(logs.insert(any())).thenReturn(1);
        export(null, null);
        verifyNoInteractions(items);
        verify(renderer).render(List.of(), java.util.Map.of());
        verify(logs).insert(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"DRAFT", "PENDING_APPROVAL", "REJECTED", "WITHDRAWN"})
    void nonApprovedStatusIsRejectedBeforeDataRead(String status) {
        assertThrows(BusinessException.class, () -> export(status, null));
        verifyNoInteractions(applications, items, renderer, logs);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void bothProjectAndExportPermissionAreRequired(boolean projectAllowed) {
        if (projectAllowed) {
            doThrow(BusinessException.forbidden("没有台账导出权限")).when(permissions)
                    .requireSystemPermission(7L, 9L, SystemPermissionCodes.SEAL_EXPORT);
        } else {
            doThrow(BusinessException.forbidden("没有项目范围")).when(permissions).checkProjectPermission(7L, 9L);
        }
        assertThrows(BusinessException.class, () -> export(null, null));
        verifyNoInteractions(applications, items, renderer, logs);
    }

    @Test
    void applicationLimitIsEnforcedBeforeLoadingDetailsOrCreatingDocument() {
        when(applications.selectList(any())).thenReturn(Collections.nCopies(10_001, new SealApplication()));
        var error = assertThrows(BusinessException.class, () -> export(null, null));
        assertTrue(error.getMessage().contains("10000"));
        verifyNoInteractions(items, renderer, logs);
    }

    @Test
    void auditWriteFailureDoesNotReturnAFile() {
        when(applications.selectList(any())).thenReturn(List.of());
        when(renderer.render(anyList(), anyMap())).thenReturn(new byte[]{80, 75});
        when(logs.insert(any())).thenReturn(0);
        assertThrows(BusinessException.class, () -> export(null, null));
    }

    private SealLedgerService.LedgerExport export(String status, String keyword) {
        return service.export(9L, "DAY", LocalDate.of(2026, 9, 11), null, null, keyword, status, user, null);
    }
}
