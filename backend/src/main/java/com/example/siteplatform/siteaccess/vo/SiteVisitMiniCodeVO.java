package com.example.siteplatform.siteaccess.vo;

import lombok.Data;

@Data
public class SiteVisitMiniCodeVO {
    private Long invitationId;
    private String inviteNo;
    private String sceneCode;
    private String pagePath;
    private String codeType;
    private String imageMimeType;
    private String imageContent;
    private String hint;
}
