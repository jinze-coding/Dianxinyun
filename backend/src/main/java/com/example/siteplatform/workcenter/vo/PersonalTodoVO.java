package com.example.siteplatform.workcenter.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class PersonalTodoVO {
    private Long id;
    private String todoKey;
    private String businessType;
    private String taskType;
    private String type;
    private Long targetId;
    private Long taskId;
    private Long projectId;
    private String projectName;
    private String title;
    private String summary;
    private String applicantName;
    private LocalDateTime dueAt;
    private String dueText;
    private String priority;
    private LocalDateTime createdAt;
    private String routeCode;
    private Map<String, Object> routeParams;
    private String scope;
    private Boolean readOnly;

    // Existing inspection clients still render these fields when present.
    private String boxCode;
    private String installLocation;
    private LocalDateTime reviewDueTime;
    private Long assignedReviewerId;
    private String assignedReviewerName;
    private Integer reviewOverdue;
}
