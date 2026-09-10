package com.example.siteplatform.siteaccess.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class PublicGuardMatchedPassVO {
    @JsonIgnore
    private Long sourceId;
    private String sourceType;
    private String sourceNo;
    private String subject;
    private String visitorCompany;
    private String contactName;
    private Integer visitorCount;
    private String travelMode;
    private String vehiclePlate;
    private LocalDateTime visitStartTime;
    private LocalDateTime validUntil;
    private List<Person> people;

    @Data
    public static class Person {
        private String personType;
        private String personCompany;
        private String personName;
    }
}
