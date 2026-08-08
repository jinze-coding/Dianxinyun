package com.example.siteplatform.seal.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class SealApplicationVO {
    private Long id;
    private String applicationNo;
    private String requestKey;
    private Long sourceApplicationId;
    private Long projectId;
    private String projectName;
    private String companyName;
    private String departmentName;
    private Long sealId;
    private String sealName;
    private String purpose;
    private String status;
    private String statusLabel;
    private Long applicantId;
    private String applicantName;
    private String applicantDepartmentName;
    private String applicantPhone;
    private LocalDate applicationDate;
    private LocalDateTime submitTime;
    private Long approverId;
    private String approverName;
    private String approvalOpinion;
    private LocalDateTime approvalTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private List<SealApplicationItemVO> items = new ArrayList<>();
    private Integer itemCount;
    private Integer totalCopies;
    private List<SealApplicationFileVO> files = new ArrayList<>();
    private List<SealCcRecipientVO> ccRecipients = new ArrayList<>();
    private List<SealApplicationLogVO> logs = new ArrayList<>();
    private Boolean canEdit;
    private Boolean canSubmit;
    private Boolean canApprove;
    private Boolean canReject;
    private Boolean canTransfer;
    private Boolean canCancel;
    private Boolean canUploadStampedResult;
    private Boolean canArchive;
}
