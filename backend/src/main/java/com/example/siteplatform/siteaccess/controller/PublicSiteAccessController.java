package com.example.siteplatform.siteaccess.controller;

import com.example.siteplatform.common.Result;
import com.example.siteplatform.project.dto.PublicProjectProfileVO;
import com.example.siteplatform.project.service.ProjectProfileService;
import com.example.siteplatform.project.service.ProjectRouteImageService;
import com.example.siteplatform.siteaccess.dto.PublicProjectProfileImageRequest;
import com.example.siteplatform.siteaccess.dto.PublicProjectProfileRequest;
import com.example.siteplatform.siteaccess.dto.PublicProjectRouteImageRequest;
import com.example.siteplatform.siteaccess.dto.PublicGuardVisitSubmitRequest;
import com.example.siteplatform.siteaccess.dto.PublicGuardProjectProfileImageRequest;
import com.example.siteplatform.siteaccess.dto.PublicGuardProjectProfileRequest;
import com.example.siteplatform.siteaccess.dto.PublicGuardVisitorSessionRequest;
import com.example.siteplatform.siteaccess.dto.PublicMeetingVisitorSessionRequest;
import com.example.siteplatform.siteaccess.dto.PublicMeetingVisitSubmitRequest;
import com.example.siteplatform.siteaccess.dto.PublicSiteVisitResolveRequest;
import com.example.siteplatform.siteaccess.dto.PublicSiteVisitSubmitRequest;
import com.example.siteplatform.siteaccess.dto.PublicVisitorProfileRequest;
import com.example.siteplatform.siteaccess.dto.PublicVisitorSessionCreateRequest;
import com.example.siteplatform.siteaccess.service.SiteAccessService;
import com.example.siteplatform.siteaccess.service.GuardVisitService;
import com.example.siteplatform.siteaccess.service.MeetingVisitService;
import com.example.siteplatform.siteaccess.vo.PublicGuardVisitPassVO;
import com.example.siteplatform.siteaccess.vo.PublicGuardVisitorSessionVO;
import com.example.siteplatform.siteaccess.vo.PublicMeetingVisitorSessionVO;
import com.example.siteplatform.siteaccess.vo.PublicMeetingVisitPassVO;
import com.example.siteplatform.siteaccess.vo.PublicSiteVisitInvitationVO;
import com.example.siteplatform.siteaccess.vo.PublicVisitorSessionVO;
import com.example.siteplatform.siteaccess.vo.SiteVisitorProfileVO;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/public/site-access")
public class PublicSiteAccessController {
    private final SiteAccessService service;
    private final GuardVisitService guardVisitService;
    private final MeetingVisitService meetingVisitService;

    public PublicSiteAccessController(SiteAccessService service, GuardVisitService guardVisitService,
                                      MeetingVisitService meetingVisitService) {
        this.service = service;
        this.guardVisitService = guardVisitService;
        this.meetingVisitService = meetingVisitService;
    }

    @PostMapping("/project-profile")
    public Result<PublicProjectProfileVO> projectProfile(
            @Valid @RequestBody PublicProjectProfileRequest request) {
        return Result.success(service.publicProjectProfile(request.getInviteToken()));
    }

    @PostMapping("/project-profile/images")
    public ResponseEntity<Resource> projectProfileImage(
            @Valid @RequestBody PublicProjectProfileImageRequest request) {
        ProjectProfileService.PublicProjectProfileImageContent content = service.publicProjectProfileImage(
                request.getInviteToken(), request.getImageIndex());
        return projectProfileImageResponse(content, request.getImageIndex());
    }

    @PostMapping("/project-location/route-image")
    public ResponseEntity<Resource> projectRouteImage(
            @Valid @RequestBody PublicProjectRouteImageRequest request) {
        ProjectRouteImageService.PublicProjectRouteImageContent content =
                service.publicProjectRouteImage(request.getInviteToken());
        String fileName = "project-route-image." + content.extension();
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(fileName, StandardCharsets.UTF_8).build();
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .contentType(content.mediaType())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "sandbox")
                .header("Cross-Origin-Resource-Policy", "same-origin");
        if (content.fileSize() != null && content.fileSize() >= 0) response.contentLength(content.fileSize());
        return response.body(content.resource());
    }

    @PostMapping("/guard/project-profile")
    public Result<PublicProjectProfileVO> guardProjectProfile(
            @Valid @RequestBody PublicGuardProjectProfileRequest request) {
        return Result.success(guardVisitService.publicProjectProfile(request.getSceneToken()));
    }

    @PostMapping("/guard/project-profile/images")
    public ResponseEntity<Resource> guardProjectProfileImage(
            @Valid @RequestBody PublicGuardProjectProfileImageRequest request) {
        ProjectProfileService.PublicProjectProfileImageContent content = guardVisitService.publicProjectProfileImage(
                request.getSceneToken(), request.getImageIndex());
        return projectProfileImageResponse(content, request.getImageIndex());
    }

    private ResponseEntity<Resource> projectProfileImageResponse(
            ProjectProfileService.PublicProjectProfileImageContent content, Integer imageIndex) {
        String fileName = "project-image-" + (imageIndex + 1) + "." + content.extension();
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(fileName, StandardCharsets.UTF_8).build();
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .contentType(content.mediaType())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "sandbox")
                .header("Cross-Origin-Resource-Policy", "same-origin");
        if (content.fileSize() != null && content.fileSize() >= 0) response.contentLength(content.fileSize());
        return response.body(content.resource());
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

    @PostMapping("/meeting/session")
    public Result<PublicMeetingVisitorSessionVO> createMeetingSession(
            @Valid @RequestBody PublicMeetingVisitorSessionRequest request) {
        return Result.success(meetingVisitService.createPublicSession(request));
    }

    @PostMapping("/meeting/submit")
    public Result<PublicMeetingVisitPassVO> submitMeetingRegistration(
            @Valid @RequestBody PublicMeetingVisitSubmitRequest request,
            @RequestHeader("X-Visitor-Session") String visitorSessionToken) {
        return Result.success(meetingVisitService.submitPublic(request, visitorSessionToken));
    }

    @PostMapping("/meeting/profiles/list")
    public Result<List<SiteVisitorProfileVO>> meetingVisitorProfiles(
            @RequestHeader("X-Visitor-Session") String visitorSessionToken) {
        return Result.success(meetingVisitService.publicProfiles(visitorSessionToken));
    }

    @PostMapping("/meeting/profiles/detail")
    public Result<SiteVisitorProfileVO> meetingVisitorProfileDetail(
            @Valid @RequestBody PublicVisitorProfileRequest request,
            @RequestHeader("X-Visitor-Session") String visitorSessionToken) {
        return Result.success(meetingVisitService.publicProfile(visitorSessionToken, request.getProfileCode()));
    }

    @PostMapping("/meeting/profiles/disable")
    public Result<Void> disableMeetingVisitorProfile(
            @Valid @RequestBody PublicVisitorProfileRequest request,
            @RequestHeader("X-Visitor-Session") String visitorSessionToken) {
        meetingVisitService.disablePublicProfile(visitorSessionToken, request.getProfileCode());
        return Result.success();
    }

    @PostMapping("/guard/session")
    public Result<PublicGuardVisitorSessionVO> createGuardSession(
            @Valid @RequestBody PublicGuardVisitorSessionRequest request) {
        return Result.success(guardVisitService.createPublicSession(request));
    }

    @PostMapping("/guard/submit")
    public Result<PublicGuardVisitPassVO> submitGuardRegistration(
            @Valid @RequestBody PublicGuardVisitSubmitRequest request,
            @RequestHeader("X-Visitor-Session") String visitorSessionToken) {
        return Result.success(guardVisitService.submitPublic(request, visitorSessionToken));
    }

    @PostMapping("/guard/profiles/list")
    public Result<List<SiteVisitorProfileVO>> guardVisitorProfiles(
            @RequestHeader("X-Visitor-Session") String visitorSessionToken) {
        return Result.success(guardVisitService.publicProfiles(visitorSessionToken));
    }

    @PostMapping("/guard/profiles/detail")
    public Result<SiteVisitorProfileVO> guardVisitorProfileDetail(
            @Valid @RequestBody PublicVisitorProfileRequest request,
            @RequestHeader("X-Visitor-Session") String visitorSessionToken) {
        return Result.success(guardVisitService.publicProfile(visitorSessionToken, request.getProfileCode()));
    }

    @PostMapping("/guard/profiles/disable")
    public Result<Void> disableGuardVisitorProfile(
            @Valid @RequestBody PublicVisitorProfileRequest request,
            @RequestHeader("X-Visitor-Session") String visitorSessionToken) {
        guardVisitService.disablePublicProfile(visitorSessionToken, request.getProfileCode());
        return Result.success();
    }
}
