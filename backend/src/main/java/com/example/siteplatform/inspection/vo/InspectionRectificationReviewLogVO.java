package com.example.siteplatform.inspection.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InspectionRectificationReviewLogVO {
    private Long id;
    private Long rectificationId;
    private Long projectId;
    private Long electricBoxId;
    private Long inspectionRecordId;
    private String actionType;
    private String fromStatus;
    private String toStatus;
    private Long operatorId;
    private String operatorName;
    private String comment;
    private String photoFileIds;
    private LocalDateTime createTime;
}
