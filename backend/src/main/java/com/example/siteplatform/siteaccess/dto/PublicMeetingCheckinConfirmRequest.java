package com.example.siteplatform.siteaccess.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PublicMeetingCheckinConfirmRequest {
    @Valid @NotEmpty @Size(max = 50)
    private List<PersonSelection> attendees = new ArrayList<>();
    @Valid @NotNull
    private MeetingCheckinLocationRequest location;

    @Data
    public static class PersonSelection {
        @NotNull @Positive
        private Long personId;
        @Size(max = 50)
        private String completedName;
    }
}
