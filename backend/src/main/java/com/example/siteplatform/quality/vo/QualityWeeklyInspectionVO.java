package com.example.siteplatform.quality.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class QualityWeeklyInspectionVO {
    private Long id;
    private Long projectId;
    private String inspectionNo;
    private LocalDate weekStart;
    private LocalDate weekEnd;
    private LocalDate inspectionDate;
    private String status;
    private String conclusion;
    private List<Long> overviewPhotoFileIds;
    private Integer submittedIssueCount;
    private Integer pendingCount;
    private Integer recheckCount;
    private Integer closedCount;
    private Integer voidedCount;
    private String createdByName;
    private String lastEditedByName;
    private String submittedByName;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private LocalDateTime submittedTime;
    private Integer version;
    private Boolean lateSubmission;
    private List<QualityWeeklyDraftItemVO> draftItems;
    private List<QualityIssueVO> issues;
}
