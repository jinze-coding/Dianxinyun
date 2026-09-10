package com.example.siteplatform.siteaccess.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SiteMeetingAttendanceActionRequest {
    private LocalDateTime occurredTime;
    @NotBlank @Size(max = 300)
    private String reason;
    @NotNull @Min(0)
    private Integer version;
}
