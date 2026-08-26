package com.example.siteplatform.inspection.general.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("general_inspection_task")
public class GeneralInspectionTask {
    @TableId(type = IdType.AUTO) private Long id;
    private Long projectId;
    private Long planId;
    private Long planVersionId;
    private Long templateId;
    private Long templateVersionId;
    private Long pointId;
    private Integer revisionNo;
    private Long replacesTaskId;
    private String planName;
    private String templateName;
    private String pointCode;
    private String pointName;
    private String pointTypeCode;
    private String pointTypeName;
    private String buildingName;
    private String floorName;
    private String locationDesc;
    private String slotCode;
    private String slotName;
    private LocalDate occurrenceDate;
    private LocalDateTime availableTime;
    private LocalDateTime startTime;
    private LocalDateTime dueTime;
    private Long assigneeId;
    private String assigneeName;
    private String backupAssigneeIds;
    private Long defaultRectifierId;
    private String defaultRectifierName;
    private Integer defaultRectificationDays;
    private Long reviewerId;
    private String reviewerName;
    private String backupReviewerIds;
    private Integer qrRequired;
    private Integer qrVersion;
    private Long scanVerifiedBy;
    private LocalDateTime scanVerifiedTime;
    private String status;
    private Long submittedById;
    private String submittedByName;
    private LocalDateTime submittedTime;
    private Integer onTime;
    private Integer overallPhotoMin;
    private Integer overallPhotoMax;
    private Integer overallRemarkRequired;
    private String overallPhotoFileIds;
    private String remark;
    private String publicRemark;
    private Integer abnormalCount;
    private String cancelReason;
    private Long cancelledById;
    private String cancelledByName;
    private LocalDateTime cancelledTime;
    private String correctionNote;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
