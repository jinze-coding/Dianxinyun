package com.example.siteplatform.siteaccess.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class MeetingCheckinLocationRequest {
    @NotNull
    private Boolean locationAvailable;
    @DecimalMin("-90") @DecimalMax("90")
    private BigDecimal latitude;
    @DecimalMin("-180") @DecimalMax("180")
    private BigDecimal longitude;
    @Min(0) @Max(100000)
    private Integer accuracyMeters;
}
