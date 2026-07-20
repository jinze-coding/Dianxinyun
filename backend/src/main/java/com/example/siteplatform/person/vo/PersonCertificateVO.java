package com.example.siteplatform.person.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class PersonCertificateVO {
    private Long id;
    private Long projectId;
    private Long personId;
    private String certificateType;
    private String certificateNo;
    private LocalDate issueDate;
    private LocalDate expiryDate;
    private Long fileId;
    private String fileName;
    private String remark;
    private String warningLevel;
    private String warningLabel;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
