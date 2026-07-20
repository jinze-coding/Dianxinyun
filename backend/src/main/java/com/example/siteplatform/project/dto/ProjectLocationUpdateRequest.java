package com.example.siteplatform.project.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProjectLocationUpdateRequest {
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String province;
    private String city;
    private String district;
    private String address;
    private String coordinateType;
}
