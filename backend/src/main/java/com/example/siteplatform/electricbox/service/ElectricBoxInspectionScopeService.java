package com.example.siteplatform.electricbox.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.electricbox.dto.ElectricBoxScopeRequest;
import com.example.siteplatform.electricbox.entity.ElectricBox;
import com.example.siteplatform.electricbox.entity.ElectricBoxInspectionScope;
import com.example.siteplatform.electricbox.mapper.ElectricBoxInspectionScopeMapper;
import com.example.siteplatform.electricbox.mapper.ElectricBoxMapper;
import com.example.siteplatform.electricbox.vo.ElectricBoxScopeVO;
import com.example.siteplatform.project.constant.InspectionPermissionCodes;
import com.example.siteplatform.project.service.ProjectPermissionService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Service
public class ElectricBoxInspectionScopeService {

    private final ElectricBoxInspectionScopeMapper scopeMapper;
    private final ElectricBoxMapper electricBoxMapper;
    private final ProjectPermissionService permissionService;

    public ElectricBoxInspectionScopeService(ElectricBoxInspectionScopeMapper scopeMapper,
                                             ElectricBoxMapper electricBoxMapper,
                                             ProjectPermissionService permissionService) {
        this.scopeMapper = scopeMapper;
        this.electricBoxMapper = electricBoxMapper;
        this.permissionService = permissionService;
    }

    public ElectricBoxScopeVO getCurrent(Long boxId, SysUser currentUser) {
        ElectricBox box = requireBox(boxId);
        permissionService.checkProjectPermission(currentUser.getId(), box.getProjectId());
        ElectricBoxInspectionScope latest = latestAt(boxId, LocalDate.now());
        return toVO(box, latest, LocalDate.now());
    }

    public ElectricBoxScopeVO getCurrentForBox(ElectricBox box) {
        LocalDate today = LocalDate.now();
        return toVO(box, latestAt(box.getId(), today), today);
    }

    @Transactional
    public ElectricBoxScopeVO update(Long boxId, ElectricBoxScopeRequest request, SysUser currentUser) {
        ElectricBox box = requireBox(boxId);
        if (!permissionService.hasInspectionPermission(currentUser.getId(), box.getProjectId(), InspectionPermissionCodes.BOX_MANAGE)) {
            throw BusinessException.forbidden("无电箱巡检范围管理权限");
        }
        if (request == null || request.getIncluded() == null) {
            throw new BusinessException("是否纳入日检不能为空");
        }
        LocalDate effectiveDate = request.getEffectiveDate() == null ? LocalDate.now() : request.getEffectiveDate();
        if (request.getEndDate() != null && request.getEndDate().isBefore(effectiveDate)) {
            throw new BusinessException("结束日期不能早于生效日期");
        }

        List<ElectricBoxInspectionScope> openRecords = scopeMapper.selectList(
                new LambdaQueryWrapper<ElectricBoxInspectionScope>()
                        .eq(ElectricBoxInspectionScope::getElectricBoxId, boxId)
                        .isNull(ElectricBoxInspectionScope::getEndDate)
                        .le(ElectricBoxInspectionScope::getEffectiveDate, effectiveDate)
                        .orderByDesc(ElectricBoxInspectionScope::getEffectiveDate));
        for (ElectricBoxInspectionScope open : openRecords) {
            LocalDate end = effectiveDate.minusDays(1);
            open.setEndDate(end.isBefore(open.getEffectiveDate()) ? open.getEffectiveDate() : end);
            scopeMapper.updateById(open);
        }

        ElectricBoxInspectionScope scope = new ElectricBoxInspectionScope();
        scope.setProjectId(box.getProjectId());
        scope.setElectricBoxId(boxId);
        scope.setIncluded(Boolean.TRUE.equals(request.getIncluded()) ? 1 : 0);
        scope.setEffectiveDate(effectiveDate);
        scope.setEndDate(request.getEndDate());
        scope.setReason(StringUtils.hasText(request.getReason()) ? request.getReason().trim() : null);
        scope.setOperatorId(currentUser.getId());
        scope.setOperatorName(displayName(currentUser));
        scopeMapper.insert(scope);
        return toVO(box, scope, LocalDate.now());
    }

    public boolean isRequired(ElectricBox box, LocalDate date) {
        if (box == null || date == null || !"ACTIVE".equals(box.getStatus())) {
            return false;
        }
        ElectricBoxInspectionScope latest = latestAt(box.getId(), date);
        if (latest == null) {
            return true;
        }
        if (latest.getEndDate() != null && latest.getEndDate().isBefore(date)) {
            return false;
        }
        return Integer.valueOf(1).equals(latest.getIncluded());
    }

    public int countRequiredDays(ElectricBox box, YearMonth month) {
        return countRequiredDaysThrough(box, month, month == null ? null : month.atEndOfMonth());
    }

    public int countRequiredDaysThrough(ElectricBox box, YearMonth month, LocalDate cutoffDate) {
        if (box == null || month == null || cutoffDate == null) {
            return 0;
        }
        LocalDate startDate = month.atDay(1);
        LocalDate endDate = cutoffDate.isBefore(month.atEndOfMonth()) ? cutoffDate : month.atEndOfMonth();
        if (endDate.isBefore(startDate)) {
            return 0;
        }
        int count = 0;
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            if (isRequired(box, date)) {
                count++;
            }
        }
        return count;
    }

    private ElectricBoxInspectionScope latestAt(Long boxId, LocalDate date) {
        return scopeMapper.selectOne(new LambdaQueryWrapper<ElectricBoxInspectionScope>()
                .eq(ElectricBoxInspectionScope::getElectricBoxId, boxId)
                .le(ElectricBoxInspectionScope::getEffectiveDate, date)
                .orderByDesc(ElectricBoxInspectionScope::getEffectiveDate)
                .orderByDesc(ElectricBoxInspectionScope::getId)
                .last("LIMIT 1"));
    }

    private ElectricBox requireBox(Long id) {
        ElectricBox box = electricBoxMapper.selectById(id);
        if (box == null) {
            throw BusinessException.notFound("电箱不存在");
        }
        return box;
    }

    private ElectricBoxScopeVO toVO(ElectricBox box, ElectricBoxInspectionScope scope, LocalDate date) {
        ElectricBoxScopeVO vo = new ElectricBoxScopeVO();
        vo.setProjectId(box.getProjectId());
        vo.setElectricBoxId(box.getId());
        vo.setEffectiveToday(isRequired(box, date));
        if (scope == null) {
            vo.setIncluded(true);
            vo.setEffectiveDate(box.getCreateTime() == null ? null : box.getCreateTime().toLocalDate());
            return vo;
        }
        BeanUtils.copyProperties(scope, vo);
        vo.setIncluded(Integer.valueOf(1).equals(scope.getIncluded()));
        return vo;
    }

    private String displayName(SysUser user) {
        return StringUtils.hasText(user.getRealName()) ? user.getRealName() : user.getUsername();
    }
}
