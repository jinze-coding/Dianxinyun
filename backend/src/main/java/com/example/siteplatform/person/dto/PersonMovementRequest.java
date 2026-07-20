package com.example.siteplatform.person.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PersonMovementRequest {
    private LocalDateTime occurredAt;
    private String remark;
}
