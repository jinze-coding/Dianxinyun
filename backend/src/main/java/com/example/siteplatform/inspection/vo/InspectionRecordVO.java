package com.example.siteplatform.inspection.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class InspectionRecordVO {
    private Long id;
    private Long projectId;
    private Long electricBoxId;
    private String boxCode;
    private String boxName;
    private String installLocation;
    private String templateCode;
    private String source;
    private String problemCategory;
    private LocalDate checkDate;
    private LocalDateTime inspectedAt;
    private Long inspectorId;
    private String inspectorName;
    private String status;
    private String reviewStatus;
    private Long reviewerId;
    private String reviewerName;
    private LocalDateTime reviewTime;
    private LocalDateTime reviewDueTime;
    private Long assignedReviewerId;
    private String assignedReviewerName;
    private String reviewComment;
    private Integer reviewOverdue;
    private Integer abnormalCount;
    private Integer outerPhotoCount;
    private Integer innerPhotoCount;
    private List<Long> outerPhotoFileIds;
    private List<Long> innerPhotoFileIds;
    private List<Long> problemPhotoFileIds;
    private String remark;
    private List<InspectionRecordItemVO> items;
    private List<InspectionReviewLogVO> reviewLogs;
}
