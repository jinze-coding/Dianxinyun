package com.example.siteplatform.device.constant;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeviceTypeTest {

    @Test
    void normalizesKnownChineseAndEnglishAliasesToCanonicalValues() {
        assertEquals(DeviceType.TOWER_CRANE, DeviceType.normalize("塔吊"));
        assertEquals(DeviceType.TOWER_CRANE, DeviceType.normalize("TOWER-CRANE"));
        assertEquals(DeviceType.ELEVATOR, DeviceType.normalize("施工电梯"));
        assertEquals(DeviceType.MONITOR, DeviceType.normalize("环境监测"));
        assertEquals(DeviceType.PUMP, DeviceType.normalize("泵车"));
        assertEquals(DeviceType.OTHER, DeviceType.normalize("其他"));
    }

    @Test
    void keepsUnknownExtensionTypeInsteadOfRejectingExistingFunctionality() {
        assertEquals("custom_sensor", DeviceType.normalize(" custom_sensor "));
        assertEquals(List.of("custom_sensor"), DeviceType.compatibleQueryValues(" custom_sensor "));
    }

    @Test
    void towerCraneQueryValuesCoverCanonicalAndLegacyStoredValues() {
        assertEquals(
                List.of("tower_crane", "塔吊", "塔式起重机"),
                DeviceType.compatibleQueryValues(DeviceType.TOWER_CRANE));
    }
}
