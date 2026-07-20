package com.example.siteplatform.quality.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class QualityAssignRequest {
    private Long assigneeId;
    private LocalDate deadline;
    private String comment;
}
