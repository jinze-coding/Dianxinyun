package com.example.siteplatform.siteaccess.material;

import jakarta.validation.constraints.*;

public final class MeetingMaterialRequests {
    private MeetingMaterialRequests() {}
    public record Upload(@NotBlank @Size(max=200) String fileName, @Min(1) @Max(1073741824L) long totalSize,
            @NotBlank @Pattern(regexp="[a-fA-F0-9]{64}") String sha256,
            @NotBlank @Size(max=200) String title, @NotBlank String category,
            @Size(max=1000) String description, Long materialId, Integer expectedVersion,
            @Size(max=500) String changeNote) {}
    public record Edit(@NotBlank @Size(max=200) String title, @NotBlank String category,
            @Size(max=1000) String description, @NotNull Integer expectedVersion) {}
    public record Publication(@NotNull Integer expectedVersion, Long versionId) {}
    public record State(@NotNull Integer expectedVersion, @NotNull Boolean withdrawn) {}
    public record Resolve(@NotBlank @Size(max=128) String inviteToken) {}
}
