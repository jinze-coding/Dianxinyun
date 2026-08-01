package com.example.siteplatform.inspection.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.electricbox.entity.ElectricBox;
import com.example.siteplatform.electricbox.mapper.ElectricBoxMapper;
import com.example.siteplatform.electricbox.service.ElectricBoxInspectionScopeService;
import com.example.siteplatform.inspection.entity.InspectionRecord;
import com.example.siteplatform.inspection.mapper.InspectionRecordItemMapper;
import com.example.siteplatform.inspection.mapper.InspectionRecordMapper;
import com.example.siteplatform.inspection.mapper.InspectionRectificationMapper;
import com.example.siteplatform.inspection.vo.InspectionMonthSummaryVO;
import com.example.siteplatform.inspection.vo.InspectionRecordVO;
import com.example.siteplatform.inspection.vo.PublicElectricBoxMonthlyVO;
import com.example.siteplatform.inspection.vo.PublicElectricBoxSummaryVO;
import com.example.siteplatform.project.mapper.ProjectInfoMapper;
import com.example.siteplatform.project.service.ProjectPermissionService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InspectionServicePeriodTest {

    @Mock
    private ElectricBoxMapper electricBoxMapper;
    @Mock
    private InspectionRecordMapper inspectionRecordMapper;
    @Mock
    private InspectionRecordItemMapper inspectionRecordItemMapper;
    @Mock
    private InspectionRectificationMapper inspectionRectificationMapper;
    @Mock
    private ProjectInfoMapper projectInfoMapper;
    @Mock
    private ProjectPermissionService permissionService;
    @Mock
    private ElectricBoxInspectionScopeService scopeService;

    private InspectionService service;
    private SysUser currentUser;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), InspectionRecordMapper.class.getName()),
                InspectionRecord.class);
        service = spy(new InspectionService());
        ReflectionTestUtils.setField(service, "electricBoxMapper", electricBoxMapper);
        ReflectionTestUtils.setField(service, "inspectionRecordMapper", inspectionRecordMapper);
        ReflectionTestUtils.setField(service, "inspectionRecordItemMapper", inspectionRecordItemMapper);
        ReflectionTestUtils.setField(service, "inspectionRectificationMapper", inspectionRectificationMapper);
        ReflectionTestUtils.setField(service, "projectInfoMapper", projectInfoMapper);
        ReflectionTestUtils.setField(service, "projectPermissionService", permissionService);
        ReflectionTestUtils.setField(service, "inspectionScopeService", scopeService);
        currentUser = new SysUser();
        currentUser.setId(7L);
        lenient().when(permissionService.hasAnyInspectionPermission(eq(7L), eq(1L), any(String[].class)))
                .thenReturn(true);
    }

    @Test
    void rejectsMonthTogetherWithCheckDate() {
        BusinessException exception = assertThrows(BusinessException.class, () -> service.listRecords(
                1L, null, null, "2026-07", "2026-07-01", null, null, currentUser));
        BusinessException summaryException = assertThrows(BusinessException.class, () -> service.getMonthSummary(
                1L, null, "2026-07", "2026-07-01", currentUser));

        assertEquals(400, exception.getCode());
        assertEquals(400, summaryException.getCode());
        assertThat(exception.getMessage()).contains("不能同时");
        assertThat(summaryException.getMessage()).contains("不能同时");
    }

    @Test
    void rejectsFutureCheckDateForRecordsAndSummary() {
        String futureDate = LocalDate.now().plusDays(1).toString();

        BusinessException recordsException = assertThrows(BusinessException.class, () -> service.listRecords(
                1L, null, null, null, futureDate, null, null, currentUser));
        BusinessException summaryException = assertThrows(BusinessException.class, () -> service.getMonthSummary(
                1L, null, null, futureDate, currentUser));

        assertEquals(400, recordsException.getCode());
        assertEquals(400, summaryException.getCode());
        assertThat(recordsException.getMessage()).contains("不能晚于今天");
        assertThat(summaryException.getMessage()).contains("不能晚于今天");
    }

    @Test
    void dailySummaryCountsRequiredBoxesAndDeduplicatesByBoxAndDate() {
        LocalDate targetDate = LocalDate.now().minusDays(1);
        ElectricBox requiredWithDuplicateRecords = box(10L);
        ElectricBox requiredWithoutRecord = box(20L);
        ElectricBox outsideScope = box(30L);
        when(electricBoxMapper.selectList(any())).thenReturn(List.of(
                requiredWithDuplicateRecords, requiredWithoutRecord, outsideScope));
        when(scopeService.isRequired(any(ElectricBox.class), eq(targetDate)))
                .thenAnswer(invocation -> !Long.valueOf(30L).equals(
                        ((ElectricBox) invocation.getArgument(0)).getId()));

        List<InspectionRecordVO> records = List.of(
                record(100L, 10L, targetDate, 0),
                record(101L, 10L, targetDate, 2),
                record(102L, 30L, targetDate, 1));
        when(inspectionRecordMapper.selectList(any())).thenReturn(List.of(
                rawRecord(100L, 10L, targetDate, 0),
                rawRecord(101L, 10L, targetDate, 2),
                rawRecord(102L, 30L, targetDate, 1)));
        doReturn(records).when(service).listRecords(
                eq(1L), eq(null), eq(null), eq(null), eq(targetDate.toString()),
                eq(null), eq(null), eq(currentUser));

        InspectionMonthSummaryVO summary = service.getMonthSummary(
                1L, null, null, targetDate.toString(), currentUser);

        assertEquals("DAY", summary.getPeriodType());
        assertEquals(targetDate.toString(), summary.getPeriodValue());
        assertEquals(YearMonth.from(targetDate).toString(), summary.getMonth());
        assertEquals(2, summary.getShouldCheck());
        assertEquals(1, summary.getChecked());
        assertEquals(1, summary.getMissed());
        assertEquals(1, summary.getAbnormal());
        assertEquals(records, summary.getRecords());
    }

    @Test
    void monthlySummaryKeepsMonthContractAndAddsPeriodMetadata() {
        YearMonth targetMonth = YearMonth.now().minusMonths(1);
        LocalDate recordDate = targetMonth.atDay(1);
        ElectricBox box = box(10L);
        when(electricBoxMapper.selectList(any())).thenReturn(List.of(box));
        when(scopeService.countRequiredDaysThrough(eq(box), eq(targetMonth), any(LocalDate.class)))
                .thenReturn(3);
        when(scopeService.isRequired(box, recordDate)).thenReturn(true);
        List<InspectionRecordVO> records = List.of(record(100L, 10L, recordDate, 0));
        when(inspectionRecordMapper.selectList(any())).thenReturn(List.of(
                rawRecord(100L, 10L, recordDate, 0)));
        doReturn(records).when(service).listRecords(
                eq(1L), eq(null), eq(null), eq(targetMonth.toString()), eq(null),
                eq(null), eq(null), eq(currentUser));

        InspectionMonthSummaryVO summary = service.getMonthSummary(
                1L, null, targetMonth.toString(), null, currentUser);

        assertEquals(targetMonth.toString(), summary.getMonth());
        assertEquals("MONTH", summary.getPeriodType());
        assertEquals(targetMonth.toString(), summary.getPeriodValue());
        assertEquals(3, summary.getShouldCheck());
        assertEquals(1, summary.getChecked());
        assertEquals(2, summary.getMissed());
    }

    @Test
    void monthlySummaryCountsAbnormalRequiredDaysOnlyAndDeduplicatesByBoxAndDate() {
        YearMonth targetMonth = YearMonth.now().minusMonths(1);
        LocalDate requiredDate = targetMonth.atDay(1);
        LocalDate outsideScopeDate = targetMonth.atDay(2);
        ElectricBox requiredBox = box(10L);
        ElectricBox outsideScopeBox = box(20L);
        when(electricBoxMapper.selectList(any())).thenReturn(List.of(requiredBox, outsideScopeBox));
        when(scopeService.countRequiredDaysThrough(eq(requiredBox), eq(targetMonth), any(LocalDate.class)))
                .thenReturn(1);
        when(scopeService.countRequiredDaysThrough(eq(outsideScopeBox), eq(targetMonth), any(LocalDate.class)))
                .thenReturn(0);
        when(scopeService.isRequired(any(ElectricBox.class), any(LocalDate.class)))
                .thenAnswer(invocation -> Long.valueOf(10L).equals(
                        invocation.<ElectricBox>getArgument(0).getId()));
        when(inspectionRecordMapper.selectList(any())).thenReturn(List.of(
                rawRecord(100L, 10L, requiredDate, 1),
                rawRecord(101L, 10L, requiredDate, 2),
                rawRecord(102L, 20L, outsideScopeDate, 3)));
        doReturn(List.of()).when(service).listRecords(
                eq(1L), eq(null), eq(null), eq(targetMonth.toString()), eq(null),
                eq(null), eq(null), eq(currentUser));

        InspectionMonthSummaryVO summary = service.getMonthSummary(
                1L, null, targetMonth.toString(), null, currentUser);

        assertEquals(1, summary.getChecked());
        assertEquals(1, summary.getAbnormal());
    }

    @Test
    void publicThirtyDaySummaryUsesInspectionScopeAndDeduplicatesRecordDays() {
        LocalDate endDate = LocalDate.now();
        LocalDate requiredCheckedDate = endDate.minusDays(2);
        LocalDate requiredMissedDate = endDate.minusDays(1);
        LocalDate outsideScopeDate = endDate;
        ElectricBox box = box(10L);
        box.setPublicCode("PUBLIC-1");
        box.setPublicAccessEnabled(1);
        when(electricBoxMapper.selectOne(any())).thenReturn(box);
        when(scopeService.requiredDates(box, endDate.minusDays(29), endDate))
                .thenReturn(Set.of(requiredCheckedDate, requiredMissedDate));
        when(inspectionRecordMapper.selectList(any())).thenReturn(List.of(
                rawRecord(103L, 10L, outsideScopeDate, 3),
                rawRecord(102L, 10L, requiredCheckedDate, 1),
                rawRecord(101L, 10L, requiredCheckedDate, 2)));

        PublicElectricBoxSummaryVO summary = service.getPublicSummary("PUBLIC-1");

        assertEquals(2, summary.getShouldCheckDays());
        assertEquals(1, summary.getCheckedDays());
        assertEquals(1, summary.getMissedDays());
        assertEquals(1, summary.getAbnormalCount());
        verify(scopeService).requiredDates(box, endDate.minusDays(29), endDate);
        verify(scopeService, never()).isRequired(eq(box), any(LocalDate.class));
    }

    @Test
    void publicMonthlySummaryIgnoresAbnormalRecordsOutsideScope() {
        YearMonth targetMonth = YearMonth.now().minusMonths(1);
        LocalDate requiredDate = targetMonth.atDay(1);
        LocalDate outsideScopeDate = targetMonth.atDay(2);
        ElectricBox box = box(10L);
        box.setPublicCode("PUBLIC-1");
        box.setPublicAccessEnabled(1);
        when(electricBoxMapper.selectOne(any())).thenReturn(box);
        when(scopeService.requiredDates(box, targetMonth.atDay(1), targetMonth.atEndOfMonth()))
                .thenReturn(Set.of(requiredDate));
        when(inspectionRecordMapper.selectList(any())).thenReturn(List.of(
                rawRecord(100L, 10L, requiredDate, 1),
                rawRecord(101L, 10L, outsideScopeDate, 2)));
        when(inspectionRecordItemMapper.selectList(any())).thenReturn(List.of());
        when(inspectionRectificationMapper.selectList(any())).thenReturn(List.of());

        PublicElectricBoxMonthlyVO summary = service.getPublicMonthly("PUBLIC-1", targetMonth.toString());

        assertEquals(1, summary.getShouldCheckDays());
        assertEquals(1, summary.getCheckedDays());
        assertEquals(0, summary.getMissedDays());
        assertEquals(1, summary.getAbnormalDays());
        verify(scopeService).requiredDates(box, targetMonth.atDay(1), targetMonth.atEndOfMonth());
        verify(scopeService, never()).isRequired(eq(box), any(LocalDate.class));
    }

    @Test
    void summaryAggregationIsNotLimitedToVisibleRecordDetails() {
        LocalDate targetDate = LocalDate.now().minusDays(1);
        ElectricBox box = box(10L);
        when(electricBoxMapper.selectList(any())).thenReturn(List.of(box));
        when(scopeService.isRequired(box, targetDate)).thenReturn(true);
        when(inspectionRecordMapper.selectList(any())).thenReturn(List.of(
                rawRecord(100L, 10L, targetDate, 0)));
        doReturn(List.of()).when(service).listRecords(
                eq(1L), eq(null), eq(null), eq(null), eq(targetDate.toString()),
                eq(null), eq(null), eq(currentUser));

        InspectionMonthSummaryVO summary = service.getMonthSummary(
                1L, null, null, targetDate.toString(), currentUser);

        assertEquals(1, summary.getShouldCheck());
        assertEquals(1, summary.getChecked());
        assertEquals(0, summary.getMissed());
        assertThat(summary.getRecords()).isEmpty();
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void recordCheckDateUsesExactBusinessDateFilter() {
        LocalDate targetDate = LocalDate.now().minusDays(1);
        when(inspectionRecordMapper.selectList(any())).thenReturn(List.of());

        service.listRecords(1L, null, null, null, targetDate.toString(),
                null, null, currentUser);

        ArgumentCaptor<Wrapper<InspectionRecord>> captor = (ArgumentCaptor) ArgumentCaptor.forClass(Wrapper.class);
        verify(inspectionRecordMapper).selectList(captor.capture());
        LambdaQueryWrapper<InspectionRecord> wrapper = (LambdaQueryWrapper<InspectionRecord>) captor.getValue();
        assertThat(wrapper.getSqlSegment()).contains("check_date");
        assertThat(wrapper.getParamNameValuePairs().values()).contains(targetDate);
    }

    private ElectricBox box(Long id) {
        ElectricBox box = new ElectricBox();
        box.setId(id);
        box.setProjectId(1L);
        box.setStatus("ACTIVE");
        return box;
    }

    private InspectionRecordVO record(Long id, Long boxId, LocalDate checkDate, int abnormalCount) {
        InspectionRecordVO record = new InspectionRecordVO();
        record.setId(id);
        record.setProjectId(1L);
        record.setElectricBoxId(boxId);
        record.setCheckDate(checkDate);
        record.setAbnormalCount(abnormalCount);
        return record;
    }

    private InspectionRecord rawRecord(Long id, Long boxId, LocalDate checkDate, int abnormalCount) {
        InspectionRecord record = new InspectionRecord();
        record.setId(id);
        record.setProjectId(1L);
        record.setElectricBoxId(boxId);
        record.setSource("ELECTRICIAN_DAILY");
        record.setStatus("COMPLETED");
        record.setCheckDate(checkDate);
        record.setAbnormalCount(abnormalCount);
        return record;
    }
}
