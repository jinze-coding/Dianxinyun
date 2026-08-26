package com.example.siteplatform.inspection.general.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class GeneralInspectionRectificationVO {
    private Long id;
    private Long projectId;
    private Long taskId;
    private Long taskItemId;
    private Long pointId;
    private String pointName;
    private String itemName;
    private String problemDesc;
    private String requirement;
    private Long assigneeId;
    private String assigneeName;
    private LocalDate deadline;
    private Long reviewerId;
    private String reviewerName;
    private String status;
    private String feedback;
    private List<Long> rectificationPhotoFileIds;
    private LocalDateTime completedTime;
    private String reviewComment;
    private LocalDateTime reviewTime;
    private Integer rejectCount;
    private LocalDateTime closeTime;
    private Integer version;
    private Boolean overdue;
    private Boolean canRectify;
    private Boolean canReview;
    private Boolean canAssign;
}
