package com.example.siteplatform.quality.service;

import com.example.siteplatform.notification.service.UserNotificationService;
import com.example.siteplatform.project.entity.ProjectInfo;
import com.example.siteplatform.project.mapper.ProjectInfoMapper;
import com.example.siteplatform.quality.entity.QualityWeeklyInspection;
import com.example.siteplatform.quality.entity.QualityWeeklyReminderSetting;
import com.example.siteplatform.quality.mapper.QualityWeeklyInspectionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QualityWeeklySubmissionReminderServiceTest {

    @Mock private QualityWeeklyReminderSettingService settingService;
    @Mock private QualityWeeklyInspectionMapper inspectionMapper;
    @Mock private ProjectInfoMapper projectMapper;
    @Mock private UserNotificationService notificationService;

    private QualityWeeklySubmissionReminderService service;

    @BeforeEach
    void setUp() {
        service = new QualityWeeklySubmissionReminderService(
                settingService, inspectionMapper, projectMapper, notificationService);
    }

    @Test
    void sharedDraftAtTriggerCreatesOneControlledStationNotification() {
        LocalDate weekStart = LocalDate.of(2026, 8, 24);
        LocalDateTime scheduledAt = LocalDateTime.of(2026, 8, 30, 18, 0);
        QualityWeeklyReminderSetting setting = setting();
        QualityWeeklyInspection draft = new QualityWeeklyInspection();
        draft.setStatus("DRAFT");
        when(settingService.lockForUpdate(9L)).thenReturn(setting);
        when(settingService.scheduledAt(setting, weekStart)).thenReturn(scheduledAt);
        when(settingService.isOccurrenceEffective(setting, scheduledAt)).thenReturn(true);
        when(projectMapper.selectById(9L)).thenReturn(new ProjectInfo());
        when(settingService.isEligibleResponsibleUser(7L, 9L)).thenReturn(true);
        when(inspectionMapper.selectByProjectWeekForUpdate(9L, weekStart)).thenReturn(draft);

        assertTrue(service.remind(9L, weekStart, scheduledAt));

        verify(notificationService).notify(7L, 9L, "QUALITY_WEEKLY_INSPECTION", 41L,
                "QUALITY_WEEKLY_NOT_SUBMITTED", "质量周检未提交",
                "2026-08-24 至 2026-08-30 的质量周检尚未正式提交；仅保存共享草稿仍视为未提交。",
                "reminder:qweek:9:2026-08-24", "QUALITY_WEEKLY_INSPECTION_WEEK",
                "{\"projectId\":9,\"weekStart\":\"2026-08-24\"}");
    }

    @Test
    void submittedZeroIssueInspectionDoesNotCreateReminder() {
        LocalDate weekStart = LocalDate.of(2026, 8, 24);
        LocalDateTime scheduledAt = LocalDateTime.of(2026, 8, 30, 18, 0);
        QualityWeeklyReminderSetting setting = setting();
        QualityWeeklyInspection submitted = new QualityWeeklyInspection();
        submitted.setStatus("SUBMITTED");
        submitted.setSubmittedIssueCount(0);
        when(settingService.lockForUpdate(9L)).thenReturn(setting);
        when(settingService.scheduledAt(setting, weekStart)).thenReturn(scheduledAt);
        when(settingService.isOccurrenceEffective(setting, scheduledAt)).thenReturn(true);
        when(projectMapper.selectById(9L)).thenReturn(new ProjectInfo());
        when(settingService.isEligibleResponsibleUser(7L, 9L)).thenReturn(true);
        when(inspectionMapper.selectByProjectWeekForUpdate(9L, weekStart)).thenReturn(submitted);

        assertFalse(service.remind(9L, weekStart, scheduledAt.plusMinutes(1)));

        verify(notificationService, never()).notify(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private QualityWeeklyReminderSetting setting() {
        QualityWeeklyReminderSetting setting = new QualityWeeklyReminderSetting();
        setting.setId(41L);
        setting.setProjectId(9L);
        setting.setEnabled(1);
        setting.setResponsibleUserId(7L);
        return setting;
    }
}
