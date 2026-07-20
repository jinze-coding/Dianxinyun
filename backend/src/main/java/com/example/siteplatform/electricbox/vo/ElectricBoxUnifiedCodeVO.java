package com.example.siteplatform.electricbox.vo;

import lombok.Data;

@Data
public class ElectricBoxUnifiedCodeVO {
    private Long electricBoxId;
    private String boxCode;
    private String boxName;
    private String sceneCode;
    private String publicCode;
    private String codeType;
    private String imageMimeType;
    private String imageContent;
    private String hint;
}
