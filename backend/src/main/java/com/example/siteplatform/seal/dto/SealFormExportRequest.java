package com.example.siteplatform.seal.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.time.LocalDate;
import java.util.List;

@Data
public class SealFormExportRequest {
    @NotNull @Positive private Long projectId;
    @NotBlank @Size(max = 64) private String requestKey;
    @NotBlank @Pattern(regexp = "FILTER|SELECTED") private String selectionMode;
    @Size(max = 20) private String scope = "INITIATED";
    @Size(max = 30) private String status;
    @Size(max = 200) private String keyword;
    private LocalDate startDate;
    private LocalDate endDate;
    @Size(max = 500, message = "单次最多导出 500 份申请单，请缩小范围") private List<@NotNull @Positive Long> applicationIds;
}
