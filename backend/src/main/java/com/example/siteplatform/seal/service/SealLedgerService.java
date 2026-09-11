package com.example.siteplatform.seal.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.log.entity.OperationLog;
import com.example.siteplatform.log.mapper.OperationLogMapper;
import com.example.siteplatform.seal.entity.SealApplication;
import com.example.siteplatform.seal.entity.SealApplicationItem;
import com.example.siteplatform.seal.mapper.SealApplicationItemMapper;
import com.example.siteplatform.seal.mapper.SealApplicationMapper;
import com.example.siteplatform.system.constant.SystemPermissionCodes;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SealLedgerService {
    private static final int MAX_EXPORT_APPLICATIONS = 10_000;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private final SealApplicationMapper applicationMapper;
    private final SealApplicationItemMapper itemMapper;
    private final OperationLogMapper operationLogMapper;
    private final ProjectPermissionService permissionService;
    private final SealLedgerWordRenderer wordRenderer;

    public SealLedgerService(SealApplicationMapper applicationMapper,
                             SealApplicationItemMapper itemMapper,
                             OperationLogMapper operationLogMapper,
                             ProjectPermissionService permissionService,
                             SealLedgerWordRenderer wordRenderer) {
        this.applicationMapper = applicationMapper;
        this.itemMapper = itemMapper;
        this.operationLogMapper = operationLogMapper;
        this.permissionService = permissionService;
        this.wordRenderer = wordRenderer;
    }

    @Transactional
    public LedgerExport export(Long projectId, String period, LocalDate anchorDate,
                               LocalDate startDate, LocalDate endDate, String keyword, String status,
                               SysUser currentUser, HttpServletRequest request) {
        if (projectId == null) throw new BusinessException("项目不能为空");
        permissionService.checkProjectPermission(currentUser.getId(), projectId);
        permissionService.requireSystemPermission(currentUser.getId(), projectId, SystemPermissionCodes.SEAL_EXPORT);
        LedgerDateRange range = LedgerDateRange.resolve(period, anchorDate, startDate, endDate);
        if (org.springframework.util.StringUtils.hasText(status)
                && !SealApplicationService.APPROVED.equalsIgnoreCase(status.trim())) {
            throw new BusinessException("用印台账仅导出审批通过记录");
        }
        LambdaQueryWrapper<SealApplication> query = new LambdaQueryWrapper<SealApplication>()
                .eq(SealApplication::getProjectId, projectId)
                .eq(SealApplication::getStatus, SealApplicationService.APPROVED)
                .ge(SealApplication::getApprovalTime, range.startDate().atStartOfDay())
                .lt(SealApplication::getApprovalTime, range.endDate().plusDays(1).atStartOfDay());
        if (org.springframework.util.StringUtils.hasText(keyword)) {
            String value = keyword.trim();
            query.and(row -> row.like(SealApplication::getApplicationNo, value)
                    .or().like(SealApplication::getPurpose, value)
                    .or().like(SealApplication::getApplicantName, value)
                    .or().like(SealApplication::getSealName, value));
        }
        query.orderByAsc(SealApplication::getApprovalTime).orderByAsc(SealApplication::getApplicationNo)
                .orderByAsc(SealApplication::getId).last("LIMIT " + (MAX_EXPORT_APPLICATIONS + 1));
        List<SealApplication> applications = applicationMapper.selectList(query);
        if (applications.size() > MAX_EXPORT_APPLICATIONS) throw new BusinessException("导出记录超过10000条，请缩小日期范围");
        List<Long> applicationIds = applications.stream().map(SealApplication::getId).toList();
        Map<Long, List<SealApplicationItem>> itemsByApplication = groupItems(applicationIds);
        byte[] bytes = wordRenderer.render(applications, itemsByApplication);
        writeExportAudit(projectId, range, applications.size(), currentUser, request);
        String fileName = "用印台账_" + range.startDate() + "_" + range.endDate() + ".docx";
        return new LedgerExport(fileName, bytes, range);
    }

    private Map<Long, List<SealApplicationItem>> groupItems(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return itemMapper.selectList(new LambdaQueryWrapper<SealApplicationItem>()
                        .in(SealApplicationItem::getApplicationId, ids)
                        .orderByAsc(SealApplicationItem::getApplicationId)
                        .orderByAsc(SealApplicationItem::getSortOrder).orderByAsc(SealApplicationItem::getId))
                .stream().collect(Collectors.groupingBy(SealApplicationItem::getApplicationId,
                        java.util.LinkedHashMap::new, Collectors.toList()));
    }

    private void writeExportAudit(Long projectId, LedgerDateRange range, int count,
                                  SysUser user, HttpServletRequest request) {
        OperationLog log = new OperationLog();
        log.setUserId(user.getId());
        log.setUsername(org.springframework.util.StringUtils.hasText(user.getRealName())
                ? user.getRealName() : user.getUsername());
        log.setOperationType("EXPORT_SEAL_LEDGER");
        log.setOperationDesc("导出审批通过日期 " + range.startDate() + " 至 " + range.endDate()
                + " 的项目用印台账，共 " + count + " 条申请");
        log.setBusinessType("SEAL_LEDGER");
        log.setBusinessId(projectId);
        log.setIpAddress(request == null ? null : request.getRemoteAddr());
        log.setCreateTime(LocalDateTime.now(BUSINESS_ZONE));
        if (operationLogMapper.insert(log) != 1) throw BusinessException.of(409, "用印台账导出审计未写入");
    }

    public record LedgerExport(String fileName, byte[] content, LedgerDateRange range) { }
}
