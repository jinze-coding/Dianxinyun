package com.example.siteplatform.document.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DocumentDistributionCreateRequest {
    @NotNull @Positive
    private Long projectId;
    @NotEmpty
    private List<@Positive Long> versionIds = new ArrayList<>();
    @NotNull @Valid
    private DocumentDistributionSettingsRequest distribution;
}
