package com.example.siteplatform.inspection.general.vo;

import com.example.siteplatform.inspection.general.entity.GeneralInspectionTemplateItem;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class GeneralInspectionTemplateVO {
    private Long id;
    private String scopeType;
    private Long projectId;
    private Long sourceTemplateId;
    private String templateCode;
    private String templateName;
    private String categoryName;
    private String status;
    private Long currentVersionId;
    private Integer currentVersionNo;
    private LocalDateTime effectiveTime;
    private Integer overallPhotoMin;
    private Integer overallPhotoMax;
    private Boolean overallRemarkRequired;
    private String remark;
    private Integer version;
    private Boolean canManage;
    private List<GeneralInspectionTemplateItem> items;
}
