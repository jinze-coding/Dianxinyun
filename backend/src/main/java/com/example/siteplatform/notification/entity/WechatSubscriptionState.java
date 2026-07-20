package com.example.siteplatform.notification.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("wechat_subscription_state")
public class WechatSubscriptionState {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String appId;
    private String openid;
    private String templateCode;
    private String templateId;
    private Integer availableCount;
    private String status;
    private LocalDateTime lastAuthorizedTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
