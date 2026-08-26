package com.example.siteplatform.inspection.general.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EdgeInspectionRectificationReviewRequest {
    @NotNull private Integer expectedVersion;
    @Size(max = 1000) private String comment;
}
