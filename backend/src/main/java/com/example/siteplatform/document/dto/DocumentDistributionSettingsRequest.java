package com.example.siteplatform.document.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class DocumentDistributionSettingsRequest {
    @NotNull @Future
    private LocalDateTime deadline;
    @Size(max = 500)
    private String messageNote;
    private Boolean electronicSignatureRequired = false;
    private Boolean paperSignatureRequired = true;
    @NotEmpty @Valid
    private List<DocumentRecipientRequest> recipients = new ArrayList<>();
}
