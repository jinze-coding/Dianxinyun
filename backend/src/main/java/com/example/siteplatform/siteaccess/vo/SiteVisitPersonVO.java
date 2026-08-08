package com.example.siteplatform.siteaccess.vo;

import lombok.Data;

@Data
public class SiteVisitPersonVO {
    private Long id;
    private String personType;
    private String personName;
    private String idCard;
    private Integer sortOrder;
}
