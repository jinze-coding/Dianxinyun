package com.example.siteplatform.inspection.general.service;

public interface GeneralInspectionEventPublisher {
    void publish(GeneralInspectionDomainEvent event);
}
