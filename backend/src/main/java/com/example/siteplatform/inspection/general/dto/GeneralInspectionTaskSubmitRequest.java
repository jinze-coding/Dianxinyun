package com.example.siteplatform.inspection.general.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class GeneralInspectionTaskSubmitRequest {
    @NotNull private Integer expectedVersion;
    @Size(max = 9) private List<Long> overallPhotoFileIds;
    private String remark;
    private String publicRemark;
    @Valid @NotEmpty @Size(max = 50) private List<ItemResult> items;

    @Data
    public static class ItemResult {
        @NotNull private Long taskItemId;
        @NotNull private String result;
        private String description;
        @Size(max = 9) private List<Long> photoFileIds;
        private Long rectifierId;
        private LocalDate deadline;
        private String requirement;
    }
}
