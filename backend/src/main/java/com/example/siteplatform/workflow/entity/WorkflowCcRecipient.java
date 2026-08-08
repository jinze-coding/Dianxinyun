package com.example.siteplatform.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("workflow_cc_recipient")
public class WorkflowCcRecipient {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String businessCode;
    private Long businessId;
    private Long projectId;
    private Long userId;
    private String userName;
    private String source;
    private LocalDateTime readTime;
    private LocalDateTime createTime;
}
