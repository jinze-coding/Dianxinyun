package com.example.siteplatform.quality.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class QualityIssueCreateRequest {
    private Long projectId;
    private String title;
    private String location;
    private String description;
    private String severity;
    private Long assigneeId;
    private LocalDate deadline;
    private List<Long> photoFileIds;
}
