package com.example.siteplatform.siteaccess.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PublicMeetingCheckinSessionVO {
    private String visitorSessionToken;
    private long expiresInSeconds;
    private String pageState;
    private PublicMeetingCheckinMeetingVO meeting;
    private String registrationNo;
    private String registrationSource;
    private List<PublicMeetingCheckinAttendeeVO> attendees = new ArrayList<>();
}
