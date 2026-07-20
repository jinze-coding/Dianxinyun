package com.example.siteplatform.inspection.dto;

import lombok.Data;

@Data
public class InspectionReviewAssignRequest {
    private Long reviewerId;
    private String comment;
}
