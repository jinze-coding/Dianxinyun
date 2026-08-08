package com.example.siteplatform.system.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeletionImpactVO {
    private String targetType;
    private Long targetId;
    private String targetName;
    private boolean typedConfirmationRequired;
    private long totalAssociatedCount;
    private long fileCount;
    private long fileBytes;
    private List<Item> items = new ArrayList<>();
    private String confirmationToken;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private String code;
        private String label;
        private long count;
    }
}
