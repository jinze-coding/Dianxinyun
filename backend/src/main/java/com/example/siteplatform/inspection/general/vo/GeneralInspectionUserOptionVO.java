package com.example.siteplatform.inspection.general.vo;

import lombok.Data;

@Data
public class GeneralInspectionUserOptionVO {
    private Long userId;
    private String userName;
    private Boolean canSubmit;
    private Boolean canRectify;
    private Boolean canReview;
}
