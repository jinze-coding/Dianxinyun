package com.example.siteplatform.inspection.general.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class EdgeInspectionRectificationSheetVO {
    private Long taskId;
    private Long projectId;
    private Long pointId;
    private String pointCode;
    private String pointName;
    private String pointTypeCode;
    private String pointTypeName;
    private String buildingName;
    private String floorName;
    private String locationDesc;
    private LocalDate occurrenceDate;
    private String status;
    private String reviewComment;
    private Long assigneeId;
    private String assigneeName;
    private Long reviewerId;
    private String reviewerName;
    private LocalDate deadline;
    private Integer version;
    private Boolean overdue;
    private Boolean canRectify;
    private Boolean canReview;
    private Boolean canAssign;
    private List<Item> items;

    @Data
    public static class Item {
        private Long rectificationId;
        private Long taskItemId;
        private String itemName;
        private String problemDesc;
        private String requirement;
        private List<Long> evidencePhotoFileIds;
        private String status;
        private String feedback;
        private List<Long> photoFileIds;
        private LocalDateTime completedTime;
        private String reviewComment;
        private LocalDateTime reviewTime;
        private Integer rejectCount;
        private Integer version;
    }
}
