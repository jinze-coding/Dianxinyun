package com.example.siteplatform.inspection.general.vo;

import lombok.Data;

import java.util.List;

@Data
public class GeneralInspectionDashboardVO {
    private Long projectId;
    private Long electricBoxDueCount;
    private Long electricBoxCompletedCount;
    private Long electricBoxMissedCount;
    private Long electricBoxAbnormalCount;
    private Long generalDueCount;
    private Long generalCompletedCount;
    private Long generalMissedCount;
    private Long generalAbnormalCount;
    private Long dueCount;
    private Long completedCount;
    private Long onTimeCount;
    private Long lateCompletedCount;
    private Long missedCount;
    private Long abnormalTaskCount;
    private Long openRectificationCount;
    private Long closedRectificationCount;
    private Double completionRate;
    private Double onTimeRate;
    private Double rectificationClosureRate;
    private List<DimensionStat> breakdowns;

    @Data
    public static class DimensionStat {
        private String dimension;
        private String dimensionKey;
        private String dimensionName;
        private Long dueCount;
        private Long completedCount;
        private Long onTimeCount;
        private Long lateCompletedCount;
        private Long missedCount;
        private Long abnormalCount;
        private Long openRectificationCount;
        private Long closedRectificationCount;
        private Double rectificationClosureRate;
        private Double averageCloseHours;
    }
}
