package com.example.siteplatform.inspection.general.vo;

import lombok.Data;

import java.util.List;

@Data
public class EdgeInspectionPointTypeVO {
    private String code;
    private String name;
    private String templateName;
    private List<Item> items;

    @Data
    public static class Item {
        private String itemKey;
        private String itemName;
        private String guidance;
        private String standardReference;
        private Integer sortOrder;
    }
}
