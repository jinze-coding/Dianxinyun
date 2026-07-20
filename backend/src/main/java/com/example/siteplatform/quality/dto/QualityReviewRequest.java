package com.example.siteplatform.quality.dto;

import lombok.Data;

import java.util.List;

@Data
public class QualityReviewRequest {
    private Boolean passed;
    private String comment;
    private List<Long> photoFileIds;
}
