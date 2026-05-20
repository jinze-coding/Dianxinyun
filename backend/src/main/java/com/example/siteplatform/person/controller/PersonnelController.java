package com.example.siteplatform.person.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.common.Result;
import com.example.siteplatform.person.entity.TemporaryPerson;
import com.example.siteplatform.person.mapper.TemporaryPersonMapper;
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

    @Operation(summary = "获取人员列表")
    @GetMapping
    public Result<List<TemporaryPerson>> getPersonnelList(
            @Parameter(description = "项目ID") @RequestParam(required = false) Long projectId,
            @Parameter(description = "状态筛选") @RequestParam(required = false) String status,
            @Parameter(description = "搜索关键字") @RequestParam(required = false) String keyword,
            @RequestHeader(value = "Authorization", required = false) String token) {
        authService.getCurrentUser(token);

        LambdaQueryWrapper<TemporaryPerson> wrapper = new LambdaQueryWrapper<>();
        if (projectId != null) {
            wrapper.eq(TemporaryPerson::getProjectId, projectId);
        }
        if (status != null && !status.isEmpty() && !"全部".equals(status)) {
            wrapper.eq(TemporaryPerson::getStatus, status);
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(TemporaryPerson::getName, keyword)
                    .or().like(TemporaryPerson::getIdcard, keyword));
        }
        wrapper.orderByDesc(TemporaryPerson::getCreateTime);

        List<TemporaryPerson> list = personnelMapper.selectList(wrapper);
        return Result.success(list);
    }

    @Operation(summary = "获取人员详情")
    @GetMapping("/{id}")
    public Result<TemporaryPerson> getPersonnelById(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {
        authService.getCurrentUser(token);

        TemporaryPerson person = personnelMapper.selectById(id);
        if (person == null) {
            return Result.error("人员不存在");
        }
        return Result.success(person);
    }

    @Operation(summary = "新增人员")
    @PostMapping
    public Result<TemporaryPerson> addPersonnel(
            @RequestBody TemporaryPerson person,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);

        person.setCreateTime(LocalDateTime.now());
        person.setUpdateTime(LocalDateTime.now());
        if (person.getStatus() == null) {
            person.setStatus("待教育");
        }
        personnelMapper.insert(person);
        return Result.success(person);
    }

    @Operation(summary = "更新人员信息")
    @PutMapping("/{id}")
    public Result<TemporaryPerson> updatePersonnel(
            @PathVariable Long id,
            @RequestBody TemporaryPerson person,
            @RequestHeader(value = "Authorization", required = false) String token) {
        authService.getCurrentUser(token);

        TemporaryPerson existing = personnelMapper.selectById(id);
        if (existing == null) {
            return Result.error("人员不存在");
        }

        person.setId(id);
        person.setUpdateTime(LocalDateTime.now());
        personnelMapper.updateById(person);
        return Result.success(person);
    }

    @Operation(summary = "删除人员")
    @DeleteMapping("/{id}")
    public Result<Void> deletePersonnel(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {
        authService.getCurrentUser(token);

        personnelMapper.deleteById(id);
        return Result.success();
    }

    @Operation(summary = "批量更新人员状态")
    @PutMapping("/batch/status")
    public Result<Void> batchUpdateStatus(
            @RequestParam List<Long> ids,
            @RequestParam String status,
            @RequestHeader(value = "Authorization", required = false) String token) {
        authService.getCurrentUser(token);

        LambdaUpdateWrapper<TemporaryPerson> wrapper = new LambdaUpdateWrapper<>();
        wrapper.in(TemporaryPerson::getId, ids)
               .set(TemporaryPerson::getStatus, status)
               .set(TemporaryPerson::getUpdateTime, LocalDateTime.now());
        personnelMapper.update(null, wrapper);
        return Result.success();
    }
}
