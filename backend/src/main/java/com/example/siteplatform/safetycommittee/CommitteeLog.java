package com.example.siteplatform.safetycommittee;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("safety_committee_log")
public class CommitteeLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long recordId;
    private Long operatorId;
    private String operatorName;
    private String action;
    private String beforeJson;
    private String afterJson;
    private java.time.LocalDateTime createTime;
}
