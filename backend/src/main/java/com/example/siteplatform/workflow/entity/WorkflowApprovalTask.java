package com.example.siteplatform.workflow.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("workflow_approval_task")
public class WorkflowApprovalTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long instanceId;
    private String businessCode;
    private Long businessId;
    private Long projectId;
    private Long assigneeUserId;
    private String assigneeName;
    private String status;
    private String assignmentSource;
    private Long transferredFromTaskId;
    private Long decisionUserId;
    private String decisionUserName;
    private String decisionOpinion;
    private LocalDateTime decisionTime;
    private Integer version;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
