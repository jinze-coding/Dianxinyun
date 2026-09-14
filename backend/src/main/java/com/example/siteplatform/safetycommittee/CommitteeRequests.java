package com.example.siteplatform.safetycommittee;

import jakarta.validation.constraints.*;
import java.util.List;

public final class CommitteeRequests {
    private CommitteeRequests() {}
    public record Create(@NotNull @Positive Long projectId,
            @NotBlank String category, @Size(max=2000) String conclusion,
            @Size(max=30) List<@NotNull @Positive Long> attachmentIds,
            @NotBlank @Pattern(regexp="[a-zA-Z0-9_-]{16,64}") String requestKey) {}
    public record Edit(@NotBlank String category, @Size(max=2000) String conclusion,
            @Size(max=30) List<@NotNull @Positive Long> attachmentIds,
            @NotNull @Min(1) Integer expectedVersion) {}
    public record Upload(@NotNull @Positive Long projectId,
            @NotBlank @Size(max=200) String fileName, @Min(1) @Max(524288000) long totalSize,
            @NotBlank @Pattern(regexp="[a-fA-F0-9]{64}") String sha256,
            @NotBlank @Pattern(regexp="[a-zA-Z0-9_-]{16,64}") String draftKey,
            @Positive Long targetRecordId) {}
}
