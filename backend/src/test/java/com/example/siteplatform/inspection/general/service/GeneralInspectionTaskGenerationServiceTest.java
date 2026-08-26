package com.example.siteplatform.inspection.general.service;

import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.inspection.general.dto.GeneralInspectionPlanConfig;
import com.example.siteplatform.inspection.general.mapper.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GeneralInspectionTaskGenerationServiceTest {

    private GeneralInspectionTaskGenerationService service;

    @BeforeEach
    void setUp() {
        service = new GeneralInspectionTaskGenerationService(
                mock(GeneralInspectionProjectSettingMapper.class),
                mock(GeneralInspectionPlanMapper.class),
                mock(GeneralInspectionPlanVersionMapper.class),
                mock(GeneralInspectionTemplateVersionMapper.class),
                mock(GeneralInspectionTemplateItemMapper.class),
                mock(GeneralInspectionPointMapper.class),
                mock(GeneralInspectionTaskMapper.class),
                mock(GeneralInspectionTaskItemMapper.class),
                mock(SysUserMapper.class), new ObjectMapper());
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
