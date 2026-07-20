package com.example.siteplatform.electricbox.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class ElectricBoxScopeRequest {
    private Boolean included;
    private LocalDate effectiveDate;
    private LocalDate endDate;
    private String reason;
}
