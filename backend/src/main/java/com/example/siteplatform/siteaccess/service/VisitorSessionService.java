package com.example.siteplatform.siteaccess.service;

import com.example.siteplatform.auth.service.WechatPlatformClient;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.common.RedisRateLimitService;
import com.example.siteplatform.siteaccess.entity.SiteVisitInvitation;
import com.example.siteplatform.siteaccess.vo.PublicVisitorSessionVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

@Service
public class VisitorSessionService {
    private static final String PREFIX = "site-access:visitor-session:";
    private static final Duration TTL = Duration.ofMinutes(30);

    private final WechatPlatformClient wechatPlatformClient;
    private final VisitorDataCryptoService cryptoService;
    private final RedisRateLimitService rateLimitService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    public VisitorSessionService(WechatPlatformClient wechatPlatformClient,
                                 VisitorDataCryptoService cryptoService,
                                 RedisRateLimitService rateLimitService,
                                 StringRedisTemplate redisTemplate,
                                 ObjectMapper objectMapper) {
        this.wechatPlatformClient = wechatPlatformClient;
        this.cryptoService = cryptoService;
        this.rateLimitService = rateLimitService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public PublicVisitorSessionVO issue(String wechatCode, SiteVisitInvitation invitation) {
        if (!StringUtils.hasText(wechatCode)) throw new BusinessException("微信身份凭证不能为空");
        WechatPlatformClient.WechatIdentity identity = wechatPlatformClient.login(wechatCode.trim());
        String identityHash = cryptoService.fingerprint(identity.appId() + ":" + identity.openid());
        rateLimitService.check("public-site-visitor-session-identity", identityHash,
                30, Duration.ofMinutes(30));
        String token = randomToken();
        VisitorSessionContext context = new VisitorSessionContext(
                invitation.getId(), invitation.getProjectId(), identity.appId(), identityHash,
                cryptoService.encrypt(identity.openid()));
        try {
            redisTemplate.opsForValue().set(key(token), objectMapper.writeValueAsString(context), TTL);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("外访临时会话创建失败", exception);
        }
        return new PublicVisitorSessionVO(token, TTL.toSeconds());
    }

    public VisitorSessionContext require(String token) {
        if (!StringUtils.hasText(token) || token.trim().length() > 128) {
            throw BusinessException.of(401, "外访临时会话无效，请重新打开邀请");
        }
        String raw = redisTemplate.opsForValue().get(key(token.trim()));
        if (!StringUtils.hasText(raw)) {
            throw BusinessException.of(401, "外访临时会话已失效，请重新打开邀请");
        }
        try {
            return objectMapper.readValue(raw, VisitorSessionContext.class);
        } catch (JsonProcessingException exception) {
            throw BusinessException.of(401, "外访临时会话无效，请重新打开邀请");
        }
    }

    public VisitorSessionContext require(String token, SiteVisitInvitation invitation) {
        VisitorSessionContext context = require(token);
        if (!context.invitationId().equals(invitation.getId())
                || !context.projectId().equals(invitation.getProjectId())) {
            throw BusinessException.of(403, "常用资料与当前邀请不匹配");
        }
        return context;
    }

    public String decryptOpenid(VisitorSessionContext context) {
        return cryptoService.decrypt(context.openidEncrypted());
    }

    private String key(String token) {
        return PREFIX + cryptoService.digest(token);
    }

    private String randomToken() {
        byte[] value = new byte[32];
        secureRandom.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    public record VisitorSessionContext(Long invitationId, Long projectId, String appId,
                                        String identityHash, String openidEncrypted) {
    }
}
