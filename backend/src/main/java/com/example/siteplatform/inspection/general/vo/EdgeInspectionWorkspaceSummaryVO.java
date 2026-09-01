package com.example.siteplatform.inspection.general.vo;

import lombok.Data;

@Data
public class EdgeInspectionWorkspaceSummaryVO {
    private Long enabledPointCount;
    private Long myTodayDueCount;
    private Long myTodayPendingCount;
    private Long myTodaySubmittedCount;
    private Long myTodayCancelledCount;
    private Long myOverdueCount;
    private Long myRectificationPendingCount;
    private Long myReviewPendingCount;
}
