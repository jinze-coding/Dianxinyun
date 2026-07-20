package com.example.siteplatform.file.dto;

import lombok.Data;

@Data
public class FileUpdateRequest {
    private String fileName;
    private String fileType;
    private String status;
    private String remark;
}
