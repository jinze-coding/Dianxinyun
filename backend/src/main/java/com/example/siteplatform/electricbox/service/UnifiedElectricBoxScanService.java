package com.example.siteplatform.electricbox.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.electricbox.entity.ElectricBox;
import com.example.siteplatform.electricbox.mapper.ElectricBoxMapper;
import com.example.siteplatform.electricbox.vo.UnifiedElectricBoxScanVO;
import com.example.siteplatform.inspection.entity.InspectionRecord;
import com.example.siteplatform.inspection.mapper.InspectionRecordMapper;
import com.example.siteplatform.project.constant.InspectionPermissionCodes;
import com.example.siteplatform.project.service.ProjectPermissionService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class UnifiedElectricBoxScanService {

    private final ElectricBoxMapper electricBoxMapper;
    private final InspectionRecordMapper inspectionRecordMapper;
    private final ProjectPermissionService permissionService;
    private final ElectricBoxInspectionScopeService scopeService;

    public UnifiedElectricBoxScanService(ElectricBoxMapper electricBoxMapper,
                                         InspectionRecordMapper inspectionRecordMapper,
                                         ProjectPermissionService permissionService,
                                         ElectricBoxInspectionScopeService scopeService) {
        this.electricBoxMapper = electricBoxMapper;
        this.inspectionRecordMapper = inspectionRecordMapper;
        this.permissionService = permissionService;
        this.scopeService = scopeService;
    }

    public UnifiedElectricBoxScanVO resolve(String rawSceneCode, SysUser currentUser) {
        String sceneCode = normalizeScene(rawSceneCode);
        String publicCode = sceneCode.substring(2);
        ElectricBox box = electricBoxMapper.selectOne(new LambdaQueryWrapper<ElectricBox>()
                .eq(ElectricBox::getPublicCode, publicCode)
                .last("LIMIT 1"));
        if (box == null) {
            throw BusinessException.notFound("巡检码无效或已换码");
        }

        boolean authenticated = currentUser != null;
        boolean projectAuthorized = authenticated
                && permissionService.getInspectionPermissionCodes(currentUser.getId(), box.getProjectId()).size() > 0;
        boolean publicAccessEnabled = Integer.valueOf(1).equals(box.getPublicAccessEnabled());
        if (!projectAuthorized && !publicAccessEnabled) {
            throw BusinessException.forbidden("该电箱公开扫码访问已停用");
        }
        boolean writable = "ACTIVE".equals(box.getStatus());
        boolean inScope = scopeService.isRequired(box, LocalDate.now());
        InspectionRecord todayRecord = projectAuthorized ? findTodayRecord(box) : null;
        List<String> actions = new ArrayList<>();
        String directAction = "UNAVAILABLE";
        if (projectAuthorized) {
            boolean canSubmitDaily = permissionService.hasInspectionPermission(currentUser.getId(), box.getProjectId(),
                    InspectionPermissionCodes.INSPECTION_DAILY_SUBMIT);
            boolean canViewRecords = permissionService.hasAnyInspectionPermission(currentUser.getId(), box.getProjectId(),
                    InspectionPermissionCodes.BOX_VIEW,
                    InspectionPermissionCodes.INSPECTION_RECORD_VIEW,
                    InspectionPermissionCodes.SUMMARY_VIEW);
            if (canSubmitDaily && writable && inScope) {
                if (todayRecord == null) {
                    actions.add("DAILY_INSPECTION");
                } else {
                    actions.add("VIEW_COMPLETED_RECORD");
                }
                directAction = "START_INSPECTION";
            }
            if (canViewRecords) {
                actions.add("VIEW_RECORDS");
                if ("UNAVAILABLE".equals(directAction)) {
                    directAction = "VIEW_RECORDS";
                }
            }
        }
        if (publicAccessEnabled) {
            actions.add("VIEW_PUBLIC_MONTHLY");
            if ("UNAVAILABLE".equals(directAction)) {
                directAction = "VIEW_PUBLIC_MONTHLY";
            }
        }

        UnifiedElectricBoxScanVO vo = new UnifiedElectricBoxScanVO();
        vo.setSceneCode(sceneCode);
        vo.setMode(!actions.isEmpty() && projectAuthorized ? "INTERNAL" :
                publicAccessEnabled ? "PUBLIC_READ_ONLY" : "UNAVAILABLE");
        vo.setReason(resolveReason(box, authenticated, projectAuthorized, inScope));
        vo.setElectricBoxId(projectAuthorized ? box.getId() : null);
        vo.setProjectId(projectAuthorized ? box.getProjectId() : null);
        vo.setPublicCode(box.getPublicCode());
        vo.setBoxCode(box.getBoxCode());
        vo.setBoxName(box.getBoxName());
        vo.setInstallLocation(box.getInstallLocation());
        vo.setStatus(box.getStatus());
        vo.setPublicAccessEnabled(publicAccessEnabled);
        vo.setInspectionRequired(inScope);
        vo.setAuthenticated(authenticated);
        vo.setProjectAuthorized(projectAuthorized);
        vo.setDirectAction(directAction);
        vo.setTodayRecordId(projectAuthorized && todayRecord != null ? todayRecord.getId() : null);
        vo.setAllowedActions(List.copyOf(actions));
        return vo;
    }

    private InspectionRecord findTodayRecord(ElectricBox box) {
        return inspectionRecordMapper.selectOne(new LambdaQueryWrapper<InspectionRecord>()
                .eq(InspectionRecord::getProjectId, box.getProjectId())
                .eq(InspectionRecord::getElectricBoxId, box.getId())
                .eq(InspectionRecord::getSource, "ELECTRICIAN_DAILY")
                .eq(InspectionRecord::getCheckDate, LocalDate.now())
                .ne(InspectionRecord::getStatus, "DRAFT")
                .orderByDesc(InspectionRecord::getId)
                .last("LIMIT 1"));
    }

    private String normalizeScene(String raw) {
        if (!StringUtils.hasText(raw)) throw new BusinessException("巡检场景码不能为空");
        String value = raw.trim();
        if (!value.startsWith("B:")) value = "B:" + value;
        if (value.length() <= 2) throw new BusinessException("巡检场景码格式错误");
        return value;
    }

    private String resolveReason(ElectricBox box, boolean authenticated, boolean projectAuthorized, boolean inScope) {
        if (!"ACTIVE".equals(box.getStatus())) return "电箱已停用或拆除，禁止写操作";
        if (!authenticated) return "未登录，仅可查看公开月表";
        if (!projectAuthorized) return "当前账号无该项目权限，仅可查看公开月表并申请权限";
        if (!inScope) return "该电箱当前未纳入日检，可查看历史记录";
        return "已按账号、项目权限和设备负责关系完成分流";
    }
}
