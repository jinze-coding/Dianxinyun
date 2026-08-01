package com.example.siteplatform.device.adapter;

import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.device.entity.DeviceInfo;
import com.example.siteplatform.device.mapper.DeviceInfoMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MockTowerCraneAdapterTest {

    @Mock private DeviceInfoMapper deviceMapper;

    private MockTowerCraneAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new MockTowerCraneAdapter();
        ReflectionTestUtils.setField(adapter, "deviceMapper", deviceMapper);
    }

    @Test
    void acceptsLegacyChineseTowerCraneType() {
        DeviceInfo device = device("塔吊");
        when(deviceMapper.selectById(7L)).thenReturn(device);

        TowerCraneAdapter.TowerCraneData result = adapter.getRealTimeData(7L);

        assertEquals(7L, result.getDeviceId());
        assertEquals("测试塔吊", result.getDeviceName());
    }

    @Test
    void rejectsNonTowerCraneDevice() {
        when(deviceMapper.selectById(7L)).thenReturn(device("elevator"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> adapter.getRealTimeData(7L));

        assertEquals(400, error.getCode());
    }

    private DeviceInfo device(String type) {
        DeviceInfo device = new DeviceInfo();
        device.setId(7L);
        device.setDeviceName("测试塔吊");
        device.setDeviceType(type);
        device.setHeight("60m");
        device.setStatus("running");
        return device;
    }
}
