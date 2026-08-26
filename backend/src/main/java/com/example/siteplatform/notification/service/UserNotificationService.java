package com.example.siteplatform.notification.service;

import com.example.siteplatform.notification.entity.UserNotification;
import com.example.siteplatform.notification.mapper.UserNotificationMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
public class UserNotificationService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final UserNotificationMapper mapper;

    public UserNotificationService(UserNotificationMapper mapper) {
        this.mapper = mapper;
    }

    /** Deduplication is enforced by the unique dedup_key index; a retry is a successful no-op. */
    public void notify(Long userId, Long projectId, String businessType, Long businessId,
                       String eventCode, String title, String summary, String dedupKey) {
        notify(userId, projectId, businessType, businessId, eventCode, title, summary, dedupKey,
                "SEAL_APPLICATION_DETAIL", "{\"applicationId\":" + businessId + "}");
    }

    /**
     * Generic persisted notification entry. Route codes remain server-controlled and are
     * allowlisted again by the work-center clients before navigation.
     */
    public void notify(Long userId, Long projectId, String businessType, Long businessId,
                       String eventCode, String title, String summary, String dedupKey,
                       String routeCode, String routeParamsJson) {
        if (userId == null) return;
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        UserNotification notification = new UserNotification();
        notification.setUserId(userId);
        notification.setProjectId(projectId);
        notification.setBusinessType(businessType);
        notification.setBusinessId(businessId);
        notification.setEventCode(eventCode);
        notification.setTitle(title);
        notification.setSummary(summary);
        notification.setRouteCode(routeCode);
        notification.setRouteParamsJson(routeParamsJson);
        notification.setIsRead(0);
        notification.setDedupKey(dedupKey);
        notification.setCreateTime(now);
        notification.setUpdateTime(now);
        int rows = mapper.insertIgnore(notification);
        if (rows != 0 && rows != 1) throw new IllegalStateException("站内通知写入结果异常");
    }
}
