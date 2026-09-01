package com.example.siteplatform.inspection.general.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class EdgeInspectionExportJobVO {
    private Long id;
    private Long projectId;
    private String requestedByName;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private Integer progress;
    private Integer pointCount;
    private Integer taskCount;
    private Integer photoCount;
    private Long photoBytes;
    private String errorMessage;
    private LocalDateTime expiresTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Boolean downloadable;
}
