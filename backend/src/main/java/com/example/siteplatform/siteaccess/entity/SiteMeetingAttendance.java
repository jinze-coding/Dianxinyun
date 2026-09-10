package com.example.siteplatform.siteaccess.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("site_meeting_attendance")
public class SiteMeetingAttendance {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long invitationId;
    private Long registrationId;
    private Long personId;
    private Long projectId;
    private Long checkinQrId;
    private String status;
    private String checkinMethod;
    private LocalDateTime checkinTime;
    private String wechatAppId;
    private String checkinIdentityHash;
    private String locationResult;
    private Integer distanceMeters;
    private Integer accuracyMeters;
    private Integer referenceProjectVersion;
    private Long revokedById;
    private String revokedByName;
    private LocalDateTime revokedTime;
    private String revokeReason;
    private Integer version;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
