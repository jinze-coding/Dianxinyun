package com.example.siteplatform.document.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class DocumentDistributionRecipientVO {
    private Long id;
    private Long userId;
    private String username;
    private String realName;
    private String phone;
    private String roleNames;
    private String memberStatus;
    private String channel;
    private String status;
    private Boolean mandatory;
    private LocalDateTime notifiedTime;
    private LocalDateTime reminderSentTime;
    private LocalDateTime confirmedTime;
    private Long signatureFileId;
    private String disputeNote;
    private LocalDateTime disputeTime;
    private List<DocumentDistributionItemVO> items = new ArrayList<>();
}
