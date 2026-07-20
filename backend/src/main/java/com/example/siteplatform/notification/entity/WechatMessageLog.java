package com.example.siteplatform.notification.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("wechat_message_log")
public class WechatMessageLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String openid;
    private String templateCode;
    private String businessType;
    private Long businessId;
    private String status;
    private String requestPayload;
    private String responseCode;
    private String responseMessage;
    private Integer retryCount;
    private LocalDateTime sentTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
