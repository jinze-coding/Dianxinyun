package com.example.siteplatform.system.constant;

import org.junit.jupiter.api.Test;

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
}
