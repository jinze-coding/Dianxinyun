package com.example.siteplatform.device.adapter;

import java.util.Map;

public interface TowerCraneAdapter {
    /**
     * 获取塔吊实时数据
     * @param deviceId 设备ID
     * @return 塔吊实时数据
     */
    TowerCraneData getRealTimeData(Long deviceId);

    /**
     * 塔吊实时数据
     */
    class TowerCraneData {
        private Long deviceId;
        private String deviceName;
        private String status;
        private Double height;
        private Double load;
        private Double windSpeed;
        private String lastReport;

        public TowerCraneData() {}

        public TowerCraneData(Long deviceId, String deviceName, String status,
                              Double height, Double load, Double windSpeed, String lastReport) {
            this.deviceId = deviceId;
            this.deviceName = deviceName;
            this.status = status;
            this.height = height;
            this.load = load;
            this.windSpeed = windSpeed;
            this.lastReport = lastReport;
        }

        // Getters and Setters
        public Long getDeviceId() { return deviceId; }
        public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }
        public String getDeviceName() { return deviceName; }
        public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Double getHeight() { return height; }
        public void setHeight(Double height) { this.height = height; }
        public Double getLoad() { return load; }
        public void setLoad(Double load) { this.load = load; }
        public Double getWindSpeed() { return windSpeed; }
        public void setWindSpeed(Double windSpeed) { this.windSpeed = windSpeed; }
        public String getLastReport() { return lastReport; }
        public void setLastReport(String lastReport) { this.lastReport = lastReport; }
    }
}
