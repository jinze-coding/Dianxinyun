package com.example.siteplatform.quality.service;

import com.example.siteplatform.notification.service.UserNotificationService;
import com.example.siteplatform.project.mapper.ProjectInfoMapper;
import com.example.siteplatform.quality.entity.QualityWeeklyInspection;
import com.example.siteplatform.quality.entity.QualityWeeklyReminderSetting;
import com.example.siteplatform.quality.mapper.QualityWeeklyInspectionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class QualityWeeklySubmissionReminderService {

    private final QualityWeeklyReminderSettingService settingService;
    private final QualityWeeklyInspectionMapper inspectionMapper;
    private final ProjectInfoMapper projectMapper;
    private final UserNotificationService notificationService;

    public List<QualityReminderCandidate> dueCandidates(LocalDateTime now) {
        if (now == null) return List.of();
        LocalDate weekStart = now.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return settingService.listEnabledSettings(weekStart).stream()
                .filter(Objects::nonNull)
                .map(setting -> candidate(setting, weekStart, now))
                .filter(Objects::nonNull)
                .toList();
    }

    @Transactional
    public boolean remind(Long projectId, LocalDate weekStart, LocalDateTime now) {
        if (projectId == null || weekStart == null || now == null) return false;
        LocalDate normalizedWeekStart = weekStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        QualityWeeklyReminderSetting setting = settingService.lockForUpdate(projectId);
        if (setting == null || setting.getResponsibleUserId() == null) return false;
        LocalDateTime scheduledAt = settingService.scheduledAt(setting, normalizedWeekStart);
        if (scheduledAt == null || now.isBefore(scheduledAt)
                || !settingService.isOccurrenceEffective(setting, scheduledAt)) {
            return false;
        }
        if (projectMapper.selectById(projectId) == null
                || !settingService.isEligibleResponsibleUser(setting.getResponsibleUserId(), projectId)) {
            return false;
        }

        QualityWeeklyInspection inspection = inspectionMapper.selectByProjectWeekForUpdate(
                projectId, normalizedWeekStart);
        if (inspection != null && "SUBMITTED".equalsIgnoreCase(inspection.getStatus())) {
            return false;
        }

        LocalDate weekEnd = normalizedWeekStart.plusDays(6);
        notificationService.notify(setting.getResponsibleUserId(), projectId,
                "QUALITY_WEEKLY_INSPECTION", setting.getId(),
                "QUALITY_WEEKLY_NOT_SUBMITTED", "质量周检未提交",
                normalizedWeekStart + " 至 " + weekEnd
                        + " 的质量周检尚未正式提交；仅保存共享草稿仍视为未提交。",
                "reminder:qweek:" + projectId + ":" + normalizedWeekStart,
                "QUALITY_WEEKLY_INSPECTION_WEEK",
                "{\"projectId\":" + projectId + ",\"weekStart\":\""
                        + normalizedWeekStart + "\"}");
        return true;
    }

    private QualityReminderCandidate candidate(QualityWeeklyReminderSetting setting,
                                                LocalDate weekStart,
                                                LocalDateTime now) {
        if (setting.getProjectId() == null) return null;
        LocalDateTime scheduledAt = settingService.scheduledAt(setting, weekStart);
        if (scheduledAt == null || now.isBefore(scheduledAt)
                || !settingService.isOccurrenceEffective(setting, scheduledAt)) {
            return null;
        }
        return new QualityReminderCandidate(setting.getProjectId(), weekStart);
    }

    public record QualityReminderCandidate(Long projectId, LocalDate weekStart) {}
}
