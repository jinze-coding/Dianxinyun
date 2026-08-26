package com.example.siteplatform.inspection.general.vo;

import lombok.Data;

import java.util.List;

@Data
public class GeneralInspectionTaskItemVO {
    private Long id;
    private String itemKey;
    private String itemName;
    private String guidance;
    private String standardReference;
    private Boolean allowNa;
    private Integer normalPhotoMin;
    private Integer abnormalPhotoMin;
    private Integer photoMax;
    private Boolean normalDescriptionRequired;
    private Boolean abnormalDescriptionRequired;
    private Integer sortOrder;
    private String result;
    private String description;
    private List<Long> photoFileIds;
}
