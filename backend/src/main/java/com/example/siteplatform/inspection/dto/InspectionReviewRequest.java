package com.example.siteplatform.inspection.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class InspectionReviewRequest {
    private String reviewAction;
    private String comment;
    private Long assigneeId;
    private String assigneeName;
    private String requirement;
    private String problemCategory;
    private LocalDate deadline;
}
