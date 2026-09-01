package com.example.siteplatform.siteaccess.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SiteMeetingVisitRegistrationVoidRequest {
    @NotBlank
    @Size(max = 300)
    private String reason;

    @NotNull
    @PositiveOrZero
    private Integer version;
}
