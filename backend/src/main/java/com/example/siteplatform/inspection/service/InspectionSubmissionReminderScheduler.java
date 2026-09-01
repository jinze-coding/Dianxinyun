package com.example.siteplatform.inspection.service;

import com.example.siteplatform.quality.service.QualityWeeklySubmissionReminderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InspectionSubmissionReminderScheduler {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final InspectionSubmissionReminderService reminderService;
    private final QualityWeeklySubmissionReminderService qualityReminderService;

    @Scheduled(cron = "${inspection.submission-reminder.cron:0 * * * * ?}", zone = "Asia/Shanghai")
    public void scan() {
        scanAt(LocalDateTime.now(BUSINESS_ZONE));
    }

    void scanAt(LocalDateTime now) {
        List<QualityWeeklySubmissionReminderService.QualityReminderCandidate> qualityCandidates;
        try {
            qualityCandidates = qualityReminderService.dueCandidates(now);
        } catch (RuntimeException ex) {
            log.error("质量周检未提交提醒候选扫描失败", ex);
            qualityCandidates = List.of();
        }
        for (QualityWeeklySubmissionReminderService.QualityReminderCandidate candidate : qualityCandidates) {
            try {
                qualityReminderService.remind(candidate.projectId(), candidate.weekStart(), now);
            } catch (RuntimeException ex) {
                log.error("质量周检未提交站内提醒处理失败，projectId={}, weekStart={}",
                        candidate.projectId(), candidate.weekStart(), ex);
            }
        }
        List<InspectionSubmissionReminderService.ElectricReminderCandidate> electricCandidates;
        try {
            electricCandidates = reminderService.dueElectricCandidates(now);
        } catch (RuntimeException ex) {
            log.error("电箱日检未提交提醒候选扫描失败", ex);
            electricCandidates = List.of();
        }
        for (InspectionSubmissionReminderService.ElectricReminderCandidate candidate
                : electricCandidates) {
            try {
                reminderService.remindElectricBox(candidate.projectId(), candidate.boxId(), now.toLocalDate(), now);
            } catch (RuntimeException ex) {
                log.error("电箱日检未提交站内提醒处理失败，projectId={}, boxId={}",
                        candidate.projectId(), candidate.boxId(), ex);
            }
        }
        List<Long> edgeTaskIds;
        try {
            edgeTaskIds = reminderService.dueEdgeTaskIds(now);
        } catch (RuntimeException ex) {
            log.error("临边巡检未提交提醒候选扫描失败", ex);
            edgeTaskIds = List.of();
        }
        for (Long taskId : edgeTaskIds) {
            try {
                reminderService.remindEdgeTask(taskId, now);
            } catch (RuntimeException ex) {
                log.error("临边巡检未提交站内提醒处理失败，taskId={}", taskId, ex);
            }
        }
    }
}
