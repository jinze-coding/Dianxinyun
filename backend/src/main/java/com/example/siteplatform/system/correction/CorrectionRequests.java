package com.example.siteplatform.system.correction;

import jakarta.validation.constraints.*;
import java.util.*;

public final class CorrectionRequests {
    private CorrectionRequests() {}
    public record Preview(@NotBlank @Size(max=100) String expectedRevision,
                          @NotBlank @Size(max=500) String reason,
                          @NotNull Map<String,Object> changes,
                          Map<String,List<Map<String,Object>>> children,
                          Map<String,List<Long>> attachments,
                          boolean confirmLocation) {}
    public record Confirm(@NotBlank @Pattern(regexp="[a-f0-9-]{36}") String confirmationToken,
                          @NotBlank @Pattern(regexp="[a-zA-Z0-9-]{16,64}") String requestKey) {}
    public record Upload(@NotBlank String targetType,@NotNull @Positive Long targetId,@NotBlank String slotKey,
                         @NotBlank @Size(max=200) String fileName,@Min(1) @Max(1073741824L) long totalSize,
                         @NotBlank @Pattern(regexp="[a-fA-F0-9]{64}") String sha256) {}
}
