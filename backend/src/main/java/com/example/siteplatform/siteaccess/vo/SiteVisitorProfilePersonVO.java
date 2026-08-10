package com.example.siteplatform.siteaccess.vo;

import lombok.Data;

@Data
public class SiteVisitorProfilePersonVO {
    private String personType;
    private String personName;
    private String idCard;
    private String maskedIdCard;
    private Integer sortOrder;
}
