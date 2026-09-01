package com.example.siteplatform.document.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("document_distribution_recipient")
public class DocumentDistributionRecipient {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long batchId;
    private Long projectId;
    private Long userId;
    private String usernameSnapshot;
    private String realNameSnapshot;
    private String phoneSnapshot;
    private String roleNamesSnapshot;
    private String memberStatusSnapshot;
    private String channel;
    private String status;
    private Integer mandatory;
    private LocalDateTime notifiedTime;
    private LocalDateTime reminderSentTime;
    private LocalDateTime confirmedTime;
    private Long signatureFileId;
    private String disputeNote;
    private LocalDateTime disputeTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
