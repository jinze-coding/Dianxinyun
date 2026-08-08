package com.example.siteplatform.siteaccess.controller;

import com.example.siteplatform.common.RedisRateLimitService;
import com.example.siteplatform.common.Result;
import com.example.siteplatform.siteaccess.dto.PublicSiteVisitResolveRequest;
import com.example.siteplatform.siteaccess.dto.PublicSiteVisitSubmitRequest;
import com.example.siteplatform.siteaccess.service.SiteAccessService;
import com.example.siteplatform.siteaccess.vo.PublicSiteVisitInvitationVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/public/site-access")
public class PublicSiteAccessController {
    private final SiteAccessService service;
    private final RedisRateLimitService rateLimitService;

    public PublicSiteAccessController(SiteAccessService service, RedisRateLimitService rateLimitService) {
        this.service = service;
        this.rateLimitService = rateLimitService;
    }

    @PostMapping("/invitations/resolve")
    public Result<PublicSiteVisitInvitationVO> resolve(
            @Valid @RequestBody PublicSiteVisitResolveRequest request,
            HttpServletRequest httpRequest) {
        rateLimitService.check("public-site-visit-resolve", httpRequest.getRemoteAddr(),
                60, Duration.ofMinutes(10));
        return Result.success(service.resolvePublic(request.getInviteToken()));
    }

    @PostMapping("/invitations/submit")
    public Result<PublicSiteVisitInvitationVO> submit(
            @Valid @RequestBody PublicSiteVisitSubmitRequest request,
            HttpServletRequest httpRequest) {
        rateLimitService.check("public-site-visit-submit", httpRequest.getRemoteAddr(),
                10, Duration.ofMinutes(30));
        return Result.success(service.submitPublic(request));
    }
}
