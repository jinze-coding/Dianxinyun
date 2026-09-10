package com.example.siteplatform.siteaccess.vo;

import lombok.Data;

@Data
public class PublicGuardVisitorSessionVO {
    private String visitorSessionToken;
    private long expiresInSeconds;
    private String pageState;
    private String projectName;
    private String projectShortName;
    private PublicGuardVisitPassVO registration;
    private java.time.LocalDateTime serverTime;
    private java.util.List<PublicGuardMatchedPassVO> matchedPasses = java.util.List.of();
    private VisitorPersonalInfoVO personalInfo;
}
