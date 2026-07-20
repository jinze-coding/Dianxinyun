package com.example.siteplatform.person.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.common.Result;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.person.constant.PersonnelStatus;
import com.example.siteplatform.person.dto.PersonCertificateRequest;
import com.example.siteplatform.person.dto.PersonnelMobileSummaryVO;
import com.example.siteplatform.person.dto.PersonMovementRequest;
import com.example.siteplatform.person.entity.PersonEntryExitLog;
import com.example.siteplatform.person.entity.TemporaryPerson;
import com.example.siteplatform.person.mapper.TemporaryPersonMapper;
import com.example.siteplatform.person.service.PersonnelMobileService;
import com.example.siteplatform.person.service.PersonnelWorkflowService;
import com.example.siteplatform.person.vo.PersonCertificateVO;
import com.example.siteplatform.project.entity.ProjectInfo;
import com.example.siteplatform.project.service.ProjectPermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "临时人员管理", description = "临时人员登记、查询、修改、删除接口")
@RestController
@RequestMapping("/api/v1/personnel")
public class PersonnelController {

    @Autowired
    private TemporaryPersonMapper personnelMapper;

    @Autowired
    private AuthService authService;

    @Autowired
    private ProjectPermissionService projectPermissionService;

    @Autowired
    private PersonnelMobileService personnelMobileService;

    @Autowired
    private PersonnelWorkflowService personnelWorkflowService;

    @Operation(summary = "获取人员管理汇总")
    @GetMapping("/summary")
    public Result<PersonnelMobileSummaryVO> getSummary(
            @RequestParam Long projectId,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        return Result.success(personnelMobileService.getSummary(projectId, currentUser));
    }

    @Operation(summary = "获取小程序人员管理汇总")
    @GetMapping("/mini-program/summary")
    public Result<PersonnelMobileSummaryVO> getMobileSummary(
            @RequestParam Long projectId,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        return Result.success(personnelMobileService.getSummary(projectId, currentUser));
    }

    @Operation(summary = "获取人员列表")
    @GetMapping
    public Result<List<TemporaryPerson>> getPersonnelList(
            @Parameter(description = "项目ID") @RequestParam(required = false) Long projectId,
            @Parameter(description = "状态筛选") @RequestParam(required = false) String status,
            @Parameter(description = "搜索关键字") @RequestParam(required = false) String keyword,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);

        LambdaQueryWrapper<TemporaryPerson> wrapper = new LambdaQueryWrapper<>();
        if (projectId != null) {
            projectPermissionService.checkProjectPermission(currentUser.getId(), projectId);
            wrapper.eq(TemporaryPerson::getProjectId, projectId);
        } else if (!projectPermissionService.isPlatformAdmin(currentUser.getId())) {
            List<Long> projectIds = projectPermissionService.getUserProjects(currentUser.getId()).stream()
                    .map(ProjectInfo::getId)
                    .toList();
            if (projectIds.isEmpty()) {
                return Result.success(List.of());
            }
            wrapper.in(TemporaryPerson::getProjectId, projectIds);
        }
        if (status != null && !status.isEmpty() && !"全部".equals(status)) {
            wrapper.eq(TemporaryPerson::getStatus, PersonnelStatus.normalize(status));
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(TemporaryPerson::getName, keyword)
                    .or().like(TemporaryPerson::getIdcard, keyword));
        }
        wrapper.orderByDesc(TemporaryPerson::getCreateTime);

        List<TemporaryPerson> list = personnelMapper.selectList(wrapper);
        if (!projectPermissionService.isPlatformAdmin(currentUser.getId())) {
            list.forEach(person -> {
                if (!projectPermissionService.canManagePersonnel(currentUser.getId(), person.getProjectId())) {
                    person.setIdcard(maskIdcard(person.getIdcard()));
                    person.setPhone(maskPhone(person.getPhone()));
                }
            });
        }
        return Result.success(list);
    }

    @Operation(summary = "获取人员详情")
    @GetMapping("/{id}")
    public Result<TemporaryPerson> getPersonnelById(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);

        TemporaryPerson person = personnelMapper.selectById(id);
        if (person == null) {
            return Result.error("人员不存在");
        }
        projectPermissionService.checkProjectPermission(currentUser.getId(), person.getProjectId());
        if (!projectPermissionService.canManagePersonnel(currentUser.getId(), person.getProjectId())) {
            person.setIdcard(maskIdcard(person.getIdcard()));
            person.setPhone(maskPhone(person.getPhone()));
        }
        return Result.success(person);
    }

    @Operation(summary = "新增人员")
    @PostMapping
    public Result<TemporaryPerson> addPersonnel(
            @RequestBody TemporaryPerson person,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);

        return Result.success(personnelWorkflowService.create(person, currentUser));
    }

    @Operation(summary = "更新人员信息")
    @PutMapping("/{id}")
    public Result<TemporaryPerson> updatePersonnel(
            @PathVariable Long id,
            @RequestBody TemporaryPerson person,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);

        return Result.success(personnelWorkflowService.update(id, person, currentUser));
    }

    @Operation(summary = "删除人员")
    @DeleteMapping("/{id}")
    public Result<Void> deletePersonnel(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);

        personnelWorkflowService.delete(id, currentUser);
        return Result.success();
    }

    @Operation(summary = "批量更新人员状态")
    @PutMapping("/batch/status")
    public Result<Void> batchUpdateStatus(
            @RequestParam List<Long> ids,
            @RequestParam String status,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);

        List<TemporaryPerson> people = personnelMapper.selectList(new LambdaQueryWrapper<TemporaryPerson>()
                .in(TemporaryPerson::getId, ids));
        if (people.size() != ids.size()) {
            throw BusinessException.notFound("部分人员不存在");
        }
        for (TemporaryPerson person : people) {
            if (!projectPermissionService.canManagePersonnel(currentUser.getId(), person.getProjectId())) {
                throw BusinessException.forbidden("无人员管理权限");
            }
        }

        String normalizedStatus = PersonnelStatus.normalize(status);
        if (!PersonnelStatus.EDUCATED.equals(normalizedStatus)) {
            throw new BusinessException("进退场请使用专用接口");
        }
        LambdaUpdateWrapper<TemporaryPerson> wrapper = new LambdaUpdateWrapper<>();
        wrapper.in(TemporaryPerson::getId, ids)
               .set(TemporaryPerson::getStatus, normalizedStatus)
               .set(TemporaryPerson::getUpdateTime, LocalDateTime.now());
        personnelMapper.update(null, wrapper);
        return Result.success();
    }

    @Operation(summary = "办理人员进场")
    @PostMapping("/{id}/entry")
    public Result<PersonEntryExitLog> enter(
            @PathVariable Long id,
            @RequestBody(required = false) PersonMovementRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        return Result.success(personnelWorkflowService.move(id, "ENTRY", request, currentUser));
    }

    @Operation(summary = "办理人员离场")
    @PostMapping("/{id}/exit")
    public Result<PersonEntryExitLog> exit(
            @PathVariable Long id,
            @RequestBody(required = false) PersonMovementRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        return Result.success(personnelWorkflowService.move(id, "EXIT", request, currentUser));
    }

    @Operation(summary = "获取人员进退场流水")
    @GetMapping("/{id}/movements")
    public Result<List<PersonEntryExitLog>> getMovements(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        return Result.success(personnelWorkflowService.listMovements(id, currentUser));
    }

    @Operation(summary = "获取项目人员证件")
    @GetMapping("/certificates")
    public Result<List<PersonCertificateVO>> getCertificates(
            @RequestParam Long projectId,
            @RequestParam(required = false) Long personId,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        return Result.success(personnelWorkflowService.listCertificates(projectId, personId, currentUser));
    }

    @Operation(summary = "新增人员证件")
    @PostMapping("/{id}/certificates")
    public Result<PersonCertificateVO> createCertificate(
            @PathVariable Long id,
            @RequestBody PersonCertificateRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        return Result.success(personnelWorkflowService.createCertificate(id, request, currentUser));
    }

    @Operation(summary = "更新人员证件")
    @PutMapping("/certificates/{certificateId}")
    public Result<PersonCertificateVO> updateCertificate(
            @PathVariable Long certificateId,
            @RequestBody PersonCertificateRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        return Result.success(personnelWorkflowService.updateCertificate(certificateId, request, currentUser));
    }

    @Operation(summary = "删除人员证件")
    @DeleteMapping("/certificates/{certificateId}")
    public Result<Void> deleteCertificate(
            @PathVariable Long certificateId,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        personnelWorkflowService.deleteCertificate(certificateId, currentUser);
        return Result.success();
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private String maskIdcard(String idcard) {
        if (idcard == null || idcard.length() < 8) return idcard;
        return idcard.substring(0, 4) + "**********" + idcard.substring(idcard.length() - 4);
    }
}
