package com.example.siteplatform.system.controller;

import com.example.siteplatform.auth.dto.*;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.auth.service.WechatUserManagementService;
import com.example.siteplatform.common.PageResult;
import com.example.siteplatform.common.Result;
import com.example.siteplatform.log.entity.OperationLog;
import com.example.siteplatform.registration.dto.RegistrationApplicationVO;
import com.example.siteplatform.registration.dto.RegistrationReviewRequest;
import com.example.siteplatform.registration.service.RegistrationApplicationService;
import com.example.siteplatform.system.constant.SystemPermissionCodes;
import com.example.siteplatform.system.dto.PasswordResetRequest;
import com.example.siteplatform.system.dto.ReviewCommentRequest;
import com.example.siteplatform.system.dto.RoleSaveRequest;
import com.example.siteplatform.system.dto.SystemUserStatusRequest;
import com.example.siteplatform.system.entity.SystemMenu;
import com.example.siteplatform.system.entity.SystemPermission;
import com.example.siteplatform.system.entity.SystemRole;
import com.example.siteplatform.system.service.SystemAdministrationService;
import com.example.siteplatform.system.service.SystemPermissionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/system")
public class SystemManagementController {
    private final AuthService authService;
    private final SystemPermissionService permissionService;
    private final SystemAdministrationService administrationService;
    private final RegistrationApplicationService registrationService;
    private final WechatUserManagementService wechatUserService;

    public SystemManagementController(AuthService authService, SystemPermissionService permissionService,
                                      SystemAdministrationService administrationService,
                                      RegistrationApplicationService registrationService,
                                      WechatUserManagementService wechatUserService) {
        this.authService = authService;
        this.permissionService = permissionService;
        this.administrationService = administrationService;
        this.registrationService = registrationService;
        this.wechatUserService = wechatUserService;
    }

    @GetMapping("/registration-applications")
    public Result<PageResult<RegistrationApplicationVO>> registrationApplications(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer pageNo,
            @RequestParam(required = false) Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize,
            HttpServletRequest request) {
        permissionService.requirePlatformPermission(current(request), SystemPermissionCodes.REGISTRATION_REVIEW);
        return Result.success(registrationService.list(status, keyword, pageNo == null ? page : pageNo, pageSize));
    }

    @GetMapping("/registration-applications/{id}")
    public Result<RegistrationApplicationVO> registrationApplication(@PathVariable Long id,
                                                                      HttpServletRequest request) {
        permissionService.requirePlatformPermission(current(request), SystemPermissionCodes.REGISTRATION_REVIEW);
        return Result.success(registrationService.detail(id));
    }

    @PostMapping("/registration-applications/{id}/approve")
    public Result<RegistrationApplicationVO> approveRegistration(
            @PathVariable Long id, @RequestBody RegistrationReviewRequest body, HttpServletRequest request) {
        SysUser user = current(request);
        permissionService.requirePlatformPermission(user, SystemPermissionCodes.REGISTRATION_REVIEW);
        return Result.success(registrationService.approve(id, body, user));
    }

    @PostMapping("/registration-applications/{id}/reject")
    public Result<RegistrationApplicationVO> rejectRegistration(
            @PathVariable Long id, @RequestBody ReviewCommentRequest body, HttpServletRequest request) {
        SysUser user = current(request);
        permissionService.requirePlatformPermission(user, SystemPermissionCodes.REGISTRATION_REVIEW);
        return Result.success(registrationService.reject(id, body.getReviewComment(), user));
    }

    @GetMapping("/users")
    public Result<PageResult<Map<String, Object>>> users(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "20") Integer pageSize,
            HttpServletRequest request) {
        permissionService.requirePlatformPermission(current(request), SystemPermissionCodes.USER_VIEW);
        return Result.success(administrationService.users(keyword, status, pageNo, pageSize));
    }

    @GetMapping("/users/{userId}")
    public Result<Map<String, Object>> user(@PathVariable Long userId, HttpServletRequest request) {
        permissionService.requirePlatformPermission(current(request), SystemPermissionCodes.USER_VIEW);
        return Result.success(administrationService.user(userId));
    }

    @PutMapping("/users/{userId}/status")
    public Result<Void> updateUserStatus(@PathVariable Long userId,
                                         @RequestBody SystemUserStatusRequest body,
                                         HttpServletRequest request) {
        SysUser operator = current(request);
        permissionService.requirePlatformPermission(operator, SystemPermissionCodes.USER_STATUS);
        administrationService.changeUserStatus(userId, body.getStatus(), body.getReason(), operator);
        return Result.success();
    }

    @PostMapping({"/users/{userId}/reset-password", "/users/{userId}/password/reset"})
    public Result<Void> resetPassword(@PathVariable Long userId,
                                      @Valid @RequestBody PasswordResetRequest body,
                                      HttpServletRequest request) {
        SysUser operator = current(request);
        permissionService.requirePlatformPermission(operator, SystemPermissionCodes.USER_RESET_PASSWORD);
        administrationService.resetPassword(userId, body.getNewPassword(), operator);
        return Result.success();
    }

    @PutMapping("/users/{userId}/access")
    public Result<Void> assignUserAccess(@PathVariable Long userId,
                                         @RequestBody RegistrationReviewRequest body,
                                         HttpServletRequest request) {
        SysUser operator = current(request);
        permissionService.requirePlatformPermission(operator, SystemPermissionCodes.USER_MANAGE);
        administrationService.assignAccess(userId, body, operator);
        return Result.success();
    }

    @PutMapping("/users/{userId}/roles")
    public Result<Void> updateUserRoles(@PathVariable Long userId,
                                        @RequestBody Map<String, List<Long>> body,
                                        HttpServletRequest request) {
        SysUser operator = current(request);
        permissionService.requirePlatformPermission(operator, SystemPermissionCodes.USER_MANAGE);
        administrationService.updateUserRoles(userId, body.get("roleIds"), operator);
        return Result.success();
    }

    @GetMapping("/roles")
    public Result<List<SystemRole>> roles(HttpServletRequest request) {
        permissionService.requireAnyPlatformPermission(current(request),
                SystemPermissionCodes.USER_VIEW,
                SystemPermissionCodes.ROLE_MANAGE,
                SystemPermissionCodes.REGISTRATION_REVIEW);
        return Result.success(administrationService.roles());
    }

    @PostMapping("/roles")
    public Result<SystemRole> createRole(@Valid @RequestBody RoleSaveRequest body, HttpServletRequest request) {
        SysUser operator = current(request);
        permissionService.requirePlatformPermission(operator, SystemPermissionCodes.ROLE_MANAGE);
        return Result.success(administrationService.saveRole(null, body, operator));
    }

    @PutMapping("/roles/{id}")
    public Result<SystemRole> updateRole(@PathVariable Long id, @Valid @RequestBody RoleSaveRequest body,
                                         HttpServletRequest request) {
        SysUser operator = current(request);
        permissionService.requirePlatformPermission(operator, SystemPermissionCodes.ROLE_MANAGE);
        return Result.success(administrationService.saveRole(id, body, operator));
    }

    @DeleteMapping("/roles/{id}")
    public Result<Void> deleteRole(@PathVariable Long id, HttpServletRequest request) {
        SysUser operator = current(request);
        permissionService.requirePlatformPermission(operator, SystemPermissionCodes.ROLE_MANAGE);
        administrationService.deleteRole(id, operator);
        return Result.success();
    }

    @PutMapping("/roles/{id}/permissions")
    public Result<Void> updateRolePermissions(@PathVariable Long id,
                                              @RequestBody Map<String, List<Long>> body,
                                              HttpServletRequest request) {
        SysUser operator = current(request);
        permissionService.requirePlatformPermission(operator, SystemPermissionCodes.ROLE_MANAGE);
        administrationService.updateRolePermissions(id, body.get("permissionIds"), body.get("menuIds"), operator);
        return Result.success();
    }

    @GetMapping("/menus")
    public Result<List<SystemMenu>> menus(@RequestParam(required = false) String clientType,
                                          HttpServletRequest request) {
        permissionService.requireAnyPlatformPermission(current(request),
                SystemPermissionCodes.USER_VIEW,
                SystemPermissionCodes.ROLE_MANAGE,
                SystemPermissionCodes.MENU_MANAGE);
        return Result.success(administrationService.menus(clientType));
    }

    @PostMapping("/menus")
    public Result<SystemMenu> createMenu(@RequestBody SystemMenu body, HttpServletRequest request) {
        SysUser operator = current(request);
        permissionService.requirePlatformPermission(operator, SystemPermissionCodes.MENU_MANAGE);
        return Result.success(administrationService.saveMenu(null, body, operator));
    }

    @PutMapping("/menus/{id}")
    public Result<SystemMenu> updateMenu(@PathVariable Long id, @RequestBody SystemMenu body,
                                         HttpServletRequest request) {
        SysUser operator = current(request);
        permissionService.requirePlatformPermission(operator, SystemPermissionCodes.MENU_MANAGE);
        return Result.success(administrationService.saveMenu(id, body, operator));
    }

    @PutMapping("/menus/{id}/status")
    public Result<SystemMenu> updateMenuStatus(@PathVariable Long id,
                                               @RequestBody Map<String, Object> body,
                                               HttpServletRequest request) {
        SysUser operator = current(request);
        permissionService.requirePlatformPermission(operator, SystemPermissionCodes.MENU_MANAGE);
        Object status = body.containsKey("enabled") ? body.get("enabled") : body.get("status");
        return Result.success(administrationService.updateMenuStatus(id, status, operator));
    }

    @GetMapping("/permissions")
    public Result<List<SystemPermission>> permissions(HttpServletRequest request) {
        permissionService.requireAnyPlatformPermission(current(request),
                SystemPermissionCodes.USER_VIEW,
                SystemPermissionCodes.ROLE_MANAGE,
                SystemPermissionCodes.MENU_MANAGE);
        return Result.success(administrationService.permissions());
    }

    @PostMapping("/permissions")
    public Result<SystemPermission> createPermission(@RequestBody SystemPermission body,
                                                     HttpServletRequest request) {
        SysUser operator = current(request);
        permissionService.requirePlatformPermission(operator, SystemPermissionCodes.MENU_MANAGE);
        return Result.success(administrationService.savePermission(null, body, operator));
    }

    @PutMapping("/permissions/{id}")
    public Result<SystemPermission> updatePermission(@PathVariable Long id,
                                                     @RequestBody SystemPermission body,
                                                     HttpServletRequest request) {
        SysUser operator = current(request);
        permissionService.requirePlatformPermission(operator, SystemPermissionCodes.MENU_MANAGE);
        return Result.success(administrationService.savePermission(id, body, operator));
    }

    @GetMapping("/audit-logs")
    public Result<PageResult<OperationLog>> auditLogs(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "20") Integer pageSize,
            HttpServletRequest request) {
        permissionService.requirePlatformPermission(current(request), SystemPermissionCodes.AUDIT_VIEW);
        return Result.success(administrationService.auditLogs(keyword, pageNo, pageSize));
    }

    @GetMapping("/wechat-bindings")
    public Result<WechatUserPageVO> wechatBindings(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String bindingStatus,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "20") Integer pageSize,
            HttpServletRequest request) {
        SysUser operator = current(request);
        permissionService.requirePlatformPermission(operator, SystemPermissionCodes.WECHAT_MANAGE);
        return Result.success(wechatUserService.list(null, keyword, bindingStatus, null,
                null, null, pageNo, pageSize, operator));
    }

    @PutMapping("/wechat-bindings/{userId}/{bindingId}/status")
    public Result<WechatBindingVO> updateWechatStatus(
            @PathVariable Long userId, @PathVariable Long bindingId,
            @RequestBody WechatBindingStatusRequest body, HttpServletRequest request) {
        SysUser operator = current(request);
        permissionService.requirePlatformPermission(operator, SystemPermissionCodes.WECHAT_MANAGE);
        return Result.success(wechatUserService.updateBindingStatus(userId, bindingId, body, operator));
    }

    @PostMapping("/wechat-bindings/{userId}/{bindingId}/unbind")
    public Result<WechatBindingVO> unbindWechat(
            @PathVariable Long userId, @PathVariable Long bindingId,
            @RequestBody WechatUnbindRequest body, HttpServletRequest request) {
        SysUser operator = current(request);
        permissionService.requirePlatformPermission(operator, SystemPermissionCodes.WECHAT_MANAGE);
        return Result.success(wechatUserService.unbind(userId, bindingId, body, operator));
    }

    private SysUser current(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        String token = bearer != null && bearer.startsWith("Bearer ") ? bearer.substring(7).trim() : null;
        return authService.getCurrentUser(token);
    }
}
