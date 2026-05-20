package com.example.siteplatform.camera.adapter;

public interface HikvisionAdapter {
    /**
     * 获取摄像头播放地址
     * @param cameraId 摄像头ID
     * @return 播放地址信息
     */
    PlayUrlResult getPlayUrl(Long cameraId);

    /**
     * 播放地址结果
     */
    class PlayUrlResult {
        private Long cameraId;
        private String cameraName;
        private String playUrl;
        private String streamType;
        private String expireTime;

        public PlayUrlResult() {}

        public PlayUrlResult(Long cameraId, String cameraName, String playUrl, String streamType, String expireTime) {
            this.cameraId = cameraId;
            this.cameraName = cameraName;
            this.playUrl = playUrl;
            this.streamType = streamType;
            this.expireTime = expireTime;
        }

        // Getters and Setters
        public Long getCameraId() { return cameraId; }
        public void setCameraId(Long cameraId) { this.cameraId = cameraId; }
        public String getCameraName() { return cameraName; }
        public void setCameraName(String cameraName) { this.cameraName = cameraName; }
        public String getPlayUrl() { return playUrl; }
        public void setPlayUrl(String playUrl) { this.playUrl = playUrl; }
        public String getStreamType() { return streamType; }
        public void setStreamType(String streamType) { this.streamType = streamType; }
        public String getExpireTime() { return expireTime; }
        public void setExpireTime(String expireTime) { this.expireTime = expireTime; }
    }
}
