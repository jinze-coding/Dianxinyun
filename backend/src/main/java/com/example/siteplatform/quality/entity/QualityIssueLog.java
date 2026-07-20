package com.example.siteplatform.quality.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("quality_issue_log")
public class QualityIssueLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long issueId;
    private Long projectId;
    private String actionType;
    private String fromStatus;
    private String toStatus;
    private Long operatorId;
    private String operatorName;
    private String comment;
    private String photoFileIds;
    private LocalDateTime createTime;
}
