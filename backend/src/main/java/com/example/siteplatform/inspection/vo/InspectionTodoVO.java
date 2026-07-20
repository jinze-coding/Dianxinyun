package com.example.siteplatform.inspection.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InspectionTodoVO {
    private Long id;
    private String type;
    private String title;
    private Long projectId;
    private String projectName;
    private String boxCode;
    private String installLocation;
    private String dueText;
    private Long targetId;
    private String priority;
    private LocalDateTime reviewDueTime;
    private Long assignedReviewerId;
    private String assignedReviewerName;
    private Integer reviewOverdue;
}
