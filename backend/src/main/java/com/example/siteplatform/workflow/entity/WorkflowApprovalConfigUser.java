package com.example.siteplatform.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("workflow_approval_config_user")
public class WorkflowApprovalConfigUser {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long configId;
    private Long projectId;
    private Long userId;
    private String assignmentType;
    private Integer sortOrder;
    private LocalDateTime createTime;
}
