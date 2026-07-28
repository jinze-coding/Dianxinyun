package com.example.siteplatform.registration.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RegistrationSubmitResponse {
    private Long applicationId;
    private String status;
    private String statusToken;

    public String getQueryToken() {
        return statusToken;
    }

    public String getStatusQueryToken() {
        return statusToken;
    }
}
