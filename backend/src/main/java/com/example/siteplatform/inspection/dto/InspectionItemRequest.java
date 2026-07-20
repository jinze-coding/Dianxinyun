package com.example.siteplatform.inspection.dto;

import lombok.Data;

@Data
public class InspectionItemRequest {
    private String itemCode;
    private String itemName;
    private String result;
    private String description;
}
