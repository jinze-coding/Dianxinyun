package com.example.siteplatform.camera.adapter;

import com.example.siteplatform.camera.entity.CameraResource;
import com.example.siteplatform.camera.mapper.CameraResourceMapper;
import com.example.siteplatform.common.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 海康视频适配器 - 模拟实现
 * 真实接入时请替换此实现
 */
@Component
public class MockHikvisionAdapter implements HikvisionAdapter {

    @Autowired
    private CameraResourceMapper cameraMapper;

    @Override
    public PlayUrlResult getPlayUrl(Long cameraId) {
        CameraResource camera = cameraMapper.selectById(cameraId);
        if (camera == null) {
            throw BusinessException.notFound("摄像头不存在");
        }

        if (camera.getOnlineStatus() == 0) {
            throw BusinessException.of(400, "摄像头离线");
        }

        // 生成模拟播放地址
        String playUrl = generateMockPlayUrl(camera);
        String expireTime = LocalDateTime.now().plusHours(24)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        return new PlayUrlResult(
                camera.getId(),
                camera.getCameraName(),
                playUrl,
                "FLV",
                expireTime
        );
    }

    private String generateMockPlayUrl(CameraResource camera) {
        // 这里生成模拟的播放地址
        // 实际生产环境中应调用海康威视SDK获取真实播放地址
        return String.format(
                "https://stream.example.com/live/%s.flv?token=mock-token-%d",
                camera.getCameraCode(),
                System.currentTimeMillis()
        );
    }
}
