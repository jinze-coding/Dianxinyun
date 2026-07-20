package com.example.siteplatform.electricbox.service;

import com.example.siteplatform.electricbox.entity.ElectricBox;
import com.example.siteplatform.electricbox.mapper.ElectricBoxInspectionScopeMapper;
import com.example.siteplatform.electricbox.mapper.ElectricBoxMapper;
import com.example.siteplatform.project.service.ProjectPermissionService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

class ElectricBoxInspectionScopeServiceTest {

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
}
