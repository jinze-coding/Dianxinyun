package com.example.siteplatform.siteaccess.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class SiteVisitorProfileVO {
    private Long id;
    private String profileCode;
    private Long projectId;
    private String profileName;
    private String visitorCompany;
    private String contactName;
    private String contactPhone;
    private String maskedContactPhone;
    private Integer visitorCount;
    private String travelMode;
    private String vehiclePlate;
    private String status;
    private Integer version;
    private LocalDateTime lastUsedTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private List<SiteVisitorProfilePersonVO> people = new ArrayList<>();
}
