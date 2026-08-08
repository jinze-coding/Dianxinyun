package com.example.siteplatform.siteaccess.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("site_visit_invitation")
public class SiteVisitInvitation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String inviteNo;
    private String tokenHash;
    private String tokenEncrypted;
    private String status;
    private LocalDateTime visitStartTime;
    private LocalDateTime visitEndTime;
    private String purpose;
    private String visitLocation;
    private Long hostUserId;
    private String hostName;
    private String hostPhoneEncrypted;
    private String internalRemark;
    private String visitorCompany;
    private String contactName;
    private String contactPhoneEncrypted;
    private Integer visitorCount;
    private String travelMode;
    private String vehiclePlate;
    private String visitorRemark;
    private LocalDateTime privacyAgreedTime;
    private LocalDateTime submittedTime;
    private String voidReason;
    private Long voidedById;
    private String voidedByName;
    private LocalDateTime voidedTime;
    private Long createdById;
    private String createdByName;
    private Integer version;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
