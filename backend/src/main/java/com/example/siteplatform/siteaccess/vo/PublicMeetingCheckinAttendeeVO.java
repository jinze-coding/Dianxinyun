package com.example.siteplatform.siteaccess.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PublicMeetingCheckinAttendeeVO {
    private Long personId;
    private String personType;
    private String personCompany;
    private String personName;
    private Integer sortOrder;
    private String attendanceStatus;
    private LocalDateTime checkinTime;
}
