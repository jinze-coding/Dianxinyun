package com.example.siteplatform.electricbox.vo;

import lombok.Data;

@Data
public class ElectricBoxImportRowVO {
    private Integer rowNumber;
    private String level;
    private String message;
    private String boxCode;
    private String boxName;
    private String installLocation;
    private String qrCode;
    private Long electricBoxId;
}
