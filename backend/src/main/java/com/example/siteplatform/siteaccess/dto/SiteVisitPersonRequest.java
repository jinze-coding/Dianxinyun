package com.example.siteplatform.siteaccess.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SiteVisitPersonRequest {
    @Size(max = 200)
    private String personCompany;

    @Size(max = 50)
    private String personName;

    @Size(max = 11)
    private String personPhone;
}
