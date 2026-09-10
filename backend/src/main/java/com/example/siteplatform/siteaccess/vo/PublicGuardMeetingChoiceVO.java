package com.example.siteplatform.siteaccess.vo;

import java.time.LocalDateTime;

public record PublicGuardMeetingChoiceVO(String choiceToken, String title,
        LocalDateTime visitStartTime, LocalDateTime visitEndTime, String location, boolean registered) {}
