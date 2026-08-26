package com.example.siteplatform.quality.vo;

import lombok.Data;

import java.time.LocalDate;

@Data
public class QualityWeeklySummaryVO {
    private Long projectId;
    private LocalDate weekStart;
    private LocalDate weekEnd;
    private Boolean hasInspection;
    private Long inspectionId;
    private String inspectionNo;
    private String status;
    private Integer version;
    private Integer draftItemCount;
    private Integer submittedIssueCount;
    private Integer pendingCount;
    private Integer recheckCount;
    private Integer closedCount;
    private Integer voidedCount;
    private Boolean lateSubmission;
    private Boolean canManage;
}
