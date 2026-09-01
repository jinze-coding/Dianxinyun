package com.example.siteplatform.quality.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class QualityIssueExportJobVO {
    private Long id;
    private Long projectId;
    private String requestedByName;
    private LocalDate startDate;
    private LocalDate endDate;
    private String issueSource;
    private String issueStatus;
    private String keyword;
    private String status;
    private Integer progress;
    private Integer issueCount;
    private Integer photoCount;
    private Long photoBytes;
    private String errorMessage;
    private LocalDateTime expiresTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Boolean downloadable;
}
