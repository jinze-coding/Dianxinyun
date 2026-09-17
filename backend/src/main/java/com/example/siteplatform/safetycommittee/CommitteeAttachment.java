package com.example.siteplatform.safetycommittee;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("safety_committee_attachment")
public class CommitteeAttachment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long recordId;
    private Long targetRecordId;
    private String draftKey;
    private Long uploaderId;
    private Long fileId;
    private String uploadKey;
    private String status;
    private Integer sortOrder;
    private String previewKind;
    private String previewStatus;
    private Integer rotationDegrees;
    private Integer rotationVersion;
    private Long previewFileId;
    private Integer attempts;
    private String workerId;
    private java.time.LocalDateTime leaseUntil;
    private String failureMessage;
    private java.time.LocalDateTime expiresAt;
    private java.time.LocalDateTime createTime;
    private java.time.LocalDateTime updateTime;
}
