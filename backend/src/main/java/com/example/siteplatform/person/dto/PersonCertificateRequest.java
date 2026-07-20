package com.example.siteplatform.person.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class PersonCertificateRequest {
    private String certificateType;
    private String certificateNo;
    private LocalDate issueDate;
    private LocalDate expiryDate;
    private Long fileId;
    private String remark;
}
