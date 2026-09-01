package com.example.siteplatform.document.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class DocumentIncomingBatchSaveRequest {
    @NotNull @Positive
    private Long projectId;
    @NotBlank @Size(max = 200)
    private String sourceOrganization;
    @Size(max = 100)
    private String senderName;
    @Size(max = 100)
    private String sourceReferenceNo;
    @Size(max = 30)
    private String receiveMethod;
    @NotNull
    private LocalDateTime receivedAt;
    @Size(max = 500)
    private String remark;
    private Integer expectedVersion;
    @Valid
    private List<DocumentIncomingItemRequest> items = new ArrayList<>();
}
