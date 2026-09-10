package com.example.siteplatform.siteaccess.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PublicVisitorSessionVO {
    private String visitorSessionToken;
    private long expiresInSeconds;
    private VisitorPersonalInfoVO personalInfo;

    public PublicVisitorSessionVO(String token, long expires) {
        this.visitorSessionToken = token;
        this.expiresInSeconds = expires;
    }
}
