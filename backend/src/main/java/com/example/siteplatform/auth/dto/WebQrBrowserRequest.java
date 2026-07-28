package com.example.siteplatform.auth.dto;

import lombok.Data;

@Data
public class WebQrBrowserRequest {
    private String browserSecret;
    private String browserVerifier;
    private String exchangeCode;

    public String resolvedBrowserSecret() {
        return browserSecret != null && !browserSecret.isBlank() ? browserSecret : browserVerifier;
    }
}
