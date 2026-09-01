package com.example.siteplatform.document.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class DocumentDistributionBatchVO {
    private Long id;
    private Long projectId;
    private Long incomingBatchId;
    private String distributionNo;
    private LocalDateTime deadline;
    private String notificationTemplate;
    private String messageNote;
    private Boolean electronicSignatureRequired;
    private Boolean paperSignatureRequired;
    private String qrStatus;
    private String status;
    private Long publishedBy;
    private String publishedByName;
    private LocalDateTime publishedTime;
    private LocalDateTime overdueNotificationTime;
    private String voidReason;
    private Boolean overdue;
    private Boolean signatureRequiredForCurrentRecipient;
    private Boolean scanRequiredForCurrentRecipient;
    private List<DocumentDistributionItemVO> items = new ArrayList<>();
    private List<DocumentDistributionRecipientVO> recipients = new ArrayList<>();
    private DocumentDistributionRecipientVO currentRecipient;
}
