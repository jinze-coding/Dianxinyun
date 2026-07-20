package com.example.siteplatform.inspection.vo;

import lombok.Data;

@Data
public class InspectionRecordItemVO {
    private Long id;
    private Long recordId;
    private String itemCode;
    private String itemName;
    private String result;
    private String description;
}
