package com.example.siteplatform.registration.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegistrationStatusRequest {
    @Size(max = 128)
    private String statusToken;
    @Size(max = 128)
    private String queryToken;
    @Size(max = 128)
    private String statusQueryToken;

    public String resolvedToken() {
        if (statusToken != null && !statusToken.isBlank()) return statusToken;
        if (queryToken != null && !queryToken.isBlank()) return queryToken;
        return statusQueryToken;
    }
}
