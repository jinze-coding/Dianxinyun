package com.example.siteplatform.registration.controller;

import com.example.siteplatform.common.RedisRateLimitService;
import com.example.siteplatform.common.Result;
import com.example.siteplatform.registration.dto.RegistrationApplicationVO;
import com.example.siteplatform.registration.dto.RegistrationStatusRequest;
import com.example.siteplatform.registration.dto.RegistrationSubmitRequest;
import com.example.siteplatform.registration.dto.RegistrationSubmitResponse;
import com.example.siteplatform.registration.service.RegistrationApplicationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/registration-applications")
public class RegistrationApplicationController {
    private final RegistrationApplicationService service;
    private final RedisRateLimitService rateLimitService;

    public RegistrationApplicationController(RegistrationApplicationService service,
                                             RedisRateLimitService rateLimitService) {
        this.service = service;
        this.rateLimitService = rateLimitService;
    }

    @PostMapping
    public Result<RegistrationSubmitResponse> submit(@Valid @RequestBody RegistrationSubmitRequest request,
                                                      HttpServletRequest httpRequest) {
        rateLimitService.check("registration", httpRequest.getRemoteAddr(), 5, Duration.ofMinutes(30));
        return Result.success(service.submit(request));
    }

    @PostMapping("/status")
    public Result<RegistrationApplicationVO> status(@Valid @RequestBody RegistrationStatusRequest request,
                                                     HttpServletRequest httpRequest) {
        rateLimitService.check("registration-status", httpRequest.getRemoteAddr(), 30, Duration.ofMinutes(10));
        return Result.success(service.status(request.resolvedToken()));
    }

    @PostMapping("/cancel")
    public Result<RegistrationApplicationVO> cancel(@Valid @RequestBody RegistrationStatusRequest request,
                                                     HttpServletRequest httpRequest) {
        rateLimitService.check("registration-cancel", httpRequest.getRemoteAddr(), 10, Duration.ofMinutes(10));
        return Result.success(service.cancel(request.resolvedToken()));
    }
}
