package com.example.siteplatform.workcenter.vo;

import lombok.Data;

import java.util.Map;

@Data
public class WorkSummaryVO {
    private Long pendingCount;
    private Long ccCount;
    private Long unreadNotificationCount;
    private Long badgeCount;

    // Web compatibility aliases and breakdowns.
    private Long total;
    private Long todoCount;
    private Map<String, Long> byBusinessType;
    private Map<String, Long> byTaskType;
}
