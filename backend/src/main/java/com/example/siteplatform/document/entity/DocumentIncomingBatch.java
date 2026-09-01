package com.example.siteplatform.document.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("document_incoming_batch")
public class DocumentIncomingBatch {
    @TableId(type = IdType.AUTO)
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
    private Long voidedBy;
    private String voidedByName;
    private LocalDateTime voidedTime;
    private String voidReason;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
