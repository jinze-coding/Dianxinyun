package com.example.siteplatform.seal.vo;

import com.example.siteplatform.seal.entity.SealFormExportJob;
import java.time.LocalDateTime;

public record SealFormExportJobVO(Long id, Long projectId, String status, Integer applicationCount,
                                  Integer processedCount, Integer pageCount, String fileName,
                                  String errorMessage, LocalDateTime createTime, LocalDateTime expiresTime) {
    public static SealFormExportJobVO from(SealFormExportJob job) {
        return new SealFormExportJobVO(job.getId(), job.getProjectId(), job.getStatus(), job.getApplicationCount(),
                job.getProcessedCount(), job.getPageCount(), job.getFileName(), job.getErrorMessage(),
                job.getCreateTime(), job.getExpiresTime());
    }
}
