package com.example.siteplatform.seal.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("seal_application")
public class SealApplication {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String applicationNo;
    private String requestKey;
    private Long sourceApplicationId;
    private Long projectId;
    private Long sealId;
    private String sealName;
    private String companyName;
    private String departmentName;
    private String purpose;
    private Long applicantId;
    private String applicantName;
    private String applicantPhone;
    private LocalDate applicationDate;
    private String status;
    private Long approvalInstanceId;
    private LocalDateTime submitTime;
    private Long approverId;
    private String approverName;
    private String approvalOpinion;
    private LocalDateTime approvalTime;
    private Integer version;
    @TableLogic
    private Integer deleted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
