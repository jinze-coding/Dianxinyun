package com.example.siteplatform.device.adapter;

import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.device.constant.DeviceStatus;
import com.example.siteplatform.device.constant.DeviceType;
import com.example.siteplatform.device.entity.DeviceInfo;
import com.example.siteplatform.device.mapper.DeviceInfoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;

/**
 * 塔吊适配器 - 模拟实现
 * 真实接入时请替换此实现
 */
@Component
public class MockTowerCraneAdapter implements TowerCraneAdapter {

    @Autowired
    private DeviceInfoMapper deviceMapper;

    private final Random random = new Random();

    @Override
    public TowerCraneData getRealTimeData(Long deviceId) {
        DeviceInfo device = deviceMapper.selectById(deviceId);
        if (device == null) {
            throw BusinessException.notFound("设备不存在");
        }

        if (!DeviceType.TOWER_CRANE.equals(DeviceType.normalize(device.getDeviceType()))) {
            throw new BusinessException(400, "该设备不是塔吊设备");
        }

        // 生成模拟数据
        Double height = parseDouble(device.getHeight()) + random.nextDouble() * 2;
        Double load = random.nextDouble() * 5;
        Double windSpeed = random.nextDouble() * 10;
        String lastReport = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        return new TowerCraneData(
                device.getId(),
                device.getDeviceName(),
                DeviceStatus.normalize(device.getStatus()),
                Math.round(height * 100) / 100.0,
                Math.round(load * 100) / 100.0,
                Math.round(windSpeed * 100) / 100.0,
                lastReport
        );
    }

    private Double parseDouble(String value) {
        if (value == null || value.isEmpty()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(value.replace("m", "").replace("M", ""));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
