package com.example.siteplatform.siteaccess.vo;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class MeetingAttendanceScreenVO {
    private Long invitationId;
    private Long projectId;
    private String title;
    private String inviteNo;
    private String meetingStatus;
    private LocalDateTime visitStartTime;
    private LocalDateTime visitEndTime;
    private LocalDateTime serverTime;
    private boolean canShowQr;
    private String qrStatus;
    private Integer qrVersion;
    private Long reservedPersonCount;
    private Long reservedCheckedInCount;
    private Long reservedPendingCount;
    private Long walkInCheckedInCount;
    private Long totalCheckedInCount;
    private long total;
    private int pageNo;
    private int pageSize;
    private List<Person> records;

    @Data
    public static class Person {
        private Long personId;
        private String personName;
        private String personCompany;
        private LocalDateTime checkinTime;
    }
}
