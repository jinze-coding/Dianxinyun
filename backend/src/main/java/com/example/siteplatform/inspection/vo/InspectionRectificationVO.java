package com.example.siteplatform.inspection.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class InspectionRectificationVO {
    private Long id;
    private Long projectId;
    private Long electricBoxId;
    private Long inspectionRecordId;
    private Long recordItemId;
    private String boxCode;
    private String boxName;
    private String installLocation;
    private String orderNo;
    private String inspectorName;
    private LocalDateTime createdAt;
    private String problemDesc;
    private String problemCategory;
    private String requirement;
    private Long assigneeId;
    private String assigneeName;
    private String responsiblePhone;
    private LocalDate deadline;
    private String status;
    private String feedback;
    private LocalDateTime completedAt;
    private LocalDateTime reviewTime;
    private String reviewComment;
    private Integer rejectCount;
    private LocalDate recheckDeadline;
    private String escalationStatus;
    private LocalDateTime escalationTime;
    private String escalationNote;
    private List<Long> beforePhotoFileIds;
    private List<Long> rectificationPhotoFileIds;
    private List<InspectionRectificationReviewLogVO> reviewLogs;
    private Boolean canRectify;
    private Boolean canReview;
    private Boolean canAssign;
}
