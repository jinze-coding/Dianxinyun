package com.example.siteplatform.project.dto;

import lombok.Data;

@Data
public class ResponsibilityImpactVO {
    private Long projectId;
    private String projectName;
    private Long userId;
    private long responsibleElectricBoxCount;
    private long safetyManagedElectricBoxCount;
    private long pendingInspectionReviewCount;
    private long openRectificationCount;
    private long openQualityIssueCount;
    private long pendingSealApprovalCount;
    private long sealApprovalConfigCount;

    public long getTotalCount() {
        return responsibleElectricBoxCount + safetyManagedElectricBoxCount
                + pendingInspectionReviewCount + openRectificationCount + openQualityIssueCount
                + pendingSealApprovalCount + sealApprovalConfigCount;
    }
}
