package com.example.siteplatform.quality.dto;

import lombok.Data;

import java.util.List;

@Data
public class QualityRectificationRequest {
    private String description;
    private List<Long> photoFileIds;
}
