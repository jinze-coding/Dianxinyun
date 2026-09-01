package com.example.siteplatform.document.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class DocumentUploadSessionVO {
    private String sessionId;
    private String fileName;
    private Long totalSize;
    private Integer chunkSize;
    private Integer chunkCount;
    private List<Integer> uploadedChunks = new ArrayList<>();
    private Long fileResourceId;
    private LocalDateTime expiresAt;
}
