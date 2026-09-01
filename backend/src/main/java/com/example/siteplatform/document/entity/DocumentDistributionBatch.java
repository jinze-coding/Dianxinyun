package com.example.siteplatform.document.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("document_distribution_batch")
public class DocumentDistributionBatch {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long incomingBatchId;
    private String distributionNo;
    private LocalDateTime deadline;
    private String notificationTemplate;
    private String messageNote;
    private Integer electronicSignatureRequired;
    private Integer paperSignatureRequired;
    private String qrSceneDigest;
    private String qrSceneCiphertext;
    private String qrStatus;
    private String status;
    private Long publishedBy;
    private String publishedByName;
    private LocalDateTime publishedTime;
    private LocalDateTime overdueNotificationTime;
    private Long voidedBy;
    private String voidedByName;
    private LocalDateTime voidedTime;
    private String voidReason;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
