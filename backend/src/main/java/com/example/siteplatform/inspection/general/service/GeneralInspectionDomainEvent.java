package com.example.siteplatform.inspection.general.service;

import java.time.LocalDateTime;
import java.util.Map;

public record GeneralInspectionDomainEvent(
        String eventType,
        Long projectId,
        String businessType,
        Long businessId,
        LocalDateTime occurredTime,
        Map<String, Object> payload
) {
}
