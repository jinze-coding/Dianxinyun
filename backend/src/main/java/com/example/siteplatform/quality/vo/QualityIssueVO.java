package com.example.siteplatform.quality.vo;

import com.example.siteplatform.quality.entity.QualityIssueLog;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class QualityIssueVO {
    private Long id;
    private Long projectId;
    private String issueNo;
    private String title;
    private String location;
    private String description;
    private List<Long> issuePhotoFileIds;
    private String severity;
    private String status;
    private Long assigneeId;
    private String assigneeName;
    private LocalDate deadline;
    private String rectificationDescription;
    private List<Long> rectificationPhotoFileIds;
    private LocalDateTime rectifiedTime;
    private String reviewerName;
    private String reviewComment;
    private LocalDateTime reviewTime;
    private List<Long> reviewPhotoFileIds;
    private String createdByName;
    private LocalDateTime createTime;
    private Boolean overdue;
    private String dueText;
    private Boolean canRectify;
    private Boolean canReview;
    private List<QualityIssueLog> logs;
}
