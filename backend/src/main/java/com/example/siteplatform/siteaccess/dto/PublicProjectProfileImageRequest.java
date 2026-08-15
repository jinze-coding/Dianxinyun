package com.example.siteplatform.siteaccess.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PublicProjectProfileImageRequest {
    @NotBlank
    @Size(max = 64)
    private String inviteToken;
    @NotNull
    @PositiveOrZero
    private Integer imageIndex;
}
