package com.example.siteplatform.inspection.general.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class EdgeInspectionRectificationCompleteRequest {
    @NotNull private Integer expectedVersion;
    @Valid @NotEmpty @Size(max = 50) private List<ItemFeedback> items;

    @Data
    public static class ItemFeedback {
        @NotNull private Long rectificationId;
        @NotNull private Integer expectedVersion;
        @NotNull @Size(max = 1000) private String feedback;
        @NotEmpty @Size(max = 9) private List<Long> photoFileIds;
    }
}
