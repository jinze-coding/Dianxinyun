package com.example.siteplatform.inspection.general.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class GeneralInspectionBatchCancelRequest {
    @NotEmpty @Size(max = 500) private List<Long> taskIds;
    @NotBlank private String reason;
}
