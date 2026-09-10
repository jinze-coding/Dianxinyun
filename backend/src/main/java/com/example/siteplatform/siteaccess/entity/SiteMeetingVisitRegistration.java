package com.example.siteplatform.siteaccess.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("site_meeting_visit_registration")
public class SiteMeetingVisitRegistration {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String registrationNo;
    private Long invitationId;
    private Long projectId;
    private String wechatAppId;
    private String visitorIdentityHash;
    private String status;
    private String registrationSource;
    private String visitorCompany;
    private String contactName;
    private String contactPhoneEncrypted;
    private Integer visitorCount;
    private String travelMode;
    private String vehiclePlate;
    private String visitorRemark;
    private Long sourceProfileId;
    private LocalDateTime privacyAgreedTime;
    private LocalDateTime registeredTime;
    private String voidReason;
    private Long voidedById;
    private String voidedByName;
    private LocalDateTime voidedTime;
    private Integer version;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
