package com.example.siteplatform.inspection.general.vo;

import lombok.Data;

import java.util.List;

@Data
public class PublicGeneralInspectionMonthlyVO {
    private String projectShortName;
    private String pointCode;
    private String pointName;
    private String locationDesc;
    private String month;
    private Integer shouldCheckCount;
    private Integer checkedCount;
    private Integer missedCount;
    private Integer abnormalCount;
    private List<VersionSection> sections;

    @Data
    public static class VersionSection {
        private String versionLabel;
        private String templateName;
        private List<String> itemNames;
        private List<Row> rows;
    }

    @Data
    public static class Row {
        private String date;
        private String slotName;
        private String status;
        private String inspectorName;
        private String publicRemark;
        private List<String> results;
    }
}
