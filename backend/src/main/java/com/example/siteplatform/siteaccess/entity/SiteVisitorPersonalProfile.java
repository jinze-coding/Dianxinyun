package com.example.siteplatform.siteaccess.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("site_visitor_personal_profile")
public class SiteVisitorPersonalProfile {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String wechatAppId;
    private String ownerIdentityHash;
    private Boolean rememberEnabled;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String visitorCompany;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String contactName;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String contactPhoneEncrypted;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String travelMode;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String vehiclePlate;
    private LocalDateTime privacyAgreedTime;
    private LocalDateTime lastSubmittedTime;
    private Integer version;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
