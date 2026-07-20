package com.example.siteplatform.quality.vo;

import lombok.Data;

@Data
public class QualityIssueSummaryVO {
    private Integer todayCheckCount;
    private Integer pendingCount;
    private Integer overdueCount;
    private Integer recheckCount;
    private Integer closedCount;
    private Integer closureRate;
    private Boolean canManage;
}
