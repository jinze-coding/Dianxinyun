package com.example.siteplatform.siteaccess.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SiteMeetingCheckinRotateRequest {
    @NotNull @Min(0)
    private Integer version;
}
