package com.example.siteplatform.device.constant;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeviceStatusTest {

    @Test
    void normalizesKnownEnglishAndChineseAliases() {
        assertEquals(DeviceStatus.RUNNING, DeviceStatus.normalize("运行中"));
        assertEquals(DeviceStatus.STOPPED, DeviceStatus.normalize("STOPPED"));
        assertEquals(DeviceStatus.ABNORMAL, DeviceStatus.normalize("告警"));
        assertEquals(DeviceStatus.ABNORMAL, DeviceStatus.normalize("danger"));
        assertEquals(DeviceStatus.MAINTENANCE, DeviceStatus.normalize("维护中"));
    }

    @Test
    void compatibleQueryValuesCoverCanonicalAndHistoricalValues() {
        assertEquals(
                List.of("running", "RUNNING", "运行中", "正常"),
                DeviceStatus.compatibleQueryValues("运行中"));
        assertTrue(DeviceStatus.compatibleQueryValues("ABNORMAL").contains("异常"));
        assertTrue(DeviceStatus.compatibleQueryValues("ABNORMAL").contains("ALARM"));
    }

    @Test
    void distinguishesSupportedAndUnknownValues() {
        assertTrue(DeviceStatus.isSupported("维修中"));
        assertFalse(DeviceStatus.isSupported("vendor_custom"));
        assertFalse(DeviceStatus.isSupported(" "));
        assertFalse(DeviceStatus.isSupported(null));
    }
}
