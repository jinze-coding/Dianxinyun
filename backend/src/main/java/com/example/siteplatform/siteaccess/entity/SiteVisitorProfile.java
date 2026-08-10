package com.example.siteplatform.siteaccess.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("site_visitor_profile")
public class SiteVisitorProfile {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String profileCode;
    private Long projectId;
    private String wechatAppId;
    private String ownerOpenidEncrypted;
    private String ownerOpenidHash;
    private String profileName;
    private String visitorCompany;
    private String contactName;
    private String contactPhoneEncrypted;
    private Integer visitorCount;
    private String travelMode;
    private String vehiclePlate;
    private String status;
    private LocalDateTime privacyAgreedTime;
    private LocalDateTime lastUsedTime;
    private Integer version;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
