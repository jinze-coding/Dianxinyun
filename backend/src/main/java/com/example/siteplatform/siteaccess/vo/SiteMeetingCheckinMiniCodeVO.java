package com.example.siteplatform.siteaccess.vo;

import lombok.Data;

@Data
public class SiteMeetingCheckinMiniCodeVO {
    private Long invitationId;
    private Long checkinQrId;
    private String inviteNo;
    private String qrStatus;
    private Integer qrVersion;
    private String sceneCode;
    private String pagePath;
    private String codeType;
    private String imageMimeType;
    private String imageContent;
    private String hint;
}
