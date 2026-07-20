package com.example.siteplatform.log.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_operation_log")
public class OperationLog {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String username;
    private String operationType;
    private String operationDesc;
    private String businessType;
    private Long businessId;
    private String ipAddress;
    private LocalDateTime createTime;
}
