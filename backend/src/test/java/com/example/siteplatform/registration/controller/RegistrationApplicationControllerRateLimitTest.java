package com.example.siteplatform.registration.controller;

import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.common.RedisRateLimitService;
import com.example.siteplatform.registration.service.RegistrationApplicationService;
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
class RegistrationApplicationControllerRateLimitTest {

    @Mock private RegistrationApplicationService service;
    @Mock private RedisRateLimitService rateLimitService;
    @Mock private HttpServletRequest request;

    private RegistrationApplicationController controller;

    @BeforeEach
    void setUp() {
        controller = new RegistrationApplicationController(service, rateLimitService);
        when(request.getRemoteAddr()).thenReturn("203.0.113.9");
    }

    @Test
    void publicProjectSearchIsRateLimitedBeforeQueryingProjects() {
        controller.projectOptions("智慧工地", request);

        verify(rateLimitService).check(
                "registration-project-search", "203.0.113.9", 30, Duration.ofMinutes(10));
        verify(service).searchProjectOptions("智慧工地");
    }

    @Test
    void publicProjectCatalogUsesTheSameRateLimit() {
        controller.projectOptions("", request);

        verify(rateLimitService).check(
                "registration-project-search", "203.0.113.9", 30, Duration.ofMinutes(10));
        verify(service).searchProjectOptions("");
    }

    @Test
    void rateLimitFailureStopsProjectSearch() {
        doThrow(BusinessException.of(429, "操作过于频繁，请稍后再试"))
                .when(rateLimitService).check(
                        "registration-project-search", "203.0.113.9", 30, Duration.ofMinutes(10));

        assertThrows(BusinessException.class,
                () -> controller.projectOptions("智慧工地", request));

        verify(service, never()).searchProjectOptions("智慧工地");
    }
}
