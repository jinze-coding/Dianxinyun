package com.example.siteplatform.inspection.general.service;

import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.inspection.general.dto.GeneralInspectionPlanConfig;
import com.example.siteplatform.inspection.general.entity.*;
import com.example.siteplatform.inspection.general.mapper.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GeneralInspectionTaskGenerationServiceTest {

    private GeneralInspectionTaskGenerationService service;
    private GeneralInspectionPlanMapper planMapper;
    private GeneralInspectionPointMapper pointMapper;
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        planMapper = mock(GeneralInspectionPlanMapper.class);
        pointMapper = mock(GeneralInspectionPointMapper.class);
        transactionTemplate = mock(TransactionTemplate.class);
        service = new GeneralInspectionTaskGenerationService(
                planMapper,
                mock(GeneralInspectionPlanVersionMapper.class),
                mock(GeneralInspectionTemplateMapper.class),
                mock(GeneralInspectionTemplateVersionMapper.class),
                mock(GeneralInspectionTemplateItemMapper.class),
                pointMapper,
                mock(GeneralInspectionTaskMapper.class),
                mock(GeneralInspectionTaskItemMapper.class),
                mock(SysUserMapper.class),
                mock(GeneralInspectionPermissionService.class),
                new ObjectMapper(),
                transactionTemplate);
    }

    @Test
    void previewsMonthEndAndCrossMidnightAsOnePointSlotOccurrence() {
        GeneralInspectionPlanConfig config = baseConfig("MONTHLY");
        config.setMonthDays(List.of(-1));
        GeneralInspectionPlanConfig.Slot slot = slot("NIGHT", "夜间", LocalTime.of(23, 0),
                LocalTime.of(1, 0), 1);
        config.setSlots(List.of(slot));

        List<Map<String, Object>> rows = service.preview(config,
                LocalDateTime.of(2026, 2, 27, 8, 0), 3);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0))
                .containsEntry("date", LocalDate.of(2026, 2, 28))
                .containsEntry("startTime", LocalDateTime.of(2026, 2, 28, 23, 0))
                .containsEntry("dueTime", LocalDateTime.of(2026, 3, 1, 1, 0))
                .containsEntry("availableTime", LocalDateTime.of(2026, 2, 28, 22, 30));
    }

    @Test
    void weeklyPreviewOnlyIncludesConfiguredWeekdaysAndPointOverrides() {
        GeneralInspectionPlanConfig config = baseConfig("WEEKLY");
        config.setWeekdays(List.of(1, 5));
        config.setEarlyMinutes(120);
        config.setSlots(List.of(slot("DAY", "白班", LocalTime.of(9, 0), LocalTime.of(17, 0), 0)));

        List<Map<String, Object>> rows = service.preview(config,
                LocalDateTime.of(2026, 8, 24, 0, 0), 7);

        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(row -> row.get("date"))
                .containsExactly(LocalDate.of(2026, 8, 24), LocalDate.of(2026, 8, 28));
        assertThat(rows).allSatisfy(row -> {
            assertThat(row).containsEntry("pointId", 11L).containsEntry("assigneeId", 21L);
            assertThat((LocalDateTime) row.get("availableTime"))
                    .isEqualTo(((LocalDateTime) row.get("startTime")).minusHours(2));
        });
    }

    @Test
    void previewUsesPlanAssigneeWhenPointDoesNotOverrideIt() {
        GeneralInspectionPlanConfig config = baseConfig("DAILY");
        config.setAssigneeId(99L);
        config.getPoints().get(0).setAssigneeId(null);
        config.setSlots(List.of(slot("DAY", "白班", LocalTime.of(9, 0), LocalTime.of(10, 0), 0)));

        List<Map<String, Object>> rows = service.preview(config,
                LocalDateTime.of(2026, 8, 26, 0, 0), 1);

        assertThat(rows).singleElement().satisfies(row -> assertThat(row).containsEntry("assigneeId", 99L));
    }

    @Test
    void edgeCatchupOnlyIncludesOccurrencesStartingAfterPointActivation() {
        LocalDateTime created = LocalDateTime.of(2026, 8, 26, 17, 59);
        assertThat(GeneralInspectionTaskGenerationService.eligibleForPointActivation(true, created,
                LocalDateTime.of(2026, 8, 26, 8, 0))).isFalse();
        assertThat(GeneralInspectionTaskGenerationService.eligibleForPointActivation(true, created,
                LocalDateTime.of(2026, 8, 26, 18, 0))).isTrue();
        // 跨午夜任务不能因为截止时间落在激活后就补生成，仍以执行开始时间为准。
        assertThat(GeneralInspectionTaskGenerationService.eligibleForPointActivation(true, created,
                LocalDateTime.of(2026, 8, 25, 23, 0))).isFalse();
        assertThat(GeneralInspectionTaskGenerationService.eligibleForPointActivation(false, created,
                LocalDateTime.of(2026, 8, 25, 15, 0))).isTrue();
    }

    @Test
    void scheduleResumeCursorSkipsPausedPeriodButAllowsFutureOccurrences() {
        LocalDateTime resumedAt = LocalDateTime.of(2026, 8, 29, 15, 0);

        assertThat(GeneralInspectionTaskGenerationService.eligibleForGenerationLowerBound(true, resumedAt,
                LocalDateTime.of(2026, 8, 27, 8, 0))).isFalse();
        assertThat(GeneralInspectionTaskGenerationService.eligibleForGenerationLowerBound(true, resumedAt,
                LocalDateTime.of(2026, 8, 29, 8, 0))).isFalse();
        assertThat(GeneralInspectionTaskGenerationService.eligibleForGenerationLowerBound(true, resumedAt,
                resumedAt)).isTrue();
        assertThat(GeneralInspectionTaskGenerationService.eligibleForGenerationLowerBound(true, resumedAt,
                LocalDateTime.of(2026, 8, 30, 8, 0))).isTrue();
    }

    @Test
    void unchangedCursorStillAllowsNormalEnabledDowntimeCatchup() {
        LocalDateTime enabledLowerBound = LocalDateTime.of(2026, 8, 20, 10, 0);

        assertThat(GeneralInspectionTaskGenerationService.eligibleForGenerationLowerBound(true,
                enabledLowerBound, LocalDateTime.of(2026, 8, 28, 8, 0))).isTrue();
        assertThat(GeneralInspectionTaskGenerationService.eligibleForGenerationLowerBound(true,
                enabledLowerBound, LocalDateTime.of(2026, 8, 19, 8, 0))).isFalse();
    }

    @Test
    void rollingCursorAheadDoesNotSuppressNextTaskForNewPoint() {
        LocalDateTime scheduleLowerBound = LocalDateTime.of(2026, 8, 20, 9, 0);
        LocalDateTime pointCreatedAt = LocalDateTime.of(2026, 8, 26, 17, 0);
        LocalDateTime rollingCursor = LocalDateTime.of(2026, 8, 27, 18, 0);
        LocalDateTime nextTaskStart = LocalDateTime.of(2026, 8, 27, 8, 0);

        assertThat(nextTaskStart).isBefore(rollingCursor);
        assertThat(GeneralInspectionTaskGenerationService.eligibleForGenerationLowerBound(true,
                scheduleLowerBound, nextTaskStart)).isTrue();
        assertThat(GeneralInspectionTaskGenerationService.eligibleForPointActivation(true,
                pointCreatedAt, nextTaskStart)).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void scheduledGenerationWrapsEveryPlanInAnExplicitTransaction() {
        GeneralInspectionPlan plan = new GeneralInspectionPlan();
        plan.setId(31L);
        plan.setProjectId(9L);
        when(planMapper.selectList(any())).thenReturn(List.of(plan));
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<Integer> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        when(planMapper.selectByIdForUpdate(31L)).thenReturn(null);

        service.generateScheduledTasks();

        verify(transactionTemplate).execute(any());
        verify(planMapper).selectByIdForUpdate(31L);
    }

    @Test
    void materializationLocksPointSoConcurrentDeactivationCannotMissNewTask() {
        GeneralInspectionPlan plan = new GeneralInspectionPlan();
        plan.setProjectId(2L);
        plan.setPlanCode(EdgeInspectionConfigService.EDGE_PLAN_CODE);
        GeneralInspectionPlanConfig.PointAssignment assignment =
                new GeneralInspectionPlanConfig.PointAssignment();
        assignment.setPointId(11L);
        when(pointMapper.selectByIdForUpdate(11L)).thenReturn(null);

        assertThat(service.materialize(plan, new GeneralInspectionPlanVersion(),
                new GeneralInspectionPlanConfig(), new GeneralInspectionPlanConfig.Slot(), assignment,
                LocalDate.of(2026, 8, 26), LocalDateTime.of(2026, 8, 26, 8, 0))).isFalse();

        verify(pointMapper).selectByIdForUpdate(11L);
        verify(pointMapper, org.mockito.Mockito.never()).selectById(11L);
    }

    private GeneralInspectionPlanConfig baseConfig(String frequency) {
        GeneralInspectionPlanConfig config = new GeneralInspectionPlanConfig();
        config.setFrequency(frequency);
        config.setEffectiveStart(LocalDate.of(2026, 1, 1));
        config.setEarlyMinutes(30);
        GeneralInspectionPlanConfig.PointAssignment point = new GeneralInspectionPlanConfig.PointAssignment();
        point.setPointId(11L);
        point.setAssigneeId(21L);
        point.setReviewerId(31L);
        point.setRectificationDays(3);
        config.setPoints(List.of(point));
        return config;
    }

    private GeneralInspectionPlanConfig.Slot slot(String code, String name, LocalTime start,
                                                   LocalTime due, int dueDayOffset) {
        GeneralInspectionPlanConfig.Slot slot = new GeneralInspectionPlanConfig.Slot();
        slot.setSlotCode(code);
        slot.setSlotName(name);
        slot.setStartTime(start);
        slot.setDueTime(due);
        slot.setDueDayOffset(dueDayOffset);
        return slot;
    }
}
