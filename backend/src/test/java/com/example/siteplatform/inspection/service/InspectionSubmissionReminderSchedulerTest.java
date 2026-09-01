package com.example.siteplatform.inspection.service;

import com.example.siteplatform.quality.service.QualityWeeklySubmissionReminderService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.*;

class InspectionSubmissionReminderSchedulerTest {

    @Test
    void oneCandidateFailureDoesNotStopRemainingReminderProcessing() {
        InspectionSubmissionReminderService service = mock(InspectionSubmissionReminderService.class);
        QualityWeeklySubmissionReminderService qualityService =
                mock(QualityWeeklySubmissionReminderService.class);
        LocalDateTime now = LocalDateTime.of(2026, 8, 29, 18, 1);
        LocalDate weekStart = LocalDate.of(2026, 8, 24);
        when(qualityService.dueCandidates(now)).thenReturn(List.of(
                new QualityWeeklySubmissionReminderService.QualityReminderCandidate(2L, weekStart)));
        when(service.dueElectricCandidates(now)).thenReturn(List.of(
                new InspectionSubmissionReminderService.ElectricReminderCandidate(1L, 10L),
                new InspectionSubmissionReminderService.ElectricReminderCandidate(1L, 11L)));
        when(service.remindElectricBox(1L, 10L, now.toLocalDate(), now))
                .thenThrow(new IllegalStateException("single candidate failure"));
        when(service.dueEdgeTaskIds(now)).thenReturn(List.of(20L));

        new InspectionSubmissionReminderScheduler(service, qualityService).scanAt(now);

        verify(qualityService).remind(2L, weekStart, now);
        verify(service).remindElectricBox(1L, 11L, now.toLocalDate(), now);
        verify(service).remindEdgeTask(20L, now);
    }
}
