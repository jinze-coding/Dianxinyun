package com.example.siteplatform.document.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class DocumentIncomingBatchVO {
    private Long id;
    private Long projectId;
    private String incomingNo;
    private String sourceOrganization;
    private String senderName;
    private String sourceReferenceNo;
    private String receiveMethod;
    private LocalDateTime receivedAt;
    private Long receiverId;
    private String receiverName;
    private String remark;
    private String status;
    private Long publishedBy;
    private String publishedByName;
    private LocalDateTime publishedTime;
    private String voidReason;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private List<DocumentIncomingItemVO> items = new ArrayList<>();
    private Long distributionBatchId;
}
