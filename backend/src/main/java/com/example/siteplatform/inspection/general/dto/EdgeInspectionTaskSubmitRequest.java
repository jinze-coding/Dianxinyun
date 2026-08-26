package com.example.siteplatform.inspection.general.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class EdgeInspectionTaskSubmitRequest {
    @NotNull private Integer expectedVersion;
    @NotEmpty @Size(max = 9) private List<Long> overallPhotoFileIds;
    private String remark;
    @Valid @NotEmpty @Size(max = 50) private List<ItemResult> items;

    @Data
    public static class ItemResult {
        @NotNull private Long taskItemId;
        @NotNull private String result;
        private String description;
        @Size(max = 9) private List<Long> photoFileIds;
    }

    public GeneralInspectionTaskSubmitRequest toGeneralRequest() {
        GeneralInspectionTaskSubmitRequest request = new GeneralInspectionTaskSubmitRequest();
        request.setExpectedVersion(expectedVersion);
        request.setOverallPhotoFileIds(overallPhotoFileIds);
        request.setRemark(remark);
        request.setItems(items.stream().map(item -> {
            GeneralInspectionTaskSubmitRequest.ItemResult result = new GeneralInspectionTaskSubmitRequest.ItemResult();
            result.setTaskItemId(item.getTaskItemId());
            result.setResult(item.getResult());
            result.setDescription(item.getDescription());
            result.setPhotoFileIds(item.getPhotoFileIds());
            return result;
        }).toList());
        return request;
    }
}
