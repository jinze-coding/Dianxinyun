package com.example.siteplatform.electricbox.service;

import com.example.siteplatform.electricbox.entity.ElectricBox;
import com.example.siteplatform.electricbox.entity.ElectricBoxInspectionScope;
import com.example.siteplatform.electricbox.mapper.ElectricBoxInspectionScopeMapper;
import com.example.siteplatform.electricbox.mapper.ElectricBoxMapper;
import com.example.siteplatform.project.service.ProjectPermissionService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ElectricBoxInspectionScopeServiceTest {

    @Test
    void excludesDatesBeforeElectricBoxWasCreated() {
        ElectricBoxInspectionScopeService service = new ElectricBoxInspectionScopeService(
                mock(ElectricBoxInspectionScopeMapper.class),
                mock(ElectricBoxMapper.class),
                mock(ProjectPermissionService.class));
        ElectricBox box = new ElectricBox();
        box.setId(10L);
        box.setStatus("ACTIVE");
        box.setCreateTime(LocalDateTime.of(2026, 7, 10, 8, 30));

        assertFalse(service.isRequired(box, LocalDate.of(2026, 7, 9)));
    }

    @Test
    void countsOnlyRequiredDaysThroughCutoffDate() {
        ElectricBoxInspectionScopeService service = spy(new ElectricBoxInspectionScopeService(
                mock(ElectricBoxInspectionScopeMapper.class),
                mock(ElectricBoxMapper.class),
                mock(ProjectPermissionService.class)));
        ElectricBox box = new ElectricBox();
        box.setId(10L);
        box.setStatus("ACTIVE");
        doReturn(true).when(service).isRequired(eq(box), any(LocalDate.class));

        YearMonth month = YearMonth.of(2026, 7);
        assertEquals(12, service.countRequiredDaysThrough(box, month, LocalDate.of(2026, 7, 12)));
        assertEquals(31, service.countRequiredDaysThrough(box, month, LocalDate.of(2026, 8, 1)));
        assertEquals(0, service.countRequiredDaysThrough(box, month, LocalDate.of(2026, 6, 30)));
        assertEquals(31, service.countRequiredDays(box, month));
    }

    @Test
    void resolvesDateRangeFromOneScopeHistoryQuery() {
        ElectricBoxInspectionScopeMapper scopeMapper = mock(ElectricBoxInspectionScopeMapper.class);
        ElectricBoxInspectionScopeService service = new ElectricBoxInspectionScopeService(
                scopeMapper,
                mock(ElectricBoxMapper.class),
                mock(ProjectPermissionService.class));
        ElectricBox box = new ElectricBox();
        box.setId(10L);
        box.setStatus("ACTIVE");
        box.setCreateTime(LocalDateTime.of(2026, 7, 3, 8, 30));
        ElectricBoxInspectionScope excluded = scope(1L, LocalDate.of(2026, 7, 5),
                LocalDate.of(2026, 7, 9), 0);
        ElectricBoxInspectionScope included = scope(2L, LocalDate.of(2026, 7, 10),
                null, 1);
        when(scopeMapper.selectList(any())).thenReturn(List.of(excluded, included));

        Set<LocalDate> requiredDates = service.requiredDates(
                box, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 12));

        assertEquals(Set.of(
                LocalDate.of(2026, 7, 3),
                LocalDate.of(2026, 7, 4),
                LocalDate.of(2026, 7, 10),
                LocalDate.of(2026, 7, 11),
                LocalDate.of(2026, 7, 12)), requiredDates);
        verify(scopeMapper, times(1)).selectList(any());
    }

    private ElectricBoxInspectionScope scope(Long id, LocalDate effectiveDate,
                                             LocalDate endDate, int included) {
        ElectricBoxInspectionScope scope = new ElectricBoxInspectionScope();
        scope.setId(id);
        scope.setElectricBoxId(10L);
        scope.setEffectiveDate(effectiveDate);
        scope.setEndDate(endDate);
        scope.setIncluded(included);
        return scope;
    }
}
