package com.example.siteplatform.document.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("document_circulation_event")
public class DocumentCirculationEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long incomingBatchId;
    private Long distributionBatchId;
    private Long recipientId;
    private Long documentId;
    private Long versionId;
    private Long userId;
    private String userName;
    private String eventType;
    private String channel;
    private String eventResult;
    private String eventSummary;
    private String eventDataJson;
    private String clientIp;
    private LocalDateTime createTime;
}
