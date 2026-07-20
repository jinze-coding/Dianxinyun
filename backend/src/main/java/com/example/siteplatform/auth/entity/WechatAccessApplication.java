package com.example.siteplatform.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("wechat_access_application")
public class WechatAccessApplication {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String appId;
    private String openid;
    private String phone;
    private String realName;
    private Long projectId;
    private String sourceType;
    private Long sourceId;
    private Long matchedUserId;
    private String status;
    private Long reviewerId;
    private String reviewerName;
    private String reviewComment;
    private LocalDateTime reviewTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
