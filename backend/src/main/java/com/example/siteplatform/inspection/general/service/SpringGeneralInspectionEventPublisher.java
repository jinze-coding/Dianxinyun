package com.example.siteplatform.inspection.general.service;

import com.example.siteplatform.inspection.general.entity.GeneralInspectionEventOutbox;
import com.example.siteplatform.inspection.general.mapper.GeneralInspectionEventOutboxMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class SpringGeneralInspectionEventPublisher implements GeneralInspectionEventPublisher {
    private final GeneralInspectionEventOutboxMapper outboxMapper;
    private final ObjectMapper objectMapper;

    @Override
    public void publish(GeneralInspectionDomainEvent event) {
        if (event == null || event.businessId() == null || event.projectId() == null) return;
        Map<String, Object> payload = event.payload() == null ? Map.of() : event.payload();
        String sequence = String.valueOf(payload.getOrDefault("version", 0));
        GeneralInspectionEventOutbox outbox = new GeneralInspectionEventOutbox();
        outbox.setEventKey(event.eventType() + ":" + event.businessType() + ":" + event.businessId() + ":" + sequence);
        outbox.setEventType(event.eventType());
        outbox.setProjectId(event.projectId());
        outbox.setBusinessType(event.businessType());
        outbox.setBusinessId(event.businessId());
        outbox.setPayloadJson(json(payload));
        outbox.setStatus("PENDING");
        outbox.setOccurredTime(event.occurredTime());
        outbox.setRetryCount(0);
        try {
            outboxMapper.insert(outbox);
        } catch (DuplicateKeyException ignored) {
            // 调度补偿及多实例并发使用事件键幂等。
        } catch (RuntimeException ex) {
            // 外部渠道只是扩展能力，发件箱异常不能阻断巡检、整改或复查事务。
            log.warn("通用巡检预留事件写入失败，eventKey={}", outbox.getEventKey(), ex);
        }
    }

    private String json(Map<String, Object> payload) {
        try {
            String value = objectMapper.writeValueAsString(payload);
            return value.length() <= 4000 ? value : value.substring(0, 4000);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }
}
