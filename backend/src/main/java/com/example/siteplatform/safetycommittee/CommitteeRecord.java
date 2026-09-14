package com.example.siteplatform.safetycommittee;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("safety_committee_record")
public class CommitteeRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long inspectorId;
    private String inspectorName;
    private java.time.LocalDateTime inspectedAt;
    private String category;
    private String conclusion;
    private Integer version;
    private String requestKey;
    private String requestHash;
    private java.time.LocalDateTime createTime;
    private java.time.LocalDateTime updateTime;
}
