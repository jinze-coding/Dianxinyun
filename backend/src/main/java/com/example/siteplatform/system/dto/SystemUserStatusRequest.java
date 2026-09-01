package com.example.siteplatform.system.dto;

import lombok.Data;

@Data
public class SystemUserStatusRequest {
    private Object status;
    private String reason;
    private Boolean confirmResponsibilityRelease;

    public boolean isConfirmResponsibilityRelease() {
        return Boolean.TRUE.equals(confirmResponsibilityRelease);
    }
}
