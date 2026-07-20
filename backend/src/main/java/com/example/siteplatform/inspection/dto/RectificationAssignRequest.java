package com.example.siteplatform.inspection.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class RectificationAssignRequest {
    private Long assigneeId;
    private LocalDate deadline;
    private String comment;
}
