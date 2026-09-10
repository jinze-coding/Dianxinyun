package com.example.siteplatform.siteaccess.controller;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.common.PageResult;
import com.example.siteplatform.common.Result;
import com.example.siteplatform.siteaccess.dto.SiteVisitInvitationCreateRequest;
import com.example.siteplatform.siteaccess.dto.SiteVisitInvitationUpdateRequest;
import com.example.siteplatform.siteaccess.dto.SiteVisitVoidRequest;
import com.example.siteplatform.siteaccess.dto.SiteGuardVisitQrRotateRequest;
import com.example.siteplatform.siteaccess.dto.SiteGuardVisitQrStatusRequest;
import com.example.siteplatform.siteaccess.dto.SiteGuardVisitRegistrationUpdateRequest;
import com.example.siteplatform.siteaccess.dto.SiteMeetingVisitRegistrationUpdateRequest;
import com.example.siteplatform.siteaccess.dto.SiteMeetingVisitRegistrationVoidRequest;
import com.example.siteplatform.siteaccess.dto.SiteMeetingAttendanceActionRequest;
import com.example.siteplatform.siteaccess.dto.SiteMeetingAttendeeUpdateRequest;
import com.example.siteplatform.siteaccess.dto.SiteMeetingCheckinRotateRequest;
import com.example.siteplatform.siteaccess.dto.SiteMeetingCheckinSettingsUpdateRequest;
import com.example.siteplatform.siteaccess.dto.SiteMeetingCheckinStatusRequest;
import com.example.siteplatform.siteaccess.dto.SiteMeetingWalkInCreateRequest;
import com.example.siteplatform.siteaccess.service.GuardVisitService;
import com.example.siteplatform.siteaccess.service.MeetingVisitService;
import com.example.siteplatform.siteaccess.service.MeetingCheckinService;
import com.example.siteplatform.siteaccess.service.SiteAccessService;
import com.example.siteplatform.siteaccess.vo.SiteGuardVisitMiniCodeVO;
import com.example.siteplatform.siteaccess.vo.SiteGuardVisitQrVO;
import com.example.siteplatform.siteaccess.vo.SiteGuardVisitRegistrationVO;
import com.example.siteplatform.siteaccess.vo.SiteMeetingVisitRegistrationVO;
import com.example.siteplatform.siteaccess.vo.SiteMeetingAttendanceSummaryVO;
import com.example.siteplatform.siteaccess.vo.SiteMeetingAttendeeVO;
import com.example.siteplatform.siteaccess.vo.SiteMeetingCheckinMiniCodeVO;
import com.example.siteplatform.siteaccess.vo.SiteMeetingCheckinSettingsVO;
import com.example.siteplatform.siteaccess.vo.SiteVisitHostOptionVO;
import com.example.siteplatform.siteaccess.vo.SiteVisitInvitationVO;
import com.example.siteplatform.siteaccess.vo.SiteVisitMiniCodeVO;
import com.example.siteplatform.siteaccess.vo.SiteVisitorProfileVO;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/site-access")
public class SiteAccessController {
    private final SiteAccessService service;
    private final GuardVisitService guardVisitService;
    private final MeetingVisitService meetingVisitService;
    private final MeetingCheckinService meetingCheckinService;
    private final AuthService authService;

    public SiteAccessController(SiteAccessService service, GuardVisitService guardVisitService,
                                MeetingVisitService meetingVisitService,
                                MeetingCheckinService meetingCheckinService,
                                AuthService authService) {
        this.service = service;
        this.guardVisitService = guardVisitService;
        this.meetingVisitService = meetingVisitService;
        this.meetingCheckinService = meetingCheckinService;
        this.authService = authService;
    }

    @GetMapping("/invitations")
    public Result<PageResult<SiteVisitInvitationVO>> page(
            @RequestParam Long projectId,
            @RequestParam(required = false) String inviteType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(service.page(projectId, inviteType, status, keyword, startDate, endDate,
                pageNo, pageSize, authService.getCurrentUser(token)));
    }

    @GetMapping("/invitations/{id}")
    public Result<SiteVisitInvitationVO> detail(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(service.detail(id, authService.getCurrentUser(token)));
    }

    @GetMapping("/host-options")
    public Result<List<SiteVisitHostOptionVO>> hostOptions(
            @RequestParam Long projectId,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(service.hostOptions(projectId, authService.getCurrentUser(token)));
    }

    @PostMapping("/invitations")
    public Result<SiteVisitInvitationVO> create(
            @Valid @RequestBody SiteVisitInvitationCreateRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(service.create(request, authService.getCurrentUser(token)));
    }

    @PutMapping("/invitations/{id}")
    public Result<SiteVisitInvitationVO> update(
            @PathVariable Long id,
            @Valid @RequestBody SiteVisitInvitationUpdateRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(service.update(id, request, authService.getCurrentUser(token)));
    }

    @PostMapping("/invitations/{id}/void")
    public Result<SiteVisitInvitationVO> voidInvitation(
            @PathVariable Long id,
            @Valid @RequestBody SiteVisitVoidRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(service.voidInvitation(id, request.getReason(), authService.getCurrentUser(token)));
    }

    @GetMapping("/invitations/{id}/mini-code")
    public Result<SiteVisitMiniCodeVO> miniCode(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(service.miniCode(id, authService.getCurrentUser(token)));
    }

    @GetMapping("/visitors/export")
    public ResponseEntity<byte[]> export(
            @RequestParam Long projectId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        SiteAccessService.ExportFile file = service.export(
                projectId, status, keyword, startDate, endDate, currentUser);
        String fileName = URLEncoder.encode(file.fileName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(file.content());
    }

    @GetMapping("/visitor-profiles")
    public Result<PageResult<SiteVisitorProfileVO>> visitorProfiles(
            @RequestParam Long projectId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(service.visitorProfiles(projectId, status, keyword, pageNo, pageSize,
                authService.getCurrentUser(token)));
    }

    @GetMapping("/visitor-profiles/{id}")
    public Result<SiteVisitorProfileVO> visitorProfileDetail(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(service.visitorProfileDetail(id, authService.getCurrentUser(token)));
    }

    @PostMapping("/visitor-profiles/{id}/disable")
    public Result<SiteVisitorProfileVO> disableVisitorProfile(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(service.disableVisitorProfile(id, authService.getCurrentUser(token)));
    }

    @GetMapping("/invitations/{invitationId}/meeting-registrations")
    public Result<PageResult<SiteMeetingVisitRegistrationVO>> meetingRegistrations(
            @PathVariable Long invitationId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(meetingVisitService.page(invitationId, status, keyword, pageNo, pageSize,
                authService.getCurrentUser(token)));
    }

    @GetMapping("/meeting-registrations/{id}")
    public Result<SiteMeetingVisitRegistrationVO> meetingRegistrationDetail(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(meetingVisitService.detail(id, authService.getCurrentUser(token)));
    }

    @PutMapping("/meeting-registrations/{id}")
    public Result<SiteMeetingVisitRegistrationVO> updateMeetingRegistration(
            @PathVariable Long id,
            @Valid @RequestBody SiteMeetingVisitRegistrationUpdateRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(meetingVisitService.update(id, request, authService.getCurrentUser(token)));
    }

    @PostMapping("/meeting-registrations/{id}/void")
    public Result<SiteMeetingVisitRegistrationVO> voidMeetingRegistration(
            @PathVariable Long id,
            @Valid @RequestBody SiteMeetingVisitRegistrationVoidRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(meetingVisitService.voidRegistration(
                id, request.getReason(), request.getVersion(), authService.getCurrentUser(token)));
    }

    @GetMapping("/meeting-registrations/export")
    public ResponseEntity<byte[]> exportMeetingRegistrations(
            @RequestParam Long projectId,
            @RequestParam(required = false) Long invitationId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String meetingStatus,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestHeader(value = "Authorization", required = false) String token) {
        MeetingVisitService.ExportFile file = meetingVisitService.export(projectId, invitationId, status,
                meetingStatus, keyword, startDate, endDate, authService.getCurrentUser(token));
        String fileName = URLEncoder.encode(file.fileName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(file.content());
    }

    @GetMapping("/invitations/{id}/meeting-check-in/settings")
    public Result<SiteMeetingCheckinSettingsVO> meetingCheckinSettings(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(meetingCheckinService.settings(id, authService.getCurrentUser(token)));
    }

    @PutMapping("/invitations/{id}/meeting-check-in/settings")
    public Result<SiteMeetingCheckinSettingsVO> updateMeetingCheckinSettings(
            @PathVariable Long id,
            @Valid @RequestBody SiteMeetingCheckinSettingsUpdateRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(meetingCheckinService.updateSettings(id, request, authService.getCurrentUser(token)));
    }

    @PostMapping("/invitations/{id}/meeting-check-in/status")
    public Result<SiteMeetingCheckinSettingsVO> changeMeetingCheckinStatus(
            @PathVariable Long id,
            @Valid @RequestBody SiteMeetingCheckinStatusRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(meetingCheckinService.changeStatus(
                id, request.getEnabled(), request.getVersion(), authService.getCurrentUser(token)));
    }

    @PostMapping("/invitations/{id}/meeting-check-in/rotate")
    public Result<SiteMeetingCheckinSettingsVO> rotateMeetingCheckinQr(
            @PathVariable Long id,
            @Valid @RequestBody SiteMeetingCheckinRotateRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(meetingCheckinService.rotate(id, request.getVersion(), authService.getCurrentUser(token)));
    }

    @GetMapping("/invitations/{id}/meeting-check-in/mini-code")
    public Result<SiteMeetingCheckinMiniCodeVO> meetingCheckinMiniCode(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(meetingCheckinService.miniCode(id, authService.getCurrentUser(token)));
    }

    @GetMapping("/invitations/{id}/meeting-attendance/screen")
    public Result<com.example.siteplatform.siteaccess.vo.MeetingAttendanceScreenVO> meetingScreen(
            @PathVariable Long id, @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(meetingCheckinService.screen(id, pageNo, authService.getCurrentUser(token)));
    }

    @GetMapping("/invitations/{id}/meeting-attendance/summary")
    public Result<SiteMeetingAttendanceSummaryVO> meetingAttendanceSummary(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(meetingCheckinService.summary(id, authService.getCurrentUser(token)));
    }

    @GetMapping("/invitations/{id}/meeting-attendees")
    public Result<PageResult<SiteMeetingAttendeeVO>> meetingAttendees(
            @PathVariable Long id,
            @RequestParam(required = false) String registrationSource,
            @RequestParam(required = false) String attendanceStatus,
            @RequestParam(required = false) String locationResult,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(meetingCheckinService.attendees(id, registrationSource, attendanceStatus,
                locationResult, keyword, pageNo, pageSize, authService.getCurrentUser(token)));
    }

    @PostMapping("/invitations/{id}/meeting-attendees/walk-ins")
    public Result<SiteMeetingAttendeeVO> createMeetingWalkIn(
            @PathVariable Long id,
            @Valid @RequestBody SiteMeetingWalkInCreateRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(meetingCheckinService.createManualWalkIn(id, request, authService.getCurrentUser(token)));
    }

    @PostMapping("/meeting-attendees/{personId}/manual-check-in")
    public Result<SiteMeetingAttendeeVO> manualMeetingCheckIn(
            @PathVariable Long personId,
            @Valid @RequestBody SiteMeetingAttendanceActionRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(meetingCheckinService.manualCheckIn(personId, request, authService.getCurrentUser(token)));
    }

    @PostMapping("/meeting-attendees/{personId}/revoke")
    public Result<SiteMeetingAttendeeVO> revokeMeetingCheckIn(
            @PathVariable Long personId,
            @Valid @RequestBody SiteMeetingAttendanceActionRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(meetingCheckinService.revoke(personId, request, authService.getCurrentUser(token)));
    }

    @PutMapping("/meeting-attendees/{personId}")
    public Result<SiteMeetingAttendeeVO> updateMeetingAttendee(
            @PathVariable Long personId,
            @Valid @RequestBody SiteMeetingAttendeeUpdateRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(meetingCheckinService.updateAttendee(personId, request, authService.getCurrentUser(token)));
    }

    @GetMapping("/meeting-attendance/export")
    public ResponseEntity<byte[]> exportMeetingAttendance(
            @RequestParam Long projectId,
            @RequestParam Long invitationId,
            @RequestHeader(value = "Authorization", required = false) String token) {
        MeetingCheckinService.ExportFile file = meetingCheckinService.export(
                projectId, invitationId, authService.getCurrentUser(token));
        String fileName = URLEncoder.encode(file.fileName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(file.content());
    }

    @GetMapping("/guard/qr")
    public Result<SiteGuardVisitQrVO> guardQr(
            @RequestParam Long projectId,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(guardVisitService.currentQr(projectId, authService.getCurrentUser(token)));
    }

    @PostMapping("/guard/qr")
    public Result<SiteGuardVisitQrVO> createGuardQr(
            @RequestParam Long projectId,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(guardVisitService.createQr(projectId, authService.getCurrentUser(token)));
    }

    @PostMapping("/guard/qr/{id}/status")
    public Result<SiteGuardVisitQrVO> changeGuardQrStatus(
            @PathVariable Long id,
            @Valid @RequestBody SiteGuardVisitQrStatusRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(guardVisitService.changeStatus(id, request.getEnabled(), request.getVersion(),
                authService.getCurrentUser(token)));
    }

    @PostMapping("/guard/qr/{id}/rotate")
    public Result<SiteGuardVisitQrVO> rotateGuardQr(
            @PathVariable Long id,
            @Valid @RequestBody SiteGuardVisitQrRotateRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(guardVisitService.rotate(id, request.getVersion(), authService.getCurrentUser(token)));
    }

    @GetMapping("/guard/qr/{id}/mini-code")
    public Result<SiteGuardVisitMiniCodeVO> guardMiniCode(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(guardVisitService.miniCode(id, authService.getCurrentUser(token)));
    }

    @GetMapping("/guard/registrations")
    public Result<PageResult<SiteGuardVisitRegistrationVO>> guardRegistrations(
            @RequestParam Long projectId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(guardVisitService.page(projectId, status, keyword, startDate, endDate,
                pageNo, pageSize, authService.getCurrentUser(token)));
    }

    @GetMapping("/guard/registrations/{id}")
    public Result<SiteGuardVisitRegistrationVO> guardRegistrationDetail(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(guardVisitService.detail(id, authService.getCurrentUser(token)));
    }

    @PutMapping("/guard/registrations/{id}")
    public Result<SiteGuardVisitRegistrationVO> updateGuardRegistration(
            @PathVariable Long id,
            @Valid @RequestBody SiteGuardVisitRegistrationUpdateRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(guardVisitService.update(id, request, authService.getCurrentUser(token)));
    }

    @PostMapping("/guard/registrations/{id}/void")
    public Result<SiteGuardVisitRegistrationVO> voidGuardRegistration(
            @PathVariable Long id,
            @Valid @RequestBody SiteVisitVoidRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(guardVisitService.voidRegistration(
                id, request.getReason(), authService.getCurrentUser(token)));
    }

    @GetMapping("/guard/registrations/export")
    public ResponseEntity<byte[]> exportGuardRegistrations(
            @RequestParam Long projectId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestHeader(value = "Authorization", required = false) String token) {
        GuardVisitService.ExportFile file = guardVisitService.export(
                projectId, status, keyword, startDate, endDate, authService.getCurrentUser(token));
        String fileName = URLEncoder.encode(file.fileName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(file.content());
    }
}
