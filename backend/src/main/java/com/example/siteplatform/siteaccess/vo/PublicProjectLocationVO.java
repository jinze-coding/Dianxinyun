package com.example.siteplatform.siteaccess.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PublicProjectLocationVO {
    private String address;
    private Boolean navigable;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String coordinateType;
    private Boolean routeImageAvailable;
}
