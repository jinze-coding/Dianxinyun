package com.example.siteplatform.person.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class PersonnelMobileSummaryVO {
    private Integer onsiteCount;
    private Integer todayEntryCount;
    private Integer pendingEducationCount;
    private Integer certificateWarningCount;
    private Boolean canManage;
    private List<PersonItem> people;
    private List<TrainingItem> trainings;

    @Data
    public static class PersonItem {
        private Long id;
        private String name;
        private String gender;
        private String maskedIdcard;
        private String maskedPhone;
        private String idcard;
        private String phone;
        private String team;
        private String trade;
        private LocalDateTime entryTime;
        private String status;
        private String statusLabel;
        private String remark;
        private Integer certificateCount;
        private Integer certificateWarningCount;
    }

    @Data
    public static class TrainingItem {
        private Long id;
        private String title;
        private String type;
        private LocalDateTime trainingTime;
        private String place;
        private String trainer;
        private String status;
        private String statusLabel;
        private Integer personCount;
    }
}
