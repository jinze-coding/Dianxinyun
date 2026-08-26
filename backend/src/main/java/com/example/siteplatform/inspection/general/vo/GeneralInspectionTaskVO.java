package com.example.siteplatform.inspection.general.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class GeneralInspectionTaskVO {
    private Long id;
    private Long projectId;
    private Long pointId;
    private Integer revisionNo;
    private Long replacesTaskId;
    private String planName;
    private String templateName;
    private String pointCode;
    private String pointName;
    private String locationDesc;
    private String slotCode;
    private String slotName;
    private LocalDate occurrenceDate;
    private LocalDateTime availableTime;
    private LocalDateTime startTime;
    private LocalDateTime dueTime;
    private Long assigneeId;
    private String assigneeName;
    private Long reviewerId;
    private String reviewerName;
    private Boolean qrRequired;
    private Boolean scanVerified;
    private String status;
    private String displayStatus;
    private Boolean overdue;
    private Boolean lateSubmission;
    private Boolean canExecute;
    private Boolean canManage;
    private LocalDateTime submittedTime;
    private Integer overallPhotoMin;
    private Integer overallPhotoMax;
    private Boolean overallRemarkRequired;
    private List<Long> overallPhotoFileIds;
    private String remark;
    private String publicRemark;
    private Integer abnormalCount;
    private Integer version;
    private List<GeneralInspectionTaskItemVO> items;
}
