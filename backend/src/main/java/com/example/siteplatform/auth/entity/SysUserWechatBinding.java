package com.example.siteplatform.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user_wechat_binding")
public class SysUserWechatBinding {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String appId;
    private String openid;
    private String unionid;
    private String phone;
    private String status;
    private LocalDateTime bindTime;
    private LocalDateTime lastLoginTime;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
