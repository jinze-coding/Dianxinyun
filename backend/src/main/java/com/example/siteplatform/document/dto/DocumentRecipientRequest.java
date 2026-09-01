package com.example.siteplatform.document.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DocumentRecipientRequest {
    @NotNull @Positive
    private Long userId;
    @NotBlank
    private String channel;
    @Valid
    private List<DocumentRecipientCopyRequest> copies = new ArrayList<>();
}
