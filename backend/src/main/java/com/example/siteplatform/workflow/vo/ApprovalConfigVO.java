package com.example.siteplatform.workflow.vo;

import com.example.siteplatform.seal.vo.SealUserOptionVO;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class ApprovalConfigVO {
    private Long id;
    private String businessCode;
    private Long projectId;
    private String projectName;
    private Long sealId;
    private String sealName;
    private String approvalMode;
    private Boolean enabled;
    private Integer configVersion;
    private List<Long> approverUserIds = new ArrayList<>();
    private List<Long> defaultCcUserIds = new ArrayList<>();
    private List<SealUserOptionVO> approvers = new ArrayList<>();
    private List<SealUserOptionVO> defaultCcUsers = new ArrayList<>();
    private LocalDateTime updateTime;
}
