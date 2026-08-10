package com.example.siteplatform.siteaccess.controller;

import com.example.siteplatform.common.Result;
import com.example.siteplatform.siteaccess.dto.PublicSiteVisitResolveRequest;
import com.example.siteplatform.siteaccess.dto.PublicSiteVisitSubmitRequest;
import com.example.siteplatform.siteaccess.dto.PublicVisitorProfileRequest;
import com.example.siteplatform.siteaccess.dto.PublicVisitorSessionCreateRequest;
import com.example.siteplatform.siteaccess.service.SiteAccessService;
import com.example.siteplatform.siteaccess.vo.PublicSiteVisitInvitationVO;
import com.example.siteplatform.siteaccess.vo.PublicVisitorSessionVO;
import com.example.siteplatform.siteaccess.vo.SiteVisitorProfileVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/public/site-access")
public class PublicSiteAccessController {
    private final SiteAccessService service;

    public PublicSiteAccessController(SiteAccessService service) {
        this.service = service;
    }

    @PostMapping("/invitations/resolve")
    public Result<PublicSiteVisitInvitationVO> resolve(
            @Valid @RequestBody PublicSiteVisitResolveRequest request) {
        return Result.success(service.resolvePublic(request.getInviteToken()));
    }

    @PostMapping("/invitations/submit")
    public Result<PublicSiteVisitInvitationVO> submit(
            @Valid @RequestBody PublicSiteVisitSubmitRequest request,
            @RequestHeader(value = "X-Visitor-Session", required = false) String visitorSessionToken) {
        return Result.success(service.submitPublic(request, visitorSessionToken));
    }

    @PostMapping("/visitor-sessions")
    public Result<PublicVisitorSessionVO> createVisitorSession(
            @Valid @RequestBody PublicVisitorSessionCreateRequest request) {
        return Result.success(service.createVisitorSession(request));
    }

    @PostMapping("/visitor-profiles/list")
    public Result<List<SiteVisitorProfileVO>> visitorProfiles(
            @RequestHeader("X-Visitor-Session") String visitorSessionToken) {
        return Result.success(service.publicVisitorProfiles(visitorSessionToken));
    }

    @PostMapping("/visitor-profiles/detail")
    public Result<SiteVisitorProfileVO> visitorProfileDetail(
            @Valid @RequestBody PublicVisitorProfileRequest request,
            @RequestHeader("X-Visitor-Session") String visitorSessionToken) {
        return Result.success(service.publicVisitorProfile(visitorSessionToken, request.getProfileCode()));
    }

    @PostMapping("/visitor-profiles/disable")
    public Result<Void> disableVisitorProfile(
            @Valid @RequestBody PublicVisitorProfileRequest request,
            @RequestHeader("X-Visitor-Session") String visitorSessionToken) {
        service.disablePublicVisitorProfile(visitorSessionToken, request.getProfileCode());
        return Result.success();
    }
}
