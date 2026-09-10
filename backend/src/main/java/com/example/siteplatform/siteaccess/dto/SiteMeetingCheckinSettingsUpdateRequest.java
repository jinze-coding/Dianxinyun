package com.example.siteplatform.siteaccess.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SiteMeetingCheckinSettingsUpdateRequest {
    @NotNull
    private LocalDateTime checkinStartTime;
    @NotNull
    private LocalDateTime checkinEndTime;
    @NotNull @Min(50) @Max(2000)
    private Integer locationRadiusMeters;
    @NotNull @Min(0)
    private Integer version;
}
