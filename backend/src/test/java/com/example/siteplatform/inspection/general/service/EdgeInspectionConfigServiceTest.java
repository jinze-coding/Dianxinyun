package com.example.siteplatform.inspection.general.service;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.inspection.general.dto.GeneralInspectionPlanConfig;
import com.example.siteplatform.inspection.general.mapper.*;
import com.example.siteplatform.project.mapper.SysUserProjectMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class EdgeInspectionConfigServiceTest {

    @Test
    void inactivePointIsTerminalAndMustBeRecreated() {
        assertThatCode(() -> EdgeInspectionConfigService.requirePointStatusTransition("ACTIVE", "INACTIVE"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> EdgeInspectionConfigService.requirePointStatusTransition("INACTIVE", "ACTIVE"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    org.assertj.core.api.Assertions.assertThat(exception.getCode()).isEqualTo(409);
                    org.assertj.core.api.Assertions.assertThat(exception.getMessage()).contains("新建点位");
                });
    }

    @Test
    void settingSavePreservesCursorUnlessPausedPlanIsResumed() {
        LocalDateTime oldCursor = LocalDateTime.of(2026, 8, 24, 10, 0);
        LocalDateTime savedAt = LocalDateTime.of(2026, 8, 26, 15, 30);

        assertThat(EdgeInspectionConfigService.settingSaveCursor(oldCursor, false, savedAt))
                .isEqualTo(oldCursor);
        assertThat(EdgeInspectionConfigService.settingSaveCursor(oldCursor, true, savedAt))
                .isEqualTo(savedAt);
        assertThat(EdgeInspectionConfigService.settingSaveCursor(savedAt.plusHours(2), true, savedAt))
                .isEqualTo(savedAt.plusHours(2));
    }

    @Test
    void reminderActivationStartsNewBoundaryAndHistoricalConfigDefaultsOff() {
        LocalDateTime previous = LocalDateTime.of(2026, 8, 20, 9, 0);
        LocalDateTime savedAt = LocalDateTime.of(2026, 8, 29, 10, 0);

        assertThat(EdgeInspectionConfigService.resolveReminderEffectiveTime(false, null, null, savedAt)).isNull();
        assertThat(EdgeInspectionConfigService.resolveReminderEffectiveTime(false, null, true, savedAt))
                .isEqualTo(savedAt);
        assertThat(EdgeInspectionConfigService.resolveReminderEffectiveTime(true, previous, true, savedAt))
                .isEqualTo(previous);
        assertThat(EdgeInspectionConfigService.resolveReminderEffectiveTime(true, previous, false, savedAt)).isNull();
    }

    @Test
    void nextReminderUsesConfiguredDueTimeAfterEffectiveBoundary() {
        GeneralInspectionPlanConfig config = new GeneralInspectionPlanConfig();
        config.setFrequency("DAILY");
        config.setEffectiveStart(LocalDate.of(2026, 8, 1));
        config.setSubmissionReminderEnabled(true);
        config.setReminderEffectiveTime(LocalDateTime.of(2026, 8, 29, 10, 0));
        GeneralInspectionPlanConfig.Slot slot = new GeneralInspectionPlanConfig.Slot();
        slot.setDueTime(LocalTime.of(18, 0));
        slot.setDueDayOffset(0);
        config.setSlots(List.of(slot));

        assertThat(EdgeInspectionConfigService.nextReminderTime(
                config, LocalDateTime.of(2026, 8, 29, 11, 0)))
                .isEqualTo(LocalDateTime.of(2026, 8, 29, 18, 0));
        assertThat(EdgeInspectionConfigService.nextReminderTime(
                config, LocalDateTime.of(2026, 8, 29, 18, 1)))
                .isEqualTo(LocalDateTime.of(2026, 8, 30, 18, 0));
    }

}
