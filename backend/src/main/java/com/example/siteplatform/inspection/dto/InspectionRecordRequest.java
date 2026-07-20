package com.example.siteplatform.inspection.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class InspectionRecordRequest {
    private Long projectId;
    private Long electricBoxId;
    private String templateCode;
    private String source;
    private String problemCategory;
    private LocalDate checkDate;
    private List<Long> outerPhotoFileIds;
    private List<Long> innerPhotoFileIds;
    private String remark;
    private Long assigneeId;
    private String requirement;
    private LocalDate deadline;
    private List<InspectionItemRequest> items;
}
