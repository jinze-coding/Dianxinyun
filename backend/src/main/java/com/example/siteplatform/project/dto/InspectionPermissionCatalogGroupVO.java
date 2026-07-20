package com.example.siteplatform.project.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InspectionPermissionCatalogGroupVO {
    private String groupCode;
    private String groupName;
    private List<InspectionPermissionCatalogItemVO> items;
}
