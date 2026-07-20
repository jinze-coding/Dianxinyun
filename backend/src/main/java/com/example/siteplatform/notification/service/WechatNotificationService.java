package com.example.siteplatform.notification.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.siteplatform.auth.entity.SysUserWechatBinding;
import com.example.siteplatform.auth.mapper.SysUserWechatBindingMapper;
import com.example.siteplatform.notification.dto.WechatSubscriptionRequest;
import com.example.siteplatform.notification.entity.WechatMessageLog;
import com.example.siteplatform.notification.entity.WechatSubscriptionState;
import com.example.siteplatform.notification.mapper.WechatMessageLogMapper;
import com.example.siteplatform.notification.mapper.WechatSubscriptionStateMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class WechatNotificationService {

    private final SysUserWechatBindingMapper bindingMapper;
    private final WechatSubscriptionStateMapper stateMapper;
    private final WechatMessageLogMapper logMapper;
    @Value("${wechat.mini-program.app-id:touristappid}") private String appId;

    public WechatNotificationService(SysUserWechatBindingMapper bindingMapper,
                                     WechatSubscriptionStateMapper stateMapper,
                                     WechatMessageLogMapper logMapper) {
        this.bindingMapper = bindingMapper;
        this.stateMapper = stateMapper;
        this.logMapper = logMapper;
    }

    @Transactional
    public void recordAuthorization(Long userId, WechatSubscriptionRequest request) {
        SysUserWechatBinding binding = activeBinding(userId);
        if (binding == null || request == null || request.getTemplateResults() == null) return;
        for (Map.Entry<String, String> entry : request.getTemplateResults().entrySet()) {
            WechatSubscriptionState state = stateMapper.selectOne(new LambdaQueryWrapper<WechatSubscriptionState>()
                    .eq(WechatSubscriptionState::getUserId, userId).eq(WechatSubscriptionState::getAppId, appId)
                    .eq(WechatSubscriptionState::getTemplateCode, entry.getKey()).last("LIMIT 1"));
            boolean create = state == null;
            if (create) state = new WechatSubscriptionState();
            state.setUserId(userId); state.setAppId(appId); state.setOpenid(binding.getOpenid());
            state.setTemplateCode(entry.getKey()); state.setStatus(entry.getValue().toUpperCase());
            if ("ACCEPT".equals(state.getStatus())) state.setAvailableCount((state.getAvailableCount() == null ? 0 : state.getAvailableCount()) + 1);
            state.setLastAuthorizedTime(LocalDateTime.now()); state.setUpdateTime(LocalDateTime.now());
            if (create) { state.setCreateTime(LocalDateTime.now()); stateMapper.insert(state); } else stateMapper.updateById(state);
        }
    }

    public void notifyUser(Long userId, String templateCode, String businessType, Long businessId, String summary) {
        try {
            SysUserWechatBinding binding = activeBinding(userId);
            WechatSubscriptionState state = binding == null ? null : stateMapper.selectOne(new LambdaQueryWrapper<WechatSubscriptionState>()
                    .eq(WechatSubscriptionState::getUserId, userId).eq(WechatSubscriptionState::getAppId, appId)
                    .eq(WechatSubscriptionState::getTemplateCode, templateCode).last("LIMIT 1"));
            WechatMessageLog log = new WechatMessageLog();
            log.setUserId(userId); log.setOpenid(binding == null ? null : binding.getOpenid()); log.setTemplateCode(templateCode);
            log.setBusinessType(businessType); log.setBusinessId(businessId); log.setRequestPayload(summary);
            log.setRetryCount(0); log.setCreateTime(LocalDateTime.now()); log.setUpdateTime(LocalDateTime.now());
            if (state == null || !"ACCEPT".equals(state.getStatus()) || state.getAvailableCount() == null || state.getAvailableCount() <= 0) {
                log.setStatus("SKIPPED"); log.setResponseMessage("未绑定微信或无可用订阅次数；站内待办不受影响");
            } else {
                log.setStatus("PENDING"); log.setResponseMessage("已进入微信发送队列；业务状态以站内待办为准");
            }
            logMapper.insert(log);
        } catch (Exception ignored) {
            // 微信提醒是增强能力，任何异常都不得阻断巡检业务。
        }
    }

    private SysUserWechatBinding activeBinding(Long userId) {
        if (userId == null) return null;
        return bindingMapper.selectOne(new LambdaQueryWrapper<SysUserWechatBinding>()
                .eq(SysUserWechatBinding::getUserId, userId).eq(SysUserWechatBinding::getAppId, appId)
                .eq(SysUserWechatBinding::getStatus, "ACTIVE").last("LIMIT 1"));
    }
}
