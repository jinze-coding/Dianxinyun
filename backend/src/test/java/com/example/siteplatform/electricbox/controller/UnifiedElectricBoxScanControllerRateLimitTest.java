package com.example.siteplatform.electricbox.controller;

import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.common.RedisRateLimitService;
import com.example.siteplatform.electricbox.service.UnifiedElectricBoxScanService;
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
class UnifiedElectricBoxScanControllerRateLimitTest {

    @Mock private UnifiedElectricBoxScanService scanService;
    @Mock private AuthService authService;
    @Mock private RedisRateLimitService rateLimitService;
    @Mock private HttpServletRequest request;

    private UnifiedElectricBoxScanController controller;

    @BeforeEach
    void setUp() {
        controller = new UnifiedElectricBoxScanController(scanService, authService, rateLimitService);
        when(request.getRemoteAddr()).thenReturn("203.0.113.9");
    }

    @Test
    void scanIsRateLimitedBeforeOptionalSessionAndBusinessQueries() {
        controller.resolve("B:PUBLIC-1", null, request);

        verify(rateLimitService).check(
                "unified-electric-box-scan", "203.0.113.9", 60, Duration.ofMinutes(10));
        verify(authService).getCurrentUserIfPresent(null);
        verify(scanService).resolve("B:PUBLIC-1", null);
    }

    @Test
    void exceededLimitStopsBeforeOptionalAuthentication() {
        doThrow(BusinessException.of(429, "操作过于频繁，请稍后再试"))
                .when(rateLimitService).check(
                        "unified-electric-box-scan", "203.0.113.9", 60, Duration.ofMinutes(10));

        assertThrows(BusinessException.class,
                () -> controller.resolve("B:PUBLIC-1", "Bearer token", request));

        verify(authService, never()).getCurrentUserIfPresent("Bearer token");
        verify(scanService, never()).resolve("B:PUBLIC-1", null);
    }
}
