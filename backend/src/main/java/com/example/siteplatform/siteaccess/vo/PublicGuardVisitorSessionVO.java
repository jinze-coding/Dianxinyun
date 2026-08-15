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
}
