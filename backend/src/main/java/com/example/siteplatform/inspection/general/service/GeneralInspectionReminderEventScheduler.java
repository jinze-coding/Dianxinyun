package com.example.siteplatform.inspection.general.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.siteplatform.inspection.general.entity.GeneralInspectionEventOutbox;
import com.example.siteplatform.inspection.general.entity.GeneralInspectionRectification;
import com.example.siteplatform.inspection.general.entity.GeneralInspectionTask;
import com.example.siteplatform.inspection.general.mapper.GeneralInspectionEventOutboxMapper;
import com.example.siteplatform.inspection.general.mapper.GeneralInspectionRectificationMapper;
import com.example.siteplatform.inspection.general.mapper.GeneralInspectionTaskMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeneralInspectionReminderEventScheduler {
    private final GeneralInspectionTaskMapper taskMapper;
    private final GeneralInspectionRectificationMapper rectificationMapper;
    private final GeneralInspectionEventPublisher eventPublisher;
    private final GeneralInspectionEventOutboxMapper outboxMapper;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ObjectMapper objectMapper;

    @Scheduled(cron = "${general-inspection.reminder-event-cron:15 */5 * * * ?}")
    public void produceReminderEvents() {
        LocalDateTime now = LocalDateTime.now();
        List<GeneralInspectionTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<GeneralInspectionTask>()
                .eq(GeneralInspectionTask::getStatus, "PENDING")
                .le(GeneralInspectionTask::getAvailableTime, now)
                .orderByAsc(GeneralInspectionTask::getDueTime).last("LIMIT 1000"));
        for (GeneralInspectionTask task : tasks) {
            emit(task, "TASK_AVAILABLE", now);
            if (!task.getDueTime().isAfter(now)) emit(task, "TASK_OVERDUE", now);
            else if (!task.getDueTime().isAfter(now.plusMinutes(30))) emit(task, "TASK_DUE_SOON", now);
        }
        List<GeneralInspectionRectification> rectifications = rectificationMapper.selectList(
                new LambdaQueryWrapper<GeneralInspectionRectification>()
                        .in(GeneralInspectionRectification::getStatus, "UNASSIGNED", "COMPLETED")
                        .orderByAsc(GeneralInspectionRectification::getId).last("LIMIT 1000"));
        for (GeneralInspectionRectification rectification : rectifications) {
            String type = "UNASSIGNED".equals(rectification.getStatus())
                    ? "RECTIFICATION_UNASSIGNED" : "RECTIFICATION_REVIEW_PENDING";
            eventPublisher.publish(new GeneralInspectionDomainEvent(type, rectification.getProjectId(),
                    "RECTIFICATION", rectification.getId(), now,
                    Map.of("version", value(rectification.getVersion()))));
        }
    }

    @Scheduled(cron = "${general-inspection.event-dispatch-cron:30 * * * * ?}")
    public void dispatchOutbox() {
        outboxMapper.recoverStuck();
        List<GeneralInspectionEventOutbox> rows = outboxMapper.selectList(
                new LambdaQueryWrapper<GeneralInspectionEventOutbox>()
                        .in(GeneralInspectionEventOutbox::getStatus, "PENDING", "FAILED")
                        .lt(GeneralInspectionEventOutbox::getRetryCount, 5)
                        .orderByAsc(GeneralInspectionEventOutbox::getCreateTime).last("LIMIT 100"));
        for (GeneralInspectionEventOutbox row : rows) {
            if (outboxMapper.claim(row.getId()) != 1) continue;
            try {
                applicationEventPublisher.publishEvent(toDomainEvent(row));
                if (outboxMapper.markPublished(row.getId()) != 1) {
                    log.warn("通用巡检事件发布状态未更新，eventId={}", row.getId());
                }
            } catch (RuntimeException ex) {
                outboxMapper.markFailed(row.getId(), abbreviate(ex.getMessage()));
                log.warn("通用巡检预留事件发布失败，eventId={}", row.getId(), ex);
            }
        }
    }

    private void emit(GeneralInspectionTask task, String eventType, LocalDateTime now) {
        eventPublisher.publish(new GeneralInspectionDomainEvent(eventType, task.getProjectId(), "TASK",
                task.getId(), now, Map.of("version", 0, "dueTime", String.valueOf(task.getDueTime()))));
    }

    private GeneralInspectionDomainEvent toDomainEvent(GeneralInspectionEventOutbox row) {
        Map<String, Object> payload;
        try {
            payload = objectMapper.readValue(row.getPayloadJson(), new TypeReference<>() {});
        } catch (Exception ignored) {
            payload = Map.of();
        }
        return new GeneralInspectionDomainEvent(row.getEventType(), row.getProjectId(), row.getBusinessType(),
                row.getBusinessId(), row.getOccurredTime(), payload);
    }

    private int value(Integer value) { return value == null ? 0 : value; }
    private String abbreviate(String value) {
        if (value == null) return "事件监听器执行失败";
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }
}
