package com.example.siteplatform.project.constant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InspectionPermissionCodesTest {

    @Test
    void edgeExportIsAFirstClassInspectionPermission() {
        assertThat(InspectionPermissionCodes.ALL_CODES)
                .contains(InspectionPermissionCodes.EDGE_INSPECTION_EXPORT);
        assertThat(InspectionPermissionCodes.normalize(
                java.util.List.of(" edge_inspection_export ")))
                .containsExactly(InspectionPermissionCodes.EDGE_INSPECTION_EXPORT);
    }
}
