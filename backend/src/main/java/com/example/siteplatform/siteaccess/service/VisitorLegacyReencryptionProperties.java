package com.example.siteplatform.siteaccess.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("site-access.legacy-reencryption")
class VisitorLegacyReencryptionProperties {
    public enum Mode { VERIFY, APPLY }

    private Mode mode;
    private Long expectedTotal;
    private Long expectedCurrent;
    private Long expectedLegacy;
    private Long expectedTokenMatches;
    private String expectedFingerprint;
    private String confirmation;

    public Mode getMode() { return mode; }
    public void setMode(Mode mode) { this.mode = mode; }
    public Long getExpectedTotal() { return expectedTotal; }
    public void setExpectedTotal(Long expectedTotal) { this.expectedTotal = expectedTotal; }
    public Long getExpectedCurrent() { return expectedCurrent; }
    public void setExpectedCurrent(Long expectedCurrent) { this.expectedCurrent = expectedCurrent; }
    public Long getExpectedLegacy() { return expectedLegacy; }
    public void setExpectedLegacy(Long expectedLegacy) { this.expectedLegacy = expectedLegacy; }
    public Long getExpectedTokenMatches() { return expectedTokenMatches; }
    public void setExpectedTokenMatches(Long expectedTokenMatches) { this.expectedTokenMatches = expectedTokenMatches; }
    public String getExpectedFingerprint() { return expectedFingerprint; }
    public void setExpectedFingerprint(String expectedFingerprint) { this.expectedFingerprint = expectedFingerprint; }
    public String getConfirmation() { return confirmation; }
    public void setConfirmation(String confirmation) { this.confirmation = confirmation; }
}
