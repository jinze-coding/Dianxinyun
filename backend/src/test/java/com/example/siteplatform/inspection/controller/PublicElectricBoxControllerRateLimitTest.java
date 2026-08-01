package com.example.siteplatform.inspection.controller;

import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.common.RedisRateLimitService;
import com.example.siteplatform.inspection.service.InspectionService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicElectricBoxControllerRateLimitTest {

    @Mock private InspectionService inspectionService;
    @Mock private RedisRateLimitService rateLimitService;
    @Mock private HttpServletRequest request;

    private PublicElectricBoxController controller;

    @BeforeEach
    void setUp() {
        controller = new PublicElectricBoxController();
        ReflectionTestUtils.setField(controller, "inspectionService", inspectionService);
        ReflectionTestUtils.setField(controller, "rateLimitService", rateLimitService);
        when(request.getRemoteAddr()).thenReturn("203.0.113.9");
    }

    @Test
    void summaryIsRateLimitedByValidatedClientIpBeforeQueryingBusinessData() {
        controller.getSummary("PUBLIC-1", request);

        verify(rateLimitService).check(
                "public-electric-box-summary", "203.0.113.9", 60, Duration.ofMinutes(10));
        verify(inspectionService).getPublicSummary("PUBLIC-1");
    }

    @Test
    void monthlyRequestStopsBeforeDatabaseWorkWhenLimitIsExceeded() {
        doThrow(BusinessException.of(429, "操作过于频繁，请稍后再试"))
                .when(rateLimitService).check(
                        "public-electric-box-monthly", "203.0.113.9", 30, Duration.ofMinutes(10));

        assertThrows(BusinessException.class,
                () -> controller.getMonthlyRecords("PUBLIC-1", "2026-07", request));

        verify(inspectionService, never()).getPublicMonthly("PUBLIC-1", "2026-07");
    }
}
