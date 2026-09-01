package com.example.siteplatform.project.service;

import com.example.siteplatform.project.constant.InspectionPermissionCodes;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class InspectionPermissionTemplateCatalogTest {

    @Test
    void electricBoxAndEdgePermissionsHaveIndependentUnambiguousCatalogs() {
        var catalog = new InspectionPermissionTemplateService().permissionCatalog();

        assertThat(catalog).extracting(group -> group.getGroupCode())
                .containsExactly("ELECTRIC_BOX_INSPECTION", "EDGE_INSPECTION", "PERMISSION")
                .doesNotContain("BOX", "INSPECTION", "SUMMARY");
        assertThat(catalog)
                .filteredOn(group -> "ELECTRIC_BOX_INSPECTION".equals(group.getGroupCode()))
                .singleElement()
                .satisfies(group -> {
                    assertThat(group.getGroupName()).isEqualTo("电箱巡检");
                    assertThat(group.getItems()).extracting(item -> item.getCode())
                            .containsExactlyInAnyOrderElementsOf(Set.of(
                                    InspectionPermissionCodes.BOX_VIEW,
                                    InspectionPermissionCodes.BOX_MANAGE,
                                    InspectionPermissionCodes.BOX_QR_MANAGE,
                                    InspectionPermissionCodes.BOX_PUBLIC_ACCESS,
                                    InspectionPermissionCodes.INSPECTION_DAILY_SUBMIT,
                                    InspectionPermissionCodes.INSPECTION_RECORD_VIEW,
                                    InspectionPermissionCodes.SUMMARY_VIEW,
                                    InspectionPermissionCodes.SUMMARY_EXPORT));
                    assertThat(group.getItems()).allSatisfy(item ->
                            assertThat(item.getName()).contains("电箱"));
                });
        assertThat(catalog)
                .filteredOn(group -> "EDGE_INSPECTION".equals(group.getGroupCode()))
                .singleElement()
                .satisfies(group -> {
                    assertThat(group.getGroupName()).isEqualTo("临边巡检");
                    assertThat(group.getItems()).extracting(item -> item.getCode())
                            .containsExactlyInAnyOrder(
                                    InspectionPermissionCodes.EDGE_INSPECTION_VIEW,
                                    InspectionPermissionCodes.EDGE_INSPECTION_MANAGE,
                                    InspectionPermissionCodes.EDGE_INSPECTION_SUBMIT,
                                    InspectionPermissionCodes.EDGE_INSPECTION_RECTIFY,
                                    InspectionPermissionCodes.EDGE_INSPECTION_REVIEW,
                                    InspectionPermissionCodes.EDGE_INSPECTION_EXPORT);
                    assertThat(group.getItems()).allSatisfy(item ->
                            assertThat(item.getName()).contains("临边"));
                });
    }
}
