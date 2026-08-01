package com.example.siteplatform.quality.vo;

import lombok.Data;

@Data
public class QualityTodoVO {
    private Long id;
    private String type;
    private String title;
    private Long projectId;
    private String projectName;
    private String boxCode;
    private String installLocation;
    private String dueText;
    private Long targetId;
    private String businessType;
    private String priority;
    private Integer reviewOverdue;
}
