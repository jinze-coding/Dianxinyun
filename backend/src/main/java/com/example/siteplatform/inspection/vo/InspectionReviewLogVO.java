package com.example.siteplatform.inspection.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InspectionReviewLogVO {
    private Long id;
    private Long recordId;
    private Long projectId;
    private Long electricBoxId;
    private String actionType;
    private Long fromReviewerId;
    private String fromReviewerName;
    private Long toReviewerId;
    private String toReviewerName;
    private Long operatorId;
    private String operatorName;
    private String comment;
    private LocalDateTime createTime;
}
