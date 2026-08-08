package com.example.siteplatform.registration.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegistrationProjectOptionVO {
    private Long projectId;
    private String projectName;
    private String shortName;
    private String area;
    private Boolean available;
}
