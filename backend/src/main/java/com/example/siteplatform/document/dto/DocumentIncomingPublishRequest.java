package com.example.siteplatform.document.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DocumentIncomingPublishRequest {
    @NotEmpty @Valid
    private List<DocumentIncomingItemRequest> items = new ArrayList<>();
    @NotNull @Valid
    private DocumentDistributionSettingsRequest distribution;
    private Integer expectedVersion;
}
