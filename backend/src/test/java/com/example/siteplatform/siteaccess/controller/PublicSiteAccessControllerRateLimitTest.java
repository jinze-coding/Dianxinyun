package com.example.siteplatform.siteaccess.controller;

import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.common.RedisRateLimitService;
import com.example.siteplatform.siteaccess.dto.PublicSiteVisitResolveRequest;
import com.example.siteplatform.siteaccess.dto.PublicSiteVisitSubmitRequest;
import com.example.siteplatform.siteaccess.service.SiteAccessService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicSiteAccessControllerRateLimitTest {
    @Mock private SiteAccessService service;
    @Mock private RedisRateLimitService rateLimitService;
    @Mock private HttpServletRequest httpRequest;

    private PublicSiteAccessController controller;

    @BeforeEach
    void setUp() {
        controller = new PublicSiteAccessController(service, rateLimitService);
        when(httpRequest.getRemoteAddr()).thenReturn("203.0.113.9");
    }

    @Test
    void resolveIsRateLimitedBeforeInvitationLookup() {
        PublicSiteVisitResolveRequest request = new PublicSiteVisitResolveRequest();
        request.setInviteToken("development-token-value");

        controller.resolve(request, httpRequest);

        verify(rateLimitService).check(
                "public-site-visit-resolve", "203.0.113.9", 60, Duration.ofMinutes(10));
        verify(service).resolvePublic("development-token-value");
    }

    @Test
    void submitIsRateLimitedBeforeLockingInvitation() {
        PublicSiteVisitSubmitRequest request = new PublicSiteVisitSubmitRequest();

        controller.submit(request, httpRequest);

        verify(rateLimitService).check(
                "public-site-visit-submit", "203.0.113.9", 10, Duration.ofMinutes(30));
        verify(service).submitPublic(request);
    }

    @Test
    void exceededSubmitLimitStopsBeforeBusinessService() {
        doThrow(BusinessException.of(429, "操作过于频繁，请稍后再试"))
                .when(rateLimitService).check(
                        "public-site-visit-submit", "203.0.113.9", 10, Duration.ofMinutes(30));

        assertThrows(BusinessException.class,
                () -> controller.submit(new PublicSiteVisitSubmitRequest(), httpRequest));

        verify(service, never()).submitPublic(org.mockito.ArgumentMatchers.any());
    }
}
