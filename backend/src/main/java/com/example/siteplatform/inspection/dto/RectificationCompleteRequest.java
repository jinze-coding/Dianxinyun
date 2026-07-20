package com.example.siteplatform.inspection.dto;

import lombok.Data;

import java.util.List;

@Data
public class RectificationCompleteRequest {
    private String feedback;
    private List<Long> photoFileIds;
}
