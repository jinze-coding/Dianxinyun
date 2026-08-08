package com.example.siteplatform.seal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("seal_application_log")
public class SealApplicationLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long applicationId;
    private Long projectId;
    private String actionCode;
    private String fromStatus;
    private String toStatus;
    private Long operatorId;
    private String operatorName;
    private String opinion;
    private String description;
    private String ipAddress;
    private LocalDateTime createTime;
}
