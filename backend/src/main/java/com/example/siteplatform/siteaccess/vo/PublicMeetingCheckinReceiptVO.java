package com.example.siteplatform.siteaccess.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class PublicMeetingCheckinReceiptVO {
    private String registrationNo;
    private String registrationSource;
    private Integer checkedInCount;
    private Integer pendingCount;
    private String locationResult;
    private Integer distanceMeters;
    private Integer accuracyMeters;
    private LocalDateTime serverTime;
    private List<PublicMeetingCheckinAttendeeVO> attendees = new ArrayList<>();
}
