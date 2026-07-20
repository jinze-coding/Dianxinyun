package com.example.siteplatform.project.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class MiniProgramWorkspaceOverviewVO {
    private Integer onsitePersonCount;
    private Integer todayEntryCount;
    private Integer cameraTotal;
    private Integer onlineCameraCount;
    private Integer fileTotal;
    private Integer todayFileCount;
    private Integer deviceTotal;
    private Integer alarmDeviceCount;
    private Integer projectProgress;
    private String riskAlert;
    private List<CameraItem> cameras;
    private List<FileItem> recentFiles;
    private List<DeviceItem> devices;

    @Data
    public static class CameraItem {
        private Long id;
        private String name;
        private String code;
        private String area;
        private String type;
        private String streamUrl;
        private Boolean online;
    }

    @Data
    public static class FileItem {
        private Long id;
        private String name;
        private String type;
        private String status;
        private LocalDateTime createTime;
    }

    @Data
    public static class DeviceItem {
        private Long id;
        private String name;
        private String code;
        private String type;
        private String status;
        private LocalDateTime lastReport;
        private String remark;
    }
}
