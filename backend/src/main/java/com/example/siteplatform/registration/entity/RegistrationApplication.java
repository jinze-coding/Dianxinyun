package com.example.siteplatform.registration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("registration_application")
public class RegistrationApplication {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String passwordHash;
    private String realName;
    private String phone;
    private String email;
    private String applicationReason;
    private String desiredProjectIds;
    private String desiredProjectText;
    private String sourceType;
    private String registrationMode;
    private String phoneVerificationType;
    private String appId;
    private String openid;
    private String unionid;
    private String status;
    private String statusTokenHash;
    private Long createdUserId;
    private Long reviewerId;
    private String reviewerName;
    private String reviewComment;
    private LocalDateTime reviewTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
