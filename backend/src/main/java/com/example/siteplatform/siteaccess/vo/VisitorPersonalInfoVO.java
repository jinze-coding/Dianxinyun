package com.example.siteplatform.siteaccess.vo;

import lombok.Data;

@Data
public class VisitorPersonalInfoVO {
    private boolean rememberInfo;
    private boolean available;
    private String visitorCompany;
    private String contactName;
    private String contactPhone;
    private String travelMode;
    private String vehiclePlate;
}
