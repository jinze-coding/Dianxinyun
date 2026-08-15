package com.example.siteplatform.project.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PublicProjectProfileImageVO {
    private Integer imageIndex;
    private String mimeType;
    private Boolean cover;
}
