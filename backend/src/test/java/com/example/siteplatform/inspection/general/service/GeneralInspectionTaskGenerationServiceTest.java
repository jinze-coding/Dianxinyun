package com.example.siteplatform.inspection.general.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.inspection.general.dto.GeneralInspectionPlanConfig;
import com.example.siteplatform.inspection.general.entity.*;
import com.example.siteplatform.inspection.general.mapper.*;
import com.example.siteplatform.project.constant.InspectionPermissionCodes;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.springframework.dao.DuplicateKeyException;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GeneralInspectionTaskGenerationServiceTest {

    private GeneralInspectionTaskGenerationService service;
    private GeneralInspectionPlanMapper planMapper;
    private GeneralInspectionPlanVersionMapper planVersionMapper;
    private GeneralInspectionTemplateMapper templateMapper;
    private GeneralInspectionTemplateVersionMapper templateVersionMapper;
    private GeneralInspectionTemplateItemMapper templateItemMapper;
    private GeneralInspectionPointMapper pointMapper;
    private GeneralInspectionTaskMapper taskMapper;
    private GeneralInspectionTaskItemMapper taskItemMapper;
    private SysUserMapper userMapper;
    private GeneralInspectionPermissionService permissionService;
    private TransactionTemplate transactionTemplate;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"),
                GeneralInspectionPlan.class);
        planMapper = mock(GeneralInspectionPlanMapper.class);
        planVersionMapper = mock(GeneralInspectionPlanVersionMapper.class);
        templateMapper = mock(GeneralInspectionTemplateMapper.class);
        templateVersionMapper = mock(GeneralInspectionTemplateVersionMapper.class);
        templateItemMapper = mock(GeneralInspectionTemplateItemMapper.class);
        pointMapper = mock(GeneralInspectionPointMapper.class);
        taskMapper = mock(GeneralInspectionTaskMapper.class);
        taskItemMapper = mock(GeneralInspectionTaskItemMapper.class);
        userMapper = mock(SysUserMapper.class);
        permissionService = mock(GeneralInspectionPermissionService.class);
        transactionTemplate = mock(TransactionTemplate.class);
        objectMapper = new ObjectMapper().findAndRegisterModules();
        service = new GeneralInspectionTaskGenerationService(
                planMapper,
                planVersionMapper,
                templateMapper,
                templateVersionMapper,
                templateItemMapper,
                pointMapper,
                taskMapper,
                taskItemMapper,
                userMapper,
                permissionService,
                objectMapper,
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
    void pointActivatedDuringOpenWindowDoesNotBackfillOccurrenceThatAlreadyStarted() {
        LocalDateTime start = LocalDateTime.of(2026, 8, 26, 8, 0);
        LocalDateTime activated = LocalDateTime.of(2026, 8, 26, 15, 0);

        assertThat(GeneralInspectionTaskGenerationService.eligibleForPointActivation(
                true, activated, start)).isFalse();
        assertThat(GeneralInspectionTaskGenerationService.eligibleForPointActivation(
                true, activated, LocalDateTime.of(2026, 8, 27, 8, 0))).isTrue();
        assertThat(GeneralInspectionTaskGenerationService.eligibleForPointActivation(
                false, activated, start)).isTrue();
    }

    @Test
    void scheduleResumeIncludesCurrentOpenWindowAndSkipsClosedPausedPeriod() {
        LocalDateTime resumedAt = LocalDateTime.of(2026, 8, 29, 15, 0);
        LocalDateTime now = LocalDateTime.of(2026, 8, 29, 15, 5);

        assertThat(GeneralInspectionTaskGenerationService.eligibleForActivationWindow(true, resumedAt,
                LocalDateTime.of(2026, 8, 27, 8, 0), LocalDateTime.of(2026, 8, 27, 18, 0), now)).isFalse();
        assertThat(GeneralInspectionTaskGenerationService.eligibleForActivationWindow(true, resumedAt,
                LocalDateTime.of(2026, 8, 29, 8, 0), LocalDateTime.of(2026, 8, 29, 18, 0), now)).isTrue();
        assertThat(GeneralInspectionTaskGenerationService.eligibleForActivationWindow(true, resumedAt,
                resumedAt, LocalDateTime.of(2026, 8, 29, 18, 0), now)).isTrue();
        assertThat(GeneralInspectionTaskGenerationService.eligibleForActivationWindow(true, resumedAt,
                LocalDateTime.of(2026, 8, 30, 8, 0), LocalDateTime.of(2026, 8, 30, 18, 0), now)).isTrue();
    }

    @Test
    void unchangedCursorStillAllowsNormalEnabledDowntimeCatchup() {
        LocalDateTime enabledLowerBound = LocalDateTime.of(2026, 8, 20, 10, 0);

        assertThat(GeneralInspectionTaskGenerationService.eligibleForActivationWindow(true,
                enabledLowerBound, LocalDateTime.of(2026, 8, 28, 8, 0),
                LocalDateTime.of(2026, 8, 28, 18, 0), LocalDateTime.of(2026, 8, 29, 8, 0))).isTrue();
        assertThat(GeneralInspectionTaskGenerationService.eligibleForActivationWindow(true,
                enabledLowerBound, LocalDateTime.of(2026, 8, 19, 8, 0),
                LocalDateTime.of(2026, 8, 19, 18, 0), LocalDateTime.of(2026, 8, 29, 8, 0))).isFalse();
    }

    @Test
    void rollingCursorAheadDoesNotSuppressNextTaskForNewPoint() {
        LocalDateTime scheduleLowerBound = LocalDateTime.of(2026, 8, 20, 9, 0);
        LocalDateTime pointCreatedAt = LocalDateTime.of(2026, 8, 26, 17, 0);
        LocalDateTime rollingCursor = LocalDateTime.of(2026, 8, 27, 18, 0);
        LocalDateTime nextTaskStart = LocalDateTime.of(2026, 8, 27, 8, 0);

        assertThat(nextTaskStart).isBefore(rollingCursor);
        assertThat(GeneralInspectionTaskGenerationService.eligibleForActivationWindow(true,
                scheduleLowerBound, nextTaskStart, LocalDateTime.of(2026, 8, 27, 18, 0),
                LocalDateTime.of(2026, 8, 27, 7, 0))).isTrue();
        assertThat(GeneralInspectionTaskGenerationService.eligibleForActivationWindow(true,
                pointCreatedAt, nextTaskStart, LocalDateTime.of(2026, 8, 27, 18, 0),
                LocalDateTime.of(2026, 8, 27, 7, 0))).isTrue();
    }

    @Test
    void activationAtDueBoundaryMatchesOnTimeSubmissionRule() {
        LocalDateTime start = LocalDateTime.of(2026, 8, 29, 8, 0);
        LocalDateTime due = LocalDateTime.of(2026, 8, 29, 18, 0);

        assertThat(GeneralInspectionTaskGenerationService.eligibleForActivationWindow(
                true, due, start, due, due)).isTrue();
        assertThat(GeneralInspectionTaskGenerationService.eligibleForActivationWindow(
                true, due, start, due, due.plusNanos(1))).isFalse();
    }

    @Test
    void activationDuringCrossMidnightWindowIncludesPreviousOccurrenceDate() {
        LocalDateTime start = LocalDateTime.of(2026, 8, 29, 23, 0);
        LocalDateTime due = LocalDateTime.of(2026, 8, 30, 1, 0);
        LocalDateTime activated = LocalDateTime.of(2026, 8, 30, 0, 30);

        assertThat(GeneralInspectionTaskGenerationService.eligibleForActivationWindow(
                true, activated, start, due, LocalDateTime.of(2026, 8, 30, 0, 35))).isTrue();
        assertThat(GeneralInspectionTaskGenerationService.eligibleForActivationWindow(
                true, activated, start, due, LocalDateTime.of(2026, 8, 30, 1, 0, 1))).isFalse();
    }

    @Test
    void currentOpenOccurrenceUsesVersionSavedAtResume() {
        GeneralInspectionPlanVersion original = planVersion(1L, 1,
                LocalDateTime.of(2026, 8, 20, 9, 0));
        GeneralInspectionPlanVersion resumed = planVersion(2L, 2,
                LocalDateTime.of(2026, 8, 29, 15, 0));
        LocalDateTime start = LocalDateTime.of(2026, 8, 29, 8, 0);

        LocalDateTime resumeReference = GeneralInspectionTaskGenerationService.activationAwareVersionReference(
                true, start, resumed.getEffectiveTime(), LocalDateTime.of(2026, 8, 20, 10, 0));
        assertThat(resumeReference).isEqualTo(resumed.getEffectiveTime());
        assertThat(service.latestPlanVersion(List.of(original, resumed), resumeReference)).isSameAs(resumed);
    }

    @Test
    void firstEnableAt1500MaterializesCurrentOpenOccurrence() throws Exception {
        LocalDateTime enabledAt = LocalDateTime.of(2026, 8, 29, 15, 0);
        LocalDateTime now = enabledAt.plusMinutes(5);
        GeneralInspectionPlan plan = new GeneralInspectionPlan();
        plan.setId(31L);
        plan.setProjectId(2L);
        plan.setPlanCode(EdgeInspectionConfigService.EDGE_PLAN_CODE);
        plan.setPlanName("临边巡检周期设置");
        plan.setStatus("PUBLISHED");
        plan.setDeleted(0);
        plan.setGeneratedThroughTime(enabledAt);
        plan.setEdgeGenerationLowerBoundTime(enabledAt);
        when(planMapper.selectByIdForUpdate(31L)).thenReturn(plan);
        when(planMapper.update(any(), any())).thenReturn(1);

        GeneralInspectionPlanConfig config = new GeneralInspectionPlanConfig();
        config.setFrequency("DAILY");
        config.setEffectiveStart(LocalDate.of(2026, 8, 1));
        config.setEarlyMinutes(0);
        config.setSlots(List.of(slot("EDGE_SLOT", "临边巡检时段",
                LocalTime.of(8, 0), LocalTime.of(18, 0), 0)));
        GeneralInspectionPlanVersion first = planVersion(41L, 1, enabledAt);
        first.setPlanId(31L);
        first.setConfigJson(objectMapper.writeValueAsString(config));
        when(planVersionMapper.selectList(any())).thenReturn(List.of(first));

        GeneralInspectionPoint point = activePoint();
        point.setEdgeActiveSinceTime(LocalDateTime.of(2026, 8, 20, 9, 0));
        when(pointMapper.selectList(any())).thenReturn(List.of(point));
        when(pointMapper.selectByIdForUpdate(point.getId())).thenReturn(point);
        stubPublishedTemplate();
        when(taskMapper.insert(any())).thenAnswer(invocation -> {
            GeneralInspectionTask task = invocation.getArgument(0);
            task.setId(71L);
            return 1;
        });
        when(taskItemMapper.insert(any())).thenReturn(1);

        assertThat(service.generatePlan(31L, now, now.plusMinutes(5))).isEqualTo(1);
        ArgumentCaptor<GeneralInspectionTask> taskCaptor = ArgumentCaptor.forClass(GeneralInspectionTask.class);
        verify(taskMapper).insert(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getPlanVersionId()).isEqualTo(41L);
        assertThat(taskCaptor.getValue().getStartTime()).isEqualTo(LocalDateTime.of(2026, 8, 29, 8, 0));
        assertThat(taskCaptor.getValue().getDueTime()).isEqualTo(LocalDateTime.of(2026, 8, 29, 18, 0));
    }

    @Test
    void firstEnableAfterDueDoesNotMaterializeClosedOccurrence() throws Exception {
        LocalDateTime enabledAt = LocalDateTime.of(2026, 8, 29, 15, 0);
        LocalDateTime now = LocalDateTime.of(2026, 8, 29, 18, 0, 1);
        GeneralInspectionPlan plan = publishedEdgePlan(enabledAt, enabledAt);
        stubPlan(plan);

        GeneralInspectionPlanConfig config = dailyConfig(
                LocalTime.of(8, 0), LocalTime.of(18, 0), 0, null);
        when(planVersionMapper.selectList(any())).thenReturn(List.of(
                planVersionWithConfig(41L, 1, enabledAt, config)));
        stubActivePoint(LocalDateTime.of(2026, 8, 20, 9, 0));

        assertThat(service.generatePlan(31L, now, now.plusMinutes(5))).isZero();
        verify(taskMapper, never()).insert(any());
    }

    @Test
    void crossMidnightResumeStillScansPreviousOccurrenceDateWhenCursorReachedDayAfterTomorrow()
            throws Exception {
        LocalDateTime resumedAt = LocalDateTime.of(2026, 8, 30, 0, 30);
        LocalDateTime now = resumedAt.plusMinutes(5);
        GeneralInspectionPlan plan = publishedEdgePlan(
                LocalDateTime.of(2026, 8, 31, 0, 30), resumedAt);
        stubPlan(plan);

        GeneralInspectionPlanConfig config = dailyConfig(
                LocalTime.of(23, 0), LocalTime.of(1, 0), 1, null);
        when(planVersionMapper.selectList(any())).thenReturn(List.of(
                planVersionWithConfig(41L, 2, resumedAt, config)));
        stubActivePoint(LocalDateTime.of(2026, 8, 20, 9, 0));
        stubPublishedTemplate();
        stubSuccessfulTaskInsert(71L);

        assertThat(service.generatePlan(31L, now, now.plusMinutes(5))).isEqualTo(1);
        ArgumentCaptor<GeneralInspectionTask> taskCaptor = ArgumentCaptor.forClass(GeneralInspectionTask.class);
        verify(taskMapper).insert(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getOccurrenceDate()).isEqualTo(LocalDate.of(2026, 8, 29));
        assertThat(taskCaptor.getValue().getStartTime()).isEqualTo(LocalDateTime.of(2026, 8, 29, 23, 0));
        assertThat(taskCaptor.getValue().getDueTime()).isEqualTo(LocalDateTime.of(2026, 8, 30, 1, 0));
    }

    @Test
    void resumeWithAssigneeBUsesNewVersionAndAssigneeSnapshotForCurrentOpenOccurrence()
            throws Exception {
        long previousAssigneeId = 201L;
        long resumedAssigneeId = 202L;
        LocalDateTime resumedAt = LocalDateTime.of(2026, 8, 29, 15, 0);
        LocalDateTime now = resumedAt.plusMinutes(5);
        GeneralInspectionPlan plan = publishedEdgePlan(resumedAt, resumedAt);
        stubPlan(plan);

        GeneralInspectionPlanConfig previousConfig = dailyConfig(
                LocalTime.of(8, 0), LocalTime.of(18, 0), 0, previousAssigneeId);
        GeneralInspectionPlanConfig resumedConfig = dailyConfig(
                LocalTime.of(8, 0), LocalTime.of(18, 0), 0, resumedAssigneeId);
        when(planVersionMapper.selectList(any())).thenReturn(List.of(
                planVersionWithConfig(40L, 1, LocalDateTime.of(2026, 8, 20, 9, 0), previousConfig),
                planVersionWithConfig(41L, 2, resumedAt, resumedConfig)));
        stubActivePoint(LocalDateTime.of(2026, 8, 20, 9, 0));
        stubPublishedTemplate();
        stubEligibleAssignee(resumedAssigneeId, "刘国平");
        stubSuccessfulTaskInsert(71L);

        assertThat(service.generatePlan(31L, now, now.plusMinutes(5))).isEqualTo(1);
        ArgumentCaptor<GeneralInspectionTask> taskCaptor = ArgumentCaptor.forClass(GeneralInspectionTask.class);
        verify(taskMapper).insert(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getPlanVersionId()).isEqualTo(41L);
        assertThat(taskCaptor.getValue().getAssigneeId()).isEqualTo(resumedAssigneeId);
        assertThat(taskCaptor.getValue().getAssigneeName()).isEqualTo("刘国平");
    }

    @Test
    void consecutiveGenerationIsIdempotentForCurrentOpenOccurrence() throws Exception {
        LocalDateTime enabledAt = LocalDateTime.of(2026, 8, 29, 15, 0);
        LocalDateTime now = enabledAt.plusMinutes(5);
        GeneralInspectionPlan plan = publishedEdgePlan(enabledAt, enabledAt);
        stubPlan(plan);

        GeneralInspectionPlanConfig config = dailyConfig(
                LocalTime.of(8, 0), LocalTime.of(18, 0), 0, null);
        when(planVersionMapper.selectList(any())).thenReturn(List.of(
                planVersionWithConfig(41L, 1, enabledAt, config)));
        stubActivePoint(LocalDateTime.of(2026, 8, 20, 9, 0));
        stubPublishedTemplate();
        when(taskMapper.insert(any()))
                .thenAnswer(invocation -> {
                    GeneralInspectionTask task = invocation.getArgument(0);
                    task.setId(71L);
                    return 1;
                })
                .thenThrow(new DuplicateKeyException("uk_general_task_occurrence"));
        when(taskItemMapper.insert(any())).thenReturn(1);

        assertThat(service.generatePlan(31L, now, now.plusMinutes(5))).isEqualTo(1);
        assertThat(service.generatePlan(31L, now.plusMinutes(1), now.plusMinutes(6))).isZero();
        verify(taskMapper, times(2)).insert(any());
        verify(taskItemMapper).insert(any());
        verify(planMapper, times(2)).update(any(), any());
    }

    @Test
    void newPointDuringCurrentOpenWindowWaitsForNextOccurrence() throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 8, 29, 15, 5);
        GeneralInspectionPlan plan = new GeneralInspectionPlan();
        plan.setId(31L);
        plan.setProjectId(2L);
        plan.setPlanCode(EdgeInspectionConfigService.EDGE_PLAN_CODE);
        plan.setPlanName("临边巡检周期设置");
        plan.setStatus("PUBLISHED");
        plan.setDeleted(0);
        plan.setGeneratedThroughTime(now);
        plan.setEdgeGenerationLowerBoundTime(LocalDateTime.of(2026, 8, 20, 9, 0));
        when(planMapper.selectByIdForUpdate(31L)).thenReturn(plan);
        when(planMapper.update(any(), any())).thenReturn(1);

        GeneralInspectionPlanConfig config = new GeneralInspectionPlanConfig();
        config.setFrequency("DAILY");
        config.setEffectiveStart(LocalDate.of(2026, 8, 1));
        config.setEarlyMinutes(0);
        config.setSlots(List.of(slot("EDGE_SLOT", "临边巡检时段",
                LocalTime.of(8, 0), LocalTime.of(18, 0), 0)));
        String configJson = objectMapper.writeValueAsString(config);
        GeneralInspectionPlanVersion original = planVersion(40L, 1,
                LocalDateTime.of(2026, 8, 20, 9, 0));
        original.setPlanId(31L);
        original.setConfigJson(configJson);
        GeneralInspectionPlanVersion current = planVersion(41L, 2,
                LocalDateTime.of(2026, 8, 29, 14, 0));
        current.setPlanId(31L);
        current.setConfigJson(configJson);
        when(planVersionMapper.selectList(any())).thenReturn(List.of(original, current));

        GeneralInspectionPoint point = activePoint();
        point.setEdgeActiveSinceTime(LocalDateTime.of(2026, 8, 29, 15, 0));
        when(pointMapper.selectList(any())).thenReturn(List.of(point));
        when(pointMapper.selectByIdForUpdate(point.getId())).thenReturn(point);
        assertThat(service.generatePlan(31L, now, now.plusMinutes(5))).isZero();
        verify(taskMapper, never()).insert(any());
    }

    @Test
    void duplicateOccurrenceRemainsIdempotentWhenCurrentWindowIsBackfilled() {
        GeneralInspectionPlan plan = new GeneralInspectionPlan();
        plan.setId(31L);
        plan.setProjectId(2L);
        plan.setPlanCode(EdgeInspectionConfigService.EDGE_PLAN_CODE);
        plan.setPlanName("临边巡检周期设置");
        GeneralInspectionPlanVersion version = planVersion(41L, 2,
                LocalDateTime.of(2026, 8, 29, 15, 0));
        GeneralInspectionPoint point = activePoint();
        point.setEdgeActiveSinceTime(LocalDateTime.of(2026, 8, 20, 9, 0));
        when(pointMapper.selectByIdForUpdate(11L)).thenReturn(point);

        stubPublishedTemplate();
        when(taskMapper.insert(any())).thenThrow(new DuplicateKeyException("uk_general_task_occurrence"));

        GeneralInspectionPlanConfig config = new GeneralInspectionPlanConfig();
        GeneralInspectionPlanConfig.PointAssignment assignment =
                new GeneralInspectionPlanConfig.PointAssignment();
        assignment.setPointId(11L);
        GeneralInspectionPlanConfig.Slot slot = slot("EDGE_SLOT", "临边巡检时段",
                LocalTime.of(8, 0), LocalTime.of(18, 0), 0);

        assertThat(service.materialize(plan, List.of(version), version, config, slot, assignment,
                LocalDate.of(2026, 8, 29), LocalDateTime.of(2026, 8, 29, 8, 0),
                LocalDateTime.of(2026, 8, 29, 18, 0), LocalDateTime.of(2026, 8, 29, 15, 5),
                LocalDateTime.of(2026, 8, 29, 15, 0))).isFalse();
        verify(taskItemMapper, never()).insert(any());
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

        GeneralInspectionTaskGenerationService scheduledService = spy(service);
        scheduledService.generateScheduledTasks();

        verify(transactionTemplate).execute(any());
        verify(planMapper).selectByIdForUpdate(31L);
        verify(scheduledService).generatePlan(31L, null, null);
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

        GeneralInspectionPlanVersion version = planVersion(41L, 1,
                LocalDateTime.of(2026, 8, 26, 8, 0));
        assertThat(service.materialize(plan, List.of(version), version,
                new GeneralInspectionPlanConfig(), new GeneralInspectionPlanConfig.Slot(), assignment,
                LocalDate.of(2026, 8, 26), LocalDateTime.of(2026, 8, 26, 8, 0),
                LocalDateTime.of(2026, 8, 26, 18, 0), LocalDateTime.of(2026, 8, 26, 9, 0),
                null)).isFalse();

        verify(pointMapper).selectByIdForUpdate(11L);
        verify(pointMapper, org.mockito.Mockito.never()).selectById(11L);
    }

    private GeneralInspectionPlan publishedEdgePlan(LocalDateTime cursor, LocalDateTime lowerBound) {
        GeneralInspectionPlan plan = new GeneralInspectionPlan();
        plan.setId(31L);
        plan.setProjectId(2L);
        plan.setPlanCode(EdgeInspectionConfigService.EDGE_PLAN_CODE);
        plan.setPlanName("临边巡检周期设置");
        plan.setStatus("PUBLISHED");
        plan.setDeleted(0);
        plan.setGeneratedThroughTime(cursor);
        plan.setEdgeGenerationLowerBoundTime(lowerBound);
        return plan;
    }

    private void stubPlan(GeneralInspectionPlan plan) {
        when(planMapper.selectByIdForUpdate(plan.getId())).thenReturn(plan);
        when(planMapper.update(any(), any())).thenReturn(1);
    }

    private GeneralInspectionPlanConfig dailyConfig(LocalTime start, LocalTime due, int dueDayOffset,
                                                    Long assigneeId) {
        GeneralInspectionPlanConfig config = new GeneralInspectionPlanConfig();
        config.setFrequency("DAILY");
        config.setEffectiveStart(LocalDate.of(2026, 8, 1));
        config.setEarlyMinutes(0);
        config.setAssigneeId(assigneeId);
        config.setSlots(List.of(slot("EDGE_SLOT", "临边巡检时段", start, due, dueDayOffset)));
        return config;
    }

    private GeneralInspectionPlanVersion planVersionWithConfig(Long id, int versionNo,
                                                               LocalDateTime effectiveTime,
                                                               GeneralInspectionPlanConfig config)
            throws Exception {
        GeneralInspectionPlanVersion version = planVersion(id, versionNo, effectiveTime);
        version.setPlanId(31L);
        version.setConfigJson(objectMapper.writeValueAsString(config));
        return version;
    }

    private void stubActivePoint(LocalDateTime activeSince) {
        GeneralInspectionPoint point = activePoint();
        point.setEdgeActiveSinceTime(activeSince);
        when(pointMapper.selectList(any())).thenReturn(List.of(point));
        when(pointMapper.selectByIdForUpdate(point.getId())).thenReturn(point);
    }

    private void stubEligibleAssignee(Long userId, String realName) {
        when(permissionService.hasActiveProjectAccess(2L, userId)).thenReturn(true);
        when(permissionService.hasInspectionPermission(
                2L, userId, InspectionPermissionCodes.EDGE_INSPECTION_SUBMIT)).thenReturn(true);
        SysUser user = new SysUser();
        user.setId(userId);
        user.setUsername("edge-inspector-" + userId);
        user.setRealName(realName);
        user.setStatus(1);
        user.setDeleted(0);
        when(userMapper.selectById(userId)).thenReturn(user);
    }

    private void stubSuccessfulTaskInsert(Long taskId) {
        when(taskMapper.insert(any())).thenAnswer(invocation -> {
            GeneralInspectionTask task = invocation.getArgument(0);
            task.setId(taskId);
            return 1;
        });
        when(taskItemMapper.insert(any())).thenReturn(1);
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

    private GeneralInspectionPlanVersion planVersion(Long id, int versionNo, LocalDateTime effectiveTime) {
        GeneralInspectionPlanVersion version = new GeneralInspectionPlanVersion();
        version.setId(id);
        version.setVersionNo(versionNo);
        version.setEffectiveTime(effectiveTime);
        return version;
    }

    private GeneralInspectionPoint activePoint() {
        GeneralInspectionPoint point = new GeneralInspectionPoint();
        point.setId(11L);
        point.setProjectId(2L);
        point.setPointCode("EDGE_11");
        point.setPointName("北侧接料平台");
        point.setPointTypeCode("LOADING_UNLOADING_PLATFORM");
        point.setPointTypeName("接料、卸料平台");
        point.setStatus("ACTIVE");
        return point;
    }

    private void stubPublishedTemplate() {
        GeneralInspectionTemplate template = new GeneralInspectionTemplate();
        template.setId(51L);
        when(templateMapper.selectOne(any())).thenReturn(template);
        GeneralInspectionTemplateVersion templateVersion = new GeneralInspectionTemplateVersion();
        templateVersion.setId(61L);
        templateVersion.setTemplateName("接料、卸料平台固定检查表");
        when(templateVersionMapper.selectOne(any())).thenReturn(templateVersion);
        GeneralInspectionTemplateItem templateItem = new GeneralInspectionTemplateItem();
        templateItem.setItemKey("ITEM_01");
        when(templateItemMapper.selectList(any())).thenReturn(List.of(templateItem));
    }
}
