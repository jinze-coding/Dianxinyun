package com.example.siteplatform.system.constant;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BusinessModuleCodesTest {

    @Test
    void sealMenuAndPermissionsStayInsideDocumentBusinessModule() {
        assertEquals(BusinessModuleCodes.DOCUMENT,
                BusinessModuleCodes.fromMenuCode("DOCUMENT_SEAL"));
        assertEquals(BusinessModuleCodes.DOCUMENT,
                BusinessModuleCodes.fromPermissionCode(SystemPermissionCodes.SEAL_VIEW));
        assertEquals(BusinessModuleCodes.DOCUMENT,
                BusinessModuleCodes.fromPermissionCode(SystemPermissionCodes.SEAL_MANAGE));
        assertEquals(BusinessModuleCodes.DOCUMENT,
                BusinessModuleCodes.fromPermissionCode(SystemPermissionCodes.SEAL_EXPORT));
    }

    @Test
    void fixedEdgePermissionsStayInsideInspectionBusinessModule() {
        assertEquals(BusinessModuleCodes.INSPECTION,
                BusinessModuleCodes.fromPermissionCode("EDGE_INSPECTION_VIEW"));
        assertEquals(BusinessModuleCodes.INSPECTION,
                BusinessModuleCodes.fromPermissionCode("EDGE_INSPECTION_RECTIFY"));
        assertEquals(BusinessModuleCodes.INSPECTION,
                BusinessModuleCodes.fromPermissionCode("EDGE_INSPECTION_EXPORT"));
    }

    @Test
    void formalBusinessPageMenusStayInsideTheirBusinessModules() {
        Map.ofEntries(
                Map.entry("SITE_VISITOR", BusinessModuleCodes.SITE_ACCESS),
                Map.entry("DOCUMENT_LIBRARY", BusinessModuleCodes.DOCUMENT),
                Map.entry("DOCUMENT_SEAL", BusinessModuleCodes.DOCUMENT),
                Map.entry("DOCUMENT_CIRCULATION", BusinessModuleCodes.DOCUMENT),
                Map.entry("DOCUMENT_RECYCLE", BusinessModuleCodes.DOCUMENT),
                Map.entry("INSPECTION_LEDGER", BusinessModuleCodes.INSPECTION),
                Map.entry("INSPECTION_RECORDS", BusinessModuleCodes.INSPECTION),
                Map.entry("INSPECTION_RECTIFICATIONS", BusinessModuleCodes.INSPECTION),
                Map.entry("INSPECTION_EDGE", BusinessModuleCodes.INSPECTION),
                Map.entry("QUALITY_ISSUES", BusinessModuleCodes.QUALITY),
                Map.entry("QUALITY_DOCUMENTS", BusinessModuleCodes.QUALITY)
        ).forEach((menuCode, moduleCode) ->
                assertEquals(moduleCode, BusinessModuleCodes.fromMenuCode(menuCode), menuCode));
    }
}
