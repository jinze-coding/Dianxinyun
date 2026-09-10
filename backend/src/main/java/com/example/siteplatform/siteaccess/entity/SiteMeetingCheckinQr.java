package com.example.siteplatform.siteaccess.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("site_meeting_checkin_qr")
public class SiteMeetingCheckinQr {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long invitationId;
    private Long projectId;
    private String sceneTokenHash;
    private String sceneTokenEncrypted;
    private String qrStatus;
    private Integer qrVersion;
    private LocalDateTime checkinStartTime;
    private LocalDateTime checkinEndTime;
    private Integer locationRadiusMeters;
    private Long createdById;
    private String createdByName;
    private Long updatedById;
    private String updatedByName;
    private Integer version;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
