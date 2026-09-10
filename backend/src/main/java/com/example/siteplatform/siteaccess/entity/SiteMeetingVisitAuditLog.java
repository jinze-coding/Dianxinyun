package com.example.siteplatform.siteaccess.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("site_meeting_visit_audit_log")
public class SiteMeetingVisitAuditLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long registrationId;
    private Long personId;
    private Long checkinQrId;
    private Long invitationId;
    private Long projectId;
    private String actionType;
    private Long operatorId;
    private String operatorName;
    private String beforeSnapshotEncrypted;
    private String afterSnapshotEncrypted;
    private String comment;
    private LocalDateTime createTime;
}
