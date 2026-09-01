package com.example.siteplatform.document.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DocumentDistributionOverdueScheduler {
    private final DocumentCirculationService service;

    public DocumentDistributionOverdueScheduler(DocumentCirculationService service) {
        this.service = service;
    }

    @Scheduled(cron = "${document.circulation.overdue-cron:0 5 * * * ?}")
    public void remind() {
        service.sendOverdueReminders(100);
    }
}
