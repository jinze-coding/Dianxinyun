package com.example.siteplatform.file.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FileActivityVO {
    private Long id;
    private Long fileId;
    private String fileName;
    private String operationType;
    private String operationLabel;
    private String operationDesc;
    private Long operatorId;
    private String operatorName;
    private LocalDateTime createTime;
}
